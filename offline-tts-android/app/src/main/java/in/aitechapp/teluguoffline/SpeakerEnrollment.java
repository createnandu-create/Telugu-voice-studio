package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Private, consented fingerprint enrollment. Never registers an unsupported synthetic clone. */
public final class SpeakerEnrollment {
  public static void run(
      Context context, String id, AtomicBoolean cancel, VoiceModelStore.Progress progress)
      throws Exception {
    VoiceProfiles profiles = new VoiceProfiles(context);
    JSONObject metadata = profiles.get(id);
    SpeakerModelStore store = new SpeakerModelStore(context.getFilesDir());
    if (!store.ready())
      throw new IOException("Install the speaker encoder in Settings → Advanced recording tools.");
    File source = profiles.audio(id);
    progress.update("Enrolling · reading reference…");
    float[] audio = ReferenceAudio.read(source, cancel);
    if (audio.length < 24000 * 10)
      throw new IOException("Record at least 10 seconds of clear speech for enrollment.");
    if (!OfflineDenoiser.VERSION.equals(metadata.optString("enhancer")))
      audio =
          OfflineDenoiser.process(
              audio, 24000, cancel, p -> progress.update("Enrolling · noise reduction " + p + "%"));
    // Choose the highest-energy contiguous 15 seconds, not disconnected snippets that alter
    // identity.
    int count = Math.min(audio.length, 24000 * 15), best = 0;
    double energy = 0, bestEnergy;
    for (int i = 0; i < count; i++) energy += audio[i] * audio[i];
    bestEnergy = energy;
    for (int i = count; i < audio.length; i++) {
      energy += audio[i] * audio[i] - audio[i - count] * audio[i - count];
      if (energy > bestEnergy) {
        bestEnergy = energy;
        best = i - count + 1;
      }
    }
    float[] stem = Arrays.copyOfRange(audio, best, best + count);
    int active = 0;
    for (int start = 0; start < stem.length; start += 240) {
      double sum = 0;
      for (int i = start; i < Math.min(stem.length, start + 240); i++) sum += stem[i] * stem[i];
      if (Math.sqrt(sum / 240) > .01) active++;
    }
    if (active < 500)
      throw new IOException(
          "Too little clear speech after cleanup. Record at least 10 seconds closer to the"
              + " microphone.");
    progress.update("Enrolling · computing local speaker fingerprint…");
    float[] vector =
        SpeakerEncoder.encode(
            store.root, OfflineDenoiser.resample(stem, 24000, 16000, cancel), cancel);
    OfflineDenoiser.check(cancel);
    File folder =
        new File(new File(profiles.root(), id + "-enrollments"), UUID.randomUUID().toString());
    if (!folder.mkdirs()) throw new IOException("Cannot create private enrollment folder");
    boolean committed = false;
    try {
      File wave = new File(folder, "cleaned-reference.wav");
      PcmWave.write(wave, stem, 24000);
      ByteBuffer bytes = ByteBuffer.allocate(192 * 4).order(ByteOrder.LITTLE_ENDIAN);
      for (float v : vector) bytes.putFloat(v);
      Files.write(new File(folder, "vocal_dna_enrolled.bin").toPath(), bytes.array());
      JSONObject report =
          new JSONObject()
              .put("encoderRevision", SpeakerModelStore.REVISION)
              .put("dimensions", 192)
              .put("format", "float32-le-l2-normalized")
              .put("sourceSha256", FileIntegrity.sha256(source))
              .put("referenceSha256", FileIntegrity.sha256(wave))
              .put("startMs", best / 24)
              .put("durationMs", count / 24)
              .put(
                  "scope",
                  "Speaker fingerprint only. No compatible Telugu cloning model installed.")
              .put("consentConfirmed", true);
      FileIntegrity.write(new File(folder, "manifest.json"), report.toString());
      OfflineDenoiser.check(cancel);
      metadata
          .put("enrollmentPath", folder.getName())
          .put("status", "Speaker fingerprint enrolled · 192-D · cloning unavailable");
      FileIntegrity.write(new File(profiles.root(), id + ".json"), metadata.toString());
      committed = true;
    } finally {
      if (!committed) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
        folder.delete();
      }
    }
  }

  private SpeakerEnrollment() {}
}
