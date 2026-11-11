package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared male/female model pack. Every graph is SHA-256 checked before atomic promotion. */
public final class TinyModelStore {
  public static final String REVISION = "55cfc683fafb55326a0a01932a3b2bb4374c4fc0";
  public static final long BYTES = 91011543L;

  public static final class Asset {
    public final String name, sha;
    public final long bytes;

    Asset(String name, long bytes, String sha) {
      this.name = name;
      this.bytes = bytes;
      this.sha = sha;
    }
  }

  public static final Asset[] ASSETS = {
    new Asset(
        "text_encoder.onnx",
        17692254L,
        "728d9c2529e00d30de480c598369fad24919f83cd0ac44994a64724bbd3e3839"),
    new Asset(
        "flow_decoder.onnx",
        19169253L,
        "a523c39fe6b5df3a8cc76953fce9572ea6b7d3b8677d9232a4d5968fec75c40d"),
    new Asset(
        "vocoder.onnx",
        54150036L,
        "44d36238a761750447038895ab194e2378f6f4ba2c2d089a4766fa52810ef353")
  };
  public final File root;

  public TinyModelStore(File neuralRoot) {
    root = new File(neuralRoot, "indic-tiny-" + REVISION.substring(0, 12));
    root.mkdirs();
  }

  private File receipt(Asset a) {
    return new File(root, a.name + ".receipt");
  }

  public boolean ready() throws IOException {
    for (Asset a : ASSETS)
      if (!InstallReceipt.ready(new File(root, a.name), receipt(a), a.sha, REVISION)) return false;
    return true;
  }

  public boolean verify() throws IOException {
    boolean good = true;
    for (Asset a : ASSETS)
      good = InstallReceipt.verify(new File(root, a.name), receipt(a), a.sha, REVISION) && good;
    return good;
  }

  public void remove() throws IOException {
    for (Asset a : ASSETS) {
      java.nio.file.Files.deleteIfExists(new File(root, a.name).toPath());
      java.nio.file.Files.deleteIfExists(receipt(a).toPath());
    }
  }

  public long partial() {
    long size = 0;
    for (Asset a : ASSETS) size += new File(root, a.name + ".download").length();
    return size;
  }

  public void install(AtomicBoolean cancel, VoiceModelStore.Progress progress) throws Exception {
    if (ready()) {
      progress.update("Indic Tiny · READY OFFLINE · shared male/female pack");
      return;
    }
    if (root.getUsableSpace() < BYTES * 2 + 25000000)
      throw new IOException("Free 210 MB to safely install Indic Tiny.");
    for (Asset a : ASSETS) {
      File dest = new File(root, a.name);
      if (InstallReceipt.ready(dest, receipt(a), a.sha, REVISION)) continue;
      if (dest.isFile() && InstallReceipt.verify(dest, receipt(a), a.sha, REVISION)) continue;
      File part = new File(root, a.name + ".download");
      new ResumableDownload()
          .fetch(
              "https://huggingface.co/sulabhkatiyar/indic-tiny-tts-telugu/resolve/"
                  + REVISION
                  + "/onnx/"
                  + a.name,
              part,
              a.bytes,
              a.sha,
              cancel,
              (bytes, total) ->
                  progress.update("Downloading " + a.name + " · " + bytes * 100 / total + "%"));
      if (cancel.get()) throw new java.util.concurrent.CancellationException();
      progress.update("Verifying " + a.name + "…");
      FileIntegrity.promote(part, dest);
      if (!InstallReceipt.verify(dest, receipt(a), a.sha, REVISION))
        throw new IOException("Indic Tiny integrity check failed.");
      new File(part.getPath() + ".properties").delete();
    }
  }
}
