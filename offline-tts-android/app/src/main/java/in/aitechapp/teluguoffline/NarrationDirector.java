package in.aitechapp.teluguoffline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fully offline, deterministic first-stage narration director. No AI model is loaded here: intent
 * detection is heuristic and editable. Future on-device LLM engines can return the same Segment
 * contract.
 */
public final class NarrationDirector {
  public enum Style {
    AUTO,
    TUTORIAL,
    STORY,
    NEWS,
    REVIEW
  }

  public enum Emotion {
    NEUTRAL,
    WELCOME,
    GUIDE,
    EXCITED,
    SURPRISE,
    SERIOUS,
    EMPATHY,
    QUESTION,
    CALL_TO_ACTION
  }

  public static final class Segment {
    public final String text;
    public final Emotion emotion;
    public final float rate;
    public final float pitch;
    public final int pauseAfterMs;

    public Segment(String text, Emotion emotion, float rate, float pitch, int pauseAfterMs) {
      this.text = text;
      this.emotion = emotion;
      this.rate = rate;
      this.pitch = pitch;
      this.pauseAfterMs = pauseAfterMs;
    }

    public String label() {
      return emotion.name().replace('_', ' ');
    }

    public String emotionTag() {
      switch (emotion) {
        case EXCITED:
          return "<happy>";
        case SURPRISE:
          return "<surprise>";
        case EMPATHY:
          return "<sad>";
        default:
          return "";
      }
    }
  }

  private NarrationDirector() {}

  public static List<Segment> plan(String script, Style style) {
    if (script == null || script.trim().isEmpty()) return Collections.emptyList();
    return planInternal(script, style, true);
  }

  /** Studio mode never interprets the author's words as inline control tags. */
  public static List<Segment> planExact(String script, Style style) {
    if (script == null || script.trim().isEmpty()) return Collections.emptyList();
    return planInternal(script, style, false);
  }

  private static List<Segment> planInternal(String script, Style style, boolean stripMarkers) {
    ArrayList<String> sentences = split(script.replace('\r', ' '), 175);
    ArrayList<Segment> output = new ArrayList<>();
    for (String sentence : sentences) {
      String s = sentence.trim();
      if (s.isEmpty()) continue;
      Emotion e = choose(s, style == null ? Style.AUTO : style);
      float rate = 1.0f, pitch = 1.0f;
      int pause = 190;
      switch (e) {
        case WELCOME:
          rate = 0.96f;
          pitch = 1.04f;
          pause = 240;
          break;
        case GUIDE:
          rate = 0.94f;
          pitch = 1.02f;
          pause = 260;
          break;
        case EXCITED:
          rate = 1.10f;
          pitch = 1.15f;
          pause = 230;
          break;
        case SURPRISE:
          rate = 1.03f;
          pitch = 1.16f;
          pause = 330;
          break;
        case SERIOUS:
          rate = 0.88f;
          pitch = 0.94f;
          pause = 370;
          break;
        case EMPATHY:
          rate = 0.86f;
          pitch = 0.96f;
          pause = 360;
          break;
        case QUESTION:
          rate = 0.97f;
          pitch = 1.06f;
          pause = 280;
          break;
        case CALL_TO_ACTION:
          rate = 1.06f;
          pitch = 1.10f;
          pause = 290;
          break;
        case NEUTRAL:
        default:
          if (style == Style.NEWS) {
            rate = 0.95f;
            pause = 250;
          }
          if (style == Style.STORY) {
            rate = 0.93f;
            pause = 290;
          }
          break;
      }
      // Human-readable direction markers must not be spoken aloud by the TTS engine.
      String spoken =
          s.replaceAll("\\[(?i:happy|sad|surprise|warning|serious|guide)\\]", "")
              .replace("[గంభీరంగా]", "")
              .replace("[బాధగా]", "")
              .replace("[ఆశ్చర్యం]", "")
              .replace("[ఉత్సాహంగా]", "")
              .replace("[సూచన]", "")
              .trim();
      if (!stripMarkers) spoken = s;
      if (!spoken.isEmpty()) output.add(new Segment(spoken, e, rate, pitch, pause));
    }
    return output;
  }

