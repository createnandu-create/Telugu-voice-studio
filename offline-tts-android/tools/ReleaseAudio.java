package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Generates release listening fixtures from pinned models using Studio's production DSP. */
public final class ReleaseAudio {
  private static final String SCRIPT = "నమస్కారం! AiTechApp నుంచి mind-blowing tech update. Alight Motion, VN, DNA. మెయిన్ ప్రాబ్లమ్ ఏంటి? ఇక్కడ export చేయండి. గగనాన సంక్లిష్టమైన కర్కశమైన రసవత్తరమైన తాళపత్రాలలోని సస్వరమైన explanation. రెండు = రెండు.";
  public static void main(String[] args) throws Exception {
    File work = new File(args[1]), assets = new File(args[2]), out = new File(args[3]);
    AtomicBoolean cancel = new AtomicBoolean();
    DynamicEnglishPhonetizer.load(new FileInputStream(new File(assets, "english-cmudict.bin")));
    List<NarrationDirector.Segment> plan = DeliveryControls.expand(
        NarrationDirector.planExact(SCRIPT, NarrationDirector.Style.AUTO), true, true);
    if (args[0].equals("prepare")) {
      for (int i = 0; i < 2; i++) {
        ModelCatalog.Voice v = ModelCatalog.VOICES[i];
        File original = new File(work, v.id + ".onnx");
        if (!FileIntegrity.matches(original, v.originalSha)) throw new IOException(v.id + " checksum");
        File staged = new File(work, v.id + ".staged");
        Files.copy(original.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.write(staged.toPath(), Files.readAllBytes(new File(assets, "piper-metadata.bin").toPath()), StandardOpenOption.APPEND);
        PiperInputRepair.install(staged, new File(work, v.id + "-fixed.onnx"), v.legacySha, v.installedSha);
      }
      try (PrintWriter rows = new PrintWriter(new File(work, "plan.tsv"), "UTF-8")) {
        for (NarrationDirector.Segment s : plan) {
          String spoken = PronunciationLexicon.apply(s.text, Map.of(), false);
          rows.println(s.rate + "\t" + s.pitch + "\t" + s.pauseAfterMs + "\t" + s.emotion + "\t" + PronunciationLexicon.piperInput(spoken));
        }
      }
      return;
    }
    Files.writeString(new File(out, "script.txt").toPath(), SCRIPT);
    for (ModelCatalog.Voice voice : ModelCatalog.VOICES) {
      List<File> chunks = new ArrayList<>();
      try (TinyVoice tiny = voice.tiny() ? new TinyVoice(work, cancel) : null) {
        for (int i = 0; i < plan.size(); i++) {
          NarrationDirector.Segment s = plan.get(i);
          File raw = new File(work, voice.id + "-youtube-" + i + ".wav");
          if (tiny != null) {
            String text = PronunciationLexicon.apply(s.text, Map.of(), false);
            PcmWave.write(raw, tiny.generate(text, voice.gender.equals("Male"), s.rate, s.text.hashCode(), false, false), 24000);
          }
          File processed = new File(work, voice.id + "-chunk-" + i + ".wav");
          VoiceDeliveryDsp.process(raw, processed, DeliveryControls.question(s.text), false, s, cancel, true, voice.tiny());
          chunks.add(processed);
        }
      }
      File combined = new File(work, voice.id + "-joined.wav");
      PcmWave.mergeRaw(chunks, plan, combined, cancel);
      File master = new File(out, voice.name.replace(' ', '_') + ".wav");
      StudioMastering.Report report = StudioMastering.process(combined, master, cancel, msg -> {}, false);
      Files.writeString(new File(out, voice.id + "-measurements.json").toPath(), report.json());
    }
    File reference = new File(assets, "comparison/piper-padmavathi-2.wav");
    float[] quiet = OfflineDenoiser.resample(OpenVoiceEngine.read(reference), PcmWave.sampleRate(reference), 24000, cancel);
    for (int i = 0; i < quiet.length; i++) quiet[i] *= .001f;
    ReferenceLevel.normalize(quiet, true, cancel);
    double level = ReferenceLevel.loudness(quiet);
    if (Math.abs(level + 16) > .3) throw new IOException("Quiet reference loudness: " + level);
    float[] target = ReferenceLevel.excerpt(quiet);
    File base = new File(work, "venkatesh-youtube-1.wav");
    try (OpenVoiceEngine engine = new OpenVoiceEngine(work, cancel)) {
      float[] speech = OpenVoiceEngine.read(base);
      int rate = PcmWave.sampleRate(base);
      PcmWave.write(new File(out, "OpenVoice_public_reference.wav"),
          engine.convert(speech, rate, engine.embedding(speech, rate), engine.embedding(target, 24000)), 22050);
    }
    Files.writeString(new File(out, "Verification.txt").toPath(),
        "Four CPU host renders and one local OpenVoice conversion using a public pretrained reference.\nQuiet-reference regression: " +
        level + " LUFS after normalization.\nSpeaker similarity, pronunciation and handset behavior require listening and device testing.\n");
  }
}
