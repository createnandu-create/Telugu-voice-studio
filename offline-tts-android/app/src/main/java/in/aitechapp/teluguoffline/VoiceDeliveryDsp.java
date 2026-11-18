package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional offline TD-PSOLA on the final voiced region, plus gentle female EQ. No speech
 * semantics/phoneme alignment is inferred. Unvoiced or unstable tails stay untouched.
 */
public final class VoiceDeliveryDsp {
  public static final float LIFT = 1.13f;

  public static final class Result {
    public final float[] samples;
    public final boolean lifted;
    public final int start, end;

    Result(float[] s, boolean lifted, int start, int end) {
      samples = s;
      this.lifted = lifted;
      this.start = start;
      this.end = end;
    }
  }

  public static Result apply(
      float[] source, int rate, boolean question, boolean soft, AtomicBoolean cancel) {
    return apply(source, rate, question, soft, cancel, LIFT);
  }

  private static Result apply(
      float[] source, int rate, boolean question, boolean soft, AtomicBoolean cancel, float lift) {
    if (rate != 22050 && rate != 24000)
      throw new IllegalArgumentException("Unsupported voice rate");
    float[] out = source.clone();
    boolean lifted = false;
    int from = 0, to = 0;
    if (question && Math.abs(lift - 1f) > .001f) {
      int hop = rate / 100, window = rate * 60 / 1000;
      List<Integer> times = new ArrayList<>(), periods = new ArrayList<>();
      float peak = 0;
      for (float x : source) peak = Math.max(peak, Math.abs(x));
      int last = -1;
      // Search at most the final 800 ms, allowing trailing engine-generated silence.
      for (int center = source.length - window / 2 - 1;
          center >= Math.max(window / 2, source.length - rate * 8 / 10);
          center -= hop) {
        if (cancel.get()) throw new CancellationException();
        int period = period(source, center - window / 2, window, rate, peak * .025);
        if (period > 0) {
          if (last >= 0 && last - center > hop * 2) break;
          times.add(center);
          periods.add(period);
          last = center;
          if (times.get(0) - center >= rate * .30) break;
        } else if (!times.isEmpty()) break;
      }
      if (times.size() >= 10) {
        List<Integer> sorted = new ArrayList<>(periods);
        Collections.sort(sorted);
        int p = sorted.get(sorted.size() / 2);
        int min = Collections.min(periods), max = Collections.max(periods);
        if (min > p * .75 && max < p * 1.25) {
          int first = times.get(times.size() - 1), end = times.get(0);
          from = Math.max(0, first);
          to = Math.min(source.length, end);
          lifted = psola(source, out, from, to, p, rate, lift, true);
        }
      }
    }
    if (soft) {
      Biquad body = new Biquad(rate, 250, 2.0, .707), harsh = new Biquad(rate, 4000, -2.0, 1.0);
      for (int i = 0; i < out.length; i++) {
        if ((i & 8191) == 0 && cancel.get()) throw new CancellationException();
        out[i] = (float) harsh.process(body.process(out[i]));
      }
    }
    float peak = 0;
    for (float x : out) {
      if (!Float.isFinite(x)) throw new IllegalArgumentException("Invalid audio sample");
      peak = Math.max(peak, Math.abs(x));
    }
    if (peak > .98f) {
      float gain = .98f / peak;
      for (int i = 0; i < out.length; i++) out[i] *= gain;
    }
    return new Result(out, lifted, from, to);
  }

