package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import java.util.Map;

public class PresenterTest {
  @Test public void cuesFollowMeaningAndKeepOriginalWords() {
    String script = "ఒక్క క్షణం ఊహించండి. చిన్న జోక్ చెబుతాను. అబ్బో ఎంత గొప్ప ఫోటోగ్రాఫరో కదా! తర్వాత ఏం జరిగిందో తెలుసా? గురువుగారికి వందనాలు. వదులుకోకండి. చకచకా మొదలుపెడదాం.";
    var plan = NarrationDirector.planExact(script,NarrationDirector.Style.AUTO);
    assertEquals(List.of("HOOK","PLAYFUL","WRY","SUSPENSE","RESPECTFUL","MOTIVATING","BRISK"),
        plan.stream().map(s -> s.emotion.name()).collect(java.util.stream.Collectors.toList()));
    assertEquals(script,String.join(" ",plan.stream().map(s->s.text).collect(java.util.stream.Collectors.toList())));
    assertEquals("SERIOUS", NarrationDirector.planExact("జాగ్రత్త, ఇది చిన్న జోక్ కాదు!",NarrationDirector.Style.AUTO).get(0).label());
    assertEquals("EMPATHY",NarrationDirector.planExact("తుఫానులో గాయపడ్డారు.",NarrationDirector.Style.AUTO).get(0).label());
  }

  @Test public void renderEquationsWithoutEditingOrCalculating() {
    assertEquals("ఓపెన్ బ్రాకెట్ ట్వెల్వ్ ప్లస్ ఎయిట్ క్లోజ్ బ్రాకెట్ డివైడెడ్ బై ఫోర్ ఇజ్ ఈక్వల్ టు ఫైవ్",
        EnglishNotation.read("(12 + 8) ÷ 4 = 5"));
    assertEquals("ఎక్స్ స్క్వేర్డ్ ప్లస్ త్రీ ఎక్స్ గ్రేటర్ దాన్ ఆర్ ఈక్వల్ టు సెవెన్",EnglishNotation.read("x² + 3x ≥ 7"));
    assertEquals("సిక్స్‌టీన్ ఇజ్ టు నైన్",EnglishNotation.read("16:9"));
    assertTrue(PronunciationLexicon.apply("x != 2",Map.of(),false,true,true).contains("నాట్ ఈక్వల్ టు"));
    assertEquals(1,NarrationDirector.planExact("x != 2.",NarrationDirector.Style.AUTO).size());
    assertEquals("2026-11-27",EnglishNotation.read("2026-11-27"));
  }
}
