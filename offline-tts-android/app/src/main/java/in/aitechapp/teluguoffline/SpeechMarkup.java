package in.aitechapp.teluguoffline;

import java.util.regex.*;

/** Removes presentation markup from speech only. This is not a mathematical evaluator. */
public final class SpeechMarkup {
  private static final Pattern FRACTION =
      Pattern.compile("\\\\(?:dfrac|tfrac|frac)\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}");

  public static boolean math(String text) {
    return text.contains("$")
        || text.contains("\\(")
        || text.contains("\\[")
        || Pattern.compile("\\\\(?:frac|dfrac|tfrac|sqrt|times|div|cdot|alpha|beta|pi)\\b")
            .matcher(text)
            .find();
  }

  public static String clean(String original) {
    boolean math = math(original);
    String text = original;
    // Only innermost fractions are converted at each pass; nested inputs have a bounded cost.
    for (int pass = 0; pass < 16; pass++) {
      Matcher m = FRACTION.matcher(text);
      if (!m.find()) break;
      StringBuffer out = new StringBuffer();
      do {
        m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + " బై " + m.group(2)));
      } while (m.find());
      m.appendTail(out);
      text = out.toString();
    }
    text =
        text.replaceAll(
            "\\\\(?:text|textrm|textbf|textit|mathrm|mathbf|mathit|operatorname|left|right)\\b",
            " ");
    String[][] commands = {
      {"sqrt", "వర్గమూలం"},
      {"times", "గుణించు"},
      {"cdot", "గుణించు"},
      {"div", "భాగించు"},
      {"alpha", "ఆల్ఫా"},
      {"beta", "బీటా"},
      {"pi", "పై"},
      {"theta", "థీటా"},
      {"leq", "తక్కువ లేదా సమానం"},
      {"geq", "ఎక్కువ లేదా సమానం"},
      {"neq", "సమానం కాదు"},
      {"approx", "దాదాపు సమానం"},
      {"infty", "అనంతం"}
    };
    for (String[] c : commands) text = text.replaceAll("\\\\" + c[0] + "\\b", " " + c[1] + " ");
    text = text.replaceAll("[(){}\\[\\]\\\\$]", " ");
    if (math) {
      text =
          text.replace("+", " ప్లస్ ")
              .replace("-", " మైనస్ ")
              .replace("=", " సమానం ")
              .replace("/", " బై ")
              .replace("^", " ఘాతం ")
              .replace("_", " సబ్ స్క్రిప్ట్ ");
      // Isolated common variable names, never rewrite an English word or a Telugu word.
      String[][] letters = {
        {"x", "ఎక్స్"}, {"y", "వై"}, {"z", "జెడ్"}, {"a", "ఏ"}, {"b", "బీ"}, {"n", "ఎన్"}
      };
      for (String[] c : letters)
        text = text.replaceAll("(?<![\\p{L}\\p{M}])" + c[0] + "(?![\\p{L}\\p{M}])", c[1]);
    }
    // Unknown command names/content survive, so validation requests an explicit spoken form
    // instead of silently dropping factual words. No unrecognized IDs reach the neural runtime.
    return text.replaceAll("[ \\t]+", " ").trim();
  }

  private SpeechMarkup() {}
}
