package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Non-destructive, foreground enrollment cleanup. Publication happens only after complete output.
 */
public final class ReferenceEnhancement {
  public static String run(
      Context context,
      String id,
      boolean roomTail,
      AtomicBoolean cancel,
      VoiceModelStore.Progress progress)
      throws Exception {
    VoiceProfiles profiles = new VoiceProfiles(context);
    JSONObject original = profiles.get(id);
    File input = profiles.audio(id);
    File capture = new File(profiles.root(), id + "-capture.wav");
    if (capture.isFile()) input = capture;
    if (profiles.root().getUsableSpace() < 20_000_000)
      throw new IOException("Free 20 MB for a cleaned copy. Original is kept.");
    String next = UUID.randomUUID().toString();
    File output = profiles.wave(next);
    File journal = new File(profiles.root(), "enhancement-state.txt");
    FileIntegrity.write(
        journal, "Processing reference; original kept. If interrupted, tap Enhance to retry.");
    try {
      progress.update("Enhancing · decoding reference…");
      float[] audio = ReferenceAudio.read(input, cancel);
      if (roomTail)
        audio =
            LateReverb.process(
                audio, cancel, p -> progress.update("Enhancing · room tail " + p + "%"));
      audio =
          OfflineDenoiser.process(
              audio, 24000, cancel, p -> progress.update("Enhancing · noise reduction " + p + "%"));
      OfflineDenoiser.check(cancel);
      PcmWave.write(output, audio, 24000);
      OfflineDenoiser.check(cancel);
      String name = original.getString("name");
      if (name.length() > 90) name = name.substring(0, 90);
      JSONObject metadata =
          profiles.save(
              next,
              name + " · Clean",
              original.optString("label", "Custom"),
              "RNNoise noise reduction · 85 Hz high-pass"
                  + (roomTail ? " · experimental WPE room tail" : "")
                  + ". Original kept; not a cloned voice.",
              false);
      metadata
          .put("sourceProfile", id)
          .put("enhancer", OfflineDenoiser.VERSION)
          .put("wpe", roomTail)
          .put("inputSha256", FileIntegrity.sha256(input))
          .put("status", "Enhanced reference · compare with original");
      FileIntegrity.write(new File(profiles.root(), next + ".json"), metadata.toString());
      FileIntegrity.write(journal, "Completed · cleaned copy saved. Original kept.");
      return next;
    } catch (Exception | LinkageError e) {
      // Only our newly created copy is rolled back. Never delete or rename the source.
      output.delete();
      new File(output.getPath() + ".part").delete();
      new File(profiles.root(), next + ".json").delete();
      FileIntegrity.write(journal, "Cleanup stopped. Original kept; tap Enhance to retry.");
      throw e;
    }
  }

  private ReferenceEnhancement() {}
}
