package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TargetedAudioTest {
  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  public static void main(String[] args) throws Exception {
    for (String word : new String[] {"నమస్కారం", "రసవత్తరమైన", "గగనాన"}) {
      float[] d = new float[word.length() * 2 + 1];
      Arrays.fill(d, .25f);
      float[] original = d.clone();
      float[] guarded = TinyDurationGuard.apply(word, d);
      check(Arrays.equals(d, original), "duration source preserved");
      char medial = word.equals("నమస్కారం") ? 'మ' : word.equals("గగనాన") ? 'గ' : 'వ';
      int at = word.indexOf(medial, 1);
      check(guarded[at * 2 + 1] >= 6.5f, "65ms CV floor " + word);
      check(guarded[0] == d[0], "BOS blank unchanged");
    }
    String longWord = "తాళపత్రాలలోని";
    float[] d = new float[longWord.length() * 2 + 1];
    Arrays.fill(d, 8);
    float[] result = TinyDurationGuard.apply(longWord, d);
    check(Math.abs(result[1] - 8.8) < .001, "long-word duration scales by 10%");
    check(
        Arrays.equals(new float[] {1, 2, 3}, TinyDurationGuard.apply("శ", new float[] {1, 2, 3})),
        "short sibilant unchanged");
    check(
        Arrays.equals(new float[] {1, 2, 3}, TinyDurationGuard.apply("ష", new float[] {1, 2, 3})),
        "retroflex unchanged");
    String script =
        "హాయ్ ఫ్రెండ్స్! ఇక్కడ క్లిక్ చేయండి. వావ్! సమస్య ఏంటంటే పనిచేయకపోతే ఇబ్బంది. అంత సులభమా?";
    var plan = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO);
    check(plan.get(0).emotion == NarrationDirector.Emotion.WELCOME, "welcome");
    check(plan.get(1).emotion == NarrationDirector.Emotion.GUIDE, "instruction");
    check(plan.get(2).emotion == NarrationDirector.Emotion.SURPRISE, "wow");
    check(plan.get(3).emotion == NarrationDirector.Emotion.EMPATHY, "problem");
    check(
        plan.get(4).emotion == NarrationDirector.Emotion.QUESTION
            && plan.get(4).pauseAfterMs == 240,
        "question hold");
    var accented = DeliveryControls.accents(plan);
    DirectionContract.validate(script, plan);
    check(
        String.join(" ", accented.stream().map(s -> s.text).toList()).equals(script),
        "render-only accents keep every original word");
    check(accented.stream().anyMatch(s -> s.pauseAfterMs == 110), "action pause");
    check(
        accented.stream()
            .anyMatch(s -> s.text.equals("వావ్!") && Math.abs(s.rate - 1.03 / 1.3) < .001),
        "interjection hold");
    check(
        NarrationDirector.planExact("కానీ బాగుంది.", NarrationDirector.Style.AUTO).get(0).emotion
            == NarrationDirector.Emotion.NEUTRAL,
        "contrast alone is not sadness");
    int sr = 24000;
    float[] capture = new float[sr * 3];
    for (int i = sr / 2; i < sr * 5 / 2; i++)
      capture[i] = (float) (.15 * Math.sin(2 * Math.PI * 200 * i / sr));
    var cleaned = RecordingCleanup.clean(capture, sr);
    check(
        cleaned.samples.length > sr * 2 && cleaned.samples.length < sr * 23 / 10,
        "trim retains 50ms margins");
    check(cleaned.removedStart > sr * 4 / 10, "leading silence trimmed");
    float[] silence = new float[sr];
    check(RecordingCleanup.clean(silence, sr).samples.length == sr, "silent capture retained");
    for (int hz : new int[] {20, 200}) {
      float[] tone = new float[sr];
      for (int i = 0; i < sr; i++) tone[i] = (float) (.3 * Math.sin(2 * Math.PI * hz * i / sr));
      var filtered = RecordingCleanup.clean(tone, sr);
      double power = 0;
      for (int i = sr / 4; i < filtered.samples.length; i++)
        power += filtered.samples[i] * filtered.samples[i];
      double gain = Math.sqrt(power / (filtered.samples.length - sr / 4)) / (.3 / Math.sqrt(2));
      check(hz == 20 ? gain < .08 : gain > .95, "80Hz high-pass response " + hz + " " + gain);
    }
    float[] tone = new float[sr * 2];
    for (int i = 0; i < tone.length; i++)
      tone[i] = (float) (.2 * Math.sin(2 * Math.PI * 180 * i / sr));
    for (var emotion :
        new NarrationDirector.Emotion[] {
          NarrationDirector.Emotion.WELCOME,
          NarrationDirector.Emotion.SURPRISE,
          NarrationDirector.Emotion.EMPATHY
        }) {
      float pitch =
          emotion == NarrationDirector.Emotion.WELCOME
              ? 1.12f
              : emotion == NarrationDirector.Emotion.SURPRISE ? 1.2f : .92f;
      var segment = new NarrationDirector.Segment("fixture", emotion, 1, pitch, 0);
      var audio = VoiceDeliveryDsp.directed(tone, sr, segment, false, false, new AtomicBoolean());
      check(audio.samples.length == tone.length && audio.lifted, "emotion voiced shift");
      double hz = DeliveryTest.frequency(audio.samples, sr, sr / 2, sr * 3 / 2);
      check(Math.abs(hz / 180 - pitch) < .03, "independent emotion pitch " + emotion + " " + hz);
    }
    File f = Files.createTempFile("waveform", ".wav").toFile();
    PcmWave.write(f, capture, sr);
    float[] bars = WaveformEnvelope.read(f);
    check(bars[0] == 0 && bars[32] > .1, "waveform reads actual PCM");
    System.out.println(
        "PASS targeted audio: CV floors, long words, immutable script, five modes, action pause,"
            + " pitch, recording trim/HPF, waveform");
  }
}
