package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Generates release listening fixtures from pinned models using Studio's production DSP. */
public final class ReleaseAudio {
  private static final String SCRIPT = "నమస్కారం! AiTechApp నుంచి mind-blowing tech update. Alight Motion, VN, DNA. మెయిన్ ప్రాబ్లమ్ ఏంటి? ఇక్కడ export చేయండి. గగనాన సంక్లిష్టమైన కర్కశమైన రసవత్తరమైన తాళపత్రాలలోని సస్వరమైన explanation. రెండు = రెండు. ఏం రా ఎలా ఉన్నావురా? ఫ్రెండ్స్, మీరు ఎలా ఉన్నారు? This is the final version. This is the only version.";
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
    if (args[0].equals("retime")) {
      for (ModelCatalog.Voice voice : ModelCatalog.VOICES) {
        if (voice.tiny() || voice.alternative()) continue;
        try (PrintWriter rows = new PrintWriter(new File(work, voice.id + "-retry.tsv"), "UTF-8")) {
          for (int i = 0; i < plan.size(); i++) {
            NarrationDirector.Segment s = plan.get(i);
            String text = PronunciationLexicon.apply(s.text, Map.of(), false);
            File raw = new File(work, voice.id + "-youtube-" + i + ".wav");
            int hz = PcmWave.sampleRate(raw);
            float[] pcm = ConversationTiming.trimPadding(OpenVoiceEngine.read(raw), hz);
            float rate = ConversationTiming.candidateRate(ConversationTiming.targetSeconds(text, s.rate),
                pcm.length / (double) hz, s.rate);
            rows.println((rate == s.rate ? 0 : rate) + "\t0\t0\t0\t" + PronunciationLexicon.piperInput(text));
          }
        }
      }
      return;
    }
    Files.writeString(new File(out, "script.txt").toPath(), SCRIPT);
    StringBuilder stats = new StringBuilder("voice,phrase,first_seconds,selected_seconds,target_seconds\n");
    for (ModelCatalog.Voice voice : ModelCatalog.VOICES) {
      if (voice.alternative()) continue;
      List<NarrationDirector.Segment> voicePlan = voice.tiny() ? DeliveryControls.expand(
          NarrationDirector.planExact(SCRIPT, NarrationDirector.Style.AUTO), true, false) : plan;
      boolean soft = voice.tiny() && voice.gender.equals("Female");
      List<File> chunks = new ArrayList<>(), baseline = new ArrayList<>();
      int hz = voice.tiny() ? 24000 : 22050;
      int improved = 0;
      RenderCleanup.Cleaner cleaner = RenderCleanup.forVoice(Arrays.asList(ModelCatalog.VOICES).indexOf(voice), false, cancel);
      try (TinyVoice tiny = voice.tiny() ? new TinyVoice(work, cancel) : null) {
        if (tiny != null) {
          String phrase = "ఈ రోజు ఒక ముఖ్యమైన విషయం తెలుసుకుందాం.";
          boolean male = voice.gender.equals("Male");
          float[] mel = tiny.acousticMel(phrase, male, 1, phrase.hashCode(), false);
          String label = voice.name.replace(' ', '_');
          PcmWave.write(new File(out, label + "_Native.wav"), tiny.vocode(mel), hz);
          TinyVoice.restoreMelContrast(mel, mel.length / 100, male);
          PcmWave.write(new File(out, label + "_Calibrated.wav"), tiny.vocode(mel), hz);
        }
        for (int i = 0; i < voicePlan.size(); i++) {
          NarrationDirector.Segment s = voicePlan.get(i);
          String text = PronunciationLexicon.apply(s.text, Map.of(), false);
          File raw = new File(work, voice.id + "-youtube-" + i + ".wav");
          File retry = new File(work, voice.id + "-retry-" + i + ".wav");
          if (tiny != null)
            PcmWave.write(raw, tiny.generate(text, voice.gender.equals("Male"), s.rate, s.text.hashCode(), false, false), hz);
          float[] first = OpenVoiceEngine.read(raw);
          float[] selected = ConversationTiming.render(text, s.rate, hz, true, rate -> {
            if (rate == s.rate) return first;
            if (tiny == null) return OpenVoiceEngine.read(retry);
            return tiny.generate(text, voice.gender.equals("Male"), rate, s.text.hashCode(), false, false);
          });
          double initial = ConversationTiming.trimPadding(first, hz).length / (double) hz;
          double actual = selected.length / (double) hz;
          double target = ConversationTiming.targetSeconds(text, s.rate);
          if (Math.abs(actual - initial) > .025) improved++;
          stats.append(voice.id + "," + i + "," + initial + "," + actual + "," + target + "\n");
          File chosen = new File(work, voice.id + "-selected-" + i + ".wav");
          PcmWave.write(chosen, selected, hz);
          File off = new File(work, voice.id + "-off-" + i + ".wav");
          VoiceDeliveryDsp.process(chosen, off, false, soft, null, cancel, true, voice.tiny(), false);
          baseline.add(off);
          File processed = new File(work, voice.id + "-chunk-" + i + ".wav");
          VoiceDeliveryDsp.process(chosen, processed, false, soft, null, cancel, true, voice.tiny(), cleaner);
          chunks.add(processed);
        }
      }
      if (improved == 0) throw new IOException("No measurable native timing change for " + voice.name);
      for (boolean enabled : new boolean[] {false, true}) {
        File combined = new File(work, voice.id + "-joined.wav");
        PcmWave.mergeRaw(enabled ? chunks : baseline, voicePlan, combined, cancel);
        String name = voice.name.replace(' ', '_') + (enabled ? "_Clean" : "_Before");
        File master = new File(out, name + ".wav");
        StudioMastering.Report report = StudioMastering.process(combined, master, cancel, msg -> {}, false);
        Files.writeString(new File(out, name + ".json").toPath(), report.json());
      }
    }
    Files.writeString(new File(out, "phrase-timing.csv").toPath(), stats.toString());
    File reference = new File(assets, "comparison/indic-tiny-male-2.wav");
    float[] quiet = OfflineDenoiser.resample(OpenVoiceEngine.read(reference), PcmWave.sampleRate(reference), 24000, cancel);
    for (int i = 0; i < quiet.length; i++) quiet[i] *= .001f;
    ReferenceLevel.normalize(quiet, true, cancel);
    double level = ReferenceLevel.loudness(quiet);
    if (Math.abs(level + 16) > .3) throw new IOException("Quiet reference loudness: " + level);
    float[] target = ReferenceLevel.excerpt(quiet);
    File base = new File(work, "venkatesh-selected-1.wav");
    RenderCleanup.Cleaner cleaner = RenderCleanup.forVoice(0, true, cancel);
    try (OpenVoiceEngine engine = new OpenVoiceEngine(work, cancel)) {
      float[] speech = OpenVoiceEngine.read(base);
      int rate = PcmWave.sampleRate(base);
      File raw = new File(work, "clone-raw.wav");
      PcmWave.write(raw, engine.convert(speech, rate, engine.embedding(speech, rate), engine.embedding(target, 24000)), 22050);
      for (boolean cleanup : new boolean[]{false,true}) {
        File stage = new File(work, "clone-stage.wav");
        VoiceDeliveryDsp.process(raw, stage, false, false, null, cancel, true, true, cleanup ? cleaner : null);
        StudioMastering.process(stage, new File(out, cleanup ? "OpenVoice_Clean.wav" : "OpenVoice_Before.wav"),
            cancel, msg -> {}, false);
      }
    }
    Files.writeString(new File(out, "Verification.txt").toPath(),
        "Five loudness-matched host comparisons. Each Before/Clean pair shares exactly the same generated take. The OpenVoice excerpt uses a public male reference.\nQuiet-reference regression: " +
        level + " LUFS after normalization.\nSpeaker similarity, pronunciation and handset behavior require listening and device testing.\n");
  }
}
