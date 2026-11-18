package in.aitechapp.teluguoffline;

import java.util.regex.*;

/**
 * Speech-only cardinal/math expansion; never changes the saved script or evaluates an expression.
 */
public final class MathScienceNormalizer {
  private static final String[] SMALL = {
    "సున్నా",
    "ఒకటి",
    "రెండు",
    "మూడు",
    "నాలుగు",
    "ఐదు",
    "ఆరు",
    "ఏడు",
    "ఎనిమిది",
    "తొమ్మిది",
    "పది",
    "పదకొండు",
    "పన్నెండు",
    "పదమూడు",
    "పద్నాలుగు",
    "పదిహేను",
    "పదహారు",
    "పదిహేడు",
    "పద్దెనిమిది",
    "పందొమ్మిది"
  };
  private static final String[] TENS = {
    "", "", "ఇరవై", "ముప్పై", "నలభై", "యాభై", "అరవై", "డెబ్బై", "ఎనభై", "తొంభై"
  };

  public static String number(long n) {
    if (n < 0) return "మైనస్ " + number(-n);
    if (n < 20) return SMALL[(int) n];
    if (n < 100) return TENS[(int) n / 10] + (n % 10 == 0 ? "" : " " + number(n % 10));
    if (n < 1000)
      return (n / 100 == 1 ? (n % 100 == 0 ? "వంద" : "నూట") : number(n / 100) + " వందల")
          + (n % 100 == 0 ? "" : " " + number(n % 100));
    if (n < 100000)
      return (n / 1000 == 1 ? "వెయ్యి" : number(n / 1000) + " వేల")
          + (n % 1000 == 0 ? "" : " " + number(n % 1000));
    if (n < 10000000)
      return (n / 100000 == 1 ? "లక్ష" : number(n / 100000) + " లక్షల")
          + (n % 100000 == 0 ? "" : " " + number(n % 100000));
    return number(n / 10000000) + " కోట్ల" + (n % 10000000 == 0 ? "" : " " + number(n % 10000000));
  }

  static String digits(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (b.length() > 0) b.append(' ');
      b.append(SMALL[c - '0']);
    }
    return b.toString();
  }

  static String numeric(String s) {
    if (s.length() > 12 || (s.length() > 1 && s.charAt(0) == '0')) return digits(s);
    return number(Long.parseLong(s));
  }

  private static String replace(
      String s, String regex, java.util.function.Function<Matcher, String> f) {
    Matcher m = Pattern.compile(regex).matcher(s);
    StringBuffer b = new StringBuffer();
    while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(f.apply(m)));
    m.appendTail(b);
    return b.toString();
  }

  public static String apply(String source) {
    String s = replace(source, "[0-9]{1,3}(?:,[0-9]{3})+", m -> m.group().replace(",", ""));
    s =
        s.replaceAll("(?<![A-Za-z])-(?=[0-9])", " మైనస్ ")
            .replace("+", " ప్లస్ ")
            .replace("×", " ఇంటు ")
            .replace("÷", " బై ")
            .replace("^", " ఘాతం ")
            .replace("@", " అట్ ")
            .replace("#", " హ్యాష్ ");
    s = replace(s, "(?i)(?<![A-Za-z0-9])4K(?![A-Za-z0-9])", m -> "ఫోర్ కే");
    s = replace(s, "(?i)([0-9]+)\\s*[-–]\\s*D\\b", m -> numeric(m.group(1)) + " డీ");
    s = replace(s, "\\\\sqrt\\s*\\{([^{}]*)\\}", m -> "రూట్ " + m.group(1));
    s = s.replace("√", " రూట్ ").replace("=", " ఈక్వల్స్ టు ").replace("%", " శాతం ");
    s =
        replace(
            s,
            "([0-9]+)\\s*:\\s*([0-9]+)",
            m -> numeric(m.group(1)) + " ఈజ్ టు " + numeric(m.group(2)));
    s =
        replace(
            s,
            "[0-9]+(?:\\.[0-9]+)?",
            m -> {
              String n = m.group();
              int dot = n.indexOf('.');
              return dot < 0
                  ? numeric(n)
                  : numeric(n.substring(0, dot)) + " పాయింట్ " + digits(n.substring(dot + 1));
            });
    return s;
  }

  private MathScienceNormalizer() {}
}
