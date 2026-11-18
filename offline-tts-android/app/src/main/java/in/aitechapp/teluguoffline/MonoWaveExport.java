package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Losslessly compact the studio master's identical stereo channels. Preserve other formats. */
public final class MonoWaveExport {
  public static File prepare(File source, File cache, AtomicBoolean cancel) throws IOException {
    if (!source.getName().equals("narration-studio.wav")) return source;
    File dest = new File(cache, "studio-mono.wav"), part = new File(cache, "studio-mono.wav.part");
    try (RandomAccessFile in = new RandomAccessFile(source, "r");
        RandomAccessFile out = new RandomAccessFile(part, "rw")) {
      byte[] header = new byte[44];
      in.readFully(header);
      if (header[20] != 1 || header[22] != 2 || header[34] != 16) { part.delete(); return source; }
      long bytes = in.length() - 44;
      if (bytes % 4 != 0) throw new IOException("Incomplete stereo master");
      int rate = (header[24]&255) | (header[25]&255)<<8 | (header[26]&255)<<16 | header[27]<<24;
      out.setLength(0);
      PcmWave.header(out, bytes/2, rate);
      out.seek(44);
      byte[] pcm = new byte[32768];
      while (bytes > 0) {
        if (cancel.get()) throw new CancellationException();
        int n = (int)Math.min(bytes,pcm.length);
        in.readFully(pcm,0,n);
        for (int i=0; i<n; i+=4) {
          if (pcm[i]!=pcm[i+2] || pcm[i+1]!=pcm[i+3]) { part.delete(); return source; }
          pcm[i/2]=pcm[i]; pcm[i/2+1]=pcm[i+1];
        }
        out.write(pcm,0,n/2);
        bytes-=n;
      }
      out.getFD().sync();
    } catch (IOException | RuntimeException e) {
      part.delete();
      throw e;
    }
    FileIntegrity.promote(part,dest);
    return dest;
  }
  private MonoWaveExport() {}
}
