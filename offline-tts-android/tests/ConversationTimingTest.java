package in.aitechapp.teluguoffline;

import java.util.Arrays;

public final class ConversationTimingTest {
  public static void main(String[] args) throws Exception {
    String phrase = "ఇప్పుడు మనం ఇక్కడ చూద్దాం.";
    double target = ConversationTiming.targetSeconds(phrase, 1);
    int hz = 24000;
    float[] first = new float[(int) (hz * target * 1.18)];
    Arrays.fill(first, .2f);
    float[] better = Arrays.copyOf(first, (int) (hz * target));
    int[] calls = {0};
    ConversationTiming.Synthesizer model = rate -> {
      calls[0]++;
      return rate == 1 ? first : better;
    };
    if (ConversationTiming.render(phrase, 1, hz, false, model) != first || calls[0] != 1)
      throw new AssertionError("Off should use exactly the original take");
    if (ConversationTiming.render(phrase, 1, hz, true, model) != better || calls[0] != 3)
      throw new AssertionError("On should select the closer intact native take");
    if (ConversationTiming.render(phrase, 1, hz, true, r -> r == 1 ? first : new float[better.length]) != first)
      throw new AssertionError("A silent retry must never replace speech");
    if (ConversationTiming.render(phrase, 1, hz, true, rate -> {
      if (rate == 1) return first;
      throw new ConversationTiming.UnusableTake("No stop token");
    }) != first) throw new AssertionError("Preserve speech when the optional take fails");
    if (ConversationTiming.targetSeconds("hello world", 1) != 0)
      throw new AssertionError("Do not estimate unexpanded English");
    System.out.println("PASS conversational timing, bypass, alternate take and silence guard");
  }
}
