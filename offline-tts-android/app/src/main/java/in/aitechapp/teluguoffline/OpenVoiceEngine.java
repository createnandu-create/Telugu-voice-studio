package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenVoice V2 tone conversion. Uses its own 256-D embedding, never ECAPA's unrelated 192-D vector.
 */
public final class OpenVoiceEngine implements AutoCloseable {
  private final OrtEnvironment env = OrtEnvironment.getEnvironment();
  private OrtSession encoder, converter;
  private final AtomicBoolean cancel;

  public OpenVoiceEngine(File root, AtomicBoolean cancel) throws OrtException {
    this.cancel = cancel;
    try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
      opts.setIntraOpNumThreads(2);
      opts.setInterOpNumThreads(1);
      opts.setMemoryPatternOptimization(false);
      try {
        encoder = env.createSession(new File(root, "tone_ref_encoder_q8.onnx").getPath(), opts);
        converter = env.createSession(new File(root, "tone_converter_q8.onnx").getPath(), opts);
      } catch (OrtException e) {
        close();
        throw e;
      }
    }
  }

  public static float[] read(File file) throws IOException {
    int n = (int) (PcmWave.validate(file) / 2);
    if (n > 24000 * 65) throw new IOException("Split voice conversion into short chunks");
    float[] x = new float[n];
    try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
      f.seek(44);
      for (int i = 0; i < n; i++) x[i] = Short.reverseBytes(f.readShort()) / 32768f;
    }
    return x;
  }

  public static float[][] spectrum(float[] original, AtomicBoolean cancel) {
    if (original.length < 1024) throw new IllegalArgumentException("Voice sample is too short");
    int frames = (original.length + 255) / 256, length = frames * 256;
    float[][] out = new float[frames][513];
    for (int t = 0; t < frames; t++) {
      OfflineDenoiser.check(cancel);
      double[] re = new double[1024], im = new double[1024];
      for (int j = 0; j < 1024; j++) {
        int p = t * 256 + j - 384;
        if (p < 0) p = -p;
        if (p >= length) p = 2 * length - 2 - p;
        float x = p < original.length ? original[p] : 0;
        re[j] = x * (.5 - .5 * Math.cos(2 * Math.PI * j / 1024));
      }
      LateReverb.fft(re, im, false);
      for (int f = 0; f < 513; f++)
        out[t][f] = (float) Math.sqrt(re[f] * re[f] + im[f] * im[f] + 1e-6);
    }
    return out;
  }

  private OrtSession.Result run(OrtSession session, Map<String, OnnxTensor> inputs)
      throws Exception {
    OfflineDenoiser.check(cancel);
    try (OrtSession.RunOptions options = new OrtSession.RunOptions()) {
      AtomicBoolean finished = new AtomicBoolean();
      Thread watcher =
          new Thread(
              () -> {
                while (!finished.get()) {
                  if (cancel.get()) {
                    try {
                      options.setTerminate(true);
                    } catch (OrtException ignored) {
                    }
                    return;
                  }
                  try {
                    Thread.sleep(50);
                  } catch (InterruptedException e) {
                    return;
                  }
                }
              },
              "openvoice-cancel");
      watcher.setDaemon(true);
      watcher.start();
      try {
        return session.run(inputs, options);
      } finally {
        finished.set(true);
        watcher.interrupt();
        watcher.join();
      }
    }
  }

  public float[] embedding(float[] audio, int rate) throws Exception {
    float[] x = OfflineDenoiser.resample(audio, rate, 22050, cancel);
    if (x.length > 22050 * 15) x = Arrays.copyOf(x, 22050 * 15);
    try (OnnxTensor spec = OnnxTensor.createTensor(env, new float[][][] {spectrum(x, cancel)});
        OrtSession.Result result = run(encoder, Map.of("spec", spec))) {
      float[] v = ((float[][]) result.get(0).getValue())[0].clone();
      validateEmbedding(v);
      return v;
    }
  }

  static void validateEmbedding(float[] vector) throws IOException {
    if (vector.length != 256)
      throw new IOException("OpenVoice requires its own 256-D tone embedding");
    double power = 0;
    for (float v : vector) {
      if (!Float.isFinite(v)) throw new IOException("Invalid tone embedding");
      power += v * v;
    }
    if (power < 1e-10) throw new IOException("Empty tone embedding");
  }

  public float[] convert(float[] audio, int rate, float[] source, float[] target) throws Exception {
    validateEmbedding(source);
    validateEmbedding(target);
    float[] x = OfflineDenoiser.resample(audio, rate, 22050, cancel);
    float[][] rows = spectrum(x, cancel);
    float[][][] channel = new float[1][513][rows.length];
    for (int t = 0; t < rows.length; t++)
      for (int f = 0; f < 513; f++) channel[0][f][t] = rows[t][f];
    float[][][] src = new float[1][256][1], dst = new float[1][256][1];
    for (int i = 0; i < 256; i++) {
      src[0][i][0] = source[i];
      dst[0][i][0] = target[i];
    }
    try (OnnxTensor spec = OnnxTensor.createTensor(env, channel);
        OnnxTensor lens = OnnxTensor.createTensor(env, new long[] {rows.length});
        OnnxTensor s = OnnxTensor.createTensor(env, src);
        OnnxTensor d = OnnxTensor.createTensor(env, dst);
        OrtSession.Result result =
            run(converter, Map.of("spec", spec, "spec_lengths", lens, "src_g", s, "tgt_g", d))) {
      OfflineDenoiser.check(cancel);
      float[] out = ((float[][][]) result.get(0).getValue())[0][0];
      if (out.length < x.length) throw new IOException("Converter returned truncated audio");
      out = Arrays.copyOf(out, x.length);
      double peak = 0;
      for (float v : out) {
        if (!Float.isFinite(v)) throw new IOException("Invalid converted PCM");
        peak = Math.max(peak, Math.abs(v));
      }
      if (peak < 1e-7) throw new IOException("Converter returned silence");
      if (peak > .97) for (int i = 0; i < out.length; i++) out[i] *= .97 / peak;
      return out;
    }
  }

  public static void saveEmbedding(File file, float[] vector) throws IOException {
    validateEmbedding(vector);
    ByteBuffer b = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
    for (float v : vector) b.putFloat(v);
    Files.write(file.toPath(), b.array());
  }

  public static float[] loadEmbedding(File file) throws IOException {
    byte[] bytes = Files.readAllBytes(file.toPath());
    if (bytes.length != 1024) throw new IOException("Tone embedding is damaged");
    FloatBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    float[] v = new float[256];
    b.get(v);
    validateEmbedding(v);
    return v;
  }

  public void close() {
    for (OrtSession session : new OrtSession[] {encoder, converter})
      if (session != null)
        try {
          session.close();
        } catch (OrtException ignored) {
        }
    encoder = converter = null;
  }
}
