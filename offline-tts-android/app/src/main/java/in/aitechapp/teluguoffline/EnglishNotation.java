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
  private static final String ATOM = "(?:" + N + "(?:[abcijkmnpqrtuvwxyz](?![a-zA-Z]))?|[abcijkmnpqrtuvwxyzπ∞])(?:[²³])?";
  private static final String TERM = "(?:[\\(\\[]\\s*)*[-−]?\\s*(?:(?:√|sin|cos|tan|log|ln)\\s*\\(?\\s*)?" + ATOM + "(?:\\s*[)\\]])*";
  private static final String OP = "(?:<=|>=|!=|[+−*/×÷^=<>≤≥≠-])";
  private static final Pattern EXPRESSION = Pattern.compile("(?<![a-zA-Z0-9_:/.-])"
      + TERM + "(?:\\s*" + OP + "\\s*" + TERM + ")+(?![a-zA-Z0-9_/:%])");
  private static final Pattern PART = Pattern.compile(N + "|sin|cos|tan|log|ln|<=|>=|!=|[^\\s]");
  private static final java.util.Map<String,String> SYMBOLS = new java.util.HashMap<>();
  static {
    SYMBOLS.put("+", "ప్లస్");
    SYMBOLS.put("-", "మైనస్");
    SYMBOLS.put("−", "మైనస్");
    SYMBOLS.put("*", "టైమ్స్");
    SYMBOLS.put("×", "టైమ్స్");
    SYMBOLS.put("/", "డివైడెడ్ బై");
    SYMBOLS.put("÷", "డివైడెడ్ బై");
    SYMBOLS.put("^", "టు ది పవర్ ఆఫ్");
    SYMBOLS.put("=", "ఇజ్ ఈక్వల్ టు");
    SYMBOLS.put("√", "రూట్");
    SYMBOLS.put(">", "గ్రేటర్ దాన్");
    SYMBOLS.put("<", "లెస్ దాన్");
    SYMBOLS.put("≥", "గ్రేటర్ దాన్ ఆర్ ఈక్వల్ టు");
    SYMBOLS.put("≤", "లెస్ దాన్ ఆర్ ఈక్వల్ టు");
    SYMBOLS.put("≠", "నాట్ ఈక్వల్ టు");
    SYMBOLS.put("²", "స్క్వేర్డ్");
    SYMBOLS.put("³", "క్యూబ్డ్");
    SYMBOLS.put("(", "ఓపెన్ బ్రాకెట్");
    SYMBOLS.put(")", "క్లోజ్ బ్రాకెట్");
    SYMBOLS.put("[", "ఓపెన్ బ్రాకెట్");
    SYMBOLS.put("]", "క్లోజ్ బ్రాకెట్");
    SYMBOLS.put("sin", "సైన్");
    SYMBOLS.put("cos", "కాస్");
    SYMBOLS.put("tan", "టాన్");
    SYMBOLS.put("log", "లాగ్");
    SYMBOLS.put("ln", "నేచురల్ లాగ్");
    SYMBOLS.put("∞", "ఇన్ఫినిటీ");
    SYMBOLS.put("x", "ఎక్స్");
    SYMBOLS.put("y", "వై");
    SYMBOLS.put("z", "జెడ్");
    SYMBOLS.put("π", "పై");
  }

  private static String expressions(String input) {
    Matcher equations = EXPRESSION.matcher(input);
    StringBuffer result = new StringBuffer();
    while (equations.find()) {
      String expression = equations.group().trim();
      if (expression.equals("m/s") || expression.matches("[0-9]{1,4}[-/][0-9]{1,2}[-/][0-9]{1,4}")) continue;
      Matcher parts = PART.matcher(expression);
      java.util.StringJoiner reading = new java.util.StringJoiner(" ");
      while (parts.find()) {
        String token = parts.group();
        if (token.equals("<=")) token = "≤";
        if (token.equals(">=")) token = "≥";
        if (token.equals("!=")) token = "≠";
        String pronunciation = SYMBOLS.get(token);
        if (pronunciation == null) pronunciation = Character.isDigit(token.charAt(0)) ? number(token)
            : DynamicEnglishPhonetizer.apply(token.toUpperCase(java.util.Locale.ROOT));
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
        .replaceAll("(?<=[xyzabc0-9])²", " స్క్వేర్డ్ ").replaceAll("(?<=[xyzabc0-9])³", " క్యూబ్డ్ ");
  }

  static String read(String input) {
    for(char c='౦'; c<='౯'; c++) input=input.replace(c,(char)('0'+c-'౦'));
    input = calculus(input).replaceAll("\\\\frac\\s*\\{("+N+")\\}\\s*\\{("+N+")\\}", "($1/$2)");
    Matcher m=TOKEN.matcher(expressions(TechnicalTelugu.clocks(input)));
    StringBuffer out=new StringBuffer();
    while(m.find()) {
      String spoken;
      if (m.group(1)!=null || m.group(2)!=null) spoken="రూట్ "+number(m.group(1)!=null?m.group(1):m.group(2));
      else if(m.group(3)!=null) spoken=number(m.group(3))+" ఇజ్ టు "+number(m.group(4));
      else if(m.group(5)!=null) spoken=number(m.group(5))+" "+(m.group(6).equals("%")?"పర్సెంట్":m.group(6).equalsIgnoreCase("K")?"కే":"డీ");
      else if(m.group(7)!=null) spoken="ఇజ్ ఈక్వల్ టు "+number(m.group(7));
      else spoken="వోకల్ డీ ఎన్ ఏ";
      m.appendReplacement(out,Matcher.quoteReplacement(" "+spoken+" "));
    }
    return symbols(TechnicalTelugu.measurements(m.appendTail(out).toString())).trim().replaceAll(" +"," ");
  }
  private EnglishNotation() {}
}
