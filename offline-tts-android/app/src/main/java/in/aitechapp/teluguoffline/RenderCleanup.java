package in.aitechapp.teluguoffline;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Use RNNoise only as an estimate. Reconstruct from the original complex spectrum. */
final class RenderCleanup {
  private static final int N = 512, HOP = N / 2;

  // Fitted by tools/train-render-mask.py to 559 public Rasa utterances with seeded interference.
  private static final int[] MASK = {
      57, 58, 91, 95, 94, 100, 76, 72, 80, 89, 98, 98, 89, 87, 89, 95, 96, 99, 96, 95, 95, 97, 96, 99,
      99, 99, 99, 99, 99, 100, 99, 100, 100, 100, 100, 100, 99, 98, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100,
      64, 66, 71, 83, 98, 100, 79, 73, 72, 75, 88, 100, 92, 87, 85, 85, 92, 99, 98, 97, 96, 94, 97, 100,
      100, 99, 99, 99, 99, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 99, 100, 100,
      65, 63, 63, 71, 92, 100, 82, 78, 75, 72, 83, 99, 94, 92, 91, 88, 91, 98, 98, 98, 98, 97, 97, 98,
      100, 100, 100, 99, 99, 99, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100,
      62, 58, 55, 56, 73, 100, 80, 74, 67, 61, 69, 96, 93, 90, 86, 80, 83, 91, 98, 98, 96, 94, 94, 92,
      100, 100, 100, 99, 99, 97, 100, 100, 100, 100, 100, 99, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100,
      57, 60, 61, 64, 73, 96, 62, 64, 64, 63, 70, 93, 59, 63, 67, 69, 74, 89, 56, 53, 58, 66, 76, 90,
      68, 75, 60, 58, 67, 82, 100, 100, 98, 87, 69, 18, 100, 100, 100, 100, 51, 0, 100, 100, 100, 100, 100, 100,
      55, 100, 100, 100, 100, 100, 76, 85, 96, 97, 100, 100, 86, 88, 84, 90, 99, 100, 92, 93, 90, 97, 77, 99,
      95, 90, 93, 96, 97, 100, 92, 96, 95, 93, 99, 100, 97, 95, 99, 99, 100, 100, 100, 100, 100, 99, 100, 100,
      66, 85, 99, 100, 100, 100, 75, 66, 71, 85, 99, 100, 89, 76, 78, 87, 94, 100, 96, 83, 84, 89, 97, 99,
      99, 92, 85, 84, 96, 100, 100, 99, 97, 92, 96, 100, 100, 93, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100,
      67, 93, 100, 100, 100, 100, 81, 77, 78, 93, 100, 100, 92, 89, 89, 93, 95, 100, 96, 93, 92, 93, 98, 100,
      98, 96, 94, 93, 97, 99, 97, 97, 96, 95, 98, 100, 99, 99, 98, 98, 99, 100, 100, 99, 100, 100, 99, 99,
      67, 69, 70, 80, 92, 100, 80, 77, 81, 81, 91, 100, 89, 87, 85, 86, 92, 99, 95, 89, 89, 91, 95, 97,
      97, 96, 92, 88, 95, 99, 92, 96, 96, 96, 97, 100, 96, 96, 96, 98, 99, 99, 99, 99, 99, 99, 98, 97,
      62, 67, 69, 74, 80, 91, 73, 77, 77, 78, 81, 100, 74, 86, 87, 87, 90, 97, 56, 68, 87, 92, 95, 99,
      64, 56, 55, 69, 92, 100, 81, 80, 66, 57, 60, 99, 91, 90, 89, 84, 76, 71, 99, 99, 97, 96, 96, 25,
  };
  private static final double[] MIN_GAIN = {.84, .76, .58, .43, .36};

  private static int band(int hz) {
    if (hz < 700) return 0;
    if (hz < 1800) return 1;
    if (hz < 3500) return 2;
    return hz < 6000 ? 3 : 4;
  }

  private static int cell(double ratio, int offset, int step, int max) {
    return Math.max(0, Math.min(max, (int)Math.floor((10*Math.log10(ratio)+offset)/step)));
  }

  // Normalized periodicity of the low-passed waveform. Fricatives and unstable frames
  // retain their broadband detail; stable vowels tolerate stronger inter-harmonic attenuation.
  private static double periodicity(float[] x, int center, int rate, double[] down, double[] correlations) {
    int first = center - down.length*2;
    double sum = 0;
    for (int i=0; i<down.length; i++) {
      int at = first + 4*i;
      down[i] = at < 0 || at+3 >= x.length ? 0 : (x[at]+x[at+1]+x[at+2]+x[at+3])*.25;
      sum += down[i];
    }
    double mean = sum/down.length;
    for (int i=0; i<down.length; i++) down[i] -= mean;
    double best=0;
    int low=rate/1600, high=rate/280;
    for (int lag=low; lag<=high; lag++) {
      double a=0,b=0,c=0;
      for (int i=lag; i<down.length; i++) {
        double u=down[i], v=down[i-lag];
        a+=u*u; b+=v*v; c+=u*v;
      }
      correlations[lag]=c/Math.sqrt(1e-14+a*b);
      best=Math.max(best,correlations[lag]);
    }
    // First strong local maximum avoids octave and subharmonic locking.
    correlations[0]=0;
    for (int lag=low+1; lag<high; lag++) {
      double peak=correlations[lag], a=correlations[lag-1], b=correlations[lag+1];
      if (peak >= Math.max(.58, best*.94) && peak > a && peak >= b) {
        double offset=.5*(a-b)/(a-2*peak+b);
        correlations[0]=4*(lag+offset);
        return Math.min(1,Math.max(0,(peak-.54)/.38));
      }
    }
    return 0;
  }

