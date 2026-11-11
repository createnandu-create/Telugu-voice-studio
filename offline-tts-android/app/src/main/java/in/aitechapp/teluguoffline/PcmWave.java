package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded memory PCM16 mono WAV. Only canonical WAVs written by this pipeline are accepted. */
public final class PcmWave {
  public static final int RATE = 22050;
  // Experimental playback guard: padding cannot reconstruct a missing synthesized consonant.
  public static final int HEAD_PADDING_MS = 150;
  public static final int TAIL_PADDING_MS = 100;
  public static final long HEAD_PADDING_BYTES = Math.round(RATE * HEAD_PADDING_MS / 1000.0) * 2L;
  public static final long TAIL_PADDING_BYTES = Math.round(RATE * TAIL_PADDING_MS / 1000.0) * 2L;

  private static void le(RandomAccessFile f, long v, int n) throws IOException {
    for (int i = 0; i < n; i++) f.write((int) (v >> (8 * i)) & 255);
  }

  private static long readLe(RandomAccessFile f, int n) throws IOException {
    long v = 0;
    for (int i = 0; i < n; i++) v |= (long) f.readUnsignedByte() << (8 * i);
    return v;
  }

  private static void header(RandomAccessFile f, long bytes, int rate) throws IOException {
    if (bytes < 0 || bytes > 0xffffffffL - 36)
      throw new IOException("WAV exceeds 4 GB. Split the script.");
    f.seek(0);
    f.writeBytes("RIFF");
    le(f, bytes + 36, 4);
    f.writeBytes("WAVEfmt ");
    le(f, 16, 4);
    le(f, 1, 2);
    le(f, 1, 2);
    le(f, rate, 4);
    le(f, rate * 2, 4);
    le(f, 2, 2);
    le(f, 16, 2);
    f.writeBytes("data");
    le(f, bytes, 4);
  }

