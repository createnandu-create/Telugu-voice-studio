package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OfflineDataTest {
  @Test
  public void verifiedInstallReturnsWithoutNetworkOrAssetPreparation() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    VoiceModelStore store = new VoiceModelStore(c);
    File file = new File(store.root, "fixture.onnx");
    Files.write(file.toPath(), new byte[] {1, 2, 3, 4});
    String hash = FileIntegrity.sha256(file);
    ModelCatalog.Voice voice = new ModelCatalog.Voice("fixture", "Fixture", "Custom", hash, hash);
    assertTrue(store.verify(voice));
    long modified = file.lastModified();
    // This ID has no remote model and no bundled token table. Any network/install work would fail.
    store.install(voice, new AtomicBoolean(), message -> {});
    assertEquals(modified, file.lastModified());
    assertTrue(store.ready(voice));
  }

  @Test
  public void profileRenameSelectAndReopenDoNotChangeAudio() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    VoiceProfiles library = new VoiceProfiles(c);
    String id = "persistent-test";
    File audio = library.audio(id);
    Files.write(audio.toPath(), new byte[] {4, 3, 2, 1});
    String before = FileIntegrity.sha256(audio);
    JSONObject p =
        new JSONObject()
            .put("id", id)
            .put("name", "Original")
            .put("label", "Custom")
            .put("description", "")
            .put("durationMs", 20000)
            .put("created", 1)
            .put("status", "Recorded reference only");
    FileIntegrity.write(new File(library.root(), id + ".json"), p.toString());
    library.rename(id, "My narrator", "Male", "Only a reference");
    library.select(id);
    FileIntegrity.write(
        new File(library.root(), "pending-recording.json"),
        new JSONObject().put("id", "unfinished").toString());
    VoiceProfiles reopened = new VoiceProfiles(c);
    assertEquals(1, reopened.list().size());
    assertEquals(id, reopened.selected());
    assertEquals("My narrator", reopened.get(id).getString("name"));
    assertEquals(before, FileIntegrity.sha256(audio));
  }
}