  /** Normalized autocorrelation; prefer the first strong local peak, not an octave subharmonic. */
  static int period(float[] x, int start, int size, int rate, double floor) {
    double mean = 0;
    for (int i = 0; i < size; i++) mean += x[start + i];
    mean /= size;
    double power = 0;
    for (int i = 0; i < size; i++) {
      double v = x[start + i] - mean;
      power += v * v;
    }
    if (Math.sqrt(power / size) < Math.max(.001, floor)) return -1;
    int low = rate / 420, high = rate / 65;
    double[] score = new double[high + 1];
    double best = 0;
    for (int lag = low; lag <= high; lag++) {
      double a = 0, b = 0, c = 0;
      for (int i = 0; i < size - lag; i++) {
        double u = x[start + i] - mean, v = x[start + i + lag] - mean;
        a += u * v;
        b += u * u;
        c += v * v;
      }
      score[lag] = a / Math.sqrt(Math.max(1e-16, b * c));
      best = Math.max(best, score[lag]);
    }
    if (best < .65) return -1;
    for (int lag = low + 1; lag < high; lag++)
      if (score[lag] >= Math.max(.65, best * .94)
          && score[lag] >= score[lag - 1]
          && score[lag] > score[lag + 1]) return lag;
    return -1;
  }

  private static boolean psola(
      float[] source,
      float[] out,
      int start,
      int end,
      int period,
      int rate,
      float targetFactor,
      boolean sweep) {
    if (end - start < period * 8) return false;
    // Place analysis marks near positive waveform peaks, following the estimated pitch period.
    List<Integer> marks = new ArrayList<>();
    int first = start + period;
    for (int i = start + period; i < Math.min(end - period, start + period * 2); i++)
      if (source[i] > source[first]) first = i;
    marks.add(first);
    while (true) {
      int expected = marks.get(marks.size() - 1) + period;
      if (expected + period >= end) break;
      int best = expected;
      for (int j = expected - period / 5; j <= expected + period / 5; j++)
        if (source[j] > source[best]) best = j;
      if (best <= marks.get(marks.size() - 1)) return false;
      marks.add(best);
    }
    if (marks.size() < 6) return false;
    double[] sum = new double[end - start], weight = new double[end - start];
    int chosen = 0;
    double position = marks.get(0);
    while (position <= marks.get(marks.size() - 1)) {
      while (chosen + 1 < marks.size()
          && Math.abs(marks.get(chosen + 1) - position) < Math.abs(marks.get(chosen) - position))
        chosen++;
      int center = marks.get(chosen), target = (int) Math.round(position);
      for (int j = -period; j <= period; j++) {
        int src = center + j, dst = target + j;
        if (src < 0 || src >= source.length || dst < start || dst >= end) continue;
        double w = .5 + .5 * Math.cos(Math.PI * j / period);
        sum[dst - start] += source[src] * w;
        weight[dst - start] += w;
      }
      double progress =
          Math.min(
              1, Math.max(0, (position - start) / (double) Math.max(1, end - start - rate * .06)));
      // Smoothly introduce the rise and hold the requested +13% near the end of the voiced region.
      double factor =
          1 + (targetFactor - 1) * (sweep ? (.5 - .5 * Math.cos(Math.PI * progress)) : 1);
      position += period / factor;
    }
    int fade = Math.max(period * 2, rate * 25 / 1000);
    for (int i = start; i < end; i++) {
      if (weight[i - start] < .25) continue;
      double blend = Math.min(1, Math.min((i - start) / (double) fade, (end - i) / (double) fade));
      blend = .5 - .5 * Math.cos(Math.PI * Math.max(0, blend));
      out[i] = (float) (source[i] * (1 - blend) + (sum[i - start] / weight[i - start]) * blend);
    }
    return true;
  }

