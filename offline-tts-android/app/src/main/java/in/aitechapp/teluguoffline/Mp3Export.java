package in.aitechapp.teluguoffline;

import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.*;
import java.io.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streaming local LAME MP3 encoder. No javax.sound, Android codec service or network required. */
public final class Mp3Export {
  public interface Progress {
    void update(long done, long total);
  }

  private static int le(RandomAccessFile f, int n) throws IOException {
    int v = 0;
    for (int i = 0; i < n; i++) v |= f.readUnsignedByte() << (i * 8);
    return v;
  }

  public static void encode(File input, File destination, AtomicBoolean cancel, Progress progress)
      throws IOException {
    File partial = new File(destination.getPath() + ".part");
    Lame lame = new Lame();
    GainAnalysis ga = new GainAnalysis();
    BitStream bs = new BitStream();
    Presets presets = new Presets();
    QuantizePVT qp = new QuantizePVT();
    Quantize quant = new Quantize();
    VBRTag vbr = new VBRTag();
    Version ver = new Version();
    ID3Tag id3 = new ID3Tag();
    Reservoir res = new Reservoir();
    Takehiro take = new Takehiro();
    MPGLib mpg = new MPGLib();
    Interface intf = new Interface();
    Common common = new Common();
    lame.setModules(ga, bs, presets, qp, quant, vbr, ver, id3, mpg);
    bs.setModules(ga, mpg, ver, vbr);
    id3.setModules(bs, ver);
    presets.setModules(lame);
    quant.setModules(bs, res, qp, take);
    qp.setModules(take, res, lame.enc.psy);
    res.setModules(bs);
    take.setModules(qp);
    vbr.setModules(lame, bs, ver);
    mpg.setModules(intf, common);
    intf.setModules(vbr, common);
    LameGlobalFlags flags = lame.lame_init();
    try (RandomAccessFile in = new RandomAccessFile(input, "r");
        FileOutputStream out = new FileOutputStream(partial)) {
      if (in.length() < 44 || in.readInt() != 0x52494646)
        throw new IOException("Select a completed PCM WAV to save as MP3.");
      in.seek(8);
      if (in.readInt() != 0x57415645
          || in.readInt() != 0x666d7420
          || le(in, 4) != 16
          || le(in, 2) != 1) throw new IOException("Unsupported WAV header.");
      int channels = le(in, 2), rate = le(in, 4);
      in.skipBytes(4);
      int align = le(in, 2), bits = le(in, 2);
      if ((channels != 1 && channels != 2)
          || bits != 16
          || align != channels * 2
          || in.readInt() != 0x64617461)
        throw new IOException("MP3 requires PCM16 mono or stereo WAV.");
      long bytes = Integer.toUnsignedLong(le(in, 4));
      if (bytes != in.length() - 44 || bytes % align != 0) throw new IOException("Incomplete WAV.");
      flags.num_channels = channels;
      flags.in_samplerate = rate;
      flags.out_samplerate = 44100;
      flags.mode = channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO;
      flags.brate = 320;
      flags.quality = 2;
      flags.VBR = VbrMode.vbr_off;
      // No placeholder Xing header: every byte written is a complete MPEG audio frame.
      flags.bWriteVbrTag = false;
      flags.write_id3tag_automatic = false;
      flags.findReplayGain = false;
      if (lame.lame_init_params(flags) < 0)
        throw new IOException("MP3 encoder could not initialize.");
      int[] left = new int[8192], right = new int[8192];
      byte[] pcm = new byte[8192 * align], encoded = new byte[20000];
      long done = 0;
      while (done < bytes) {
        if (cancel.get()) throw new CancellationException();
        int count = (int) Math.min(pcm.length, bytes - done);
        in.readFully(pcm, 0, count);
        int frames = count / align;
        for (int i = 0; i < frames; i++) {
          int at = i * align;
          left[i] = ((pcm[at] & 255) | (pcm[at + 1] << 8)) << 16;
          right[i] = channels == 1 ? left[i] : ((pcm[at + 2] & 255) | (pcm[at + 3] << 8)) << 16;
        }
        int n = lame.lame_encode_buffer_int(flags, left, right, frames, encoded, 0, encoded.length);
        if (n < 0) throw new IOException("MP3 encoding failed (" + n + "). WAV is kept.");
        out.write(encoded, 0, n);
        done += count;
        progress.update(done, bytes);
      }
      if (cancel.get()) throw new CancellationException();
      int n = lame.lame_encode_flush(flags, encoded, 0, encoded.length);
      if (n < 0) throw new IOException("MP3 flush failed.");
      out.write(encoded, 0, n);
      out.getFD().sync();
    } catch (IOException | RuntimeException e) {
      partial.delete();
      throw e;
    } finally {
      lame.lame_close(flags);
    }
    FileIntegrity.promote(partial, destination);
  }

  private Mp3Export() {}
}
