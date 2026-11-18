package in.aitechapp.teluguoffline;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Use RNNoise only as an estimate. Reconstruct from the original complex spectrum. */
final class RenderCleanup {
  private static final int N = 512, HOP = N / 2;

  static float[] apply(float[] dry, int rate, boolean neuralVocoder, AtomicBoolean cancel) {
    if (dry.length == 0) return dry;
    float[] estimate = new float[dry.length];
    for (int start = 0; start < dry.length; start += rate * 30) {
      int count = Math.min(rate * 30, dry.length - start);
      float[] block = Arrays.copyOfRange(dry, start, start + Math.max(rate, count));
      float[] cleaned = OfflineDenoiser.process(block, rate, cancel, ignored -> {});
      System.arraycopy(cleaned, 0, estimate, start, count);
    }
    double[] window = new double[N], gain = new double[HOP+1], persistence = new double[HOP+1];
    double[] re = new double[N], im = new double[N], wet = new double[N], wi = new double[N];
    double[] power = new double[HOP+1], reference = new double[HOP+1];
    float[] out = new float[dry.length];
    Arrays.fill(gain, 1);
    for (int i = 0; i < N; i++) window[i] = Math.sin(Math.PI * (i + .5) / N);
    for (int start = -HOP; start < dry.length; start += HOP) {
      OfflineDenoiser.check(cancel);
      double energy = 0;
      for (int i = 0; i < N; i++) {
        int at = start + i;
        boolean inside = at >= 0 && at < dry.length;
        re[i] = inside ? dry[at] * window[i] : 0;
        wet[i] = inside ? estimate[at] * window[i] : 0;
        im[i] = wi[i] = 0;
        energy += re[i] * re[i];
      }
      LateReverb.fft(re, im, false);
      LateReverb.fft(wet, wi, false);
      for (int k = 0; k <= HOP; k++) {
        power[k] = re[k]*re[k] + im[k]*im[k];
        reference[k] = wet[k]*wet[k] + wi[k]*wi[k];
      }
      for (int k = 0; k <= HOP; k++) {
        int lo = Math.max(0,k-1), hi = Math.min(HOP,k+1);
        double band = power[lo] + 2*power[k] + power[hi];
        double ratio = Math.sqrt((reference[lo]+2*reference[k]+reference[hi]+1e-10)/(band+1e-10));
        double floor = energy < N * .000008 ? .40 : neuralVocoder ? .65 : .76;
        double target = Math.max(floor, Math.min(1, ratio));
        // A sustained narrow upper-band peak gets a small extra cut. Broad consonant bursts pass.
        if (k >= 4 && k + 4 <= HOP && (long) k * rate >= 3200L * N) {
          double side = .25 * (power[k-4]+power[k-3]+power[k+3]+power[k+4]);
          double peak = Math.max(0, Math.min(1, (power[k]/(side+1e-9)-3)/12));
          persistence[k] += .18 * (peak-persistence[k]);
          target *= 1 - (neuralVocoder ? .27 : .14) * persistence[k];
        }
        gain[k] += (target < gain[k] ? .32 : .55) * (target - gain[k]);
        re[k] *= gain[k]; im[k] *= gain[k];
        if (k > 0 && k < HOP) { re[N-k] = re[k]; im[N-k] = -im[k]; }
      }
      LateReverb.fft(re, im, true);
      for (int i = Math.max(0,-start); i < Math.min(N,dry.length-start); i++)
        out[start+i] += (float) (re[i] * window[i]);
    }
    return out;
  }
  private RenderCleanup() {}
}
