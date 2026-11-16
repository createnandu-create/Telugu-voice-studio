package in.aitechapp.teluguoffline;

import java.util.*;

/** Deterministic, editable render controls; never rewrites the saved script or invents phonemes. */
public final class DeliveryControls {
  public static final int COMMA_MS = 180;

  public static boolean question(String text) {
    String end = text.trim().replaceAll("[\\\"'”’]+$", "");
    return end.endsWith("?") || end.endsWith("కదా!");
  }

  public static List<NarrationDirector.Segment> expand(
      List<NarrationDirector.Segment> original, boolean commas, boolean careful) {
    List<NarrationDirector.Segment> out = new ArrayList<>();
    for (NarrationDirector.Segment s : original) {
      int start = 0;
      for (int i = 0; i < s.text.length(); i++) {
        char c = s.text.charAt(i);
        boolean numeric =
            i > 0
                && i + 1 < s.text.length()
                && Character.isDigit(s.text.charAt(i - 1))
                && Character.isDigit(s.text.charAt(i + 1));
        if (commas
            && c == ','
            && !numeric
            && s.text.substring(start, i + 1).codePoints().anyMatch(Character::isLetterOrDigit)) {
          add(out, s, s.text.substring(start, i + 1), COMMA_MS, careful);
          start = i + 1;
        }
      }
      if (start < s.text.length()) add(out, s, s.text.substring(start), s.pauseAfterMs, careful);
    }
    return out;
  }

  /** Word/phrase boundaries for explicit duration holds; original plan remains untouched. */
  public static List<NarrationDirector.Segment> accents(List<NarrationDirector.Segment> input) {
    List<NarrationDirector.Segment> out = new ArrayList<>();
    for (NarrationDirector.Segment s : input) {
      String target = null;
      float hold = 1f;
      int before = 0;
      if (s.emotion == NarrationDirector.Emotion.GUIDE) {
        for (String t : new String[] {"క్లిక్ చేయండి", "సెలెక్ట్ చేసుకోండి", "నొక్కండి", "చూడండి"})
          if (s.text.contains(t)) {
            target = t;
            before = 110;
            break;
          }
      } else if (s.emotion == NarrationDirector.Emotion.WELCOME) {
        for (String t : new String[] {"AiTechApps", "ఏఐ టెక్ యాప్స్"})
          if (s.text.contains(t)) {
            target = t;
            hold = 1.15f;
            break;
          }
      } else if (s.emotion == NarrationDirector.Emotion.SURPRISE && s.text.contains("వావ్")) {
        target = "వావ్";
        hold = 1.30f;
      }
      if (target == null) {
        out.add(s);
        continue;
      }
      int start = s.text.indexOf(target), end = start + target.length();
      if (start > 0) add(out, s, s.text.substring(0, start), before, false);
      if ((start > 0 && !Character.isWhitespace(s.text.charAt(start - 1)))
          || (end < s.text.length()
              && (Character.isLetterOrDigit(s.text.charAt(end))
                  || Character.getType(s.text.charAt(end)) == Character.NON_SPACING_MARK
                  || Character.getType(s.text.charAt(end)) == Character.COMBINING_SPACING_MARK))) {
        out.add(s);
        continue;
      }
      // Attach adjacent punctuation; never synthesize a punctuation-only chunk.
      while (end < s.text.length()
          && !Character.isLetterOrDigit(s.text.charAt(end))
          && !Character.isWhitespace(s.text.charAt(end))) end++;
      boolean tail = !s.text.substring(end).trim().isEmpty();
      out.add(
          new NarrationDirector.Segment(
              s.text.substring(start, end),
              s.emotion,
              Math.max(.5f, s.rate / hold),
              s.pitch,
              tail ? 0 : s.pauseAfterMs));
      if (tail) add(out, s, s.text.substring(end), s.pauseAfterMs, false);
    }
    return out;
  }

  public static boolean action(String s) {
    return s.trim().matches("^(క్లిక్ చేయండి|సెలెక్ట్ చేసుకోండి|నొక్కండి|చూడండి)[.!?।]*$");
  }

  private static void add(
      List<NarrationDirector.Segment> out,
      NarrationDirector.Segment source,
      String text,
      int pause,
      boolean careful) {
    if (text.trim().isEmpty()) return;
    float rate = source.rate;
    // This is a slower reading option, not a phoneme correctness guarantee.
    if (careful && (text.indexOf('్') >= 0 || text.indexOf('శ') >= 0 || text.indexOf('ష') >= 0))
      rate = Math.max(.75f, rate * .92f);
    out.add(new NarrationDirector.Segment(text.trim(), source.emotion, rate, source.pitch, pause));
  }

  private DeliveryControls() {}
}
