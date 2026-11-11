package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline streaming mastering. Does not reconstruct missing phonemes or replace the VITS vocoder.
 */
public final class StudioMastering {
  public static final int RATE = 44100, HEAD = 6615, TAIL = 4410;
  public static final String VERSION = "warm-v1";
  public static final double TARGET_LUFS = -14.0, CEILING_DBTP = -1.0;
  // A small reconstruction/PCM rounding margin below the requested ceiling.
  private static final double LIMIT = Math.pow(10, -1.10 / 20.0);
  private static final int BLOCK = 441;

  public interface Progress {
    void update(String message);
  }

  public static final class Report {
    public double inputLufs, outputLufs, truePeakDbtp, driveDb;
    public long frames;
    public int iterations;

    public String json() {
      return String.format(
          Locale.US,
          "{\"preset\":\"%s\",\"sample_rate\":44100,\"channels\":2,\"head_frames\":6615,\"tail_frames\":4410,\"pre_limit_lufs\":%.4f,\"output_lufs\":%.4f,\"true_peak_dbtp_4x\":%.4f,\"drive_db\":%.4f,\"iterations\":%d,\"frames\":%d}",
          VERSION,
          inputLufs,
          outputLufs,
          truePeakDbtp,
          driveDb,
          iterations,
          frames);
    }
  }

  private static void check(AtomicBoolean c) {
    if (c.get() || Thread.currentThread().isInterrupted())
      throw new CancellationException("Mastering paused; raw audio retained.");
  }

  interface Signal {
    long size();

    double get(long index) throws IOException;
  }

  /** Two cached pages make centered FIR lookahead bounded in memory, including long narrations. */
  private static final class Samples implements Signal, AutoCloseable {
    private final RandomAccessFile file;
    private final int stride, offset;
    private final boolean floating;
    private final long frames;
    private final long[] pages = {-1, -1};
    private final double[][] values = {new double[4096], new double[4096]};
    private int replace;

    Samples(File path, int offset, int stride, boolean floating, long frames) throws IOException {
      file = new RandomAccessFile(path, "r");
      this.offset = offset;
      this.stride = stride;
      this.floating = floating;
      this.frames = frames;
    }

    public long size() {
      return frames;
    }

    public double get(long index) throws IOException {
      if (index < 0 || index >= frames) return 0;
      long page = index / 4096;
      int slot = pages[0] == page ? 0 : pages[1] == page ? 1 : -1;
      if (slot < 0) {
        slot = replace;
        replace = 1 - replace;
        pages[slot] = page;
        int count = (int) Math.min(4096, frames - page * 4096);
        byte[] b = new byte[count * stride];
        file.seek(offset + page * 4096 * stride);
        file.readFully(b);
        for (int i = 0; i < count; i++) {
          int j = i * stride;
          values[slot][i] =
              floating
                  ? Float.intBitsToFloat(
                      ((b[j] & 255) << 24)
                          | ((b[j + 1] & 255) << 16)
                          | ((b[j + 2] & 255) << 8)
                          | (b[j + 3] & 255))
                  : (short) ((b[j] & 255) | ((b[j + 1] & 255) << 8)) / 32768.0;
        }
      }
      return values[slot][(int) (index % 4096)];
    }

    public void close() throws IOException {
      file.close();
    }
  }

  /** Normalized 32-tap Blackman-windowed sinc polyphase bank. Phase zero is an exact copy. */
  private static final double[][] FIR = new double[4][32];

  static {
    for (int phase = 1; phase < 4; phase++) {
      double sum = 0;
      for (int k = 0; k < 32; k++) {
        double x = phase / 4.0 - (k - 15);
        double w = .42 + .5 * Math.cos(Math.PI * x / 16) + .08 * Math.cos(2 * Math.PI * x / 16);
        double v = Math.sin(Math.PI * x) / (Math.PI * x) * w;
        FIR[phase][k] = v;
        sum += v;
      }
      for (int k = 0; k < 32; k++) FIR[phase][k] /= sum;
    }
  }

  private static double sinc(Signal s, long frame, int phase) throws IOException {
    if (phase == 0) return s.get(frame);
    double sum = 0;
    for (int k = 0; k < 32; k++) sum += s.get(frame + k - 15) * FIR[phase][k];
    return sum;
  }

  static final class Biquad {
    final double b0, b1, b2, a1, a2;
    double z1, z2;

    Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
      this.b0 = b0 / a0;
      this.b1 = b1 / a0;
      this.b2 = b2 / a0;
      this.a1 = a1 / a0;
      this.a2 = a2 / a0;
    }

    double next(double x) {
      double y = b0 * x + z1;
      z1 = b1 * x - a1 * y + z2;
      z2 = b2 * x - a2 * y;
      return y;
    }

