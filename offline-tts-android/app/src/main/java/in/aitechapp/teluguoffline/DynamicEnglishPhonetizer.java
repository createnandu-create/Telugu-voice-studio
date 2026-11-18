package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** CMU phonemes mapped to Telugu script; deterministic letter-name fallback for unseen words. */
public final class DynamicEnglishPhonetizer {
  private static volatile Map<String, String> lexicon = Collections.emptyMap();
  private static final String[] LETTERS = {
    "ఏ", "బీ", "సీ", "డీ", "ఈ", "ఎఫ్", "జీ", "హెచ్", "ఐ", "జే", "కే", "ఎల్", "ఎం", "ఎన్", "ఓ", "పీ",
    "క్యూ", "ఆర్", "ఎస్", "టీ", "యూ", "వీ", "డబ్ల్యూ", "ఎక్స్", "వై", "జెడ్"
  };
  private static final Map<String, String> WORDS = new LinkedHashMap<>();

  static {
    WORDS.put("aitechapps", "ఏఐ టెక్ యాప్స్");
    WORDS.put("aitechapp", "ఏఐ టెక్ యాప్");
    WORDS.put("pixellab", "పిక్సెల్ ల్యాబ్");
    WORDS.put("kinemaster", "కైన్ మాస్టర్");
    WORDS.put("alight", "అలైట్");
    WORDS.put("motion", "మోషన్");
    WORDS.put("vn", "వి ఎన్");
    WORDS.put("mind", "మైండ్");
    WORDS.put("blowing", "బ్లోయింగ్");
    WORDS.put("tech", "టెక్");
    WORDS.put("update", "అప్‌డేట్");
    WORDS.put("export", "ఎక్స్‌పోర్ట్");
    WORDS.put("explanation", "ఎక్స్‌ప్లనేషన్");
    WORDS.put("main", "మెయిన్");
    WORDS.put("problem", "ప్రాబ్లమ్");
    WORDS.put("vocal", "వోకల్");
    WORDS.put("dna", "డీ ఎన్ ఏ");
    WORDS.put("ai", "ఏఐ");
    WORDS.put("youtube", "యూట్యూబ్");
    WORDS.put("hello", "హలో");
    WORDS.put("friends", "ఫ్రెండ్స్");
  }

  public static synchronized void load(InputStream compressed) throws IOException {
    if (!lexicon.isEmpty()) {
      compressed.close();
      return;
    }
    Map<String, String> words = new HashMap<>();
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(compressed), java.nio.charset.StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        int p = line.indexOf(' ');
        if (p < 1) continue;
        String word = line.substring(0, p);
        if (word.contains("(")) continue;
        words.putIfAbsent(word.toLowerCase(Locale.ROOT), phones(line.substring(p + 1).trim()));
      }
    }
    lexicon = Collections.unmodifiableMap(words);
  }

  private static String vowel(String p, boolean sign) {
    String[] a;
    switch (p) {
      case "AA":
        a = new String[] {"ఆ", "ా"};
        break;
      case "AE":
      case "EH":
        a = new String[] {"ఎ", "ె"};
        break;
      case "AH":
        a = new String[] {"అ", ""};
        break;
      case "AO":
        a = new String[] {"ఆ", "ా"};
        break;
      case "AW":
        a = new String[] {"ఔ", "ౌ"};
        break;
      case "AY":
        a = new String[] {"ఐ", "ై"};
        break;
      case "ER":
        a = new String[] {"అర్", "ర్"};
        break;
      case "EY":
        a = new String[] {"ఏ", "ే"};
        break;
      case "IH":
        a = new String[] {"ఇ", "ి"};
        break;
      case "IY":
        a = new String[] {"ఈ", "ీ"};
        break;
      case "OW":
        a = new String[] {"ఓ", "ో"};
        break;
      case "OY":
        a = new String[] {"ఒయ్", "ొయ్"};
        break;
      case "UH":
        a = new String[] {"ఉ", "ు"};
        break;
      case "UW":
        a = new String[] {"ఊ", "ూ"};
        break;
      default:
        return null;
    }
    return a[sign ? 1 : 0];
  }

  public static String phones(String sequence) {
    Map<String, String> consonants =
        Map.ofEntries(
            Map.entry("B", "బ"),
            Map.entry("CH", "చ"),
            Map.entry("D", "డ"),
            Map.entry("DH", "ద"),
            Map.entry("F", "ఫ"),
            Map.entry("G", "గ"),
            Map.entry("HH", "హ"),
            Map.entry("JH", "జ"),
            Map.entry("K", "క"),
            Map.entry("L", "ల"),
            Map.entry("M", "మ"),
            Map.entry("N", "న"),
            Map.entry("NG", "ంగ"),
            Map.entry("P", "ప"),
            Map.entry("R", "ర"),
            Map.entry("S", "స"),
            Map.entry("SH", "ష"),
            Map.entry("T", "ట"),
            Map.entry("TH", "థ"),
            Map.entry("V", "వ"),
            Map.entry("W", "వ"),
            Map.entry("Y", "య"),
            Map.entry("Z", "జ"),
            Map.entry("ZH", "ష"));
    StringBuilder b = new StringBuilder();
    boolean pending = false;
    for (String raw : sequence.split("\\s+")) {
      String p = raw.replaceAll("[012]", "");
      String v = vowel(p, pending);
      if (v != null) {
        b.append(v);
        pending = false;
      } else {
        String c = consonants.get(p);
        if (c != null) {
          if (pending) b.append('్');
          b.append(c);
          pending = true;
        }
      }
    }
    if (pending) b.append('్');
    return b.toString();
  }

  private static String word(String word) {
    String known = WORDS.get(word.toLowerCase(Locale.ROOT));
    if (known != null) return known;
    if (!word.equals(word.toUpperCase(Locale.ROOT))) {
      known = lexicon.get(word.toLowerCase(Locale.ROOT));
      if (known != null && !known.isEmpty()) return known;
    }
    StringBuilder b = new StringBuilder();
    for (char c : word.toUpperCase(Locale.ROOT).toCharArray()) {
      if (c < 'A' || c > 'Z') continue;
      if (b.length() > 0) b.append(' ');
      b.append(LETTERS[c - 'A']);
    }
    return b.toString();
  }

  public static String apply(String source) {
    Matcher m = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?").matcher(source);
    StringBuffer b = new StringBuffer();
    while (m.find()) {
      String value = word(m.group());
      if (m.group().equalsIgnoreCase("the")) {
        int next = m.end();
        while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
        int end = next;
        while (end < source.length() && isLatin(source.charAt(end))) end++;
        if (end > next) {
          String onset = word(source.substring(next, end));
          if (!onset.isEmpty()) value = onset.charAt(0) >= 'అ' && onset.charAt(0) <= 'ఔ' ? "దీ" : "ద";
        }
      }
      m.appendReplacement(b, Matcher.quoteReplacement(value));
    }
    m.appendTail(b);
    return b.toString().replaceAll("(?<=[\\p{L}\\p{M}])[-–](?=[\\p{L}\\p{M}])", " ");
  }

  private static boolean isLatin(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private DynamicEnglishPhonetizer() {}
}
