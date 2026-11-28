package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** CMU phonemes mapped to Telugu script; grapheme-to-phoneme fallback for out-of-dictionary words. */
public final class DynamicEnglishPhonetizer {
  private static volatile Map<String, String> lexicon = Collections.emptyMap();
  private static final String[] LETTERS = {
    "ఏ", "బీ", "సీ", "డీ", "ఈ", "ఎఫ్", "జీ", "హెచ్", "ఐ", "జే", "కే", "ఎల్", "ఎం", "ఎన్", "ఓ", "పీ",
    "క్యూ", "ఆర్", "ఎస్", "టీ", "యూ", "వీ", "డబ్ల్యూ", "ఎక్స్", "వై", "జెడ్"
  };
  private static final Map<String, String> WORDS = new LinkedHashMap<>();

  static {
    WORDS.put("sankranti", "సంక్రాంతి");
    WORDS.put("muggu", "ముగ్గు");
    WORDS.put("haritha", "హరిత");
    WORDS.put("teacher", "టీచర్");
    WORDS.put("regular", "రెగ్యులర్");
    WORDS.put("color", "కలర్");
    WORDS.put("pro", "ప్రో");
    WORDS.put("free", "ఫ్రీ");
    WORDS.put("link", "లింక్");
    WORDS.put("download", "డౌన్‌లోడ్");
    WORDS.put("easy", "ఈజీ");
    WORDS.put("font", "ఫాంట్");
    WORDS.put("app","యాప్");
    WORDS.put("apps","యాప్స్");
    WORDS.put("pixellab", "పిక్సెల్ ల్యాబ్");
    WORDS.put("kinemaster", "కైన్ మాస్టర్");
    WORDS.put("alight", "అలైట్");
    WORDS.put("motion", "మోషన్");
    WORDS.put("mind", "మైండ్");
    WORDS.put("blowing", "బ్లోయింగ్");
    WORDS.put("tech", "టెక్");
    WORDS.put("update", "అప్‌డేట్");
    WORDS.put("export", "ఎక్స్‌పోర్ట్");
    WORDS.put("explanation", "ఎక్స్‌ప్లనేషన్");
    WORDS.put("main", "మెయిన్");
    WORDS.put("problem", "ప్రాబ్లమ్");
    WORDS.put("vocal", "వోకల్");
    WORDS.put("subscribe", "సబ్‌స్క్రైబ్");
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

  private static final Map<String, String> CONSONANTS =
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
  public static String phones(String sequence) {
    StringBuilder b = new StringBuilder();
    boolean pending = false;
    for (String raw : sequence.split("\\s+")) {
      String p = raw.replaceAll("[012]", "");
      String v = vowel(p, pending);
      if (v != null) {
        b.append(v);
        pending = false;
      } else {
        String c = CONSONANTS.get(p);
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

  private static final Set<String> INITIALISMS = Set.of("USB","APK","AI","PDF","DNA","API","CPU","GPU","UI","URL","LED","HDMI","HTML","ID");
  // Longest common graphemes first. The resulting phonemes share the CMU Telugu renderer.
  private static final String[][] GRAPHEMES = {
    {"tion","SH AH N"}, {"sion","ZH AH N"}, {"tch","CH"}, {"igh","AY"}, {"sch","S K"},
    {"ch","CH"}, {"sh","SH"}, {"th","TH"}, {"ph","F"}, {"ng","NG"}, {"qu","K W"},
    {"ck","K"}, {"dh","DH"}, {"bh","B HH"}, {"kh","K HH"}, {"ee","IY"}, {"ea","IY"},
    {"oo","UW"}, {"ai","EY"}, {"ay","EY"}, {"oa","OW"}, {"ou","AW"}, {"oi","OY"},
    {"oy","OY"}, {"au","AO"}, {"ar","AA R"}, {"or","AO R"}, {"er","ER"}
  };
  private static final String[] SINGLE = ("AE B K D EH F G HH IH JH K L M N OW P K R S T AH V W K_S Y Z").split(" ");

  private static String approximate(String word) {
    StringJoiner out = new StringJoiner(" ");
    for (int at = 0; at < word.length();) {
      char c = word.charAt(at);
      if (c < 'a' || c > 'z') { at++; continue; }
      String sound = null;
      for (String[] rule : GRAPHEMES) {
        if (word.startsWith(rule[0], at)) {
          sound = rule[1];
          at += rule[0].length();
          break;
        }
      }
      if (sound == null) {
        sound = SINGLE[c - 'a'].replace('_',' ');
        if (c == 'y' && at > 0 && (at+1 == word.length() || "aeiou".indexOf(word.charAt(at+1)) < 0))
          sound = at+1 == word.length() ? "IY" : "IH";
        if (c == 'c' && at+1 < word.length() && "eiy".indexOf(word.charAt(at+1)) >= 0) sound = "S";
        at++;
      }
      out.add(sound);
    }
    return phones(out.toString());
  }

  static String letter(char c) { return LETTERS[Character.toUpperCase(c) - 'A']; }

  private static String word(String word) {
    String lower = word.toLowerCase(Locale.ROOT);
    if (word.equals("I")) return "ఐ";
    if (word.equals("AI")) return "ఏఐ";
    if (word.equals("VN")) return "వి ఎన్";
    if (INITIALISMS.contains(word) || word.matches("[B-DF-HJ-NP-TV-XZ]{2,4}")) {
      StringJoiner names = new StringJoiner(" ");
      for (char c : word.toCharArray()) names.add(letter(c));
      return names.toString();
    }
    if (word.length() == 1 && !"ai".contains(lower)) return letter(word.charAt(0));
    String known = WORDS.get(lower);
    if (known == null) known = lexicon.get(lower);
    if (known != null && !known.isEmpty()) return known;
    if (word.length() == 1 && Character.isUpperCase(word.charAt(0))) return letter(word.charAt(0));
    return approximate(lower);
  }

  static final Pattern BRAND = Pattern.compile(
      "(?iu)(?<![\\p{L}\\p{M}\\p{N}])a[.\\s]*i[.\\s-]*tech[\\s-]*app(s?)(?![A-Za-z])");

  public static String apply(String source) {
    source = source.replace('’', (char) 39).replaceAll("[“”‘\"]", " ");
    Matcher brand = BRAND.matcher(source);
    StringBuffer branded = new StringBuffer();
    while (brand.find()) {
      brand.appendReplacement(branded, "ఏఐటెక్ యాప్");
    }
    source = brand.appendTail(branded).toString();
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
    return b.toString().replace((char) 39, ' ').trim().replaceAll(" +", " ").replaceAll("(?<=[\\p{L}\\p{M}])[-–](?=[\\p{L}\\p{M}])", " ");
  }

  private static boolean isLatin(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private DynamicEnglishPhonetizer() {}
}
