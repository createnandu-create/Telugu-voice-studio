package in.aitechapp.teluguoffline;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline Telugu readings for measurements, expressions and common technical English phrases. */
final class TechnicalTelugu {
  private static final String NUM = "[0-9]+(?:\\.[0-9]+)?";
  private static final String LEFT = "(?<![A-Za-z0-9_.])";
  private static final String RIGHT = "(?![A-Za-z0-9_])";
  private static final Pattern ROOT = Pattern.compile("(?:√\\s*(" + NUM + ")(?![0-9A-Za-z])|\\\\sqrt\\s*\\{\\s*(" + NUM + ")\\s*\\})");
  private static final Pattern RATIO = Pattern.compile(LEFT + "([0-9]+)\\s*:\\s*([0-9]+)" + RIGHT);
  private static final Pattern CLOCK = Pattern.compile("(?i)" + LEFT + "(1[0-2]|0?[1-9]):([0-5][0-9])\\s*([ap])\\.?m\\.?(?![A-Za-z])");
  private static final Pattern CLOCK24 = Pattern.compile("(?i)(?:\\b(?:at|time)\\s+|సమయం\\s+)([01]?\\d|2[0-3]):([0-5]\\d)(?![:\\d])");
  private static final Pattern TIMECODE = Pattern.compile(LEFT + "([0-9]+):([0-5][0-9]):([0-5][0-9])" + RIGHT);
  private static final Pattern GROUPED = Pattern.compile(LEFT + "\\d{1,3}(?:(?:,\\d{2})+,\\d{3}|(?:,\\d{3})+)(?![,\\d])");
  private static final Pattern SET_RATIO = Pattern.compile("(?i)\\bset (?:the )?aspect ratio to (\\d+\\s*:\\s*\\d+)");
  private static final Pattern EXPORT = Pattern.compile("(?i)\\b(export|render) (?:the )?video (?:in|at) ([248])k" + RIGHT);
  private static final Pattern RENDER_RATE = Pattern.compile("(?i)\\brender at (" + NUM + ")\\s*fps" + RIGHT);
  private static final Pattern DIMENSIONS = Pattern.compile(LEFT + "(\\d+)\\s*[x×]\\s*(\\d+)\\s*(?:pixels?|px)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMPARE = Pattern.compile(LEFT + "(-?" + NUM + ")\\s*(>=|<=|!=|[<>≥≤≠])\\s*(-?" + NUM + ")");
  private static final Pattern VIDEO_K = Pattern.compile("(?i)" + LEFT + "(2|4|8)k" + RIGHT);
  private static final Pattern UNIT = Pattern.compile(LEFT + "(" + NUM + ")\\s*(MB/s|Mb/s|Mbps|MBps|kbps|GHz|MHz|kHz|Hz|fps|FPS|GB|MB|KB|ms|mAh|%)" + RIGHT);
  private static final Pattern ARITHMETIC = Pattern.compile("(?i)^(?:what is\\s+|calculate\\s+|solve\\s+)?(-?\\d{1,6})\\s*([+×*÷/−-])\\s*(-?\\d{1,6})\\s*(?:ఎంత|ఎంత అవుతుంది)?\\s*\\?$");
  private static final Pattern ROOT_QUESTION = Pattern.compile("(?i)^(?:what is (?:the )?square root of\\s+|what is\\s*√\\s*|√\\s*)(\\d{1,8})\\s*(?:ఎంత)?\\s*\\?$");
  private static final Pattern IGNORE_CASE_ROOT = Pattern.compile("(?i)\\b(?:the )?square root of\\s+(" + NUM + ")\\s+(?:is|equals)\\s+(" + NUM + ")" + RIGHT);
  private static final Pattern OPERATOR = Pattern.compile("(?<=\\d)\\s*(>=|<=|!=|[+*×÷/=<>≥≤≠−-])\\s*(?=[-−]?\\d)");
  private static final Map<String,String> TERMS = new LinkedHashMap<>();
  private static final Pattern TERM;
  static {
    TERMS.put("(?:the )?aspect ratio(?: is)?", "దృశ్య నిష్పత్తి");
    TERMS.put("(?:the )?quality ratio(?: is)?|క్వాలిటీ రేషియో", "నాణ్యత నిష్పత్తి");
    TERMS.put("(?:the )?refresh rate(?: is)?", "తెర నవీకరణ వేగం");
    TERMS.put("(?:the )?frame rate(?: is)?", "వీడియోలో చిత్రాలు మారే వేగం");
    TERMS.put("(?:the )?resolution(?: is)?", "రిజల్యూషన్");
    TERMS.put("(?:the )?sample rate(?: is)?", "ఆడియో నమూనాల రేటు");
    TERMS.put("(?:the )?download speed(?: is)?", "డౌన్‌లోడ్ వేగం");
    TERMS.put("(?:the )?file size(?: is)?", "ఫైల్ పరిమాణం");
    TERMS.put("frames per second", "సెకనుకు ఫ్రేములు");
    TERMS.put("battery capacity", "బ్యాటరీ సామర్థ్యం");
    TERMS.put("background noise", "నేపథ్య శబ్దం");
    TERMS.put("noise reduction", "శబ్దాన్ని తగ్గించడం");
    TERMS.put("voice cloning", "వాయిస్ క్లోనింగ్");
    TERMS.put("offline mode", "ఆఫ్‌లైన్ మోడ్");
    TERMS.put("voice fingerprint", "స్వర ముద్ర");
    TERMS.put("vocal DNA", "వోకల్ డీ ఎన్ ఏ");
    StringBuilder pattern=new StringBuilder("(?i)(?<![A-Za-z])(?:");
    for (String key:TERMS.keySet()) {
      if (pattern.charAt(pattern.length()-1) != ':') pattern.append('|');
      pattern.append('(').append(key).append(')');
    }
    TERM=Pattern.compile(pattern.append(")(?![A-Za-z])").toString());
  }

  private static String replace(String s, Pattern pattern, Function<Matcher,String> spoken) {
    Matcher m=pattern.matcher(s);
    StringBuffer out=new StringBuffer();
    while(m.find()) m.appendReplacement(out,Matcher.quoteReplacement(spoken.apply(m)));
    return m.appendTail(out).toString();
  }

  private static String number(String value) {
    if (value.startsWith("-") || value.startsWith("−")) return "మైనస్ " + number(value.substring(1));
    int dot=value.indexOf('.');
    if(dot<0) return MathScienceNormalizer.numeric(value);
    return MathScienceNormalizer.numeric(value.substring(0,dot))+" పాయింట్ "+MathScienceNormalizer.digits(value.substring(dot+1));
  }

  private static String op(String op) {
    switch(op) {
      case "+": return "కూడిక";
      case "-": case "−": return "తీసివేత";
      case "×": case "*": return "సార్లు";
      case "÷": case "/": return "భాగించు";
      case "=": return "సమానం";
      case ">": return "కంటే ఎక్కువ";
      case "<": return "కంటే తక్కువ";
      case ">=": case "≥": return "కంటే ఎక్కువ లేదా సమానం";
      case "<=": case "≤": return "కంటే తక్కువ లేదా సమానం";
      default: return "సమానం కాదు";
    }
  }

  private static String quantity(Matcher m) {
    String n=number(m.group(1));
    switch(m.group(2)) {
      case "MBps": case "MB/s": return "సెకనుకు "+n+" మెగాబైట్లు";
      case "Mbps": case "Mb/s": return "సెకనుకు "+n+" మెగాబిట్లు";
      case "kbps": return "సెకనుకు "+n+" కిలోబిట్లు";
      case "fps": case "FPS": return "సెకనుకు "+n+" ఫ్రేములు";
      case "Hz": return n+" హెర్ట్జ్";
      case "kHz": return n+" కిలోహెర్ట్జ్";
      case "MHz": return n+" మెగాహెర్ట్జ్";
      case "GHz": return n+" గిగాహెర్ట్జ్";
      case "GB": return n+" గిగాబైట్లు";
      case "MB": return n+" మెగాబైట్లు";
      case "KB": return n+" కిలోబైట్లు";
      case "ms": return n+" మిల్లీసెకన్లు";
      case "mAh": return n+" మిల్లీ ఆంపియర్ గంటలు";
      default: return n+" శాతం";
    }
  }

  private static String answer(Matcher m) {
    BigDecimal a=new BigDecimal(m.group(1)), b=new BigDecimal(m.group(3)), result;
    String operator=m.group(2);
    switch(operator) {
      case "+": result=a.add(b); break;
      case "-": case "−": result=a.subtract(b); break;
      case "*": case "×": result=a.multiply(b); break;
      default:
        if(b.signum()==0) return number(m.group(1))+" ను సున్నాతో భాగించడం నిర్వచించబడలేదు.";
        try {result=a.divide(b);} catch(ArithmeticException repeating) {return null;}
    }
    return number(m.group(1))+" "+op(operator)+" "+number(m.group(3))+" ఎంత? సమాధానం "+number(result.stripTrailingZeros().toPlainString())+".";
  }

  static String explain(String source) {
    String s=source.trim();
    for (char c='౦'; c<='౯'; c++) s=s.replace(c,(char)('0'+c-'౦'));
    s=replace(s,GROUPED,m->m.group().replace(",",""));
    s=replace(s,SET_RATIO,m->"దృశ్య నిష్పత్తిని ఇలా పెట్టండి, "+m.group(1));
    s=replace(s,EXPORT,m->"వీడియోను "+m.group(2)+"K లో "+(m.group(1).equalsIgnoreCase("render")?"రెండర్":"ఎక్స్‌పోర్ట్")+" చేయండి");
    s=replace(s,RENDER_RATE,m->m.group(1)+" fps వచ్చేలా రెండర్ చేయండి");
    Matcher q=ROOT_QUESTION.matcher(s);
    if(q.matches()) {
      int n=Integer.parseInt(q.group(1)), k=(int)Math.sqrt(n);
      String question=number(q.group(1))+" యొక్క వర్గమూలం ఎంత?";
      return k*k==n ? question+" సమాధానం "+MathScienceNormalizer.number(k)+". "+MathScienceNormalizer.number(k)+" సార్లు "+MathScienceNormalizer.number(k)+" అంటే "+MathScienceNormalizer.number(n)+"." : question;
    }
    q=ARITHMETIC.matcher(s);
    if (q.matches()) {
      String solved=answer(q);
      if (solved!=null) return solved;
      return number(q.group(1))+" "+op(q.group(2))+" "+number(q.group(3))+" ఎంత?";
    }
    s=replace(s,CLOCK,m->number(m.group(1))+" గంటల "+MathScienceNormalizer.number(Integer.parseInt(m.group(2)))+" నిమిషాలు "+(m.group(3).equalsIgnoreCase("a")?"ఏ ఎమ్":"పీ ఎమ్"));
    s=replace(s,TIMECODE,m->number(m.group(1))+" గంటలు "+MathScienceNormalizer.number(Integer.parseInt(m.group(2)))+" నిమిషాలు "+MathScienceNormalizer.number(Integer.parseInt(m.group(3)))+" సెకన్లు");
    s=replace(s,CLOCK24,m->"సమయం "+MathScienceNormalizer.number(Integer.parseInt(m.group(1)))+" గంటల "+MathScienceNormalizer.number(Integer.parseInt(m.group(2)))+" నిమిషాలు");
    s=replace(s,DIMENSIONS,m->number(m.group(1))+" బై "+number(m.group(2))+" పిక్సెల్స్");
    s=replace(s,IGNORE_CASE_ROOT,m->number(m.group(1))+" యొక్క వర్గమూలం "+number(m.group(2)));
    s=replace(s,ROOT,m->number(m.group(1)!=null ? m.group(1) : m.group(2))+" యొక్క వర్గమూలం ");
    s=s.replaceAll("(వర్గమూలం)\\s*=\\s*", "$1 ");
    s=replace(s,RATIO,m->number(m.group(1))+" భాగాలకు "+number(m.group(2))+" భాగాలు");
    s=replace(s,VIDEO_K,m->m.group(1).equals("4")?"ఫోర్ కే":m.group(1).equals("8")?"ఎయిట్ కే":"టూ కే");
    s=replace(s,COMPARE,m->number(m.group(1))+" అనేది "+number(m.group(3))+" "+op(m.group(2)));
    s=replace(s,UNIT,TechnicalTelugu::quantity);
    s=replace(s,OPERATOR,m->" "+op(m.group(1))+" ");
    s=replace(s,TERM,m->{
      int i=1;
      for(String reading:TERMS.values()) if(m.group(i++)!=null) return reading;
      throw new AssertionError("Unknown technical term");
    });
    return s.trim();
  }

  private static final Pattern BREAK = Pattern.compile("(?iu)\\s+(?=(?:లేదా|అంత ఖచ్చితంగా|or)\\s)");
  static List<NarrationDirector.Segment> phrases(List<NarrationDirector.Segment> source, boolean enabled) {
    if (!enabled) return source;
    List<NarrationDirector.Segment> out = new ArrayList<>();
    for (NarrationDirector.Segment line : source) {
      if (line.text.length() < 45 || explain(line.text).equals(line.text.trim())) {
        out.add(line);
        continue;
      }
      String[] parts = BREAK.split(line.text);
      for (int i=0; i<parts.length; i++) {
        out.add(new NarrationDirector.Segment(parts[i], line.emotion, line.rate, line.pitch,
            i+1 == parts.length ? line.pauseAfterMs : 110));
      }
    }
    return out;
  }

  private TechnicalTelugu() {}
}
