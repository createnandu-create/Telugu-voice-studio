package in.aitechapp.teluguoffline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** English technical readings written in the Telugu alphabet accepted by the offline models. */
final class EnglishNotation {
  private static final String[] SMALL = ("జీరో వన్ టూ త్రీ ఫోర్ ఫైవ్ సిక్స్ సెవెన్ ఎయిట్ నైన్ టెన్ "
      + "ఇలెవెన్ ట్వెల్వ్ థర్టీన్ ఫోర్టీన్ ఫిఫ్టీన్ సిక్స్‌టీన్ సెవెన్‌టీన్ ఎయిటీన్ నైన్‌టీన్").split(" ");
  private static final String[] TENS = {"","","ట్వెంటీ","థర్టీ","ఫోర్టీ","ఫిఫ్టీ","సిక్స్‌టీ","సెవెంటీ","ఎయిటీ","నైంటీ"};
  private static final String N = "[0-9]+(?:\\.[0-9]+)?";
  private static final Pattern TOKEN = Pattern.compile(
      "(?i)\\\\sqrt\\s*\\{\\s*(" + N + ")\\s*\\}|√\\s*(" + N + ")"
      + "|(?<![a-z0-9:.])(" + N + ")\\s*:\\s*(" + N + ")(?![:0-9a-z])"
      + "|(?<![a-z0-9.])(" + N + ")\\s*(%|[-–]D|K)(?![a-z0-9])"
      + "|(?<![<>=!])=(?!=)\\s*([-−]?" + N + ")(?![a-z0-9])"
      + "|(?<![a-z])vocal\\s+dna(?![a-z])");
  private static String cardinal(long n) {
    if (n < 20) return SMALL[(int)n];
    if (n < 100) return TENS[(int)n/10] + rest(n%10);
    long scale = n >= 1000000000L ? 1000000000L : n >= 1000000 ? 1000000 : n >= 1000 ? 1000 : 100;
    String unit = scale == 100 ? "హండ్రెడ్" : scale == 1000 ? "థౌజండ్" : scale == 1000000 ? "మిలియన్" : "బిలియన్";
    return cardinal(n/scale) + " " + unit + rest(n%scale);
  }
  private static String rest(long n) { return n == 0 ? "" : " " + cardinal(n); }
  private static String digits(String n) {
    StringBuilder out=new StringBuilder();
    for (char c:n.toCharArray()) { if(out.length()>0) out.append(' '); out.append(SMALL[c-'0']); }
    return out.toString();
  }
  private static String number(String n) {
    if (n.startsWith("-") || n.startsWith("−")) return "మైనస్ " + number(n.substring(1));
    int dot=n.indexOf('.');
    if (dot>=0) return number(n.substring(0,dot))+" పాయింట్ "+digits(n.substring(dot+1));
    return n.length()>12 || n.length()>1 && n.startsWith("0") ? digits(n) : cardinal(Long.parseLong(n));
  }
  private static final String ATOM = "(?:" + N + "(?:[a-zA-Z](?![a-zA-Z]))?|[a-zA-ZΑ-Ωα-ω∞])(?:[²³])?";
  private static final String TERM = "(?:[\\(\\[]\\s*)*[-−]?\\s*(?:(?:√|sin|cos|tan|log|ln)\\s*\\(?\\s*)?" + ATOM + "(?:\\s*[)\\]])*";
  private static final String OP = "(?:<=|>=|!=|[+−*/×÷^=<>≤≥≠:-])";
  private static final Pattern EXPRESSION = Pattern.compile("(?<![a-zA-Z0-9_:/.-])"
      + TERM + "(?:\\s*" + OP + "\\s*" + TERM + ")+(?![a-zA-Z0-9_/:%])");
  private static final Pattern PART = Pattern.compile(N + "|sin|cos|tan|log|ln|<=|>=|!=|[^\\s]");
  private static final java.util.Map<String,String> SYMBOLS = new java.util.HashMap<>();
  static {
    SYMBOLS.put(":", "ఈజ్ టు");
    SYMBOLS.put("+", "ప్లస్");
    SYMBOLS.put("-", "మైనస్");
    SYMBOLS.put("−", "మైనస్");
    SYMBOLS.put("*", "ఇంటూ");
    SYMBOLS.put("×", "ఇంటూ");
    SYMBOLS.put("/", "బై");
    SYMBOLS.put("÷", "బై");
    SYMBOLS.put("^", "టు ది పవర్ ఆఫ్");
    SYMBOLS.put("=", "ఈక్వల్స్ టు");
    SYMBOLS.put("√", "రూట్");
    SYMBOLS.put(">", "గ్రేటర్ దాన్");
    SYMBOLS.put("<", "లెస్ దాన్");
    SYMBOLS.put("≥", "గ్రేటర్ దాన్ ఆర్ ఈక్వల్ టు");
    SYMBOLS.put("≤", "లెస్ దాన్ ఆర్ ఈక్వల్ టు");
    SYMBOLS.put("≠", "నాట్ ఈక్వల్ టు");
    SYMBOLS.put("²", "స్క్వేర్");
    SYMBOLS.put("³", "క్యూబ్");
    SYMBOLS.put("(", "");
    SYMBOLS.put(")", "");
    SYMBOLS.put("[", "");
    SYMBOLS.put("]", "");
    SYMBOLS.put("sin", "సైన్");
    SYMBOLS.put("cos", "కాస్");
    SYMBOLS.put("tan", "టాన్");
    SYMBOLS.put("log", "లాగ్");
    SYMBOLS.put("ln", "నేచురల్ లాగ్");
    SYMBOLS.put("∞", "ఇన్ఫినిటీ");
    SYMBOLS.put("x", "ఎక్స్");
    SYMBOLS.put("y", "వై");
    SYMBOLS.put("z", "జెడ్");
    String[] names = "ఆల్ఫా బీటా గామా డెల్టా ఎప్సిలాన్ జీటా ఈటా థీటా అయోటా కాపా లాంబ్డా మ్యూ న్యూ క్సై ఒమిక్రాన్ పై రో సిగ్మా టౌ అప్సిలాన్ ఫై ఖై సై ఒమేగా".split(" ");
    String chars = "αβγδεζηθικλμνξοπρστυφχψω";
    for (int i=0; i<chars.length(); i++) {
      String letter = chars.substring(i,i+1);
      SYMBOLS.put(letter,names[i]);
      SYMBOLS.put(letter.toUpperCase(java.util.Locale.ROOT),names[i]);
    }
    SYMBOLS.put("ς","సిగ్మా");
    SYMBOLS.put("ϕ","ఫై");
    SYMBOLS.put("ϑ","థీటా");
  }

