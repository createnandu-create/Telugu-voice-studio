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
    assertEquals("ట్వెల్వ్ ప్లస్ ఎయిట్ బై ఫోర్ ఈక్వల్స్ టు ఫైవ్",
        EnglishNotation.read("(12 + 8) ÷ 4 = 5"));
    assertEquals("ఎక్స్ స్క్వేర్ ప్లస్ త్రీ ఎక్స్ గ్రేటర్ దాన్ ఆర్ ఈక్వల్ టు సెవెన్",EnglishNotation.read("x² + 3x ≥ 7"));
    assertEquals("సిక్స్‌టీన్ ఈజ్ టు నైన్",EnglishNotation.read("16:9"));
    assertTrue(PronunciationLexicon.apply("x != 2",Map.of(),false,true,true).contains("నాట్ ఈక్వల్ టు"));
    assertEquals(1,NarrationDirector.planExact("x != 2.",NarrationDirector.Style.AUTO).size());
    String integral = EnglishNotation.read("\\int_{0}^{2} x²");
    assertTrue(integral.contains("ఇంటిగ్రల్ ఫ్రమ్ జీరో టు టూ") && integral.contains("స్క్వేర్"));
    assertEquals("2026-11-27",EnglishNotation.read("2026-11-27"));
  }

  @Test public void connectedNamesAndAcronymsUseTheSameOfflineVoiceInput() {
    SpeechSettings settings = new SpeechSettings();
    for (String variant : List.of("AiTechApp","Aitechapp","AI Tech App","AI Tech Apps","A.I. Tech Apps"))
      assertEquals("ఏఐటెక్ యాప్",settings.spoken(variant));
    assertEquals("సంక్రాంతి ముగ్గు హరిత టీచర్",settings.spoken("SANKRANTI Muggu Haritha TEACHER"));
    assertEquals("ఫ్రీ డౌన్‌లోడ్ లింక్ ఫాంట్",settings.spoken("FREE Download “link” 'Font'"));
    assertEquals("యూ ఎస్ బీ ఏ పీ కే హెచ్ డీ వి ఎన్",settings.spoken("USB APK HD VN"));
    for (String token : List.of("Quorvella","SAND","TYPHOON","Vimaku","crwth")) {
      String spoken = settings.spoken(token);
      assertTrue(token, spoken.matches("[\\p{InTelugu}]+"));
      assertEquals(spoken,settings.spoken(token.toLowerCase(java.util.Locale.ROOT)));
    }
    assertEquals("ఏ ఈజ్ టు బీ",settings.spoken("A:B"));
    assertEquals("డెల్టా స్క్వేర్ ఈక్వల్స్ టు ఎక్స్ క్యూబ్",settings.spoken("\\Delta^2 = X³"));
    assertEquals("ఎక్స్ టు ది పవర్ ఆఫ్ మైనస్ ట్వెల్వ్",settings.spoken("x⁻¹²"));
    assertEquals("రూట్ ఫోర్టీ నైన్ బై ఎయిట్ బై ఫోర్",settings.spoken("\\frac{\\sqrt{49}}{\\frac{8}{4}}"));
    assertEquals("ఎక్స్ స్క్వేర్ బై త్రీ", settings.spoken("\\frac{x^{2}}{3}"));
    assertEquals("వన్ బై ఫోర్", settings.spoken("¼"));
    String equation = settings.spoken("β² + √49 = 11");
    int start = 0;
    StringBuilder retry = new StringBuilder();
    int[] ends = PronunciationCheck.equationBoundaries(equation);
    assertEquals(3, ends.length);
    for (int end : ends) {
      retry.append(equation, start, end);
      start = end;
    }
    assertEquals(equation, retry.toString());
    assertEquals("వన్ థౌజండ్ టూ హండ్రెడ్ ప్లస్ ఫైవ్",settings.spoken("1,200+5"));
  }

  @Test public void phraseDictionaryAcrossCommaRetainsItsAuthoritativeReading() {
    SpeechSettings settings = new SpeechSettings();
    settings.dictionary.put("ఒకటి, రెండు","మొదటి రెండు");
    String source = "ఒకటి, రెండు మూడు.";
    var plan = NarrationDirector.planExact(source,NarrationDirector.Style.AUTO);
    var parts = settings.renderSegments(plan);
    assertEquals(1, parts.size());
    assertEquals(source, parts.get(0).text);
    assertEquals("మొదటి రెండు మూడు", settings.spoken(parts.get(0).text));
  }
}