  public static Result directed(
      float[] source,
      int rate,
      NarrationDirector.Segment segment,
      boolean question,
      boolean soft,
      AtomicBoolean cancel) {
    float[] audio = source.clone();
    boolean shifted = false;
    int start = 0, end = 0;
    float factor = question ? 1f : segment.pitch;
    if (Math.abs(factor - 1) > .001) {
      int hop = rate / 100, window = rate * 60 / 1000;
      int run = -1;
      List<Integer> periods = new ArrayList<>();
      for (int center = window / 2; center < source.length - window / 2 + hop; center += hop) {
        if (cancel.get()) throw new CancellationException();
        int period =
            center < source.length - window / 2
                ? period(source, center - window / 2, window, rate, .002)
                : -1;
        if (period > 0
            && (periods.isEmpty()
                || (period > periods.get(0) * .75 && period < periods.get(0) * 1.25))) {
          if (run < 0) run = center;
          periods.add(period);
        } else {
          if (periods.size() >= 10) {
            Collections.sort(periods);
            int p = periods.get(periods.size() / 2);
            if (psola(source, audio, run, center - hop, p, rate, factor, false)) {
              shifted = true;
              start = run;
              end = center - hop;
            }
          }
          run = -1;
          periods.clear();
        }
      }
    }
    Result finish =
        apply(
            audio,
            rate,
            question,
            soft,
            cancel,
            segment.emotion == NarrationDirector.Emotion.QUESTION ? segment.pitch : LIFT);
    return new Result(
        finish.samples,
        shifted || finish.lifted,
        finish.lifted ? finish.start : start,
        finish.lifted ? finish.end : end);
  }

  /** RBJ peaking filters: +2 dB at 250 Hz; -2 dB at 4 kHz. No high-shelf sibilant removal. */
  private static final class Biquad {
    final double b0, b1, b2, a1, a2;
    double z1, z2;

    Biquad(int rate, double frequency, double gain, double q) {
      double a = Math.pow(10, gain / 40),
          w = 2 * Math.PI * frequency / rate,
          alpha = Math.sin(w) / (2 * q),
          c = Math.cos(w),
          a0 = 1 + alpha / a;
      b0 = (1 + alpha * a) / a0;
      b1 = -2 * c / a0;
      b2 = (1 - alpha * a) / a0;
      a1 = -2 * c / a0;
      a2 = (1 - alpha / a) / a0;
    }

    double process(double x) {
      double y = b0 * x + z1;
      z1 = b1 * x - a1 * y + z2;
      z2 = b2 * x - a2 * y;
      return y;
    }
  }

  public static Result process(
      File input, File output, boolean question, boolean soft, AtomicBoolean cancel)
      throws IOException {
    return process(input, output, question, soft, null, cancel);
  }

  public static Result process(
      File input,
      File output,
      boolean question,
      boolean soft,
      NarrationDirector.Segment direction,
      AtomicBoolean cancel)
      throws IOException {
    return process(input, output, question, soft, direction, cancel, false, false);
  }

  static Result process(File input, File output, boolean question, boolean soft,
      NarrationDirector.Segment direction, AtomicBoolean cancel, boolean studio, boolean tiny)
      throws IOException {
    long bytes = PcmWave.validate(input);
    int rate = PcmWave.sampleRate(input);
    if (bytes > 16_000_000)
      throw new IOException("Voice-control chunk is too long. Split the sentence.");
    float[] samples = new float[(int) bytes / 2];
    try (RandomAccessFile in = new RandomAccessFile(input, "r")) {
      in.seek(44);
      byte[] block = new byte[16384];
      int index = 0;
      while (index < samples.length) {
        if (cancel.get()) throw new CancellationException();
        int count = Math.min(block.length, (samples.length - index) * 2);
        in.readFully(block, 0, count);
        for (int i = 0; i < count; i += 2)
          samples[index++] = (short) ((block[i] & 255) | (block[i + 1] << 8)) / 32768f;
      }
    }
    Result r =
        direction == null
            ? apply(samples, rate, question, soft, cancel)
            : directed(samples, rate, direction, question, soft, cancel);
    if (cancel.get()) throw new CancellationException();
    if (studio) {
      Biquad chest = new Biquad(rate, 160, 2, .85);
      Biquad box = new Biquad(rate, 400, -2.5, .95);
      Biquad deEss = new Biquad(rate, 7000, -2, 2);
      for (int i = 0; i < r.samples.length; i++) {
        double x = box.process(chest.process(r.samples[i]));
        r.samples[i] = (float) (tiny ? deEss.process(x) : x);
      }
    }
    PcmWave.write(output, r.samples, rate);
    return r;
  }

  private VoiceDeliveryDsp() {}
}
