package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestorationTest {
  static void require(boolean ok, String reason) {
    if (!ok) throw new AssertionError(reason);
  }

  static double rms(float[] x, int a, int b) {
    double s = 0;
    for (int i = a; i < b; i++) s += x[i] * x[i];
    return Math.sqrt(s / Math.max(1, b - a));
  }

  static float[] tone(int hz, int rate) {
    float[] x = new float[rate * 2];
    for (int i = 0; i < x.length; i++) x[i] = (float) (.2 * Math.sin(2 * Math.PI * hz * i / rate));
    return x;
  }

  public static void main(String[] args) throws Exception {
    AtomicBoolean cancel = new AtomicBoolean();
    for (int hz : new int[] {30, 150}) {
      float[] x = tone(hz, 24000);
      double before = rms(x, 24000, x.length);
      OfflineDenoiser.highPass(x, 24000);
      double db = 20 * Math.log10(rms(x, 24000, x.length) / before);
      require(hz == 30 ? db < -34 : db > -.2, "HP85 response " + hz + " " + db);
    }
    float[] tone = tone(1000, 24000);
    float[] round =
        OfflineDenoiser.resample(
            OfflineDenoiser.resample(tone, 24000, 48000, cancel), 48000, 24000, cancel);
    require(round.length == tone.length, "resampler duration");
    double error = 0;
    for (int i = 100; i < tone.length - 100; i++) error += Math.pow(tone[i] - round[i], 2);
    require(Math.sqrt(error / (tone.length - 200)) < .0001, "sinc passband");
    float[] alias = OfflineDenoiser.resample(tone(18000, 48000), 48000, 24000, cancel);
    require(rms(alias, 100, alias.length - 100) < .0001, "downsampling anti-aliasing");
    float[] silence = new float[24000];
    float[] cleaned = OfflineDenoiser.process(silence, 24000, cancel, p -> {});
    require(rms(cleaned, 0, cleaned.length) < 1e-8, "silent denoiser");
    for (int rate : new int[] {22050, 24000}) {
      Random rng = new Random(721);
      float[] lowNoise = new float[rate / 2];
      for (int i = 0; i < lowNoise.length; i++) lowNoise[i] = (float)(rng.nextGaussian() * .0015);
      float[] filtered = RenderCleanup.apply(lowNoise, rate, true, cancel);
      require(filtered.length == lowNoise.length, "short chunk must keep every frame");
      double ratio = rms(filtered, rate/5, filtered.length)/rms(lowNoise, rate/5, filtered.length);
      require(ratio < .8 && ratio > .20, "bounded noise attenuation " + ratio);
    }
    double[] re = new double[512], im = new double[512];
    re[123] = 1;
    LateReverb.fft(re, im, false);
    LateReverb.fft(re, im, true);
    require(Math.abs(re[123] - 1) < 1e-10, "FFT inverse");
    float[] dry = LateReverb.process(silence, cancel, p -> {});
    require(dry.length == silence.length && rms(dry, 0, dry.length) == 0, "silent WPE");
    cancel.set(true);
    boolean stopped = false;
    try {
      OfflineDenoiser.process(tone, 24000, cancel, p -> {});
    } catch (java.util.concurrent.CancellationException expected) {
      stopped = true;
    }
    require(stopped, "cancel native processing");
    cancel.set(false);
    Random random = new Random(57);
    float[] noise = new float[24000 * 5];
    for (int i = 0; i < noise.length; i++)
      noise[i] =
          (float) (random.nextGaussian() * .04 + .05 * Math.sin(2 * Math.PI * 60 * i / 24000));
    cleaned = OfflineDenoiser.process(noise, 24000, cancel, p -> {});
    double reduction =
        20 * Math.log10(rms(noise, 48000, noise.length) / rms(cleaned, 48000, cleaned.length));
    require(reduction > 6, "noise-only attenuation " + reduction);
    System.out.printf(
        Locale.US,
        "PASS restoration: HP85, sinc/alias rejection, native silence, FFT/WPE silence,"
            + " cancellation; synthetic noise-only attenuation %.2f dB%n",
        reduction);
    if (args.length == 2) {
      File source = new File(args[0]);
      int rate = PcmWave.sampleRate(source);
      float[] speech = new float[(int) PcmWave.validate(source) / 2];
      try (RandomAccessFile f = new RandomAccessFile(source, "r")) {
        f.seek(44);
        for (int i = 0; i < speech.length; i++)
          speech[i] = Short.reverseBytes(f.readShort()) / 32768f;
      }
      speech = OfflineDenoiser.resample(speech, rate, 24000, cancel);
      float[] noisy = new float[speech.length + 48000];
      for (int i = 0; i < noisy.length; i++) {
        double value = i >= 24000 && i < speech.length + 24000 ? speech[i - 24000] : 0;
        noisy[i] =
            (float)
                (value * .6
                    + random.nextGaussian() * .018
                    + .04 * Math.sin(2 * Math.PI * 60 * i / 24000));
      }
      long start = System.nanoTime();
      float[] enhanced = OfflineDenoiser.process(noisy, 24000, cancel, p -> {});
      double seconds = (System.nanoTime() - start) / 1e9;
      File dir = new File(args[1]);
      dir.mkdirs();
      PcmWave.write(new File(dir, "synthetic-noise-input.wav"), noisy, 24000);
      PcmWave.write(new File(dir, "rnnoise-enhanced.wav"), enhanced, 24000);
      float[] room = new float[noisy.length];
      for (int i = 0; i < room.length; i++) {
        room[i] = i >= 24000 && i < speech.length + 24000 ? speech[i - 24000] * .6f : 0;
        for (int delay = 1200; delay <= 12000; delay += 1200) {
          int pos = i - 24000 - delay;
          if (pos >= 0 && pos < speech.length)
            room[i] += speech[pos] * (float) (.18 * Math.exp(-delay / 6000.0));
        }
      }
      float[] reduced = LateReverb.process(room, cancel, p -> {});
      PcmWave.write(new File(dir, "synthetic-room-input.wav"), room, 24000);
      PcmWave.write(new File(dir, "wpe-room-reduced.wav"), reduced, 24000);
      for (float v : reduced) require(Float.isFinite(v), "WPE finite speech");
      require(reduced.length == room.length, "WPE duration");
      double noiseDb =
          20 * Math.log10(rms(noisy, 4800, 19200) / Math.max(1e-12, rms(enhanced, 4800, 19200)));
      int a = 24000 + speech.length, b = Math.min(room.length, a + 12000);
      double tailDb =
          20 * Math.log10(Math.max(1e-12, rms(room, a, b)) / Math.max(1e-12, rms(reduced, a, b)));
      String report =
          String.format(
              Locale.US,
              "{\"fixture\":\"synthetic noise and delayed echoes added to public TTS; not real"
                  + " enrollment\",\"rnnoise_noise_only_reduction_db\":%.3f,\"wpe_post_speech_tail_reduction_db\":%.3f,\"audio_seconds\":%.3f,\"host_rnnoise_seconds\":%.3f,\"device_tested\":false}",
              noiseDb,
              tailDb,
              noisy.length / 24000.0,
              seconds);
      java.nio.file.Files.writeString(new File(dir, "metrics.json").toPath(), report + "\n");
      System.out.println(report);
    }
  }
}
