package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.Arrays;

/** Offline 80 Hz high-pass + conservative endpoint RMS trim. Never overwrites the capture. */
public final class RecordingCleanup {
  public static final class Result {
    public final float[] samples;
    public final int removedStart, removedEnd;

    Result(float[] samples, int start, int end) {
      this.samples = samples;
      removedStart = start;
      removedEnd = end;
    }
  }

  public static Result clean(float[] original, int rate) {
    float[] out = original.clone();
    // RBJ second-order Butterworth high-pass, Q=1/sqrt(2).
    double w = 2 * Math.PI * 80 / rate,
        c = Math.cos(w),
        alpha = Math.sin(w) / Math.sqrt(2),
        a0 = 1 + alpha;
    double b0 = (1 + c) / 2 / a0,
        b1 = -(1 + c) / a0,
        b2 = b0,
        a1 = -2 * c / a0,
        a2 = (1 - alpha) / a0;
    double z1 = 0, z2 = 0;
    for (int i = 0; i < out.length; i++) {
      double y = b0 * out[i] + z1;
      z1 = b1 * out[i] - a1 * y + z2;
      z2 = b2 * out[i] - a2 * y;
      out[i] = (float) y;
    }
    int hop = Math.max(1, rate / 100), first = -1, last = -1;
    for (int start = 0; start < out.length; start += hop) {
      int end = Math.min(out.length, start + hop);
      double power = 0;
      for (int i = start; i < end; i++) power += out[i] * out[i];
      if (Math.sqrt(power / (end - start)) >= .01) {
        if (first < 0) first = start;
        last = end;
      }
    }
    // Preserve all-silent captures for recovery/listening, never invent speech.
    if (first < 0) return new Result(out, 0, 0);
    first = Math.max(0, first - rate / 20);
    last = Math.min(out.length, last + rate / 20);
    return new Result(Arrays.copyOfRange(out, first, last), first, out.length - last);
  }

  public static Result file(File raw, File output) throws IOException {
    long bytes = PcmWave.validate(raw);
    if (bytes > 24000 * 2 * 61L) throw new IOException("Reference exceeds one minute");
    float[] x = new float[(int) bytes / 2];
    try (RandomAccessFile f = new RandomAccessFile(raw, "r")) {
      f.seek(44);
      for (int i = 0; i < x.length; i++) x[i] = Short.reverseBytes(f.readShort()) / 32768f;
    }
    Result result = clean(x, PcmWave.sampleRate(raw));
    PcmWave.write(output, result.samples, PcmWave.sampleRate(raw));
    return result;
  }

  private RecordingCleanup() {}
}
