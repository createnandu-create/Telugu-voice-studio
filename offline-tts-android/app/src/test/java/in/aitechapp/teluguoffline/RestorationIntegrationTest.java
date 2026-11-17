package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RestorationIntegrationTest {
  @Test
  public void deletingOneReferenceRemovesItsPrivateFingerprintsOnly() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    VoiceProfiles p = new VoiceProfiles(c);
    float[] pcm = new float[24000];
    PcmWave.write(p.wave("owned"), pcm, 24000);
    PcmWave.write(p.wave("other"), pcm, 24000);
    p.save("owned", "My voice", "Custom", "", false);
    p.save("other", "Keep voice", "Female", "", false);
    File folder = new File(p.root(), "owned-enrollments/revision");
    assertTrue(folder.mkdirs());
    Files.write(new File(folder, "vocal_dna_enrolled.bin").toPath(), new byte[768]);
    p.delete("owned");
    assertFalse(folder.exists());
    assertTrue(p.audio("other").isFile());
    assertEquals("Keep voice", p.get("other").getString("name"));
  }

  @Test
  public void missingOrDamagedSpeakerFilesAreNotMarkedReady() throws Exception {
    SpeakerModelStore store =
        new SpeakerModelStore(Files.createTempDirectory("speaker-store").toFile());
    assertFalse(store.ready());
    Files.write(new File(store.root, "ecapa-speaker-v1.onnx").toPath(), new byte[64]);
    assertFalse(store.verify());
    assertFalse(store.ready());
    store.remove();
    assertFalse(store.ready());
  }

  @Test
  public void failedInitializerIsActionableInsteadOfOnlyAClassName() {
    String message =
        JobFailure.message(new NoClassDefFoundError("in.aitechapp.teluguoffline.SpeechMarkup"));
    assertTrue(message.contains("reopen"));
    assertTrue(message.contains("recordings are kept"));
    assertEquals(
        "Could not complete: No storage",
        JobFailure.message(new RuntimeException("wrapper", new IOException("No storage"))));
  }
}