  public static int sampleRate(File file) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
      f.seek(24);
      int rate = (int) readLe(f, 4);
      if (rate != 22050 && rate != 24000) throw new IOException("Unsupported native WAV rate.");
      return rate;
    }
  }

  public static long validate(File file) throws IOException {
    int rate = sampleRate(file);
    try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
      if (f.length() < 44) throw new IOException("Incomplete WAV.");
      if (f.readInt() != 0x52494646) throw new IOException("Invalid WAV RIFF.");
      if (readLe(f, 4) != f.length() - 8
          || f.readInt() != 0x57415645
          || f.readInt() != 0x666d7420
          || readLe(f, 4) != 16
          || readLe(f, 2) != 1
          || readLe(f, 2) != 1
          || readLe(f, 4) != rate
          || readLe(f, 4) != rate * 2
          || readLe(f, 2) != 2
          || readLe(f, 2) != 16
          || f.readInt() != 0x64617461) throw new IOException("Unexpected WAV format.");
      long bytes = readLe(f, 4);
      if (bytes != f.length() - 44 || bytes < 2 || bytes % 2 != 0)
        throw new IOException("Incomplete PCM data.");
      return bytes;
    }
  }

  public static void write(File file, float[] samples, int sampleRate) throws IOException {
    if ((sampleRate != RATE && sampleRate != 24000) || samples.length == 0)
      throw new IOException("Voice produced no supported audio.");
    File part = new File(file.getPath() + ".part");
    try (RandomAccessFile out = new RandomAccessFile(part, "rw")) {
      out.setLength(0);
      header(out, 2L * samples.length, sampleRate);
      byte[] b = new byte[16384];
      int used = 0;
      for (float sample : samples) {
        if (!Float.isFinite(sample)) throw new IOException("Voice produced invalid samples.");
        int value = Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
        b[used++] = (byte) value;
        b[used++] = (byte) (value >> 8);
        if (used == b.length) {
          out.write(b);
          used = 0;
        }
      }
      out.write(b, 0, used);
      out.getFD().sync();
    }
    FileIntegrity.promote(part, file);
  }

  private static void writeZeroPcm(RandomAccessFile out, byte[] zeros, long count)
      throws IOException {
    if ((count & 1L) != 0L) throw new IOException("PCM16 padding not sample-aligned");
    while (count > 0) {
      int n = (int) Math.min(count, zeros.length);
      out.write(zeros, 0, n);
      count -= n;
    }
  }

  public static void merge(
      List<File> chunks,
      List<NarrationDirector.Segment> plan,
      File destination,
      AtomicBoolean cancel)
      throws IOException {
    merge(chunks, plan, destination, cancel, false);
  }

  /** Optional WAV-only tonal A/B preset. Never changes cached source PCM chunks. */
  public static void merge(
      List<File> chunks,
      List<NarrationDirector.Segment> plan,
      File destination,
      AtomicBoolean cancel,
      boolean applyWarmthEq)
      throws IOException {
    mergeInternal(chunks, plan, destination, cancel, applyWarmthEq, true);
  }

  /** Source for studio mastering: no EQ or padding, so those effects are never applied twice. */
  public static void mergeRaw(
      List<File> chunks,
      List<NarrationDirector.Segment> plan,
      File destination,
      AtomicBoolean cancel)
      throws IOException {
    mergeInternal(chunks, plan, destination, cancel, false, false);
  }

  private static void mergeInternal(
      List<File> chunks,
      List<NarrationDirector.Segment> plan,
      File destination,
      AtomicBoolean cancel,
      boolean applyWarmthEq,
      boolean padded)
      throws IOException {
    if (chunks.size() != plan.size() || chunks.isEmpty())
      throw new IOException("Missing render chunks.");
    int rate = sampleRate(chunks.get(0));
    File part = new File(destination.getPath() + ".part");
    try (RandomAccessFile out = new RandomAccessFile(part, "rw")) {
      out.setLength(0);
      header(out, 0, rate);
      byte[] buffer = new byte[65536], silence = new byte[8192];
      // Only final WAV gets the head/tail guard; never shift each sentence or cached chunk.
      // Every generated native-rate PCM sample stays byte-for-byte unchanged.
      long bytes = padded ? Math.round(rate * HEAD_PADDING_MS / 1000.0) * 2L : 0;
      writeZeroPcm(out, silence, bytes);
      for (int i = 0; i < chunks.size(); i++) {
        if (cancel.get()) throw new CancellationException();
        long pcmBytes = validate(chunks.get(i));
        if (sampleRate(chunks.get(i)) != rate)
          throw new IOException("Mixed sample rates in render chunks.");
        bytes += pcmBytes;
        try (RandomAccessFile in = new RandomAccessFile(chunks.get(i), "r")) {
          in.seek(44);
          WarmthEq warmth = applyWarmthEq ? new WarmthEq(rate) : null;
          long remaining = pcmBytes;
          while (remaining > 0) {
            if (cancel.get()) throw new CancellationException();
            int n = (int) Math.min(remaining, buffer.length);
            in.readFully(buffer, 0, n);
            if (warmth != null) warmth.process(buffer, n);
            out.write(buffer, 0, n);
            remaining -= n;
          }
        }
        long quiet =
            (i == chunks.size() - 1) ? 0 : (long) rate * plan.get(i).pauseAfterMs / 1000 * 2;
        bytes += quiet;
        if (bytes > 0xffffffffL - 36) throw new IOException("WAV too large.");
        writeZeroPcm(out, silence, quiet);
      }
      bytes += padded ? Math.round(rate * TAIL_PADDING_MS / 1000.0) * 2L : 0;
      if (bytes > 0xffffffffL - 36) throw new IOException("WAV too large.");
      writeZeroPcm(out, silence, padded ? Math.round(rate * TAIL_PADDING_MS / 1000.0) * 2L : 0);
      header(out, bytes, rate);
      out.getFD().sync();
    } catch (IOException | RuntimeException e) {
      part.delete();
      throw e;
    }
    FileIntegrity.promote(part, destination);
  }

  private PcmWave() {}
}
