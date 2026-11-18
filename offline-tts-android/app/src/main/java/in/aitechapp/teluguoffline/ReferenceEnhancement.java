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
    File input = profiles.wave(id).isFile() ? profiles.wave(id) : profiles.audio(id);
    File capture = new File(profiles.root(), id + "-capture.wav");
    if (capture.isFile()) input = capture;
    if (profiles.root().getUsableSpace() < 20_000_000)
      throw new IOException("Free 20 MB for a cleaned copy. Original is kept.");
    String next = id;
    File output = new File(profiles.root(), id + "-cleaned-" + UUID.randomUUID() + ".wav");
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
      WarmthEq.Biquad presence = new WarmthEq.Biquad(24000, 3500, 2.5, true);
      double peak = 0;
      for (int i = 0; i < audio.length; i++) {
        audio[i] = (float) presence.filter(audio[i]);
        peak = Math.max(peak, Math.abs(audio[i]));
      }
      if (peak > .97) for (int i = 0; i < audio.length; i++) audio[i] *= .97 / peak;
      PcmWave.write(output, audio, 24000);
      OfflineDenoiser.check(cancel);
      int count = original.optInt("cleanCount", 0) + 1;
      long now = System.currentTimeMillis();
      original
          .put("activeAudio", output.getName())
          .put("cleanCount", count)
          .put("cleanedAt", now)
          .put("durationMs", audio.length / 24)
          .put("enhancer", OfflineDenoiser.VERSION)
          .put("wpe", roomTail)
          .put("inputSha256", FileIntegrity.sha256(input))
          .put("status", "✓ Cleaned (" + count + "x)")
          .put("displayStem", AudioNames.stem(original.getString("name"), "Cleaned", now));
      // Atomic sidecar promotion updates one card only after its new audio is complete.
      FileIntegrity.write(new File(profiles.root(), id + ".json"), original.toString());
      try {
        FileIntegrity.write(journal, "Completed · recording updated. Original kept.");
      } catch (IOException ignored) {
      }
      return next;
    } catch (Exception | LinkageError e) {
      // Only our newly created copy is rolled back. Never delete or rename the source.
      output.delete();
      new File(output.getPath() + ".part").delete();
      // Existing profile sidecar and original recording are never deleted.
      FileIntegrity.write(journal, "Cleanup stopped. Original kept; tap Enhance to retry.");
      throw e;
    }
  }

  private ReferenceEnhancement() {}
}
