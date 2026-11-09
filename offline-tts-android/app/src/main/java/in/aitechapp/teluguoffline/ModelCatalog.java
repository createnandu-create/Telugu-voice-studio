package in.aitechapp.teluguoffline;

/** Pinned upstream weights. No model is downloaded implicitly during inference. */
public final class ModelCatalog {
  public static final String REVISION = "cf910fb59f1d79ab3b625a63993a108751c6c25d";
  public static final long BYTES = 63516050L;

  public static final class Voice {
    public final String id, name, gender, originalSha, installedSha;

    Voice(String id, String name, String gender, String original, String installed) {
      this.id = id;
      this.name = name;
      this.gender = gender;
      originalSha = original;
      installedSha = installed;
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
        "180977ceb7886be2c69b6c240a0072396aca16fff46f79a397040e679b5e6b52"),
    new Voice(
        "padmavathi",
        "Padmavathi",
        "Female",
        "414aa5960d91ceb6e45bbdf8c27fdc71af09f205130d7be4e99470f3c2cfa57d",
        "4eecc3c3da95e6915bb1a7227b345830e3662bf419ad47da8045c5eaf1614597")
  };

  private ModelCatalog() {}
}
