package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Indic Tiny Telugu r55cfc68: character encoder, 8-step flow, Vocos + Hann ISTFT. Fixed pretrained
 * speakers, not a clone. No network, no silent unknown-character substitution. Port of upstream
 * indic_tiny_tts/export/onnx_infer.py (MIT).
 */
public final class TinyVoice implements AutoCloseable {
  private static final String SYMBOLS =
      " !\"',.?।ఁంఃఅఆఇఈఉఊఋఎఏఐఒఓఔకఖగఘఙచఛజఝఞటఠడఢణతథదధనపఫబభమయరఱలళవశషసహాిీుూృౄెేైొోౌ్ౖౙౠ";
  private final OrtEnvironment env = LocalOnnx.environment();
  private OrtSession encoder, flow, vocoder;
  private final AtomicBoolean cancel;

  public TinyVoice(File root, AtomicBoolean cancel) throws OrtException {
    this.cancel = cancel;
    try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
      options.setIntraOpNumThreads(2);
      options.setInterOpNumThreads(1);
      options.setMemoryPatternOptimization(false);
      try {
        encoder = env.createSession(new File(root, "text_encoder.onnx").getPath(), options);
        flow = env.createSession(new File(root, "flow_decoder.onnx").getPath(), options);
        vocoder = env.createSession(new File(root, "vocoder.onnx").getPath(), options);
      } catch (OrtException e) {
        close();
        throw e;
      }
    }
  }

  private void check() {
    if (cancel.get()) throw new CancellationException();
  }

  static String normalized(String original) {
    return PronunciationLexicon.apply(original, Collections.emptyMap(), false)
        .replace("\u200C", "")
        .replace("\u200D", "")
        .replaceAll("\\s+", " ")
        .trim()
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace('…', '.');
  }

  public static long[] tokens(String original) {
    String text = normalized(original);
    if (text.isEmpty() || text.length() > 400)
      throw new IllegalArgumentException(
          "Indic Tiny needs sentences of 1–400 characters. Split long sentences with punctuation.");
    long[] out = new long[text.length() * 2 + 1];
    for (int i = 0; i < text.length(); i++) {
      int id = SYMBOLS.indexOf(text.charAt(i));
      if (id < 0)
        throw new IllegalArgumentException(
            "Indic Tiny cannot read '"
                + text.charAt(i)
                + "' yet. Use Telugu text or add an explicit spoken form in Settings →"
                + " Pronunciation dictionary. Original script is kept.");
      out[2 * i + 1] = id + 2;
    }
    return out;
  }

  private OnnxTensor ints(long[] data, long... shape) throws OrtException {
    return OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape);
  }

  private OnnxTensor floats(float[] data, long... shape) throws OrtException {
    return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
  }

  private static float[] data(OrtSession.Result r, String name) throws OrtException {
    FloatBuffer f = ((OnnxTensor) r.get(name).orElseThrow()).getFloatBuffer();
    float[] a = new float[f.remaining()];
    f.get(a);
    return a;
  }

  /** Noise seed is deterministic per segment for stable resume. Zero-noise mode is test-only. */
  public float[] generate(String text, boolean male, float speed, long seed, boolean zeroNoise)
      throws Exception {
    return generate(text, male, speed, seed, zeroNoise, true);
  }

  public float[] generate(
      String text, boolean male, float speed, long seed, boolean zeroNoise, boolean guard)
      throws Exception {
    float[] mel = acousticMel(text, male, speed, seed, zeroNoise);
    int length = mel.length / 100;
    restoreMelContrast(mel, length, male);
    return vocode(mel);
  }

  float[] vocode(float[] mel) throws OrtException {
    try (OnnxTensor m = floats(mel, 1, 100, mel.length / 100);
        OrtSession.Result r = vocoder.run(Map.of("mel", m))) {
      float[] re = data(r, "real"), im = data(r, "imag");
      check();
      return istft(re, im);
    }
  }

  // Exposed to the host evaluation so the native acoustic model can be compared with studio speech.
  float[] acousticMel(String text, boolean male, float speed, long seed, boolean zeroNoise) throws Exception {
    check();
    if (!Float.isFinite(speed) || speed < .5f || speed > 2f)
      throw new IllegalArgumentException("Speaking speed must be between 0.5 and 2.");
    long[] tok = tokens(text);
    int n = tok.length;
    float[] muX, logw, cond;
    try (OnnxTensor t = ints(tok, 1, n);
        OnnxTensor len = ints(new long[] {n}, 1);
        OnnxTensor lang = ints(new long[] {6}, 1);
        OnnxTensor speaker = ints(new long[] {male ? 13 : 12}, 1);
        OnnxTensor style = ints(new long[] {0}, 1);
        OrtSession.Result r =
            encoder.run(
                Map.of(
                    "tokens",
                    t,
                    "token_lengths",
                    len,
                    "language",
                    lang,
                    "speaker",
                    speaker,
                    "style",
                    style))) {
      muX = data(r, "mu_x");
      logw = data(r, "logw");
      cond = data(r, "cond");
    }
    check();
    float scale = 1.04f / speed;
    float[] cumulative = new float[n];
    float[] duration = new float[n];
    for (int i = 0; i < n; i++) duration[i] = (float) Math.ceil((float) Math.exp(logw[i])) * scale;
    double total = 0;
    for (int i = 0; i < n; i++) {
      total += duration[i];
      cumulative[i] = (float) total;
    }
    int length = Math.max(1, (int) (float) total), padded = (length + 1) / 2 * 2;
    if (length > 6000)
      throw new IOException(
          "Sentence is too long for safe mobile rendering. Split it into shorter sentences.");
    float[] mu = new float[100 * padded], mask = new float[padded], x = new float[100 * padded];
    int token = 0;
    for (int j = 0; j < length; j++) {
      while (token < n - 1 && j >= cumulative[token]) token++;
      mask[j] = 1;
      for (int c = 0; c < 100; c++) mu[c * padded + j] = muX[c * n + token];
    }
    Random random = new Random(seed);
    float temperature = male ? .50f : .35f;
    for (int i = 0; i < x.length; i++) x[i] = zeroNoise ? 0 : (float) random.nextGaussian() * temperature;
    try (OnnxTensor m = floats(mask, 1, 1, padded);
        OnnxTensor u = floats(mu, 1, 100, padded);
        OnnxTensor co = floats(cond, 1, 112)) {
      for (int step = 0; step < 8; step++) {
        check();
        try (OnnxTensor xt = floats(x, 1, 100, padded);
            OnnxTensor time = floats(new float[] {step * .125f}, 1);
            OrtSession.Result r =
                flow.run(Map.of("x", xt, "mask", m, "mu", u, "t", time, "cond", co))) {
          float[] v = data(r, "v");
          for (int i = 0; i < x.length; i++) x[i] += .125f * v[i];
        }
      }
    }
    check();
    float[] mel = new float[100 * length];
    for (int c = 0; c < 100; c++)
      for (int j = 0; j < length; j++)
        mel[c * length + j] = x[c * padded + j] * 2.3824615950834365f - 2.425986101096874f;
    return mel;
  }

  // Calibrated on 36 matched studio utterances per speaker. Work within one mel frame at
  // a time: no temporal smoothing, waveform pitch shifting, or syllable retiming.
  private static final float[] FEMALE_CONTRAST = {.009f,-.007f,.050f,.127f,.255f,.304f,.346f,.335f,.450f,.290f};
  private static final float[] MALE_CONTRAST = {.009f,.142f,.331f,.360f,.385f,.202f,.450f,.202f,.450f,.389f};
  private static final double[] KERNEL = contrastKernel();

  private static double[] contrastKernel() {
    double[] weights = new double[25];
    double sum = 0;
    for (int j = -12; j <= 12; j++) { weights[j+12] = Math.exp(-j*j/18.0); sum += weights[j+12]; }
    for (int j = 0; j < weights.length; j++) weights[j] /= sum;
    return weights;
  }

  static void restoreMelContrast(float[] mel, int frames, boolean male) {
    float[] strengths = male ? MALE_CONTRAST : FEMALE_CONTRAST;
    float[] band = new float[100];
    for (int t = 0; t < frames; t++) {
      for (int c = 0; c < 100; c++) band[c] = mel[c*frames+t];
      for (int c = 0; c < 100; c++) {
        double position = Math.max(0,Math.min(9,(c-4.5)/10));
        int left = (int) position;
        double blend = position-left;
        double strength = strengths[left]*(1-blend) + strengths[Math.min(9,left+1)]*blend;
        double envelope = 0;
        for (int j = -12; j <= 12; j++)
          envelope += KERNEL[j+12] * band[Math.max(0,Math.min(99,c+j))];
        mel[c*frames+t] += (float)Math.max(-.85,Math.min(.85,strength*(band[c]-envelope)));
      }
    }
  }

  /** Periodic Hann, hop 256, centered 1024-point inverse real FFT, envelope normalization. */
  static float[] istft(float[] real, float[] imag) {
    int frames = real.length / 513, size = 1024 + (frames - 1) * 256;
    double[] audio = new double[size], envelope = new double[size], window = new double[1024];
    for (int i = 0; i < 1024; i++) window[i] = .5 - .5 * Math.cos(2 * Math.PI * i / 1024);
    double[] re = new double[1024], im = new double[1024];
    for (int frame = 0; frame < frames; frame++) {
      for (int k = 0; k <= 512; k++) {
        re[k] = real[k * frames + frame];
        im[k] = imag[k * frames + frame];
      }
      for (int k = 513; k < 1024; k++) {
        re[k] = re[1024 - k];
        im[k] = -im[1024 - k];
      }
      for (int i = 1, j = 0; i < 1024; i++) {
        int bit = 512;
        for (; (j & bit) != 0; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
          double z = re[i];
          re[i] = re[j];
          re[j] = z;
          z = im[i];
          im[i] = im[j];
          im[j] = z;
        }
      }
      for (int len = 2; len <= 1024; len *= 2) {
        double angle = 2 * Math.PI / len;
        for (int base = 0; base < 1024; base += len)
          for (int k = 0; k < len / 2; k++) {
            double wr = Math.cos(angle * k), wi = Math.sin(angle * k);
            int a = base + k, b = a + len / 2;
            double r = re[b] * wr - im[b] * wi, s = re[b] * wi + im[b] * wr;
            re[b] = re[a] - r;
            im[b] = im[a] - s;
            re[a] += r;
            im[a] += s;
          }
      }
      for (int i = 0; i < 1024; i++) {
        int at = frame * 256 + i;
        audio[at] += re[i] / 1024 * window[i];
        envelope[at] += window[i] * window[i];
      }
    }
    float[] out = new float[size - 1024];
    for (int i = 0; i < out.length; i++)
      out[i] = (float) (audio[i + 512] / Math.max(1e-11, envelope[i + 512]));
    return out;
  }

  @Override
  public void close() throws OrtException {
    OrtException error = null;
    for (OrtSession s : new OrtSession[] {encoder, flow, vocoder})
      if (s != null)
        try {
          s.close();
        } catch (OrtException e) {
          error = e;
        }
    encoder = flow = vocoder = null;
    if (error != null) throw error;
  }
}