  private static Emotion choose(String s, Style style) {
    String t = s.toLowerCase(Locale.ROOT);
    if (has(
        t,
        "[serious]",
        "[warning]",
        "[గంభీరంగా]",
        "జాగ్రత్త",
        "హెచ్చరిక",
        "అపాయం",
        "మోసం",
        "ప్రమాదం",
        "చేయవద్దు")) return Emotion.SERIOUS;
    if (has(t, "[sad]", "[బాధగా]", "బాధ", "విచారం", "కన్నీరు", "కష్టాలు", "దుఃఖం"))
      return Emotion.EMPATHY;
    if (has(
        t,
        "[surprise]",
        "[ఆశ్చర్యం]",
        "వావ్",
        "అరే",
        "నమ్మలేక",
        "షాక్",
        "అసలు నమ్మ",
        "అద్భుతం జరిగింది")) return Emotion.SURPRISE;
    if (has(
        t, "[happy]", "[ఉత్సాహంగా]", "చాలా అద్భుత", "సూపర్", "అద్భుతమైన", "ఆనందంగా", "అభినందనలు"))
      return Emotion.EXCITED;
    if (has(
        t,
        "[guide]",
        "[సూచన]",
        "క్లిక్",
        "నొక్కండి",
        "ట్యాప్",
        "ఎంచుకోండి",
        "సెలెక్ట్",
        "సెట్టింగ్స్ లోకి",
        "చూడండి")) return Emotion.GUIDE;
    if (has(
        t,
        "సబ్‌స్క్రైబ్",
        "సబ్ స్క్రైబ్",
        "subscribe",
        "లైక్ చేయండి",
        "షేర్ చేయండి",
        "ఫాలో అవ్వండి")) return Emotion.CALL_TO_ACTION;
    if (has(t, "నమస్కారం", "స్వాగతం", "హలో ఫ్రెండ్స్")) return Emotion.WELCOME;
    if (t.contains("?") || has(t, "ఎందుకు", "ఏంటి", "ఎలా", "నిజమేనా", "ఏమిటి"))
      return Emotion.QUESTION;
    // Punctuation alone is not evidence of excitement.
    return Emotion.NEUTRAL;
  }

  /**
   * Splits at strong boundaries before falling back to whitespace; avoids splitting surrogate
   * pairs.
   */
  static ArrayList<String> split(String value, int maxChars) {
    ArrayList<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\n') {
        addCapped(out, current.toString(), maxChars);
        current.setLength(0);
        continue;
      }
      current.append(c);
      boolean innerDot =
          c == '.'
              && i > 0
              && i + 1 < value.length()
              && !Character.isWhitespace(value.charAt(i - 1))
              && !Character.isWhitespace(value.charAt(i + 1));
      if ((c == '.' && !innerDot) || c == '!' || c == '?' || c == '।' || c == '॥') {
        // Preserve punctuation and next sentence independence.
        addCapped(out, current.toString(), maxChars);
        current.setLength(0);
      }
    }
    addCapped(out, current.toString(), maxChars);
    return out;
  }

  private static void addCapped(List<String> out, String source, int max) {
    String rest = source.trim();
    while (!rest.isEmpty()) {
      if (rest.length() <= max) {
        out.add(rest);
        return;
      }
      int cut = rest.lastIndexOf(' ', max);
      if (cut < max / 3) cut = max;
      if (cut < rest.length() && cut > 0 && Character.isHighSurrogate(rest.charAt(cut - 1))) cut--;
      if (cut <= 0) cut = max;
      String part = rest.substring(0, cut).trim();
      if (!part.isEmpty()) out.add(part);
      rest = rest.substring(cut).trim();
    }
  }

  private static boolean has(String s, String... candidates) {
    for (String x : candidates) if (s.contains(x)) return true;
    return false;
  }
}
