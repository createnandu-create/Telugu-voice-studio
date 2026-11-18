package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** OpenVoice V2 tone converter and its matching encoder. No inference network requests. */
public final class OpenVoiceModelStore {
  public static final String REVISION = "9383bf8b636a05319f94a4bb181777f32f6f8dfd";
  static final String[] NAMES = {"tone_converter_q8.onnx", "tone_ref_encoder_q8.onnx"};
  static final long[] SIZES = {40804625, 2315304};
  static final String[] HASHES = {
    "54ea73764c46cdbb74af2124e30cce42007045f1f5a60bd7520472f155eb6f4c",
    "8e46097e46a68a2137acf105b58bc67cf686ec0d811c1c45ada28557a608c0e3"
  };
  public final File root;

  public OpenVoiceModelStore(File files) {
    root = new File(files, "openvoice-v2-" + REVISION.substring(0, 12));
    root.mkdirs();
  }

  public boolean ready() throws IOException {
    for (int i = 0; i < NAMES.length; i++)
      if (!InstallReceipt.ready(
          new File(root, NAMES[i]), new File(root, NAMES[i] + ".receipt"), HASHES[i], REVISION))
        return false;
    return true;
  }

  public boolean verify() throws IOException {
    boolean ok = true;
    for (int i = 0; i < NAMES.length; i++)
      ok =
          InstallReceipt.verify(
                  new File(root, NAMES[i]),
                  new File(root, NAMES[i] + ".receipt"),
                  HASHES[i],
                  REVISION)
              && ok;
    return ok;
  }

  public void remove() throws IOException {
    for (String name : NAMES) {
      java.nio.file.Files.deleteIfExists(new File(root, name).toPath());
      java.nio.file.Files.deleteIfExists(new File(root, name + ".receipt").toPath());
      java.nio.file.Files.deleteIfExists(new File(root, name + ".download").toPath());
      java.nio.file.Files.deleteIfExists(new File(root, name + ".download.properties").toPath());
    }
  }

  public void install(AtomicBoolean cancel, VoiceModelStore.Progress progress) throws Exception {
    if (ready()) {
      progress.update("OpenVoice pack · READY OFFLINE");
      return;
    }
    if (root.getUsableSpace() < 100_000_000)
      throw new IOException("Free 100 MB to install the OpenVoice pack.");
    for (int i = 0; i < NAMES.length; i++) {
      File dest = new File(root, NAMES[i]), receipt = new File(root, NAMES[i] + ".receipt");
      if (InstallReceipt.ready(dest, receipt, HASHES[i], REVISION)) continue;
      if (dest.isFile() && InstallReceipt.verify(dest, receipt, HASHES[i], REVISION)) continue;
      File part = new File(root, NAMES[i] + ".download");
      new ResumableDownload()
          .fetch(
              "https://huggingface.co/Q-P1NG/openvoice-v2-onnx/resolve/"
                  + REVISION
                  + "/"
                  + NAMES[i],
              part,
              SIZES[i],
              HASHES[i],
              cancel,
              (bytes, total) ->
                  progress.update("Downloading OpenVoice pack · " + bytes * 100 / total + "%"));
      if (cancel.get()) throw new java.util.concurrent.CancellationException();
      progress.update("Verifying OpenVoice pack…");
      FileIntegrity.promote(part, dest);
      if (!InstallReceipt.verify(dest, receipt, HASHES[i], REVISION))
        throw new IOException("OpenVoice pack integrity failure");
    }
  }
}
