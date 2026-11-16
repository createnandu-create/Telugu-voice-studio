package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Host-only listening fixtures. Never presents x86 execution as a phone benchmark. */
public final class DeliveryAudit {
  static final String SCRIPT =
      "నమస్కారం ఫ్రెండ్స్! వెల్కమ్ బ్యాక్ టు ఏఐ టెక్ యాప్స్. ఇప్పుడు ఇక్కడ క్లిక్ చేయండి. వావ్! ఎంత"
          + " అద్భుతంగా ఉంది. సమస్య ఏంటంటే పనిచేయకపోతే ఇబ్బంది. అంత సులభమా? కాదు కదా!";

  public static void main(String[] args) throws Exception {
    File root = new File(args[1]);
    root.mkdirs();
    AtomicBoolean cancel = new AtomicBoolean();
    var original = NarrationDirector.planExact(SCRIPT, NarrationDirector.Style.AUTO);
    var plan = DeliveryControls.accents(DeliveryControls.expand(original, true, false));
    if (args[0].equals("plan")) {
      StringBuilder manifest = new StringBuilder();
      for (var s : plan)
        manifest
            .append(s.rate)
            .append('\t')
            .append(s.pitch)
            .append('\t')
            .append(s.pauseAfterMs)
            .append('\t')
            .append(s.emotion)
            .append('\t')
            .append(s.text)
            .append('\n');
      Files.writeString(new File(root, "youtuber-plan.tsv").toPath(), manifest);
    } else if (args[0].equals("tiny")) {
      try (TinyVoice tiny = new TinyVoice(new File(args[2]), cancel)) {
        for (boolean male : new boolean[] {false, true})
          for (int i = 0; i < plan.size(); i++) {
            var s = plan.get(i);
            long start = System.nanoTime();
            float[] audio = tiny.generate(s.text, male, s.rate, s.text.hashCode(), false, true);
            File file =
                new File(root, "tiny-" + (male ? "male" : "female") + "-youtube-" + i + ".wav");
            PcmWave.write(file, audio, 24000);
            System.out.println(
                file.getName() + " host_render_s=" + (System.nanoTime() - start) / 1e9);
          }
      }
    } else if (args[0].equals("merge")) {
      for (String voice : new String[] {"tiny-female", "tiny-male", "padmavathi", "venkatesh"}) {
        List<File> raw = new ArrayList<>(), directed = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
          File input = new File(root, voice + "-youtube-" + i + ".wav"),
              output = new File(root, voice + "-youtube-directed-" + i + ".wav");
          var s = plan.get(i);
          long start = System.nanoTime();
          var applied =
              VoiceDeliveryDsp.process(
                  input, output, DeliveryControls.question(s.text), false, s, cancel);
          System.out.println(
              voice
                  + " segment="
                  + i
                  + " mood="
                  + s.emotion
                  + " pitch_applied="
                  + applied.lifted
                  + " host_dsp_s="
                  + (System.nanoTime() - start) / 1e9);
          raw.add(input);
          directed.add(output);
        }
        PcmWave.merge(raw, plan, new File(root, voice + "-youtube-before-dsp.wav"), cancel);
        PcmWave.merge(directed, plan, new File(root, voice + "-youtube-directed.wav"), cancel);
      }
    }
  }
}
