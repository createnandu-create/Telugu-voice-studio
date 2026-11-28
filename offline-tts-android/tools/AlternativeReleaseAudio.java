package in.aitechapp.teluguoffline;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the same CPU graph sessions used by Android with a public synthetic reference. */
public final class AlternativeReleaseAudio {
  private static final String TEXT = "నమస్కారం ఫ్రెండ్స్! మీరు ఎలా ఉన్నారు? ఇక్కడ క్లిక్ చేయండి.";
  private static final AtomicBoolean CANCEL = new AtomicBoolean();

  private static void validate(float[] pcm, int rate) throws IOException {
    double energy = 0;
    int clipped = 0;
    for (float sample : pcm) {
      if (!Float.isFinite(sample)) throw new IOException("Non-finite speech");
      energy += sample * sample;
      if (Math.abs(sample) >= 1) clipped++;
    }
    if (pcm.length < rate || pcm.length > rate * 20 ||
        energy / pcm.length < 0.00001 || clipped > pcm.length / 100)
      throw new IOException("Invalid speech duration or level");
  }

  public static void main(String[] args) throws Exception {
    File root = new File(args[0]), out = new File(args[1]);
    File shared = new File(root, "hima"), clone = new File(root, "clone");
    float[] reference = null;
    for (int i = 4; i < ModelCatalog.VOICES.length; i++) {
      ModelCatalog.Voice profile = ModelCatalog.VOICES[i];
      try (AlternativeVoice voice = profile.hima()
          ? new HimaVoice(shared, profile.name, CANCEL)
          : profile.syspin() ? new SyspinVoice(new File(root, profile.id), CANCEL)
          : new FastPitchVoice(new File(root, "indic"), profile.gender.equals("Male"), CANCEL)) {
        float[] pcm = voice.render(TEXT);
        validate(pcm, profile.sampleRate());
        PcmWave.write(new File(out, profile.id + ".wav"), pcm, profile.sampleRate());
        if (profile.fastPitch() && voice.render(TEXT, 1.12f).length >= pcm.length)
          throw new IOException("FastPitch native pacing had no effect");
        if (i == 4) {
          reference = new float[24000 * 12];
          for (int j = 0; j < reference.length; j++) reference[j] = pcm[j % pcm.length];
        }
      }
    }
    HimaCondition conditioning = HimaCondition.extract(new File(clone, "encoder.pb"),
        new File(root, "ecapa"), reference, CANCEL);
    try (HimaVoice voice = new HimaVoice(shared, clone, "Personal Hima", conditioning, CANCEL)) {
      float[] pcm = voice.render(TEXT);
      validate(pcm, 24000);
      PcmWave.write(new File(out, "hima-public-clone.wav"), pcm, 24000);
    }
  }
}
