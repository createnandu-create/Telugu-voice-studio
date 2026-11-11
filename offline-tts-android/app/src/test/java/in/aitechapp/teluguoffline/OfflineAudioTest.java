package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class OfflineAudioTest {
  @Test
  public void namaskaramPreservesNaAndNeverSilentlyUsesUnknownToken() {
    long[] namaskaram = {0, 45, 0, 50, 0, 59, 0, 74, 0, 26, 0, 61, 0, 52, 0, 11, 0};
    assertArrayEquals(namaskaram, TinyVoice.tokens("నమస్కారం"));
    assertArrayEquals(namaskaram, TinyVoice.tokens("\uFEFF\u200Cనమస్కారం"));
    assertEquals(13, TinyVoice.tokens("అమస్కారం")[1]);
    assertThrows(IllegalArgumentException.class, () -> TinyVoice.tokens("నమస్కారం YouTube"));
    assertThrows(IllegalArgumentException.class, () -> TinyVoice.tokens("2026"));
  }

  @Test
  public void native24kChunksKeepSamplesAndRejectMixedRates() throws Exception {
    File dir = Files.createTempDirectory("tiny-wave").toFile();
    File a = new File(dir, "a.wav"), b = new File(dir, "b.wav"), out = new File(dir, "result.wav");
    float[] samples = new float[24000];
    for (int i = 0; i < samples.length; i++) samples[i] = (float) (.4 * Math.sin(i * .15));
    PcmWave.write(a, samples, 24000);
    var plan = NarrationDirector.planExact("నమస్కారం.", NarrationDirector.Style.AUTO);
    PcmWave.mergeRaw(List.of(a), plan, out, new AtomicBoolean());
    assertArrayEquals(Files.readAllBytes(a.toPath()), Files.readAllBytes(out.toPath()));
    assertEquals(24000, PcmWave.sampleRate(out));
    assertThrows(
        IOException.class,
        () ->
            StudioMastering.process(
                out, new File(dir, "master.wav"), new AtomicBoolean(), m -> {}));
    PcmWave.write(b, samples, 22050);
    var two = NarrationDirector.planExact("నమస్కారం. నమస్కారం.", NarrationDirector.Style.AUTO);
    assertThrows(
        IOException.class,
        () -> PcmWave.mergeRaw(List.of(a, b), two, new File(dir, "bad.wav"), new AtomicBoolean()));
  }

  @Test
  public void mp3IsEncodedLocallyAndCancellationKeepsSourceAndExistingOutput() throws Exception {
    File dir = Files.createTempDirectory("mp3-export").toFile();
    File wav = new File(dir, "tone.wav"), mp3 = new File(dir, "tone.mp3");
    float[] x = new float[24000];
    for (int i = 0; i < x.length; i++)
      x[i] = (float) (.4 * Math.sin(2 * Math.PI * 440 * i / 24000));
    PcmWave.write(wav, x, 24000);
    String before = FileIntegrity.sha256(wav);
    long[] done = {0};
    Mp3Export.encode(wav, mp3, new AtomicBoolean(), (a, b) -> done[0] = a);
    assertEquals(48000, done[0]);
    assertTrue(mp3.length() > 30000);
    byte[] header = Files.readAllBytes(mp3.toPath());
    assertEquals(255, header[0] & 255);
    assertEquals(224, header[1] & 224);
    String encoded = FileIntegrity.sha256(mp3);
    assertThrows(
        java.util.concurrent.CancellationException.class,
        () -> Mp3Export.encode(wav, mp3, new AtomicBoolean(true), (a, b) -> {}));
    assertEquals(before, FileIntegrity.sha256(wav));
    assertEquals(encoded, FileIntegrity.sha256(mp3));
    assertFalse(new File(mp3 + ".part").exists());
  }
}
