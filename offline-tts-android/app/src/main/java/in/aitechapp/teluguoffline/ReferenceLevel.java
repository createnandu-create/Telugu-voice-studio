package in.aitechapp.teluguoffline;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mono 24 kHz reference leveling after noise suppression or before tone extraction. */
final class ReferenceLevel {
  static final int RATE = 24000;
  private static final double LIMIT = Math.pow(10, -1.5 / 20);

  static float[] excerpt(float[] audio) {
    int length = Math.min(audio.length, RATE * 15);
    double energy = 0, best = -1;
    int start = 0;
    for (int i = 0; i < audio.length; i++) {
      energy += (double) audio[i] * audio[i];
      if (i >= length) energy -= (double) audio[i-length] * audio[i-length];
      if (i >= length - 1 && energy > best) {
        best = energy;
        start = i - length + 1;
      }
    }
    return Arrays.copyOfRange(audio, start, start + length);
  }

  static double loudness(float[] audio) {
    StudioMastering.Meter meter = new StudioMastering.Meter(RATE, 1);
    for (float x : audio) meter.add(x);
    return meter.result();
  }

  static void normalize(float[] audio, boolean presence, AtomicBoolean cancel) {
    WarmthEq.Biquad shelf = new WarmthEq.Biquad(RATE, 3500, 2.5, true);
    double peak = 0;
    for (int i = 0; i < audio.length; i++) {
      if (!Float.isFinite(audio[i])) throw new IllegalArgumentException("Invalid reference sample");
      if (presence) audio[i] = (float) shelf.filter(audio[i]);
      peak = Math.max(peak, Math.abs(audio[i]));
    }
    if (peak == 0) throw new IllegalArgumentException("The recording contains only silence.");
    // Pre-scale before the absolute BS.1770 gate, including previously over-suppressed files.
    for (int i = 0; i < audio.length; i++) audio[i] /= peak;
    double measured = loudness(audio);
    if (!Double.isFinite(measured)) {
      for (int i = 0; i < audio.length; i++) audio[i] *= LIMIT;
      return;
    }
    float[] input = audio.clone();
    int block = RATE / 100;
    double[] peaks = new double[(audio.length + block - 1) / block];
    for (int i = 0; i < audio.length; i++)
      peaks[i/block] = Math.max(peaks[i/block], Math.abs(audio[i]));
    double drive = Math.pow(10, (-16 - measured) / 20);
    double release = Math.exp(-1.0 / (RATE * .08));
    for (int pass = 0; pass < 8; pass++) {
      double gain = 1;
      for (int b = 0; b < peaks.length; b++) {
        if (cancel.get()) throw new CancellationException();
        double local = Math.max(peaks[b], peaks[Math.min(b + 1, peaks.length - 1)]);
        double target = Math.min(1, LIMIT / Math.max(1e-12, drive * local));
        for (int i = b*block; i < Math.min(audio.length, (b+1)*block); i++) {
          gain = Math.min(target, release * gain + (1-release) * target);
          audio[i] = (float) (input[i] * drive * gain);
        }
      }
      double error = -16 - loudness(audio);
      if (!Double.isFinite(error) || Math.abs(error) < .08) break;
      drive *= Math.pow(10, error / 20);
    }
  }
}
