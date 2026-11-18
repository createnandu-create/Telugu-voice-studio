package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class V080Test {
  @Test
  public void mixedMathEnglishNeverReachesTinyAsLatinAndOriginalIsRetained() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    DynamicEnglishPhonetizer.load(c.getAssets().open("english-cmudict.bin"));
    String[][] cases = {
      {"√25 = 5", "రూట్ ఇరవై ఐదు సమానం ఐదు"},
      {"\\sqrt{25}", "రూట్ ఇరవై ఐదు"},
      {"16:9", "పదహారు ఈజ్ టు తొమ్మిది"},
      {"100%", "వంద శాతం"},
      {"4K", "ఫోర్ కే"},
      {"192-D", "నూట తొంభై రెండు డీ"},
      {"AiTechApp", "ఏఐ టెక్ యాప్"},
      {"PixelLab", "పిక్సెల్ ల్యాబ్"},
      {"Vocal DNA", "వోకల్ డీ ఎన్ ఏ"},
      {"VN", "వి ఎన్"},
      {"mind-blowing tech update", "మైండ్ బ్లోయింగ్ టెక్ అప్‌డేట్"},
      {"శ్రావ్యమైన శ్రోతలకు కర్కశ", "శ్రావ్యమైన శ్రోతలకు కర్కశ"}
    };
    SpeechSettings settings = new SpeechSettings();
    for (String[] pair : cases) {
      String spoken = settings.spoken(pair[0]).trim().replaceAll("\\s+", " ");
      assertEquals(pair[0], pair[1], spoken);
      assertFalse(spoken.matches(".*[A-Za-z0-9].*"));
      assertTrue(TinyVoice.tokens(spoken).length > 1);
    }
    String script = "AiTechApp (100%) \\sqrt{25} = 5. శ్రావ్యమైన.";
    PlanStorage p =
        new PlanStorage(
            script, 3, NarrationDirector.planExact(script, NarrationDirector.Style.AUTO));
    assertEquals(script, PlanStorage.parse(p.json()).script);
    assertFalse(settings.spoken("Qzxw hello computer").matches(".*[A-Za-z].*"));
    assertTrue(
        settings.youtuber
            && !settings.durationGuard
            && settings.questionLift
            && settings.commaBuffer
            && settings.carefulDiction);
    assertFalse(settings.femaleSoftening);
  }

  @Test
  public void nativeRateChangesCreateIndependentMastersAndRejectWrongCachedRate() throws Exception {
    File root = Files.createTempDirectory("rates-v080").toFile();
    String first = null;
    for (int rate : new int[] {22050, 24000, 22050}) {
      float[] pcm = new float[rate];
      for (int i = 0; i < pcm.length; i++)
        pcm[i] = (float) (.2 * Math.sin(2 * Math.PI * 220 * i / rate));
      File raw = new File(root, "raw.wav"), out = new File(root, "master.wav");
      PcmWave.write(raw, pcm, rate);
      String sha = FileIntegrity.sha256(raw);
      StudioMastering.Report report =
          StudioMastering.process(raw, out, new AtomicBoolean(), m -> {}, false);
      byte[] b = Files.readAllBytes(out.toPath());
      ByteBuffer h = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(44100, h.getInt(24));
      assertEquals(2, h.getShort(22));
      assertEquals(44 + (44100 + 6615 + 4410) * 4, b.length);
      for (int i = 44; i < 44 + 6615 * 4; i++) assertEquals(0, b[i]);
      for (int i = b.length - 4410 * 4; i < b.length; i++) assertEquals(0, b[i]);
      assertEquals(sha, FileIntegrity.sha256(raw));
      assertTrue(report.truePeakDbtp <= -1);
      assertEquals(-14, report.outputLufs, .04);
      assertEquals(rate, report.sourceRate);
      if (rate == 22050) {
        if (first == null) first = FileIntegrity.sha256(out);
        else assertEquals(first, FileIntegrity.sha256(out));
      }
    }
    RenderJournal j =
        new RenderJournal(
            root,
            ModelCatalog.VOICES[0],
            NarrationDirector.planExact("నమస్కారం", NarrationDirector.Style.AUTO));
    PcmWave.write(j.chunk(0), new float[24000], 24000);
    assertThrows(IOException.class, () -> j.complete(0));
    assertFalse(j.reusable(0));
  }

  @Test
  public void namesAreStableAndReferenceSelectionDoesNotCreateAClone() throws Exception {
    TimeZone old = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      long t = java.time.Instant.parse("2026-11-17T10:38:00Z").toEpochMilli();
      assertEquals("Padmavathi_17Nov2026_1038AM", AudioNames.stem("Padmavathi", "", t));
      assertEquals("Eswara_Cloned_17Nov2026_1038AM", AudioNames.stem("Eswara", "Cloned", t));
      Context c = RuntimeEnvironment.getApplication();
      File folder = new File(c.getCacheDir(), "named-project");
      folder.mkdirs();
      FileIntegrity.write(new File(folder, "created-at.txt"), Long.toString(t));
      FileIntegrity.write(
          new File(folder, "render-identity.json"),
          new JSONObject().put("voice_name", "Padmavathi").toString());
      assertEquals(
          AudioNames.project(folder, "Unknown"),
          AudioNames.forAudio(new File(folder, "narration.wav")));
      FileIntegrity.write(new File(folder, "project-name.txt"), "My edited title");
      assertEquals("My edited title", AudioNames.project(folder, "Other"));
      VoiceProfiles profiles = new VoiceProfiles(c);
      JSONObject p = new JSONObject().put("id", "fixture");
      assertThrows(IOException.class, () -> CloneProfiles.directory(profiles, p));
      assertThrows(IOException.class, () -> OpenVoiceEngine.validateEmbedding(new float[192]));
      assertThrows(IOException.class, () -> OpenVoiceEngine.validateEmbedding(new float[256]));
      float[] tone = new float[256];
      tone[0] = 1;
      OpenVoiceEngine.validateEmbedding(tone);
    } finally {
      TimeZone.setDefault(old);
    }
  }
}
