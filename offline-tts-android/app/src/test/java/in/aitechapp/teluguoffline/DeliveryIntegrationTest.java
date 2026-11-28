package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DeliveryIntegrationTest {
  public static class Downloads extends ContentProvider {
    File file;
    boolean pending, deleted, published;
    ContentValues inserted;

    public boolean onCreate() {
      return true;
    }

    public Uri insert(Uri uri, ContentValues values) {
      inserted = new ContentValues(values);
      pending = values.getAsInteger(MediaStore.Downloads.IS_PENDING) == 1;
      return Uri.parse("content://media/external/downloads/91");
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
      published = v.getAsInteger(MediaStore.Downloads.IS_PENDING) == 0;
      return 1;
    }

    public int delete(Uri u, String s, String[] a) {
      deleted = true;
      return 1;
    }

    public Cursor query(Uri u, String[] p, String s, String[] a, String sort) {
      return null;
    }

    public String getType(Uri u) {
      return "audio/wav";
    }

    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
      return ParcelFileDescriptor.open(
          file,
          ParcelFileDescriptor.MODE_READ_WRITE
              | ParcelFileDescriptor.MODE_CREATE
              | ParcelFileDescriptor.MODE_TRUNCATE);
    }
  }

  @Test
  public void literalRenderPlanKeepsWordsAndPausesOnlyAtEnabledCommas() throws Exception {
    SpeechSettings settings = new SpeechSettings();
    String script = "ఇక్కడ, 100% చూడండి. 16:9 లేదా √25 = 5.";
    var original = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO);
    var rendered = settings.renderSegments(original);
    assertEquals(3, rendered.size());
    assertEquals(script, String.join(" ", rendered.stream().map(s -> s.text).collect(java.util.stream.Collectors.toList())));
    for (int i=0; i<rendered.size(); i++) {
      assertEquals(i == 0 ? 110 : 0,rendered.get(i).pauseAfterMs);
      String spoken=settings.spoken(rendered.get(i).text);
      assertFalse(spoken.contains(",") || spoken.contains("."));
      assertTrue(TinyVoice.tokens(spoken).length>1);
    }
    JSONObject saved=settings.json();
    assertTrue(SpeechSettings.fromJson(saved).englishNotation);
    saved.remove("englishNotation");
    SpeechSettings old=SpeechSettings.fromJson(saved);
    assertFalse(old.englishNotation);
    assertTrue(old.spoken("16:9").contains("భాగాలకు"));
  }

  @Test
  public void cleanupIsOptInAndSurvivesTheQueuedSpeechSnapshot() throws Exception {
    SpeechSettings settings = new SpeechSettings();
    assertFalse(settings.synthesisCleanup);
    // Old settings without this key must not silently enable sound processing on upgrade.
    assertFalse(SpeechSettings.fromJson(new JSONObject().put("entries", new JSONObject())).synthesisCleanup);
    settings.synthesisCleanup = true;
    SpeechSettings copy = SpeechSettings.fromJson(settings.json());
    settings.synthesisCleanup = false;
    assertTrue(copy.synthesisCleanup);
    assertFalse(SpeechSettings.fromJson(settings.json()).synthesisCleanup);
  }

  @Test
  public void unseenMeasurementsReachTinyTokenizerWithoutUnhandledSymbols() {
    SpeechSettings settings = new SpeechSettings();
    for (String source : new String[]{"-5°C", "37.5℃", "12 cm²", "2 m³", "220 Ω", "5 μs", "V25 = 5"}) {
      String spoken = settings.spoken(source);
      assertFalse(spoken.matches(".*[A-Za-z0-9°²³Ωμ℃].*"));
      assertTrue(TinyVoice.tokens(spoken).length > 1);
    }
  }

  @Test
  public void disabledSoundProcessingPreservesNativePcm() throws Exception {
    File root = java.nio.file.Files.createTempDirectory("unaltered-tone").toFile();
    for (int hz : new int[]{22050, 24000}) {
      float[] samples = new float[hz];
      for (int i=0; i<hz; i++) samples[i] = (float)(.1*Math.sin(2*Math.PI*190*i/hz)
          + .05*Math.sin(2*Math.PI*3700*i/hz));
      File raw=new File(root,"raw.wav"), out=new File(root,"out.wav");
      PcmWave.write(raw,samples,hz);
      VoiceDeliveryDsp.process(raw,out,false,false,null,new AtomicBoolean(),false,false,(RenderCleanup.Cleaner)null);
      assertArrayEquals(java.nio.file.Files.readAllBytes(raw.toPath()),java.nio.file.Files.readAllBytes(out.toPath()));
    }
  }

  @Test
  public void closingMarkupDoesNotCreateAnEmptyNeuralRequest() {
    SpeechSettings settings = new SpeechSettings();
    var original =
        NarrationDirector.planExact("(నమస్కారం!) {శ్రోతలకు.}", NarrationDirector.Style.AUTO);
    var audible = settings.audible(original);
    for (var segment : audible)
      assertTrue(TinyVoice.tokens(settings.spoken(segment.text)).length > 1);
    assertEquals(2, audible.size());
    assertThrows(
        IllegalArgumentException.class,
        () -> settings.audible(NarrationDirector.planExact("({})", NarrationDirector.Style.AUTO)));
  }

  @Test
  public void downloadPublishesCompleteBytesAndCancellationDeletesOnlyNewEntry() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    File original = File.createTempFile("original", ".wav", c.getCacheDir());
    Files.write(original.toPath(), new byte[150000]);
    String hash = FileIntegrity.sha256(original);
    Downloads provider = new Downloads();
    provider.file = File.createTempFile("download", ".wav", c.getCacheDir());
    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
    info.authority = "media";
    info.exported = true;
    provider.attachInfo(c, info);
    ShadowContentResolver.registerProviderInternal("media", provider);
    AudioDownloads.save(c, original, "Telugu-padmavathi.wav", new AtomicBoolean(), (a, b) -> {});
    assertTrue(provider.pending);
    assertTrue(provider.published);
    assertFalse(provider.deleted);
    assertEquals(hash, FileIntegrity.sha256(provider.file));
    assertEquals(
        "Download/Telugu Voice Studio",
        provider.inserted.getAsString(MediaStore.Downloads.RELATIVE_PATH));
    provider.published = false;
    assertThrows(
        java.util.concurrent.CancellationException.class,
        () ->
            AudioDownloads.save(c, original, "cancel.wav", new AtomicBoolean(true), (a, b) -> {}));
    assertTrue(provider.deleted);
    assertFalse(provider.published);
    assertEquals(hash, FileIntegrity.sha256(original));
  }

  @Test
  public void referenceWaveTrimKeepsOriginalAndDoesNotGrowName() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    VoiceProfiles profiles = new VoiceProfiles(c);
    String id = "pcm-fixture";
    float[] x = new float[24000 * 2];
    for (int i = 0; i < x.length; i++) x[i] = (float) (.1 * Math.sin(i * .1));
    PcmWave.write(profiles.wave(id), x, 24000);
    profiles.save(id, "My voice.trimmed.trimmed", "Custom", "test", false);
    String sha = FileIntegrity.sha256(profiles.audio(id));
    String copy = profiles.trim(id, 200, 1500);
    assertEquals(1300, VoiceProfiles.duration(profiles.audio(copy)));
    assertEquals("My voice", profiles.get(copy).getString("name"));
    assertEquals(sha, FileIntegrity.sha256(profiles.audio(id)));
    assertTrue(profiles.get(copy).getString("status").contains("reference"));
  }

  @Test
  public void controlsPersistAndOldAudioIdentityNeverUsesNewSelection() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SpeechSettings s = new SpeechSettings();
    s.durationGuard = false;
    s.youtuber = false;
    s.questionLift = false;
    s.femaleSoftening = true;
    s.dictionary.put("AiTechApps", "ఏఐ టెక్ యాప్స్");
    s.save(c);
    SpeechSettings copy = SpeechSettings.load(c);
    assertFalse(copy.durationGuard);
    assertFalse(copy.youtuber);
    assertFalse(copy.questionLift);
    assertTrue(copy.femaleSoftening);
    assertEquals("ఏఐ టెక్ యాప్స్", copy.spoken("AiTechApps"));
    File directory = new File(c.getCacheDir(), "identity");
    directory.mkdirs();
    File audio = new File(directory, "narration.wav");
    assertEquals("Unknown voice", AudioIdentity.read(audio).name);
    FileIntegrity.write(
        new File(directory, "render-identity.json"),
        new JSONObject()
            .put("voice", "padmavathi")
            .put("engine", "Piper / sherpa-onnx")
            .toString());
    assertEquals("padmavathi", AudioIdentity.read(audio).id);
    assertTrue(AudioIdentity.read(audio).exportLabel().contains("padmavathi"));
  }
}
