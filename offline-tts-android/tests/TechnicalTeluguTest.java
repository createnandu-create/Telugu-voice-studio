package in.aitechapp.teluguoffline;

import java.util.Collections;
import java.util.List;

public final class TechnicalTeluguTest {
  private static String read(String s) {
    return PronunciationLexicon.apply(s, Collections.emptyMap(), false, true);
  }
  private static void contains(String input, String expected) {
    String result = read(input);
    if (!result.contains(expected)) throw new AssertionError(input + " => " + result + " / " + expected);
  }
  public static void main(String[] args) {
    String english=PronunciationLexicon.apply("(192-D Vocal DNA), 100% 9:16 √81 = 9 8K",Collections.emptyMap(),false,true,true);
    for (String phrase:new String[]{"వన్ హండ్రెడ్ నైంటీ టూ డీ వోకల్ డీ ఎన్ ఏ","వన్ హండ్రెడ్ శాతం",
        "నైన్ ఈజ్ టు సిక్స్‌టీన్","రూట్ ఎయిటీ వన్ ఈక్వల్స్ టు నైన్","ఎయిట్ కే"})
      if (!english.contains(phrase)) throw new AssertionError(english+" / "+phrase);
    contains("16:9", "పదహారు భాగాలకు తొమ్మిది భాగాలు");
    contains("√25 = 5", "ఇరవై ఐదు యొక్క వర్గమూలం ఐదు");
    contains("\\sqrt{81} = 9", "ఎనభై ఒకటి యొక్క వర్గమూలం తొమ్మిది");
    contains("The refresh rate is 120 Hz.", "తెర నవీకరణ వేగం నూట ఇరవై హెర్ట్జ్");
    contains("8 Mbps", "మెగాబిట్లు");
    contains("8 MB/s", "మెగాబైట్లు");
    contains("Set the aspect ratio to ౯:౧౬.", "తొమ్మిది భాగాలకు పదహారు భాగాలు");
    contains("Export the video in 4K.", "వీడియోను ఫోర్ కే లో");
    contains("What is the square root of 49?", "సమాధానం ఏడు");
    contains("12 × 8 ఎంత?", "సమాధానం తొంభై ఆరు");
    contains("3-8?", "సమాధానం మైనస్ ఐదు");
    contains("12 / 0?", "నిర్వచించబడలేదు");
    contains("2:30 PM", "పీ ఎమ్");
    contains("at 14:30", "ముప్పై నిమిషాలు");
    contains("00:02:10", "రెండు నిమిషాలు పది సెకన్లు");
    contains("3 < 12", "మూడు అనేది పన్నెండు కంటే తక్కువ");
    contains("√25 = 6", "వర్గమూలం ఆరు");
    contains("Temperature is -5°C.", "ఉష్ణోగ్రత మైనస్ ఐదు డిగ్రీల సెల్సియస్");
    contains("37.5℃", "ముప్పై ఏడు పాయింట్ ఐదు డిగ్రీల సెల్సియస్");
    contains("−40 °F", "మైనస్ నలభై డిగ్రీల ఫారన్‌హీట్");
    contains("12 cm²", "పన్నెండు చదరపు సెంటీమీటర్లు");
    contains("2 m³", "రెండు ఘనపు మీటర్లు");
    contains("30 km/h", "ముప్పై కిలోమీటర్లు గంటకు");
    contains("5 μs", "ఐదు మైక్రోసెకన్లు");
    contains("220 Ω", "రెండు వందల ఇరవై ఓములు");
    contains("V25 = 5", "వీ ఇరవై ఐదు సమానం ఐదు");
    contains("2x = 10", "రెండు ఎక్స్ సమానం పది");
    contains("9:16; √144 = 12; 8K", "తొమ్మిది భాగాలకు పదహారు భాగాలు");
    contains("9:16; √144 = 12; 8K", "నూట నలభై నాలుగు యొక్క వర్గమూలం పన్నెండు");
    contains("44.1 kHz", "నలభై నాలుగు పాయింట్ ఒకటి కిలోహెర్ట్జ్");
    if (read("V25 = 5").contains("వర్గమూలం")) throw new AssertionError("Never guess a root from V");
    if (read("1/3?").contains("సమాధానం") || read("4+2*3?").contains("సమాధానం"))
      throw new AssertionError("Do not guess results outside the exact solver");
    if (!PronunciationLexicon.apply("16:9",Collections.emptyMap(),false,false).contains("ఈజ్ టు"))
      throw new AssertionError("Literal mode must stay available");
    if (!PronunciationLexicon.apply("Mbps",Collections.singletonMap("Mbps","నా పదం"),false,true).equals("నా పదం"))
      throw new AssertionError("Custom dictionary precedence");
    String line = "క్వాలిటీ రేషియో 16:9 లేదా √25 = 5 అంత ఖచ్చితంగా 4K రెసొల్యూషన్‌లో రెండర్ అవుతుంది!";
    List<NarrationDirector.Segment> plan = NarrationDirector.planExact(line,null);
    List<NarrationDirector.Segment> parts = TechnicalTelugu.phrases(plan,true);
    if (parts.size()!=3 || !plan.get(0).text.equals(line) || parts.get(0).pauseAfterMs!=110 ||
        TechnicalTelugu.phrases(plan,false)!=plan) throw new AssertionError("Native phrase boundaries");
    System.out.println("PASS Telugu technical reading, translation patterns, exact arithmetic and original script preservation");
  }
}
