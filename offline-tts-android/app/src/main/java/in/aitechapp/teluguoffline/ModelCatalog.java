package in.aitechapp.teluguoffline;

/** Pinned upstream weights. No model is downloaded implicitly during inference. */
public final class ModelCatalog {
  public static final String REVISION = "cf910fb59f1d79ab3b625a63993a108751c6c25d";
  public static final long BYTES = 63516050L;

  public static final class Voice {
    public final String id, name, gender, originalSha, installedSha, legacySha;

    Voice(String id, String name, String gender, String original, String installed) {
      this(id, name, gender, original, installed, installed);
    }

    Voice(String id, String name, String gender, String original, String legacy, String installed) {
      this.id = id;
      this.name = name;
      this.gender = gender;
      originalSha = original;
      legacySha = legacy;
      installedSha = installed;
    }

    public boolean tiny() {
      return id.startsWith("tiny-");
    }

    public boolean hima() { return id.startsWith("hima-"); }
    public boolean fastPitch() { return id.startsWith("fastpitch-"); }
    public boolean syspin() { return id.startsWith("syspin-"); }
    public boolean alternative() { return hima() || fastPitch() || syspin(); }
    public int sampleRate() { return tiny() || hima() ? 24000 : 22050; }
    public String engine() {
      if (syspin()) return "SYSPIN / Coqui VITS";
      if (hima()) return "Hima Nano INT8";
      if (fastPitch()) return "Indic-TTS / FastPitch + HiFi-GAN";
      return tiny() ? "Indic Tiny / Vocos" : "Piper / VITS";
    }

    public String url() {
      return "https://huggingface.co/rhasspy/piper-voices/resolve/"
          + REVISION
          + "/te/te_IN/"
          + id
          + "/medium/te_IN-"
          + id
          + "-medium.onnx";
    }
  }

  public static final Voice[] VOICES = {
    new Voice(
        "venkatesh",
        "Venkatesh",
        "Male",
        "dfaa5b7833cd48d946f3fe18c9c934aaa4e8590aac6922fddf34783a694c3c87",
        "180977ceb7886be2c69b6c240a0072396aca16fff46f79a397040e679b5e6b52",
        "b5b6d65767a882fc1ceba28dbe1d0387ba9d52da9d082df114dc7718dc571d59"),
    new Voice(
        "padmavathi",
        "Padmavathi",
        "Female",
        "414aa5960d91ceb6e45bbdf8c27fdc71af09f205130d7be4e99470f3c2cfa57d",
        "4eecc3c3da95e6915bb1a7227b345830e3662bf419ad47da8045c5eaf1614597",
        "8461a931a8759001165fd4b83fd8b46978cfde171d630f8e75a4e981f3482102"),
    new Voice("tiny-male", "Indic Tiny Male", "Male", "", "tiny-55cfc683-v1-male"),
    new Voice("tiny-female", "Indic Tiny Female", "Female", "", "tiny-55cfc683-v1-female"),
    new Voice("hima-srinivas","Srinivas","Male","","hima-srinivas-8fd92e39"),
    new Voice("hima-bhavana","Bhavana","Female","","hima-bhavana-8fd92e39"),
    new Voice("fastpitch-male","Indic-TTS Male","Male","","fastpitch-male-78f9e2ce"),
    new Voice("fastpitch-female","Indic-TTS Female","Female","","fastpitch-female-78f9e2ce"),
    new Voice("hima-lakshmi","Lakshmi","Female","","hima-lakshmi-8fd92e39"),
    new Voice("hima-sravani","Sravani","Female","","hima-sravani-8fd92e39"),
    new Voice("hima-karthik","Karthik","Male","","hima-karthik-8fd92e39"),
    new Voice("hima-pranay","Pranay","Male","","hima-pranay-8fd92e39"),
    new Voice("syspin-male","SYSPIN Male","Male","","022ffde2ceb933aa61230ab0bbeff9b23d0b91bc2493f483eed1fc14604338ed"),
    new Voice("syspin-female","SYSPIN Female","Female","","0ceb91c9f4628ba8766ba0b4e23eb63b238449f25c767f501351d0af708bea48")
  };

  public static int previewBase(String label, int selected) {
    if (selected < 0 || selected >= VOICES.length) selected = 0;
    if (!"Male".equalsIgnoreCase(label) && !"Female".equalsIgnoreCase(label)) return selected;
    if (VOICES[selected].gender.equalsIgnoreCase(label)) return selected;
    for (int i = 0; i < VOICES.length; i++)
      if (VOICES[i].engine().equals(VOICES[selected].engine()) && VOICES[i].gender.equalsIgnoreCase(label)) return i;
    throw new IllegalStateException("No matching narrator");
  }

  private ModelCatalog() {}
}
