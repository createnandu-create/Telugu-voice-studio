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
    CALL_TO_ACTION, HOOK, PLAYFUL, WRY, SUSPENSE, RESPECTFUL, MOTIVATING, BRISK
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

    public boolean performanceCue() {
      switch (emotion) {
        case HOOK: case PLAYFUL: case WRY: case SUSPENSE: case RESPECTFUL: case MOTIVATING: case BRISK: return true;
        default: return false;
      }
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
      Emotion e = choose(s, style == null ? Style.AUTO : style, output.isEmpty() || (output.size()==1 && output.get(0).emotion==Emotion.WELCOME));
      if (e == Emotion.NEUTRAL && !output.isEmpty()) {
        Segment previous = output.get(output.size()-1);
        if (previous.emotion == Emotion.PLAYFUL && has(previous.text, "జోక్ చెబుతా", "తమాషా ఏంటంటే")) e = Emotion.PLAYFUL;
      }
      float rate = 1.0f, pitch = 1.0f;
      int pause = 240;
      switch (e) {
        case WELCOME:
          rate = !stripMarkers && output.isEmpty() ? .96f : 1.08f;
          pitch = 1.08f;
          break;
        case GUIDE:
          rate = 1.02f;
          pitch = 1.0f;
          break;
        case EXCITED:
          rate = 1.10f;
          pitch = 1.08f;
          break;
        case SURPRISE:
          rate = 1.03f;
          pitch = 1.08f;
          break;
        case SERIOUS:
          rate = 0.88f;
          pitch = 0.94f;
          break;
        case EMPATHY:
          rate = 0.92f;
          pitch = 0.92f;
          break;
        case QUESTION:
          rate = 0.97f;
          pitch = 1.13f;
          break;
        case CALL_TO_ACTION:
          rate = 1.06f;
          pitch = 1.10f;
          break;
        case HOOK: rate = .94f; pitch = 1.04f; pause = 270; break;
        case SUSPENSE: rate = .93f; pitch = .96f; pause = 300; break;
        case WRY: rate = .98f; pitch = .97f; break;
        case PLAYFUL: rate = 1.04f; pitch = 1.08f; break;
        case RESPECTFUL: rate = .95f; pitch = .99f; break;
        case MOTIVATING: rate = 1.04f; pitch = 1.05f; break;
        case BRISK: rate = 1.07f; pitch = 1.02f; break;
        case NEUTRAL:
        default:
          if (style == Style.NEWS) {
            rate = 0.95f;
          }
          if (style == Style.STORY) {
            rate = 0.93f;
          }
          break;
      }
      if (e == Emotion.QUESTION) {
        if (has(s, "ఎలా ఉన్నావురా", "ఎలా ఉన్నావు రా", "ఎలాగున్నావురా", "ఎలా ఉన్నావ్")) {
          rate = 1.02f;
          pitch = 1.055f;
        } else if (has(s, "అందరూ ఎలా ఉన్నారు", "మీరెలా ఉన్నారు", "మీరు ఎలా ఉన్నారు", "అందరూ బాగున్నారా")) {
          rate = 1.065f;
          pitch = 1.13f;
        }
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

  private static Emotion choose(String s, Style style, boolean opening) {
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
    if (has(
        t,
        "[sad]",
        "[బాధగా]",
        "బాధ",
        "విచారం",
        "కన్నీరు",
        "కష్టాలు",
        "దుఃఖం",
        "సమస్య ఏంటంటే",
        "పనిచేయకపోతే",
        "ఇబ్బంది",
        "దురదృష్టవశాత్తు")) return Emotion.EMPATHY;
    if (has(t, "మహోదయ", "గౌరవనీయ", "వినమ్రంగా", "హృదయపూర్వక ధన్యవాదాలు", "గురువుగారికి", "గౌరవపూర్వకంగా")) return Emotion.RESPECTFUL;
    if (has(t, "వ్యంగ్యంగా", "ఎటకారం", "భలే లాజిక్", "భలే తెలివి", "అబ్బో ఎంత", "అబ్బో ఎంతటి")) return Emotion.WRY;
    if (has(t, "హాహా", "హా హా", "భలే సరదా", "తమాషా ఏంటంటే", "నవ్వొస్తోంది", "చిన్న జోక్", "సరదాగా అంటున్నా")) return Emotion.PLAYFUL;
    if (opening && has(t, "ఊహించండి", "ఒక్క క్షణం", "ఒక్క క్షణమే", "మీకు తెలుసా", "ఎప్పుడైనా ఆలోచించారా")) return Emotion.HOOK;
    if (has(t, "ఆ తర్వాత ఏం", "తర్వాత ఏం జరిగింద", "అసలు మలుపు", "రహస్యం ఏంటంటే", "ఎవరూ ఊహించలేదు", "ఇంతలోనే")) return Emotion.SUSPENSE;
    if (has(t, "నీ వల్ల అవుతుంది", "మీ వల్ల అవుతుంది", "వదులుకోకండి", "మరోసారి ప్రయత్నిద్దాం", "ధైర్యంగా ముందుకు", "చిన్న అడుగుతో")) return Emotion.MOTIVATING;
    if (has(t, "చకచకా", "త్వరగా చేద్దాం", "ఫటాఫట్", "వెంటవెంటనే")) return Emotion.BRISK;
    if (t.trim().endsWith("?") || t.trim().endsWith("కదా!")) return Emotion.QUESTION;
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
        t,
        "[happy]",
        "[ఉత్సాహంగా]",
        "చాలా అద్భుత",
        "సూపర్",
        "అద్భుతమైన",
        "అద్భుతంగా",
        "ఎంత పర్ఫెక్ట్",
        "చూశారుగా",
        "ఆనందంగా",
        "అభినందనలు")) return Emotion.EXCITED;
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
    if (has(
        t,
        "నమస్కారం",
        "స్వాగతం",
        "హలో ఫ్రెండ్స్",
        "హాయ్ ఫ్రెండ్స్",
        "వెల్కమ్ బ్యాక్",
        "టుడే టాపిక్")) return Emotion.WELCOME;
    if (t.contains("?") || has(t, "ఎందుకు", "ఏంటి", "ఎలా", "నిజమేనా", "ఏమిటి"))
      return Emotion.QUESTION;
    // Punctuation alone is not evidence of excitement.
    Emotion cue = null;
    for (String word : t.replace("‌","").replace("‍","").split("[^ఁ-౿]+")) {
      Emotion next = LEARNED.get(word);
      if (next == null) continue;
      if (cue != null && cue != next) return Emotion.NEUTRAL;
      cue = next;
    }
    return cue == null ? Emotion.NEUTRAL : cue;
  }

  private static final java.util.Map<String,Emotion> LEARNED = loadEmotionWords();

  private static java.util.Map<String,Emotion> loadEmotionWords() {
    java.util.Map<String,Emotion> words = new java.util.HashMap<>();
    try (java.io.InputStream in = NarrationDirector.class.getResourceAsStream("emotion-words.tsv")) {
      if (in == null) return words;
      java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] pair = line.split("\t");
        if (pair.length == 2) words.put(pair[0], pair[1].equals("happy") ? Emotion.EXCITED : Emotion.EMPATHY);
      }
    } catch (java.io.IOException ignored) { words.clear(); }
    return words;
  }

  /**
   * Splits at strong boundaries before falling back to whitespace; avoids splitting surrogate
   * pairs.
   */
  static ArrayList<String> split(String value, int maxChars) {
    ArrayList<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    java.util.regex.Matcher brand = DynamicEnglishPhonetizer.BRAND.matcher(value);
    boolean hasBrand = brand.find();
    for (int i = 0; i < value.length(); i++) {
      while (hasBrand && i >= brand.end()) hasBrand = brand.find();
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
      if ((c == '.' && !innerDot && !(hasBrand && i >= brand.start())) || (c == '!' && !(i+1 < value.length() && value.charAt(i+1) == '=')) || c == '?' || c == '।' || c == '॥') {
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
      if (cut < 1) {
        // A split inside a name, numeral or Telugu cluster changes its pronunciation.
        cut = rest.indexOf(' ', max);
        if (cut < 0) cut = rest.length();
      }
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
