package in.aitechapp.teluguoffline;

/**
 * Experimental user-selectable vocal warmth preset for native-rate mono PCM16. NOT a neural
 * vocoder, not noise restoration; do not enable without user A/B tests.
 *
 * <p>RBJ low shelf +3 dB / 180 Hz (slope=1), high shelf -2.5 dB / 6 kHz, with conservative output
 * headroom. Preserves PCM16 sample rate and timing.
 */
public final class WarmthEq {
  private static final double OUTPUT_HEADROOM = 0.68;
  private final Biquad low, high;

  public WarmthEq(int sampleRate) {
    if (sampleRate < 16000 || sampleRate > 96000)
      throw new IllegalArgumentException("Unsupported sample rate");
    low = new Biquad(sampleRate, 180.0, 3.0, false);
    high = new Biquad(sampleRate, 6000.0, -2.5, true);
  }

  /** In-place, signed 16-bit little-endian mono; preserves exact sample count. */
  public void process(byte[] buffer, int size) {
    if (buffer == null || size < 0 || size > buffer.length || (size & 1) != 0)
      throw new IllegalArgumentException("Invalid mono PCM16 block");
    for (int i = 0; i < size; i += 2) {
      short in = (short) ((buffer[i] & 0xff) | ((buffer[i + 1] & 0xff) << 8));
      double x = in / 32768.0;
      double y = high.filter(low.filter(x)) * OUTPUT_HEADROOM;
      // Safety cap is not a true-peak limiter; do not claim it replaces mastering.
      y = Math.max(-0.94, Math.min(0.94, y));
      int out = (int) Math.round(y * 32767.0);
      buffer[i] = (byte) out;
      buffer[i + 1] = (byte) (out >> 8);
    }
  }

  private static final class Biquad {
    private final double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;

    Biquad(int rate, double freq, double gain, boolean highShelf) {
      if (freq <= 0 || freq >= rate * 0.5) throw new IllegalArgumentException("Bad shelf");
      double a = Math.pow(10.0, gain / 40.0);
      double w = 2.0 * Math.PI * freq / rate;
      double c = Math.cos(w);
      // RBJ shelf slope S=1, alpha=sin(w)/sqrt(2).
      double alpha = Math.sin(w) / Math.sqrt(2.0);
      double beta = 2.0 * Math.sqrt(a) * alpha;
      double rawB0, rawB1, rawB2, rawA0, rawA1, rawA2;
      if (highShelf) {
        rawB0 = a * ((a + 1) + (a - 1) * c + beta);
        rawB1 = -2 * a * ((a - 1) + (a + 1) * c);
        rawB2 = a * ((a + 1) + (a - 1) * c - beta);
        rawA0 = (a + 1) - (a - 1) * c + beta;
        rawA1 = 2 * ((a - 1) - (a + 1) * c);
        rawA2 = (a + 1) - (a - 1) * c - beta;
      } else {
        rawB0 = a * ((a + 1) - (a - 1) * c + beta);
        rawB1 = 2 * a * ((a - 1) - (a + 1) * c);
        rawB2 = a * ((a + 1) - (a - 1) * c - beta);
        rawA0 = (a + 1) + (a - 1) * c + beta;
        rawA1 = -2 * ((a - 1) + (a + 1) * c);
        rawA2 = (a + 1) + (a - 1) * c - beta;
      }
      b0 = rawB0 / rawA0;
      b1 = rawB1 / rawA0;
      b2 = rawB2 / rawA0;
      a1 = rawA1 / rawA0;
      a2 = rawA2 / rawA0;
    }

    double filter(double x) {
      double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
      x2 = x1;
      x1 = x;
      y2 = y1;
      y1 = y;
      return y;
    }
  }
}