  interface Cleaner { float[] process(float[] samples, int rate); }

  static Cleaner forVoice(int voice, boolean clone, AtomicBoolean cancel) {
    // Protect onsets and fricatives; use a deeper tail reduction on Vocos than on Piper.
    final double depth = clone ? .32 : voice >= 4 ? .22 : new double[]{.36, .46, .62, .70}[voice];
    return (samples, rate) -> apply(samples, rate, voice >= 2 || clone, cancel, depth, true);
  }

  static float[] apply(float[] dry, int rate, boolean neuralVocoder, AtomicBoolean cancel) {
    return apply(dry, rate, neuralVocoder, cancel, 0, false);
  }

  private static float[] apply(float[] dry, int rate, boolean neuralVocoder, AtomicBoolean cancel, double depth, boolean preserveSpeech) {
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
    int[] bands = new int[HOP+1], counts = new int[5];
    double[] means = new double[5], prefix = new double[HOP+2], down = new double[rate/100];
    double[] correlations = new double[rate/280+1];
    for (int k=0; k<=HOP; k++) { bands[k]=band(k*rate/N); counts[bands[k]]++; }
    float[] out = new float[dry.length];
    // Estimate delayed energy independently for each speech band from previous frames only.
    double[][] delayed = new double[3][5];
    double[] tail = new double[5], tailGain = new double[5];
    int delaySlot = 0;
    double release = Math.exp(-HOP / (.075 * rate));
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
      Arrays.fill(means,0);
      for (int k = 0; k <= HOP; k++) {
        power[k] = re[k]*re[k] + im[k]*im[k];
        means[bands[k]] += power[k];
        prefix[k+1] = prefix[k]+power[k];
        reference[k] = wet[k]*wet[k] + wi[k]*wi[k];
      }
      for (int b=0; b<5; b++) means[b] /= counts[b];
      double base = .35*means[0]+.5*means[1]+.15*means[2];
      boolean fricative = means[3]+means[4] > .20*base;
      for (int b = 0; b < means.length; b++) {
        tail[b] = Math.max(delayed[delaySlot][b], tail[b] * release);
        delayed[delaySlot][b] = means[b];
        // Attenuate a falling tail only after this band has lost at least 9 dB.
        double decay = Math.max(0, 1 - 8 * means[b] / (tail[b] + 1e-16));
        tailGain[b] = 1 - depth * decay * (b == 0 ? .45 : 1);
      }
      delaySlot = (delaySlot + 1) % delayed.length;
      double voiced = fricative ? 0 : periodicity(dry,start+HOP,rate,down,correlations);
      for (int k = 0; k <= HOP; k++) {
        int lo = Math.max(0,k-1), hi = Math.min(HOP,k+1);
        double band = power[lo] + 2*power[k] + power[hi];
        double ratio = Math.sqrt((reference[lo]+2*reference[k]+reference[hi]+1e-10)/(band+1e-10));
        double floor = energy < N * .000008 ? .40 : neuralVocoder ? .65 : .76;
        double target = Math.max(floor, Math.min(1, ratio));
        int left=Math.max(0,k-5), right=Math.min(HOP,k+5);
        double local=(prefix[right+1]-prefix[left]+(5-k>0 ? (5-k)*power[0] : 0)
            + Math.max(0,k+5-HOP)*power[HOP])/11;
        double prominence=(power[k]+1e-12)/(local+1e-12);
        int level=cell((power[k]+1e-12)/(base+1e-12),45,8,7);
        int crest=cell(prominence,12,4,5);
        int group=bands[k];
        double learned=Math.max(MIN_GAIN[group], MASK[(((fricative?5:0)+group)*8+level)*6+crest]*.01);
        // Weight band valleys during periodic speech. Do not attenuate low-frequency voice body.
        double valley=Math.max(0,1-Math.min(1,prominence));
        double mix=group<2 ? 0 : group==2 ? .43 : .70;
        target=Math.sqrt(target)*learned*(1-voiced*mix*valley);
        if (group >= 2 && voiced > 0) {
          // Preserve integer harmonics. Reduce diffuse energy between them during pitched speech.
          double phase = Math.PI*k*correlations[0]/N;
          double offHarmonic = Math.sin(phase);
          double harmonicDepth = group == 2 ? .45 : .64;
          target *= 1 - voiced * harmonicDepth * (.16 + .84*offHarmonic*offHarmonic);
        }
        // A sustained narrow upper-band peak gets a small extra cut. Broad consonant bursts pass.
        if (k >= 4 && k + 4 <= HOP && (long) k * rate >= 3200L * N) {
          double side = .25 * (power[k-4]+power[k-3]+power[k+3]+power[k+4]);
          double peak = Math.max(0, Math.min(1, (power[k]/(side+1e-9)-3)/12));
          persistence[k] += .18 * (peak-persistence[k]);
          target *= 1 - (neuralVocoder ? .27 : .14) * persistence[k];
        }
        if (!fricative) target *= tailGain[group];
        // Raised vowels carry the identity of a speaker. Keep most of their
        // original harmonics even if the noise estimate treats them as ringing.
        if (preserveSpeech) {
          double activity=Math.min(1, energy/(N*.0016));
          double dryMix=activity*(fricative ? .70 : .28+.55*voiced);
          target+=dryMix*(1-target);
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
