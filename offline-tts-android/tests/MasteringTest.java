package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MasteringTest {
  static void check(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 2) {
      StudioMastering.Report r =
          StudioMastering.process(
              new File(args[0]), new File(args[1]), new AtomicBoolean(), System.out::println);
      System.out.println(r.json());
      return;
    }
    File root = Files.createTempDirectory("studio-master").toFile();
    File raw = new File(root, "raw.wav"), master = new File(root, "master.wav");
    float[] pcm = new float[PcmWave.RATE * 3];
    for (int i = 0; i < pcm.length; i++)
      pcm[i] =
          (float)
              (.12 * Math.sin(2 * Math.PI * 220 * i / PcmWave.RATE)
                  + .04 * Math.sin(2 * Math.PI * 4100 * i / PcmWave.RATE));
    PcmWave.write(raw, pcm, PcmWave.RATE);
    String sha = FileIntegrity.sha256(raw);
    StudioMastering.Report r = StudioMastering.process(raw, master, new AtomicBoolean(), s -> {});
    check(Math.abs(r.outputLufs + 14) <= .03, "-14 LUFS within tolerance");
    check(r.truePeakDbtp <= -1, "4x true peak ceiling");
    check(sha.equals(FileIntegrity.sha256(raw)), "raw remains unchanged");
    byte[] b = Files.readAllBytes(master.toPath());
    check(
        b.length == 44 + (pcm.length * 2 + StudioMastering.HEAD + StudioMastering.TAIL) * 4,
        "exact frame count");
    for (int i = 44; i < 44 + StudioMastering.HEAD * 4; i++)
      check(b[i] == 0, "150ms exact zero head");
    for (int i = b.length - StudioMastering.TAIL * 4; i < b.length; i++)
      check(b[i] == 0, "100ms exact zero tail");
    for (int i = 44; i < b.length; i += 4)
      check(b[i] == b[i + 2] && b[i + 1] == b[i + 3], "dual mono stereo");
    File cancelled = new File(root, "cancelled.wav");
    Files.write(cancelled.toPath(), new byte[] {7});
    boolean stopped = false;
    try {
      StudioMastering.process(raw, cancelled, new AtomicBoolean(true), s -> {});
    } catch (java.util.concurrent.CancellationException e) {
      stopped = true;
    }
    check(
        stopped && Files.readAllBytes(cancelled.toPath())[0] == 7,
        "cancel preserves previous output");
    check(!new File(cancelled.getPath() + ".float-work").exists(), "temporary file cleaned");
    PcmWave.write(raw, new float[22050], PcmWave.RATE);
    boolean silence = false;
    try {
      StudioMastering.process(raw, master, new AtomicBoolean(), s -> {});
    } catch (IOException e) {
      silence = true;
    }
    check(silence, "silent input not falsely claimed -14 LUFS");
    System.out.println(
        "PASS mastering: loudness, 4x true peak, exact padding, stereo, raw preservation,"
            + " cancellation, silence");
    for (File f : root.listFiles()) f.delete();
    root.delete();
  }
}
