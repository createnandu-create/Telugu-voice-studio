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
  static String read(String input) {
    for(char c='౦'; c<='౯'; c++) input=input.replace(c,(char)('0'+c-'౦'));
    Matcher m=TOKEN.matcher(TechnicalTelugu.clocks(input));
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
    return TechnicalTelugu.measurements(m.appendTail(out).toString()).trim().replaceAll(" +"," ");
  }
  private EnglishNotation() {}
}
