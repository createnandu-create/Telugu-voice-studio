package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Indic Tiny Telugu r55cfc68: character encoder, 4-step flow, Vocos + Hann ISTFT. Fixed pretrained
 * speakers, not a clone. No network, no silent unknown-character substitution. Port of upstream
 * indic_tiny_tts/export/onnx_infer.py (MIT).
 */
public final class TinyVoice implements AutoCloseable {
  private static final String SYMBOLS =
      " !\"',.?।ఁంఃఅఆఇఈఉఊఋఎఏఐఒఓఔకఖగఘఙచఛజఝఞటఠడఢణతథదధనపఫబభమయరఱలళవశషసహాిీుూృౄెేైొోౌ్ౖౙౠ";
  private final OrtEnvironment env = OrtEnvironment.getEnvironment();
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

  public static long[] tokens(String original) {
    String text =
        PronunciationLexicon.apply(original, Collections.emptyMap(), false)
            .replaceAll("\\s+", " ")
            .trim()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('…', '.');
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
    float scale = (male ? 1.0022f : 0.9942f) / speed;
    float[] cumulative = new float[n];
    double total = 0;
    for (int i = 0; i < n; i++) {
      total += (float) Math.ceil((float) Math.exp(logw[i])) * scale;
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
    for (int i = 0; i < x.length; i++) x[i] = zeroNoise ? 0 : (float) random.nextGaussian() * .667f;
    try (OnnxTensor m = floats(mask, 1, 1, padded);
        OnnxTensor u = floats(mu, 1, 100, padded);
        OnnxTensor co = floats(cond, 1, 112)) {
      for (int step = 0; step < 4; step++) {
        check();
        try (OnnxTensor xt = floats(x, 1, 100, padded);
            OnnxTensor time = floats(new float[] {step * .25f}, 1);
            OrtSession.Result r =
                flow.run(Map.of("x", xt, "mask", m, "mu", u, "t", time, "cond", co))) {
          float[] v = data(r, "v");
          for (int i = 0; i < x.length; i++) x[i] += .25f * v[i];
        }
      }
    }
    check();
    float[] mel = new float[100 * length];
    for (int c = 0; c < 100; c++)
      for (int j = 0; j < length; j++)
        mel[c * length + j] = x[c * padded + j] * 2.3824615950834365f - 2.425986101096874f;
    try (OnnxTensor m = floats(mel, 1, 100, length);
        OrtSession.Result r = vocoder.run(Map.of("mel", m))) {
      float[] re = data(r, "real"), im = data(r, "imag");
      check();
      return istft(re, im);
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
