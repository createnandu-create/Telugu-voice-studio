package in.aitechapp.teluguoffline;

import java.util.concurrent.atomic.AtomicBoolean;

/** Single-channel delayed linear prediction (WPE). Optional late-tail reduction, not zero echo. */
public final class LateReverb {
  private static final int N = 512, HOP = 128, BINS = 257, TAPS = 12, DELAY = 3;

  static void fft(double[] re, double[] im, boolean inverse) {
    int n = re.length;
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double t = re[i];
        re[i] = re[j];
        re[j] = t;
        t = im[i];
        im[i] = im[j];
        im[j] = t;
      }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double ang = (inverse ? 2 : -2) * Math.PI / len;
      double wr0 = Math.cos(ang), wi0 = Math.sin(ang);
      for (int i = 0; i < n; i += len) {
        double wr = 1, wi = 0;
        for (int j = 0; j < len / 2; j++) {
          int a = i + j, b = a + len / 2;
          double vr = re[b] * wr - im[b] * wi, vi = re[b] * wi + im[b] * wr;
          re[b] = re[a] - vr;
          im[b] = im[a] - vi;
          re[a] += vr;
          im[a] += vi;
          double next = wr * wr0 - wi * wi0;
          wi = wr * wi0 + wi * wr0;
          wr = next;
        }
      }
    }
    if (inverse)
      for (int i = 0; i < n; i++) {
        re[i] /= n;
        im[i] /= n;
      }
  }

  public static float[] process(
      float[] input, AtomicBoolean cancel, OfflineDenoiser.Progress progress) {
    int frames = (input.length + N / 2 + HOP - 1) / HOP + 1;
    float[][] xr = new float[BINS][frames], xi = new float[BINS][frames];
    double[] window = new double[N];
    for (int i = 0; i < N; i++) window[i] = Math.sin(Math.PI * i / N);
    for (int t = 0; t < frames; t++) {
      OfflineDenoiser.check(cancel);
      double[] re = new double[N], im = new double[N];
      for (int j = 0; j < N; j++) {
        int pos = t * HOP + j - N / 2;
        if (pos >= 0 && pos < input.length) re[j] = input[pos] * window[j];
      }
      fft(re, im, false);
      for (int f = 0; f < BINS; f++) {
        xr[f][t] = (float) re[f];
        xi[f][t] = (float) im[f];
      }
    }
    float[][] yr = new float[BINS][frames], yi = new float[BINS][frames];
    for (int f = 0; f < BINS; f++) {
      OfflineDenoiser.check(cancel);
      yr[f] = xr[f].clone();
      yi[f] = xi[f].clone();
      double average = 0;
      for (int t = 0; t < frames; t++) average += xr[f][t] * xr[f][t] + xi[f][t] * xi[f][t];
      average /= frames;
      if (average < 1e-14) continue;
      for (int iter = 0; iter < 2; iter++) {
        double[][] ar = new double[TAPS][TAPS], ai = new double[TAPS][TAPS];
        double[] pr = new double[TAPS], pi = new double[TAPS];
        for (int t = DELAY + TAPS - 1; t < frames; t++) {
          double power = 0;
          int count = 0;
          for (int u = Math.max(0, t - 1); u <= Math.min(frames - 1, t + 1); u++) {
            power += yr[f][u] * yr[f][u] + yi[f][u] * yi[f][u];
            count++;
          }
          double weight = 1 / Math.max(average * 1e-3, power / count);
          for (int a = 0; a < TAPS; a++) {
            double zr = xr[f][t - DELAY - a], zi = xi[f][t - DELAY - a];
            pr[a] += weight * (zr * xr[f][t] + zi * xi[f][t]);
            pi[a] += weight * (zi * xr[f][t] - zr * xi[f][t]);
            for (int b = 0; b < TAPS; b++) {
              double br = xr[f][t - DELAY - b], bi = xi[f][t - DELAY - b];
              ar[a][b] += weight * (zr * br + zi * bi);
              ai[a][b] += weight * (zi * br - zr * bi);
            }
          }
        }
        double trace = 0;
        for (int a = 0; a < TAPS; a++) trace += ar[a][a];
        double[][] matrix = new double[TAPS * 2][TAPS * 2 + 1];
        for (int a = 0; a < TAPS; a++) {
          ar[a][a] += Math.max(1e-8, trace / TAPS * 1e-3);
          for (int b = 0; b < TAPS; b++) {
            matrix[a][b] = matrix[a + TAPS][b + TAPS] = ar[a][b];
            matrix[a][b + TAPS] = -ai[a][b];
            matrix[a + TAPS][b] = ai[a][b];
          }
          matrix[a][TAPS * 2] = pr[a];
          matrix[a + TAPS][TAPS * 2] = pi[a];
        }
        double[] g = solve(matrix);
        if (g == null) break;
        for (int t = DELAY + TAPS - 1; t < frames; t++) {
          double er = 0, ei = 0;
          for (int a = 0; a < TAPS; a++) {
            double zr = xr[f][t - DELAY - a], zi = xi[f][t - DELAY - a];
            er += g[a] * zr + g[a + TAPS] * zi;
            ei += g[a] * zi - g[a + TAPS] * zr;
          }
          yr[f][t] = (float) (xr[f][t] - er);
          yi[f][t] = (float) (xi[f][t] - ei);
        }
      }
      if (f % 8 == 0) progress.update(f * 90 / BINS);
    }
    float[] out = new float[input.length];
    double[] norm = new double[input.length];
    for (int t = 0; t < frames; t++) {
      OfflineDenoiser.check(cancel);
      double[] re = new double[N], im = new double[N];
      for (int f = 0; f < BINS; f++) {
        re[f] = yr[f][t];
        im[f] = yi[f][t];
        if (f > 0 && f < N / 2) {
          re[N - f] = re[f];
          im[N - f] = -im[f];
        }
      }
      fft(re, im, true);
      for (int j = 0; j < N; j++) {
        int pos = t * HOP + j - N / 2;
        if (pos >= 0 && pos < input.length) {
          out[pos] += re[j] * window[j];
          norm[pos] += window[j] * window[j];
        }
      }
    }
    for (int i = 0; i < out.length; i++) {
      out[i] /= Math.max(1e-8, norm[i]);
      if (!Float.isFinite(out[i])) throw new IllegalStateException("Invalid room-tail output");
    }
    progress.update(100);
    return out;
  }

  private static double[] solve(double[][] a) {
    int n = a.length;
    for (int col = 0; col < n; col++) {
      int pivot = col;
      for (int i = col + 1; i < n; i++)
        if (Math.abs(a[i][col]) > Math.abs(a[pivot][col])) pivot = i;
      if (Math.abs(a[pivot][col]) < 1e-12) return null;
      double[] swap = a[col];
      a[col] = a[pivot];
      a[pivot] = swap;
      double divisor = a[col][col];
      for (int j = col; j <= n; j++) a[col][j] /= divisor;
      for (int i = 0; i < n; i++)
        if (i != col) {
          double factor = a[i][col];
          for (int j = col; j <= n; j++) a[i][j] -= factor * a[col][j];
        }
    }
    double[] x = new double[n];
    for (int i = 0; i < n; i++) x[i] = a[i][n];
    return x;
  }

  private LateReverb() {}
}
