package in.aitechapp.teluguoffline;

import java.util.List;

public final class NarrationDirectorTest {
  static void verify(String text, NarrationDirector.Emotion emotion) {
    List<NarrationDirector.Segment> xs =
        NarrationDirector.plan(text, NarrationDirector.Style.TUTORIAL);
    if (xs.size() != 1 || xs.get(0).emotion != emotion)
      throw new AssertionError(text + " => " + xs);
  }

  public static void main(String[] args) {
    verify("వావ్!", NarrationDirector.Emotion.SURPRISE);
    verify("ఇక్కడ క్లిక్ చేయండి.", NarrationDirector.Emotion.GUIDE);
    verify("జాగ్రత్తగా ఉపయోగించండి.", NarrationDirector.Emotion.SERIOUS);
    verify("నమస్కారం.", NarrationDirector.Emotion.WELCOME);
    verify("ఇది ఎందుకు పనిచేయలేదు?", NarrationDirector.Emotion.QUESTION);
    verify("దయచేసి సబ్‌స్క్రైబ్ చేయండి.", NarrationDirector.Emotion.CALL_TO_ACTION);
    List<NarrationDirector.Segment> x =
        NarrationDirector.plan(
            "ఇక్కడ క్లిక్ చేయండి. వావ్! ఎంత అద్భుతం జరిగింది.", NarrationDirector.Style.TUTORIAL);
    if (x.size() != 3
        || x.get(0).emotion != NarrationDirector.Emotion.GUIDE
        || x.get(1).emotion != NarrationDirector.Emotion.SURPRISE)
      throw new AssertionError("multi-sentence");
    if (NarrationDirector.plan("  ", NarrationDirector.Style.AUTO).size() != 0)
      throw new AssertionError("empty");
    verify("[ఆశ్చర్యం] వావ్!", NarrationDirector.Emotion.SURPRISE);
    if (NarrationDirector.plan("[happy] మంచి పని!", NarrationDirector.Style.AUTO)
        .get(0)
        .text
        .contains("[happy]")) throw new AssertionError("direction marker leaked into TTS");
    String prompt = SemanticNarrationPrompt.create("వావ్!", "REVIEW");
    if (!prompt.contains("REVIEW") || !prompt.contains("SURPRISE"))
      throw new AssertionError("semantic prompt");
    System.out.println("PASS: 11 offline narration/director contract checks");
  }
}
