package in.aitechapp.teluguoffline;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bundled RNNoise 0.1.1. Noise reduction, not speaker cloning or lost-speech reconstruction. */
public final class OfflineDenoiser {
  public static final String VERSION = "rnnoise-0.1.1-hp85-v1";

  static {
    System.loadLibrary("rnnoise_offline");
  }

  private static native long create();

  private static native void destroy(long handle);

  private static native float frame(long handle, float[] pcm480);

  public interface Progress {
    void update(int percent);
  }

  public static void check(AtomicBoolean cancel) {
    if (cancel.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
  }

  /**
   * Windowed sinc with cutoff adjusted for downsampling; normalized polyphase coefficient table.
   */
  public static float[] resample(float[] input, int from, int to, AtomicBoolean cancel) {
    if (from < 8000 || to < 8000 || from > 96000 || to > 96000)
      throw new IllegalArgumentException("Unsupported sample rate");
    if (from == to) return input.clone();
    int phases = to / gcd(from, to), radius = 32;
    double cutoff = .94 * Math.min(1.0, (double) to / from);
    double[][] weights = new double[phases][radius * 2];
    for (int p = 0; p < phases; p++) {
      double sum = 0, frac = (double) p / phases;
      for (int j = 0; j < radius * 2; j++) {
        double d = j - radius + 1 - frac;
        double sinc = Math.abs(d) < 1e-10 ? cutoff : Math.sin(Math.PI * cutoff * d) / (Math.PI * d);
        double window =
            .42 + .5 * Math.cos(Math.PI * d / radius) + .08 * Math.cos(2 * Math.PI * d / radius);
        weights[p][j] = sinc * window;
        sum += weights[p][j];
      }
      for (int j = 0; j < radius * 2; j++) weights[p][j] /= sum;
    }
    float[] out = new float[(int) Math.round(input.length * (double) to / from)];
    for (int i = 0; i < out.length; i++) {
      if ((i & 4095) == 0) check(cancel);
      long position = (long) i * from;
      int base = (int) (position / to), phase = (int) ((position % to) * phases / to);
      double sum = 0;
      for (int j = 0; j < radius * 2; j++) {
        int k = base + j - radius + 1;
        if (k >= 0 && k < input.length) sum += input[k] * weights[phase][j];
      }
      out[i] = (float) sum;
    }
    return out;
  }

  private static int gcd(int a, int b) {
    while (b != 0) {
      int t = a % b;
      a = b;
      b = t;
    }
    return a;
  }

  /** Fourth-order Butterworth, two distinct second-order Q values (not two Q=.707 filters). */
  static void highPass(float[] x, int rate) {
    for (double q : new double[] {.541196100146197, 1.306562964876377}) {
      double w = 2 * Math.PI * 85 / rate,
          c = Math.cos(w),
          alpha = Math.sin(w) / (2 * q),
          a0 = 1 + alpha;
      double b0 = (1 + c) / 2 / a0,
          b1 = -(1 + c) / a0,
          b2 = b0,
          a1 = -2 * c / a0,
          a2 = (1 - alpha) / a0;
      double z1 = 0, z2 = 0;
      for (int i = 0; i < x.length; i++) {
        double y = b0 * x[i] + z1;
        z1 = b1 * x[i] - a1 * y + z2;
        z2 = b2 * x[i] - a2 * y;
        x[i] = (float) y;
      }
    }
  }

  /**
   * One-frame analysis delay is removed, and a zero frame flushes the final speech. No endpoint
   * trim.
   */
  public static synchronized float[] process(
      float[] input, int rate, AtomicBoolean cancel, Progress progress) {
    if (input.length < rate || input.length > rate * 61L)
      throw new IllegalArgumentException("Use a 1–60 second recording.");
    for (float v : input)
      if (!Float.isFinite(v)) throw new IllegalArgumentException("Invalid recording samples");
    progress.update(0);
    float[] x = resample(input, rate, 48000, cancel);
    highPass(x, 48000);
    float[] out = new float[x.length], block = new float[480];
    long handle = create();
    if (handle == 0) throw new IllegalStateException("Could not allocate offline denoiser");
    try {
      int frames = (x.length + 479) / 480;
      for (int n = 0; n <= frames; n++) {
        check(cancel);
        Arrays.fill(block, 0);
        int start = n * 480;
        for (int j = 0; j < 480 && start + j < x.length; j++) block[j] = x[start + j] * 32768f;
        frame(handle, block);
        if (n > 0)
          for (int j = 0; j < 480 && start - 480 + j < out.length; j++)
            out[start - 480 + j] = block[j] / 32768f;
        if (n % 20 == 0) progress.update(n * 90 / frames);
      }
    } finally {
      destroy(handle);
    }
    float[] result = resample(out, 48000, rate, cancel);
    result = Arrays.copyOf(result, input.length);
    // Attenuate only if needed; never raise residual noise through automatic gain makeup.
    double peak = 0;
    for (float v : result) {
      if (!Float.isFinite(v)) throw new IllegalStateException("Invalid denoiser output");
      peak = Math.max(peak, Math.abs(v));
    }
    if (peak > .98) for (int i = 0; i < result.length; i++) result[i] *= .98 / peak;
    check(cancel);
    progress.update(100);
    return result;
  }

  private OfflineDenoiser() {}
}
