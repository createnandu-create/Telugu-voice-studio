package in.aitechapp.teluguoffline;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Speech-only transformations. The editor, plan and exported project keep the original script. */
public final class PronunciationLexicon {
  private static final String[] DIGITS = {
    "సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది"
  };

  public static String apply(String original, Map<String, String> dictionary, boolean readDigits) {
    String text = Normalizer.normalize(original, Normalizer.Form.NFC);
    // Only leading invisible formatting markers; do not modify legitimate joiners within Telugu.
    while (!text.isEmpty() && (text.charAt(0) == '\\uFEFF'
        || text.charAt(0) == '\\u200C' || text.charAt(0) == '\\u200B'
        || text.charAt(0) == '\\u200D')) {
      text = text.substring(1);
    }
    List<String> keys = new ArrayList<>(dictionary.keySet());
    keys.sort(Comparator.comparingInt(String::length).reversed());
    if (!keys.isEmpty()) {
      StringJoiner alternatives = new StringJoiner("|");
      for (String key : keys) {
        if (key.trim().isEmpty()
            || dictionary.get(key) == null
            || dictionary.get(key).trim().isEmpty())
          throw new IllegalArgumentException(
              "Pronunciation entries need both original and spoken text.");
        alternatives.add(Pattern.quote(Normalizer.normalize(key, Normalizer.Form.NFC)));
      }
      Pattern p =
          Pattern.compile(
              "(?<![\\p{L}\\p{M}\\p{N}_])(?:" + alternatives + ")(?![\\p{L}\\p{M}\\p{N}_])");
      Matcher m = p.matcher(text);
      StringBuffer out = new StringBuffer();
      while (m.find()) {
        String replacement = dictionary.get(m.group());
        if (replacement == null) {
          for (String key : keys)
            if (Normalizer.normalize(key, Normalizer.Form.NFC).equals(m.group())) {
              replacement = dictionary.get(key);
              break;
            }
        }
        m.appendReplacement(out, Matcher.quoteReplacement(replacement));
      }
      m.appendTail(out);
      text = out.toString();
    }
    if (readDigits) {
      StringBuilder out = new StringBuilder();
      text.codePoints()
          .forEach(
              c -> {
                int digit = Character.digit(c, 10);
                if (digit >= 0) out.append(' ').append(DIGITS[digit]).append(' ');
                else out.appendCodePoint(c);
              });
      text = out.toString().replaceAll("[ \\t]+", " ").trim();
    }
    return text;
  }

  private PronunciationLexicon() {}
}
