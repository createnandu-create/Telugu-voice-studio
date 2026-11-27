package in.aitechapp.teluguoffline;

import org.junit.Test;
import static org.junit.Assert.*;

public class WordCoverageTest {
  @Test public void shortWordOmittedInLongUnseenSentence() {
    String text = "మొక్కకు నీరు పోసే ముందు మట్టి కాస్త తడిగా ఉందో గమనించండి";
    WordCoverage result = WordCoverage.compare(text, text.replace("కాస్త ", ""));
    assertEquals(1, result.missing);
    assertEquals(text.indexOf("కాస్త"), result.firstMissingOffset);
    assertTrue(result.characterError < .22);
  }

  @Test public void joinedRecognitionAndSpellingVariation() {
    assertEquals(0, WordCoverage.compare("పిక్సెల్ ల్యాబ్ ఆన్ చేసి", "పిక్సల్‌ల్యాబ్ ఆన్ చేసి").missing);
    assertEquals(1, WordCoverage.compare("ఒకసారి మళ్లీ మళ్లీ చూడు", "ఒకసారి మళ్లీ చూడు").missing);
    assertEquals(0, WordCoverage.compare("నీరు పోసి చూడు", "నీరు పోసి చూడు").missing);
  }

  @Test public void repeatedClauseCannotReplaceAnOmittedWord() {
    String expected = "మొక్కకు నీరు పోసే ముందు మట్టిని చూడండి";
    String partial = "మ పోసే ముందు మట్టిని చూడండి";
    String repeated = expected.replace("నీరు ", "");
    float[] pcm = new float[0];
    PronunciationCheck.Take first = new PronunciationCheck.Take(pcm, expected, partial);
    assertFalse(new PronunciationCheck.Take(pcm, expected, repeated + " " + repeated).improvesOn(first));
    assertTrue(new PronunciationCheck.Take(pcm, expected, expected).improvesOn(first));
  }

  @Test public void instructionalAccentPreservesSamplesAndSpeed() {
    float[] guide = new float[24000], quiet = new float[guide.length];
    java.util.Arrays.fill(guide, .2f);
    java.util.Arrays.fill(quiet, .2f);
    ConversationTiming.shapeDelivery(guide, NarrationDirector.Emotion.GUIDE);
    ConversationTiming.shapeDelivery(quiet, NarrationDirector.Emotion.NEUTRAL);
    assertEquals(guide.length, quiet.length);
    assertTrue(guide[9000] > quiet[9000] && guide[9000] < .24f);
    assertEquals(quiet[0], guide[0], .000001f);
  }
}
