package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OfflinePipelineTest {
  private static int checks = 0;

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    checks++;
  }

  private static void rejects(Runnable r, String message) {
    boolean rejected = false;
    try {
      r.run();
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, message);
  }

  public static void main(String[] args) throws Exception {
    String script = "ధర 3.14 రూపాయలు. Version 2.5 works! నమస్కారం ఫ్రెండ్స్!";
    List<NarrationDirector.Segment> plan =
        NarrationDirector.planExact(script, NarrationDirector.Style.AUTO);
    check(plan.size() == 3, "decimal and version boundaries");
    DirectionContract.validate(script, plan);
    check(
        plan.get(1).emotion == NarrationDirector.Emotion.NEUTRAL, "punctuation is not excitement");
    String tagged = "[happy] అనే పదం స్క్రీన్ మీద ఉంది.";
    check(
        NarrationDirector.planExact(tagged, null).get(0).text.equals(tagged),
        "preserve literal factual markers");
    rejects(
        () ->
            DirectionContract.validate(
                "ధర 3.15 రూపాయలు. Version 2.5 works! నమస్కారం ఫ్రెండ్స్!", plan),
        "changed fact rejected");
    rejects(
        () ->
            DirectionContract.validate(
                script,
                Collections.singletonList(
                    new NarrationDirector.Segment(
                        script, NarrationDirector.Emotion.NEUTRAL, Float.NaN, 1, 200))),
        "NaN rejected");
    rejects(
        () ->
            DirectionContract.validate(
                script,
                Collections.singletonList(
                    new NarrationDirector.Segment(
                        script, NarrationDirector.Emotion.NEUTRAL, 1, 1, 2001))),
        "pause bounds");
    List<NarrationDirector.Segment> longPlan =
        NarrationDirector.planExact("నమస్కారం. ".repeat(500), null);
    check(longPlan.size() == 500, "no 220 sentence limit");
    File temp = Files.createTempDirectory("voice-pipeline-test").toFile();
    try {
      File a = new File(temp, "a.wav"),
          b = new File(temp, "b.wav"),
          out = new File(temp, "joined.wav");
      float[] samples = new float[22050];
      for (int i = 0; i < samples.length; i++) samples[i] = (float) Math.sin(i * 0.04) * 0.5f;
      PcmWave.write(a, samples, 22050);
      PcmWave.write(b, samples, 22050);
      check(PcmWave.validate(a) == 44100, "PCM valid");
      List<NarrationDirector.Segment> two =
          Arrays.asList(
              new NarrationDirector.Segment("ఒకటి.", NarrationDirector.Emotion.NEUTRAL, 1, 1, 300),
              new NarrationDirector.Segment(
                  "రెండు.", NarrationDirector.Emotion.NEUTRAL, 1, 1, 2000));
      PcmWave.merge(Arrays.asList(a, b), two, out, new AtomicBoolean());
      check(
          PcmWave.validate(out) == 101430 + PcmWave.HEAD_PADDING_BYTES
              + PcmWave.TAIL_PADDING_BYTES,
          "exact interior pause plus 150ms head/100ms tail protection");
      check(PcmWave.HEAD_PADDING_BYTES == 6616, "150ms rounded to whole 22.05kHz PCM frames");
      check(PcmWave.TAIL_PADDING_BYTES == 4410, "100ms exact PCM frames");
      try (RandomAccessFile r = new RandomAccessFile(out, "r");
           RandomAccessFile original = new RandomAccessFile(a, "r")) {
        r.seek(44);
        for (int i = 0; i < PcmWave.HEAD_PADDING_BYTES; i++)
          check(r.readUnsignedByte() == 0, "head is zero PCM");
        // Guard must never erase or alter the original first-phoneme waveform.
        original.seek(44);
        for (int i = 0; i < 128; i++)
          check(r.readUnsignedByte() == original.readUnsignedByte(),
              "first 128 synthetic PCM bytes preserved");
        r.seek(r.length() - PcmWave.TAIL_PADDING_BYTES);
        for (int i = 0; i < PcmWave.TAIL_PADDING_BYTES; i++)
          check(r.readUnsignedByte() == 0, "tail is zero PCM");
      }
      String oldHash = FileIntegrity.sha256(out);
      boolean stopped = false;
      try {
        PcmWave.merge(Arrays.asList(a, b), two, out, new AtomicBoolean(true));
      } catch (java.util.concurrent.CancellationException e) {
        stopped = true;
      }
      check(stopped && FileIntegrity.matches(out, oldHash), "cancel preserves prior output");
      RenderJournal journal = new RenderJournal(temp, ModelCatalog.VOICES[0], two);
      Files.copy(a.toPath(), journal.chunk(0).toPath());
      check(!journal.reusable(0), "uncommitted chunk rejected");
      journal.complete(0);
      check(journal.reusable(0), "committed chunk reusable");
      try (RandomAccessFile corrupt = new RandomAccessFile(journal.chunk(0), "rw")) {
        corrupt.seek(60);
        corrupt.write(3);
      }
      check(!journal.reusable(0), "corrupted PCM rejected");
      RenderJournal other = new RenderJournal(temp, ModelCatalog.VOICES[1], two);
      check(!other.directory.equals(journal.directory), "voice identity invalidates resume");
      List<NarrationDirector.Segment> changed = new ArrayList<>(two);
      changed.set(
          0,
          new NarrationDirector.Segment("ఒకటి.", NarrationDirector.Emotion.NEUTRAL, .9f, 1, 300));
      check(
          !new RenderJournal(temp, ModelCatalog.VOICES[0], changed)
              .directory.equals(journal.directory),
          "rate invalidates resume");
      File partial = new File(temp, "partial.wav");
      Files.write(partial.toPath(), new byte[20]);
      boolean bad = false;
      try {
        PcmWave.validate(partial);
      } catch (IOException e) {
        bad = true;
      }
      check(bad, "truncated WAV rejected");
      File text = new File(temp, "atomic.txt");
      FileIntegrity.write(text, "తెలుగు");
      check(FileIntegrity.read(text).equals("తెలుగు"), "atomic UTF8 persistence");
    } finally {
      try (java.util.stream.Stream<Path> paths = Files.walk(temp.toPath())) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.delete(p);
                  } catch (IOException ignored) {
                  }
                });
      }
    }
    System.out.println("PASS: " + checks + " offline pipeline integrity/resume/WAV tests");
  }
}
