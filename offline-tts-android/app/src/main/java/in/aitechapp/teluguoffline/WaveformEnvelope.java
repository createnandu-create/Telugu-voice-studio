package in.aitechapp.teluguoffline;

import java.io.*;

/** Real PCM16 peaks for the mini player; bounded memory, never invented decorative bars. */
public final class WaveformEnvelope {
  public static float[] read(File file) throws IOException {
    float[] peaks = new float[64];
    try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
      if (f.readInt() != 0x52494646) throw new IOException("Not PCM WAV");
      f.seek(12);
      int bits = 0;
      while (f.getFilePointer() + 8 <= f.length()) {
        int tag = f.readInt();
        long size = Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
        long start = f.getFilePointer();
        if (size > f.length() - start) throw new IOException("Incomplete WAV");
        if (tag == 0x666d7420 && size >= 16) {
          if (Short.reverseBytes(f.readShort()) != 1) throw new IOException("Not PCM");
          f.seek(start + 14);
          bits = Short.reverseBytes(f.readShort());
        } else if (tag == 0x64617461 && bits == 16) {
          long samples = size / 2;
          byte[] block = new byte[16384];
          long at = 0;
          while (at < samples) {
            int n = (int) Math.min(block.length / 2, samples - at);
            f.readFully(block, 0, n * 2);
            for (int i = 0; i < n; i++) {
              float x = Math.abs((short) ((block[i * 2] & 255) | (block[i * 2 + 1] << 8)) / 32768f);
              int bin = (int) ((at + i) * 64 / Math.max(1, samples));
              peaks[bin] = Math.max(peaks[bin], x);
            }
            at += n;
          }
          return peaks;
        }
        f.seek(start + size + (size & 1));
      }
    }
    throw new IOException("No PCM16 data");
  }
}
