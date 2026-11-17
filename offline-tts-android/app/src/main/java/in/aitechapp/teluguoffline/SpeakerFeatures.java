package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SpeechBrain: 16k, periodic Hamming400, hop160, centered zero padding, power mel, 80dB clip, mean.
 */
public final class SpeakerFeatures {
  public static float[][] compute(float[] audio, File bank, AtomicBoolean cancel)
      throws IOException {
    if (audio.length < 16000 * 10 || audio.length > 16000 * 15)
      throw new IOException("Enrollment needs 10–15 seconds of audio.");
    byte[] bytes = Files.readAllBytes(bank.toPath());
    if (bytes.length != 201 * 80 * 4) throw new IOException("Invalid filterbank");
    FloatBuffer floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    // Despite its filename, the pinned upstream table is frequency-major [201,80], proven by
    // support.
    float[][] weights = new float[201][80];
    for (float[] row : weights) floats.get(row);
    double[][] cos = new double[201][400], sin = new double[201][400];
    for (int f = 0; f <= 200; f++)
      for (int j = 0; j < 400; j++) {
        double window = .54 - .46 * Math.cos(2 * Math.PI * j / 400);
        cos[f][j] = Math.cos(2 * Math.PI * f * j / 400) * window;
        sin[f][j] = -Math.sin(2 * Math.PI * f * j / 400) * window;
      }
    int frames = audio.length / 160 + 1;
    float[][] features = new float[frames][80];
    double max = -Double.MAX_VALUE;
    for (int t = 0; t < frames; t++) {
      OfflineDenoiser.check(cancel);
      double[] power = new double[201];
      for (int f = 0; f <= 200; f++) {
        double re = 0, im = 0;
        for (int j = 0; j < 400; j++) {
          int pos = t * 160 + j - 200;
          if (pos >= 0 && pos < audio.length) {
            re += audio[pos] * cos[f][j];
            im += audio[pos] * sin[f][j];
          }
        }
        power[f] = re * re + im * im;
      }
      for (int m = 0; m < 80; m++) {
        double sum = 0;
        for (int f = 0; f <= 200; f++) sum += power[f] * weights[f][m];
        float db = (float) (10 * Math.log10(Math.max(1e-10, sum)));
        features[t][m] = db;
        max = Math.max(max, db);
      }
    }
    for (int m = 0; m < 80; m++) {
      double mean = 0;
      for (int t = 0; t < frames; t++) {
        features[t][m] = (float) Math.max(features[t][m], max - 80);
        mean += features[t][m];
      }
      mean /= frames;
      for (int t = 0; t < frames; t++) features[t][m] -= mean;
    }
    return features;
  }

  private SpeakerFeatures() {}
}
