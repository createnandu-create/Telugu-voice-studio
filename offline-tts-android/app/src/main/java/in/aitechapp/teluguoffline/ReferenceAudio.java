package in.aitechapp.teluguoffline;

import android.media.*;
import java.io.*;
import java.nio.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Decode existing private PCM or legacy AAC references without modifying them. */
public final class ReferenceAudio {
  public static float[] read(File input, AtomicBoolean cancel) throws Exception {
    if (input.getName().endsWith(".wav")) {
      long bytes = PcmWave.validate(input);
      int rate = PcmWave.sampleRate(input);
      if (bytes > rate * 2L * 61) throw new IOException("Use a recording under one minute.");
      float[] x = new float[(int) bytes / 2];
      try (RandomAccessFile f = new RandomAccessFile(input, "r")) {
        f.seek(44);
        for (int i = 0; i < x.length; i++) x[i] = Short.reverseBytes(f.readShort()) / 32768f;
      }
      return OfflineDenoiser.resample(x, rate, 24000, cancel);
    }
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    try {
      extractor.setDataSource(input.getPath());
      int track = -1;
      for (int i = 0; i < extractor.getTrackCount(); i++)
        if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          track = i;
          break;
        }
      if (track < 0) throw new IOException("No audio in this reference.");
      extractor.selectTrack(track);
      MediaFormat format = extractor.getTrackFormat(track);
      int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
          channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
          encoding = AudioFormat.ENCODING_PCM_16BIT;
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
      float[] samples = new float[rate * 61];
      int count = 0;
      boolean sent = false, done = false;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long lastProgress = System.nanoTime();
      while (!done) {
        OfflineDenoiser.check(cancel);
        if (System.nanoTime() - lastProgress > 15_000_000_000L)
          throw new IOException("Audio decoder stalled. Original reference kept.");
        if (!sent) {
          int index = codec.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer b = codec.getInputBuffer(index);
            int n = extractor.readSampleData(b, 0);
            if (n < 0) {
              codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              sent = true;
            } else {
              codec.queueInputBuffer(index, 0, n, extractor.getSampleTime(), 0);
              extractor.advance();
            }
            lastProgress = System.nanoTime();
          }
        }
        int index = codec.dequeueOutputBuffer(info, 10000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat f = codec.getOutputFormat();
          int actual = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          if (count != 0 && actual != rate) throw new IOException("Recording changed sample rate.");
          rate = actual;
          channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          encoding =
              f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                  ? f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                  : AudioFormat.ENCODING_PCM_16BIT;
          if (rate < 8000 || rate > 96000 || channels < 1 || channels > 8)
            throw new IOException("Unsupported recording format.");
          if (count == 0) samples = new float[rate * 61];
          lastProgress = System.nanoTime();
        } else if (index >= 0) {
          try {
            ByteBuffer b = codec.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);
            b.position(info.offset);
            b.limit(info.offset + info.size);
            int width = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
            if (encoding != AudioFormat.ENCODING_PCM_FLOAT
                && encoding != AudioFormat.ENCODING_PCM_16BIT)
              throw new IOException("Unsupported decoded PCM.");
            while (b.remaining() >= channels * width) {
              double sum = 0;
              for (int c = 0; c < channels; c++)
                sum += width == 4 ? b.getFloat() : b.getShort() / 32768f;
              if (count >= samples.length)
                throw new IOException("Use a recording under one minute.");
              samples[count++] = (float) (sum / channels);
            }
            done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            lastProgress = System.nanoTime();
          } finally {
            codec.releaseOutputBuffer(index, false);
          }
        }
      }
      return OfflineDenoiser.resample(Arrays.copyOf(samples, count), rate, 24000, cancel);
    } finally {
      if (codec != null) codec.release();
      extractor.release();
    }
  }

  private ReferenceAudio() {}
}
