package in.aitechapp.teluguoffline;

import java.util.List;

/** Mandatory boundary for manual or future model-produced plans. Never trust model text. */
public final class DirectionContract {
  public static void validate(String original, List<NarrationDirector.Segment> plan) {
    List<String> source = NarrationDirector.split(original.replace('\r', ' '), 175);
    int index = 0;
    if (plan == null || plan.isEmpty())
      throw new IllegalArgumentException("Add a Telugu script first.");
    for (NarrationDirector.Segment s : plan) {
      if (s == null
          || s.text == null
          || s.text.trim().isEmpty()
          || s.text.length() > 500
          || s.emotion == null
          || !Float.isFinite(s.rate)
          || s.rate < 0.75f
          || s.rate > 1.25f
          || !Float.isFinite(s.pitch)
          || s.pitch < 0.8f
          || s.pitch > 1.2f
          || s.pauseAfterMs < 0
          || s.pauseAfterMs > 2000)
        throw new IllegalArgumentException(
            "Invalid direction. Rate: 0.75–1.25; pitch: 0.8–1.2; pause: 0–2000 ms.");
      if (index >= source.size() || !s.text.equals(source.get(index++)))
        throw new IllegalArgumentException(
            "Direction changed the script. Restore the original words and boundaries.");
    }
    // Only the existing deterministic boundaries are allowed; even changed word spacing is
    // rejected.
    if (index != source.size())
      throw new IllegalArgumentException(
          "Direction changed the script. Restore the original words.");
  }

  private static String compact(String s) {
    StringBuilder out = new StringBuilder();
    s.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(out::appendCodePoint);
    return out.toString();
  }

  private DirectionContract() {}
}