  private static String variable(String token) {
    char letter = token.charAt(0);
    if (letter < 'A' || letter > 'z')
      throw new IllegalArgumentException("Add a spoken pronunciation for " + token);
    return DynamicEnglishPhonetizer.letter(letter);
  }

  private static String expressions(String input) {
    Matcher equations = EXPRESSION.matcher(input);
    StringBuffer result = new StringBuffer();
    while (equations.find()) {
      String expression = equations.group().trim();
      if (expression.matches("(?i)\\(?[0-9]+[-–]D\\)?") || expression.equals("m/s") || expression.matches("[0-9]{1,4}[-/][0-9]{1,2}[-/][0-9]{1,4}")) continue;
      Matcher parts = PART.matcher(expression);
      java.util.StringJoiner reading = new java.util.StringJoiner(" ");
      while (parts.find()) {
        String token = parts.group();
        if (token.equals("<=")) token = "≤";
        if (token.equals(">=")) token = "≥";
        if (token.equals("!=")) token = "≠";
        String pronunciation = SYMBOLS.get(token);
        if (pronunciation == null) pronunciation = Character.isDigit(token.charAt(0)) ? number(token)
            : variable(token);
        reading.add(pronunciation);
      }
      equations.appendReplacement(result, Matcher.quoteReplacement(" " + reading + " "));
    }
    return equations.appendTail(result).toString();
  }

