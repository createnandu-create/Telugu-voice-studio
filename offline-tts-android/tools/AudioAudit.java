package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioAudit {
  public static final String[] TEXTS = {
    "నమస్కారమండీ! రసవత్తరమైన కథ గగనాన వినిపించింది.",
    "తాళపత్రాలలోని శ్రోతలకు కర్కశ శబ్దం వినిపించింది.",
    "శ్రమించే అభ్యాసకుడికి సఫలీకృతమైందో అహర్నిశం తెలుసు.",
    "క్లిష్టమైన పదబంధాలను సస్వరంగా శాస్త్రోక్త పద్ధతిలో పరిశీలిద్దాం.",
    "కఠినమైన ధ్వని సాక్షాత్కారం. అంత సులభమా? సాధ్యమౌతుందా?"
  };

  public static void main(String[] args) throws Exception {
    File out = new File(args[1]);
    out.mkdirs();
    AtomicBoolean cancel = new AtomicBoolean();
    try (TinyVoice voice = new TinyVoice(new File(args[0]), cancel)) {
      for (boolean male : new boolean[] {false, true}) {
        String prefix = male ? "tiny-male" : "tiny-female";
        List<File> raw = new ArrayList<>(), guarded = new ArrayList<>();
        List<NarrationDirector.Segment> plan = new ArrayList<>();
        for (int i = 0; i < TEXTS.length; i++) {
          String text = TEXTS[i];
          plan.add(
              new NarrationDirector.Segment(text, NarrationDirector.Emotion.NEUTRAL, 1, 1, 180));
          for (boolean guard : new boolean[] {false, true}) {
            long at = System.nanoTime();
            float[] x = voice.generate(text, male, 1, 42, false, guard);
            File f = new File(out, prefix + "-" + i + (guard ? "-guard" : "-raw") + ".wav");
            PcmWave.write(f, x, 24000);
            (guard ? guarded : raw).add(f);
            System.out.println(
                f.getName()
                    + " seconds="
                    + x.length / 24000.0
                    + " host_render_s="
                    + (System.nanoTime() - at) / 1e9);
          }
        }
        PcmWave.merge(raw, plan, new File(out, prefix + "-baseline.wav"), cancel);
        PcmWave.merge(guarded, plan, new File(out, prefix + "-duration-guard.wav"), cancel);
      }
    }
  }
}
