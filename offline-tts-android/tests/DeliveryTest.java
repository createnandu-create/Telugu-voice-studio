package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeliveryTest {
  static void check(boolean ok, String what) {
    if (!ok) throw new AssertionError(what);
  }

  static double frequency(float[] samples, int rate, int from, int end) {
    List<Double> crossing = new ArrayList<>();
    for (int i = from + 1; i < end; i++)
      if (samples[i - 1] < 0 && samples[i] >= 0)
        crossing.add(i - 1 - samples[i - 1] / (double) (samples[i] - samples[i - 1]));
    return rate * (crossing.size() - 1) / (crossing.get(crossing.size() - 1) - crossing.get(0));
  }

  static double rms(float[] x, int start) {
    double sum = 0;
    for (int i = start; i < x.length; i++) sum += x[i] * x[i];
    return Math.sqrt(sum / (x.length - start));
  }

  public static void main(String[] args) throws Exception {
    for (int rate : new int[] {22050, 24000})
      for (int f : new int[] {110, 180, 240, 300}) {
        float[] source = new float[rate * 2];
        for (int i = 0; i < source.length; i++)
          source[i] =
              (float)
                  (.3 * Math.sin(2 * Math.PI * f * i / rate)
                      + .09 * Math.sin(4 * Math.PI * f * i / rate));
        var result = VoiceDeliveryDsp.apply(source, rate, true, false, new AtomicBoolean());
        check(result.lifted, "stable voiced tail detected");
        check(result.samples.length == source.length, "duration unchanged");
        for (int i = 0; i < result.start; i++)
          check(source[i] == result.samples[i], "initial phonemes untouched");
        int from = source.length - rate * 15 / 100, end = source.length - rate * 7 / 100;
        double ratio =
            frequency(result.samples, rate, from, end) / frequency(source, rate, from, end);
        check(
            ratio > 1.10 && ratio < 1.16, "independent zero-crossing pitch measurement: " + ratio);
      }
    for (int rate : new int[] {22050, 24000})
      for (int f : new int[] {250, 4000}) {
        float[] source = new float[rate];
        for (int i = 0; i < source.length; i++)
          source[i] = (float) (.3 * Math.sin(2 * Math.PI * f * i / rate));
        var r = VoiceDeliveryDsp.apply(source, rate, false, true, new AtomicBoolean());
        double db = 20 * Math.log10(rms(r.samples, rate / 4) / rms(source, rate / 4));
        check(Math.abs(db - (f == 250 ? 2 : -2)) < .2, "female bell EQ response " + db);
      }
    float[] noise = new float[24000];
    Random random = new Random(42);
    for (int i = 0; i < noise.length; i++) noise[i] = (random.nextFloat() - .5f) * .2f;
    var unvoiced = VoiceDeliveryDsp.apply(noise, 24000, true, false, new AtomicBoolean());
    check(!unvoiced.lifted && Arrays.equals(noise, unvoiced.samples), "unvoiced ending unchanged");
    try {
      VoiceDeliveryDsp.apply(noise, 24000, true, false, new AtomicBoolean(true));
      throw new AssertionError("cancel");
    } catch (java.util.concurrent.CancellationException expected) {
    }
    String script = "శ్రోతలకు నమస్కారం, ఇది 1,234 సంఖ్య. అంత సులభమా? కాదు కదా!";
    var plan = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO);
    var derived = DeliveryControls.expand(plan, true, false);
    DirectionContract.validate(script, plan);
    check(derived.get(0).pauseAfterMs == 110, "comma buffer");
    check(derived.get(1).text.contains("1,234"), "numeric comma is not a boundary");
    check(DeliveryControls.question("అంత సులభమా?”"), "quoted question");
    check(DeliveryControls.question("కాదు కదా!"), "rhetorical question");
    check(!DeliveryControls.question("వావ్!"), "not every exclamation rises");
    check(
        DeliveryControls.expand(
                NarrationDirector.planExact("శ్రోతలకు,, చెప్పండి.", NarrationDirector.Style.AUTO),
                true,
                false)
            .stream()
            .noneMatch(s -> s.text.equals(",")),
        "no empty comma chunks");
    File dir = Files.createTempDirectory("comma-audio").toFile();
    for (int rate : new int[] {22050, 24000}) {
      File a = new File(dir, "a.wav"), b = new File(dir, "b.wav"), out = new File(dir, "merge.wav");
      PcmWave.write(a, new float[] {.2f, .2f}, rate);
      PcmWave.write(b, new float[] {.3f, .3f}, rate);
      PcmWave.mergeRaw(List.of(a, b), derived.subList(0, 2), out, new AtomicBoolean());
      byte[] bytes = Files.readAllBytes(out.toPath());
      int zeros = rate * 110 / 1000 * 2;
      check(bytes.length == 44 + 8 + zeros, "exact comma padding length");
      for (int i = 48; i < 48 + zeros; i++) check(bytes[i] == 0, "zero PCM comma buffer");
    }
    System.out.println(
        "PASS delivery: independent pitch rise, untouched onset, duration, voiced gate, EQ,"
            + " cancellation, numeric commas and exact silence at both rates");
  }
}