  // Canonicalize equivalent LaTeX and Unicode operators before verbalizing their operands.
  private static String calculus(String input) {
    String s = input.replaceAll("\\\\sum(?![A-Za-z])", "∑").replaceAll("\\\\prod(?![A-Za-z])", "∏")
        .replaceAll("\\\\int(?![A-Za-z])", "∫").replaceAll("\\\\infty(?![A-Za-z])", "∞")
        .replaceAll("\\\\pi(?![A-Za-z])", "π").replaceAll("\\\\times(?![A-Za-z])", "×")
        .replaceAll("\\\\div(?![A-Za-z])", "÷");
    Matcher limits = Pattern.compile("([∑∏∫])_\\{([^{}]+)\\}\\^\\{([^{}]+)\\}").matcher(s);
    StringBuffer expanded = new StringBuffer();
    while (limits.find()) limits.appendReplacement(expanded, Matcher.quoteReplacement(
        limits.group(1) + " ఫ్రమ్ " + bound(limits.group(2)) + " టు " + bound(limits.group(3)) + " "));
    return limits.appendTail(expanded).toString();
  }

  private static String bound(String token) {
    String value = token.trim();
    return value.matches(N) ? number(value) : expressions(value);
  }

  private static String symbols(String text) {
    return text.replace("∑", " సమ్ ").replace("∏", " ప్రాడక్ట్ ").replace("∫", " ఇంటిగ్రల్ ")
        .replace("∂", " పార్షియల్ ").replace("∞", " ఇన్ఫినిటీ ").replace("π", " పై ")
        .replace("≈", " అప్రాక్సిమేట్లీ ఈక్వల్ టు ").replace("±", " ప్లస్ ఆర్ మైనస్ ")
        .replace("∈", " బిలాంగ్స్ టు ").replace("∪", " యూనియన్ ").replace("∩", " ఇంటర్‌సెక్షన్ ")
        .replace("∇", " నాబ్లా ").replace("≠", " నాట్ ఈక్వల్ టు ")
        .replaceAll("(?<=[a-zA-ZΑ-ω)0-9])²", " స్క్వేర్ ").replaceAll("(?<=[a-zA-ZΑ-ω)0-9])³", " క్యూబ్ ");
  }


  private static final String[] GREEK_COMMANDS = ("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega").split(" ");
  private static final Pattern SUPER = Pattern.compile("[⁻⁺]?[⁰¹²³⁴⁵⁶⁷⁸⁹]+");
  private static final Pattern COMMAND = Pattern.compile("\\\\([a-zA-Z]+)");

  /** Braces have no sound. Keep each complete fraction's numerator before its denominator. */
  private static final class LatexGroups {
    final String text;
    int pos;
    LatexGroups(String text) { this.text = text; }

    private String argument(int depth) {
      int start = pos;
      while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
      if (pos < text.length() && text.charAt(pos) == '{') {
        pos++;
        return read(depth+1);
      }
      pos = start;
      return null;
    }

    String read(int depth) {
      if (depth > 64) throw new IllegalArgumentException("Please simplify this deeply nested formula.");
      StringBuilder out = new StringBuilder();
      while (pos < text.length()) {
        char c = text.charAt(pos++);
        if (c == '}' && depth > 0) break;
        if (c == '{') {
          out.append('(').append(read(depth+1)).append(')');
        } else if (c == '\\') {
          int begin = pos;
          while (pos < text.length() && Character.isLetter(text.charAt(pos))) pos++;
          String command = text.substring(begin,pos);
          if (command.matches("[dt]?frac")) {
            String top = argument(depth), bottom = argument(depth);
            if (top == null || bottom == null)
              throw new IllegalArgumentException("A fraction needs a numerator and denominator.");
            out.append("((").append(top).append(")/(").append(bottom).append("))");
          } else out.append(c).append(command);
        } else out.append(c);
      }
      return out.toString();
    }
  }

