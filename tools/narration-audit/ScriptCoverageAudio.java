package in.aitechapp.teluguoffline;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Compare delivery with identical source takes on two scripts outside the studio demo. */
public final class ScriptCoverageAudio {
  public static void main(String[] args) throws Exception {
    File work = new File(args[0]), assets = new File(args[1]), out = new File(args[2]);
    Files.createDirectories(out.toPath());
    try (InputStream dictionary = Files.newInputStream(new File(assets, "english-cmudict.bin").toPath())) {
      DynamicEnglishPhonetizer.load(dictionary);
    }
    AtomicBoolean cancel = new AtomicBoolean();
    String[] scripts = {
        "ఇప్పుడు PixelLab తెరిచి కొత్త పొరను ఎంచుకోండి. పాత చిత్రం అలాగే ఉందా?",
        "మొక్కకు నీరు పోసే ముందు మట్టిని చూడండి. కాస్త తడిగా ఉంటే రేపటి వరకు ఆగుతారా?"
    };
    StringBuilder evidence = new StringBuilder();
    try (HimaVoice voice = new HimaVoice(new File(work, "hima"), "Srinivas", cancel);
         PronunciationCheck checker = new PronunciationCheck(new File(work, "stt"), cancel)) {
      for (int i = 0; i < scripts.length; i++) {
        List<NarrationDirector.Segment> plan = DeliveryControls.expand(
            NarrationDirector.planExact(scripts[i], NarrationDirector.Style.AUTO), false, true);
        List<NarrationDirector.Segment> timing = new ArrayList<>();
        List<File> nativeParts = new ArrayList<>(), directedParts = new ArrayList<>();
        evidence.append(scripts[i]).append('\n');
        for (NarrationDirector.Segment line : plan) {
          String input = HimaVoice.spokenForm(PronunciationLexicon.apply(line.text, Map.of(), false, true, true))
              .replaceAll("[,.]+", " ").trim();
          float[] pcm = PronunciationCheck.renderNamed(checker, input, line.rate, 24000, true, voice);
          evidence.append("Expected ").append(input).append("\nRecognized ").append(checker.recognize(pcm, 24000)).append('\n');
          File before = new File(work, "before-" + nativeParts.size() + ".wav");
          File after = new File(work, "after-" + nativeParts.size() + ".wav");
          PcmWave.write(before, pcm, 24000);
          ConversationTiming.shapeDelivery(pcm, line.emotion);
          PcmWave.write(after, pcm, 24000);
          nativeParts.add(before);
          directedParts.add(after);
          timing.add(new NarrationDirector.Segment(line.text, line.emotion, line.rate, line.pitch,
              line.text.endsWith(".") ? 0 : line.pauseAfterMs));
        }
        PcmWave.mergeRaw(nativeParts, timing, new File(out, (i+1)+"-before.wav"), cancel);
        PcmWave.mergeRaw(directedParts, timing, new File(out, (i+1)+"-directed.wav"), cancel);
      }
    }
    Files.writeString(new File(out, "script-and-recognition.txt").toPath(), evidence);
  }
}
