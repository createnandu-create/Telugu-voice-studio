package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Same acoustic take, before/after optional tone processing. No user recordings are used. */
public final class ToneComparison {
  public static void main(String[] args) throws Exception {
    File fast = new File(args[0]), tiny = new File(args[1]), out = new File(args[2]);
    out.mkdirs();
    AtomicBoolean cancel = new AtomicBoolean();
    String source = "దృశ్య నిష్పత్తి 9:16. √144 = 12. రిజల్యూషన్ 8K.";
    String spoken = PronunciationLexicon.apply(source, Map.of(), false, true);
    Files.writeString(new File(out, "script.txt").toPath(), source + "\n" + spoken);
    for (int i = 0; i < 4; i++) {
      boolean small = i >= 2, male = i % 2 == 0;
      String name = (small ? "Indic_Tiny" : "Indic_TTS") + (male ? "_Male" : "_Female");
      float[] dry, enhanced;
      int hz = small ? 24000 : 22050;
      if (small) {
        try (TinyVoice voice = new TinyVoice(tiny, cancel)) {
          float[] mel = voice.acousticMel(spoken, male, 1f, 20261120L, false);
          dry = voice.vocode(mel);
          TinyVoice.restoreMelContrast(mel, mel.length / 100, male);
          enhanced = voice.vocode(mel);
        }
      } else {
        try (FastPitchVoice voice = new FastPitchVoice(fast, male, cancel)) {
          dry = voice.render(spoken, 1f);
          enhanced = dry;
        }
      }
      File raw = new File(out, name + "-native.wav"), old = new File(out, name + "-optional.wav");
      PcmWave.write(raw, dry, hz);
      PcmWave.write(old, enhanced, hz);
      VoiceDeliveryDsp.process(old, old, false, small && !male, null, cancel, true, small,
          RenderCleanup.forVoice(small ? (male ? 2 : 3) : (male ? 6 : 7), false, cancel));
      // Loudness/peak matching is left to the audit script; original PCM remains available.
      for (String mode : new String[]{"native", "optional"})
        Files.writeString(new File(out, name + "-" + mode + "-spoken.txt").toPath(), spoken);
      System.out.println(name + " · " + dry.length / (double)hz + " s · native and optional generated");
    }
  }
}
