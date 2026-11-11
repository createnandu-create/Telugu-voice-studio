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
    new Voice("tiny-female", "Indic Tiny Female", "Female", "", "tiny-55cfc683-v1-female")
  };

  private ModelCatalog() {}
}