  private static String canonicalMath(String input) {
    Matcher commands = COMMAND.matcher(input);
    StringBuffer expanded = new StringBuffer();
    while (commands.find()) {
      String key = commands.group(1);
      String replacement = commands.group();
      if (key.equals("sqrt")) replacement = "√";
      else if (key.equals("cdot")) replacement = "×";
      else {
        int index = java.util.Arrays.asList(GREEK_COMMANDS).indexOf(key.toLowerCase(java.util.Locale.ROOT));
        if (index >= 0) replacement = "αβγδεζηθικλμνξοπρστυφχψω".substring(index,index+1);
      }
      commands.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
    }
    String s = commands.appendTail(expanded).toString();
    s = new LatexGroups(s).read(0);
    s = s.replaceAll("√\\s*\\(\\s*("+N+")\\s*\\)", "√$1");
    Matcher powers = SUPER.matcher(s);
    expanded.setLength(0);
    while (powers.find()) {
      StringBuilder n = new StringBuilder("^");
      for (char c : powers.group().toCharArray()) n.append("0123456789-+".charAt("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺".indexOf(c)));
      powers.appendReplacement(expanded, Matcher.quoteReplacement(n.toString()));
    }
    s = powers.appendTail(expanded).toString().replace('{','(').replace('}',')');
    return s.replaceAll("\\^\\s*\\(\\s*([-+]?\\d+)\\s*\\)", "^$1")
        .replaceAll("(?<=[a-zA-Z0-9Α-ω)])\\s*\\^\\s*2(?![0-9a-zA-Z]|\\.[0-9])", "²")
        .replaceAll("(?<=[a-zA-Z0-9Α-ω)])\\s*\\^\\s*3(?![0-9a-zA-Z]|\\.[0-9])", "³");
  }

  private static String expandFractionGlyphs(String input) {
    String glyphs = "¼½¾⅐⅑⅒⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞";
    StringBuilder out = new StringBuilder();
    for (char c : input.toCharArray()) {
      if (glyphs.indexOf(c) >= 0) {
        String fraction = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFKC);
        out.append(' ').append(fraction.replace('⁄','/')).append(' ');
      } else out.append(c);
    }
    return out.toString();
  }

  static String read(String input) {
    input = expandFractionGlyphs(input);
    for(char c='౦'; c<='౯'; c++) input=input.replace(c,(char)('0'+c-'౦'));
    input = TechnicalTelugu.clocks(input.replaceAll("(?<=[0-9]),(?=[0-9]{3}(?:[^0-9]|$))", ""));
    Matcher unit = Pattern.compile("(?i)(?<![a-z0-9.])("+N+")\\s*(%|[-–]D|K)(?![a-z0-9])").matcher(input);
    StringBuffer quantities = new StringBuffer();
    while (unit.find()) {
      String suffix = unit.group(2);
      String name = suffix.equals("%") ? "శాతం" : suffix.equalsIgnoreCase("K") ? "కే" : "డీ";
      unit.appendReplacement(quantities,Matcher.quoteReplacement(" "+number(unit.group(1))+" "+name+" "));
    }
    input = canonicalMath(calculus(TechnicalTelugu.measurements(unit.appendTail(quantities).toString())));
    Matcher m=TOKEN.matcher(expressions(input));
    StringBuffer out=new StringBuffer();
    while(m.find()) {
      String spoken;
      if (m.group(1)!=null || m.group(2)!=null) spoken="రూట్ "+number(m.group(1)!=null?m.group(1):m.group(2));
      else if(m.group(3)!=null) spoken=number(m.group(3))+" ఈజ్ టు "+number(m.group(4));
      else if(m.group(5)!=null) spoken=number(m.group(5))+" "+(m.group(6).equals("%")?"శాతం":m.group(6).equalsIgnoreCase("K")?"కే":"డీ");
      else if(m.group(7)!=null) spoken="ఈక్వల్స్ టు "+number(m.group(7));
      else spoken="వోకల్ డీ ఎన్ ఏ";
      m.appendReplacement(out,Matcher.quoteReplacement(" "+spoken+" "));
    }
    String result = symbols(m.appendTail(out).toString());
    StringBuilder greek = new StringBuilder();
    for (char c : result.toCharArray()) {
      String name = Character.UnicodeBlock.of(c) == Character.UnicodeBlock.GREEK ? SYMBOLS.get(String.valueOf(c)) : null;
      if (name == null) greek.append(c); else greek.append(' ').append(name).append(' ');
    }
    return greek.toString().replace("−"," మైనస్ ").replace("×"," ఇంటూ ").replace("*"," ఇంటూ ").replace("÷"," బై ").replace("/"," బై ").replace("^"," టు ది పవర్ ఆఫ్ ").replace("="," ఈక్వల్స్ టు ").replace("√", " రూట్ ").replace("²"," స్క్వేర్ ").replace("³"," క్యూబ్ ")
        .replaceAll("[(){}\\[\\]]", " ").trim().replaceAll(" +"," ");
  }
  private EnglishNotation() {}
}
