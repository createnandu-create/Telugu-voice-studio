package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** A clone becomes selectable only after real local tone conversion has produced its preview. */
public final class CloneProfiles {
  public static File directory(VoiceProfiles profiles, JSONObject p) throws IOException {
    if (!p.optBoolean("cloneConsent", false))
      throw new IOException("Confirm consent and create a voice preview");
    if (!FileIntegrity.matches(profiles.audio(p.optString("id")), p.optString("cloneSourceSha")))
      throw new IOException("Recording changed; create a new voice preview");
    String revision = p.optString("cloneDirectory");
    if (!revision.matches("[a-zA-Z0-9-]+")) throw new IOException("Create a voice preview first");
    if (!OpenVoiceModelStore.REVISION.equals(p.optString("cloneModel")))
      throw new IOException("Recreate this preview with the installed model");
    File folder = new File(new File(profiles.root(), p.optString("id") + "-clones"), revision);
    if (!FileIntegrity.matches(new File(folder, "tone256.bin"), p.optString("cloneToneSha")))
      throw new IOException("Voice profile is damaged; recreate its preview");
    PcmWave.validate(new File(folder, "preview.wav"));
    return folder;
  }

  public static File baseReference(Context c, int voice) throws IOException {
    String[] names = {
      "piper-venkatesh-2.wav",
      "piper-padmavathi-2.wav",
      "indic-tiny-male-2.wav",
      "indic-tiny-female-2.wav"
    };
    File dest = new File(c.getCacheDir(), "openvoice-base-" + voice + ".wav");
    try (InputStream in = c.getAssets().open("comparison/" + names[voice]);
        OutputStream out = new FileOutputStream(dest)) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
    PcmWave.validate(dest);
    return dest;
  }

  public static void create(
      Context context, String id, int base, AtomicBoolean cancel, VoiceModelStore.Progress progress)
      throws Exception {
    VoiceProfiles profiles = new VoiceProfiles(context);
    JSONObject p = profiles.get(id);
    OpenVoiceModelStore models = new OpenVoiceModelStore(context.getFilesDir());
    if (!models.ready())
      throw new IOException("Install OpenVoice in Settings → Voice cloning first");
    if (profiles.root().getUsableSpace() < 20_000_000)
      throw new IOException("Free 20 MB for the private preview");
    progress.update("Creating voice · reading consented reference…");
    float[] target = ReferenceAudio.read(profiles.audio(id), cancel);
    if (target.length < 240000)
      throw new IOException("Use at least 10 seconds of your own clear voice");
    target = Arrays.copyOf(target, Math.min(target.length, 24000 * 15));
    if (!OfflineDenoiser.VERSION.equals(p.optString("enhancer")))
      target =
          OfflineDenoiser.process(
              target, 24000, cancel, n -> progress.update("Creating voice · cleanup " + n + "%"));
    double power = 0;
    for (float x : target) power += x * x;
    if (Math.sqrt(power / target.length) < .003)
      throw new IOException(
          "Reference is too quiet after cleanup. Record closer to the microphone.");
    File folder = new File(new File(profiles.root(), id + "-clones"), UUID.randomUUID().toString());
    if (!folder.mkdirs()) throw new IOException("Cannot create voice preview storage");
    boolean committed = false;
    try (OpenVoiceEngine engine = new OpenVoiceEngine(models.root, cancel)) {
      progress.update("Creating voice · extracting OpenVoice tone (256-D)…");
      float[] tone = engine.embedding(target, 24000);
      File source = baseReference(context, base);
      float[] pcm = OpenVoiceEngine.read(source);
      int rate = PcmWave.sampleRate(source);
      float[] sourceTone = engine.embedding(pcm, rate);
      progress.update("Creating voice · generating local preview…");
      float[] preview = engine.convert(pcm, rate, sourceTone, tone);
      File raw = new File(folder, "raw-preview.wav");
      PcmWave.write(raw, preview, 22050);
      PcmWave.merge(
          List.of(raw),
          List.of(
              new NarrationDirector.Segment("preview", NarrationDirector.Emotion.NEUTRAL, 1, 1, 0)),
          new File(folder, "preview.wav"),
          cancel);
      OpenVoiceEngine.saveEmbedding(new File(folder, "tone256.bin"), tone);
      OfflineDenoiser.check(cancel);
      p.put("cloneDirectory", folder.getName())
          .put("cloneModel", OpenVoiceModelStore.REVISION)
          .put("cloneToneSha", FileIntegrity.sha256(new File(folder, "tone256.bin")))
          .put("cloneBase", base)
          .put("cloneConsent", true)
          .put("cloneSourceSha", FileIntegrity.sha256(profiles.audio(id)))
          .put("cloneCreated", System.currentTimeMillis());
      FileIntegrity.write(new File(profiles.root(), id + ".json"), p.toString());
      committed = true;
    } finally {
      if (!committed) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
        folder.delete();
      }
    }
  }

  private CloneProfiles() {}
}