    static Biquad shelf(double hz, double db, boolean high) {
      double a = Math.pow(10, db / 40),
          w = 2 * Math.PI * hz / RATE,
          c = Math.cos(w),
          alpha = Math.sin(w) / (2 * .707),
          beta = 2 * Math.sqrt(a) * alpha;
      if (high)
        return new Biquad(
            a * ((a + 1) + (a - 1) * c + beta),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - beta),
            (a + 1) - (a - 1) * c + beta,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - beta);
      return new Biquad(
          a * ((a + 1) - (a - 1) * c + beta),
          2 * a * ((a - 1) - (a + 1) * c),
          a * ((a + 1) - (a - 1) * c - beta),
          (a + 1) + (a - 1) * c + beta,
          -2 * ((a - 1) + (a + 1) * c),
          (a + 1) + (a - 1) * c - beta);
    }

    static Biquad kShelf() {
      double k = Math.tan(Math.PI * 1681.974450955533 / RATE),
          q = .7071752369554196,
          vh = Math.pow(10, 3.999843853973347 / 20),
          vb = Math.pow(vh, .4996667741545416);
      return new Biquad(
          vh + vb * k / q + k * k,
          2 * (k * k - vh),
          vh - vb * k / q + k * k,
          1 + k / q + k * k,
          2 * (k * k - 1),
          1 - k / q + k * k);
    }

    static Biquad kHighpass() {
      double k = Math.tan(Math.PI * 38.13547087602444 / RATE),
          q = .5003270373238773,
          a = 1 + k / q + k * k;
      return new Biquad(1, -2, 1, 1, 2 * (k * k - 1) / a, (1 - k / q + k * k) / a);
    }
  }

  /**
   * BS.1770 K weighting, 400ms/100ms blocks, -70 LUFS absolute and -10 LU relative gates. One mono
   * signal is intentionally duplicated to stereo, so channel energy is multiplied by two.
   */
  static final class Meter {
    final Biquad shelf = Biquad.kShelf(), highpass = Biquad.kHighpass();
    final double[] ring = new double[RATE * 4 / 10];
    final ArrayList<Double> blocks = new ArrayList<>();
    double sum;
    long count;

    void add(double x) {
      double y = highpass.next(shelf.next(x));
      double e = 2 * y * y;
      int i = (int) (count % ring.length);
      sum += e - ring[i];
      ring[i] = e;
      count++;
      if (count >= ring.length && (count - ring.length) % (RATE / 10) == 0)
        blocks.add(Math.max(0, sum / ring.length));
    }

    static double lufs(double e) {
      return e > 0 ? -.691 + 10 * Math.log10(e) : Double.NEGATIVE_INFINITY;
    }

    double result() {
      double total = 0;
      int n = 0;
      for (double e : blocks)
        if (lufs(e) >= -70) {
          total += e;
          n++;
        }
      if (n == 0) return Double.NEGATIVE_INFINITY;
      double gate = lufs(total / n) - 10;
      total = 0;
      n = 0;
      for (double e : blocks)
        if (lufs(e) >= -70 && lufs(e) >= gate) {
          total += e;
          n++;
        }
      return n == 0 ? Double.NEGATIVE_INFINITY : lufs(total / n);
    }
  }

  private static final class Analysis {
    double lufs, peak;
    double[] peaks;
  }

  private static Analysis analyze(Signal signal, boolean collect, AtomicBoolean cancel)
      throws IOException {
    Meter meter = new Meter();
    Analysis a = new Analysis();
    if (collect) a.peaks = new double[(int) ((signal.size() + BLOCK - 1) / BLOCK)];
    for (long n = 0; n < signal.size(); n++) {
      if ((n & 4095) == 0) check(cancel);
      meter.add(signal.get(n));
      double peak = 0;
      for (int p = 0; p < 4; p++) peak = Math.max(peak, Math.abs(sinc(signal, n, p)));
      a.peak = Math.max(a.peak, peak);
      if (collect) {
        int b = (int) (n / BLOCK);
        a.peaks[b] = Math.max(a.peaks[b], peak);
      }
    }
    a.lufs = meter.result();
    return a;
  }

  private static void le(DataOutputStream out, long value, int bytes) throws IOException {
    for (int i = 0; i < bytes; i++) out.write((int) (value >> (8 * i)) & 255);
  }

  private static void header(DataOutputStream out, long frames) throws IOException {
    long bytes = frames * 4;
    if (bytes > 0xffffffffL - 36)
      throw new IOException("Stereo WAV exceeds 4 GB; split the script.");
    out.writeBytes("RIFF");
    le(out, bytes + 36, 4);
    out.writeBytes("WAVEfmt ");
    le(out, 16, 4);
    le(out, 1, 2);
    le(out, 2, 2);
    le(out, RATE, 4);
    le(out, RATE * 4, 4);
    le(out, 4, 2);
    le(out, 16, 2);
    out.writeBytes("data");
    le(out, bytes, 4);
  }

  private static void silence(DataOutputStream out, int frames) throws IOException {
    for (int i = 0; i < frames; i++) out.writeInt(0);
  }

  /** 10ms block-peak lookahead, one-block hold, 80ms release. Verified after PCM quantization. */
  private static void limit(
      Signal signal, double[] peaks, double drive, double guard, File path, AtomicBoolean cancel)
      throws IOException {
    double envelope = 1, release = 1 - Math.exp(-1.0 / (.080 * RATE));
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path), 65536))) {
      header(out, HEAD + signal.size() + TAIL);
      silence(out, HEAD);
      for (long n = 0; n < signal.size(); n++) {
        if ((n & 4095) == 0) check(cancel);
        int b = (int) (n / BLOCK);
        double local = 0;
        for (int j = Math.max(0, b - 1); j <= Math.min(peaks.length - 1, b + 1); j++)
          local = Math.max(local, peaks[j]);
        double target = Math.min(1, LIMIT / (local * drive + 1e-20));
        envelope = target < envelope ? target : envelope + (target - envelope) * release;
        double value = signal.get(n) * drive * envelope * guard;
        if (!Double.isFinite(value) || Math.abs(value) > 1)
          throw new IOException("Mastering produced out-of-range PCM.");
        int pcm = (int) Math.round(value * 32767);
        le(out, pcm, 2);
        le(out, pcm, 2);
      }
      silence(out, TAIL);
    }
  }

  public static Report process(File raw, File destination, AtomicBoolean cancel, Progress progress)
      throws IOException {
    if (PcmWave.sampleRate(raw) != 22050)
      throw new IOException(
          "Studio preset supports Piper 22.05 kHz only; keep native audio for this voice.");
    long samples = PcmWave.validate(raw) / 2;
    long frames = samples * 2;
    File floats = new File(destination.getPath() + ".float-work"),
        part = new File(destination.getPath() + ".part");
    if (destination.getParentFile().getUsableSpace() < frames * 8 + 10_000_000)
      throw new IOException("Free storage for the stereo master; raw audio is retained.");
    try {
      progress.update("Mastering · sinc resampling and warmth EQ");
      try (Samples input = new Samples(raw, 44, 2, false, samples);
          DataOutputStream out =
              new DataOutputStream(new BufferedOutputStream(new FileOutputStream(floats), 65536))) {
        Biquad low = Biquad.shelf(180, 3, false), high = Biquad.shelf(6000, -2.5, true);
        for (long n = 0; n < frames; n++) {
          if ((n & 4095) == 0) check(cancel);
          double v = high.next(low.next(sinc(input, n / 2, n % 2 == 0 ? 0 : 2)));
          if (!Double.isFinite(v)) throw new IOException("Invalid DSP output.");
          out.writeFloat((float) v);
        }
      }
      try (Samples warm = new Samples(floats, 0, 4, true, frames)) {
        Analysis baseline = analyze(warm, true, cancel);
        if (!Double.isFinite(baseline.lufs))
          throw new IOException(
              "Audio is too short or silent for gated -14 LUFS mastering. Raw audio retained.");
        Report report = new Report();
        report.inputLufs = baseline.lufs;
        report.frames = frames + HEAD + TAIL;
        double driveDb = TARGET_LUFS - baseline.lufs;
        for (int attempt = 0; attempt < 8; attempt++) {
          check(cancel);
          progress.update("Mastering · loudness / true-peak verification " + (attempt + 1));
          double drive = Math.pow(10, driveDb / 20), guard = 1;
          Analysis measured = null;
          for (int peakPass = 0; peakPass < 3; peakPass++) {
            limit(warm, baseline.peaks, drive, guard, part, cancel);
            try (Samples output = new Samples(part, 44, 4, false, report.frames)) {
              measured = analyze(output, false, cancel);
            }
            if (measured.peak <= Math.pow(10, CEILING_DBTP / 20)) break;
            guard *= LIMIT / measured.peak;
          }
          double error = TARGET_LUFS - measured.lufs;
          report.iterations = attempt + 1;
          report.driveDb = driveDb;
          report.outputLufs = measured.lufs;
          report.truePeakDbtp = 20 * Math.log10(measured.peak);
          if (Double.isFinite(error)
              && Math.abs(error) <= .03
              && report.truePeakDbtp <= CEILING_DBTP) {
            check(cancel);
            FileIntegrity.promote(part, destination);
            return report;
          }
          if (!Double.isFinite(error))
            throw new IOException("Could not measure mastered loudness.");
          driveDb += error;
          if (Math.abs(driveDb) > 60) break;
        }
        throw new IOException(
            "Could not meet loudness/true-peak targets without more processing; raw audio"
                + " retained.");
      }
    } finally {
      floats.delete();
      part.delete();
    }
  }

  private StudioMastering() {}
}
