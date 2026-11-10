package in.aitechapp.teluguoffline;

import java.util.Arrays;

/** Checks purely local PCM16 shelf EQ behavior, not perceived speech naturalness. */
public final class WarmthEqTest {
  private static int checks;

  private static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
    checks++;
  }

  private static byte[] sine(int hz, int rate, int frames) {
    byte[] b = new byte[frames * 2];
    for (int i = 0; i < frames; i++) {
      short x = (short) Math.round(Math.sin(2.0 * Math.PI * hz * i / rate) * 12000);
      b[2 * i] = (byte) x;
      b[2 * i + 1] = (byte) (x >> 8);
    }
    return b;
  }

  private static double rms(byte[] b, int skip) {
    double v = 0;
    for (int i = skip * 2; i < b.length; i += 2) {
      short x = (short) ((b[i] & 255) | ((b[i + 1] & 255) << 8));
      v += (double) x * x;
    }
    return Math.sqrt(v / Math.max(1, b.length / 2 - skip));
  }

  public static void main(String[] args) {
    byte[] zero = new byte[4096];
    new WarmthEq(22050).process(zero, zero.length);
    for (byte b : zero) check(b == 0, "silence remains silent");

    byte[] whole = sine(210, 22050, 22050);
    byte[] blocks = whole.clone();
    new WarmthEq(22050).process(whole, whole.length);
    WarmthEq blockEq = new WarmthEq(22050);
    for (int p = 0; p < blocks.length; p += 4096) {
      int n = Math.min(4096, blocks.length - p);
      byte[] chunk = Arrays.copyOfRange(blocks, p, p + n);
      blockEq.process(chunk, n);
      System.arraycopy(chunk, 0, blocks, p, n);
    }
    check(Arrays.equals(whole, blocks), "stream blocks equal whole processing");
    check(whole.length == 44100, "sample rate and frame count unchanged");

    byte[] low = sine(150, 22050, 22050);
    byte[] high = sine(7500, 22050, 22050);
    double lowRaw = rms(low, 500), highRaw = rms(high, 500);
    new WarmthEq(22050).process(low, low.length);
    new WarmthEq(22050).process(high, high.length);
    check(
        rms(low, 500) / lowRaw > rms(high, 500) / highRaw,
        "low shelf preserves more presence than attenuated high shelf");

    for (int i = 0; i < whole.length; i += 2) {
      short sample = (short) ((whole[i] & 255) | ((whole[i + 1] & 255) << 8));
      check(Math.abs((int) sample) < 31000, "sample headroom");
    }
    boolean bad = false;
    try {
      new WarmthEq(22050).process(new byte[3], 3);
    } catch (IllegalArgumentException e) {
      bad = true;
    }
    check(bad, "invalid odd byte PCM rejected");
    System.out.println("PASS: " + checks + " WarmthEq streaming, shelf, silence and PCM checks");
  }
}
