package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded memory PCM16 mono WAV. Only canonical WAVs written by this pipeline are accepted. */
public final class PcmWave {
  public static final int RATE = 22050;

  private static void le(RandomAccessFile f, long v, int n) throws IOException {
    for (int i = 0; i < n; i++) f.write((int) (v >> (8 * i)) & 255);
  }

  private static long readLe(RandomAccessFile f, int n) throws IOException {
    long v = 0;
    for (int i = 0; i < n; i++) v |= (long) f.readUnsignedByte() << (8 * i);
    return v;
  }

  private static void header(RandomAccessFile f, long bytes) throws IOException {
    if (bytes < 0 || bytes > 0xffffffffL - 36)
      throw new IOException("WAV exceeds 4 GB. Split the script.");
    f.seek(0);
    f.writeBytes("RIFF");
    le(f, bytes + 36, 4);
    f.writeBytes("WAVEfmt ");
    le(f, 16, 4);
    le(f, 1, 2);
    le(f, 1, 2);
    le(f, RATE, 4);
    le(f, RATE * 2, 4);
    le(f, 2, 2);
    le(f, 16, 2);
    f.writeBytes("data");
    le(f, bytes, 4);
  }

  public static long validate(File file) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
      if (f.length() < 44) throw new IOException("Incomplete WAV.");
      if (f.readInt() != 0x52494646) throw new IOException("Invalid WAV RIFF.");
      if (readLe(f, 4) != f.length() - 8
          || f.readInt() != 0x57415645
          || f.readInt() != 0x666d7420
          || readLe(f, 4) != 16
          || readLe(f, 2) != 1
          || readLe(f, 2) != 1
          || readLe(f, 4) != RATE
          || readLe(f, 4) != RATE * 2
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
    if (sampleRate != RATE || samples.length == 0)
      throw new IOException("Voice produced no supported audio.");
    File part = new File(file.getPath() + ".part");
    try (RandomAccessFile out = new RandomAccessFile(part, "rw")) {
      out.setLength(0);
      header(out, 2L * samples.length);
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

  public static void merge(
      List<File> chunks,
      List<NarrationDirector.Segment> plan,
      File destination,
      AtomicBoolean cancel)
      throws IOException {
    if (chunks.size() != plan.size() || chunks.isEmpty())
      throw new IOException("Missing render chunks.");
    File part = new File(destination.getPath() + ".part");
    try (RandomAccessFile out = new RandomAccessFile(part, "rw")) {
      out.setLength(0);
      header(out, 0);
      byte[] buffer = new byte[65536], silence = new byte[8192];
      long bytes = 0;
      for (int i = 0; i < chunks.size(); i++) {
        if (cancel.get()) throw new CancellationException();
        bytes += validate(chunks.get(i));
        try (FileInputStream in = new FileInputStream(chunks.get(i))) {
          if (in.skip(44) != 44) throw new EOFException();
          int n;
          while ((n = in.read(buffer)) != -1) {
            if (cancel.get()) throw new CancellationException();
            out.write(buffer, 0, n);
          }
        }
        long quiet =
            (i == chunks.size() - 1) ? 0 : (long) RATE * plan.get(i).pauseAfterMs / 1000 * 2;
        bytes += quiet;
        if (bytes > 0xffffffffL - 36) throw new IOException("WAV too large.");
        while (quiet > 0) {
          int n = (int) Math.min(quiet, silence.length);
          out.write(silence, 0, n);
          quiet -= n;
        }
      }
      header(out, bytes);
      out.getFD().sync();
    } catch (IOException | RuntimeException e) {
      part.delete();
      throw e;
    }
    FileIntegrity.promote(part, destination);
  }

  private PcmWave() {}
}
