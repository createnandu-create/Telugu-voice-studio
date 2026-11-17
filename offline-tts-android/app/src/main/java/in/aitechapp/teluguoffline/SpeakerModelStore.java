package in.aitechapp.teluguoffline;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Speaker verification encoder, not a TTS/cloning model. No inference network requests. */
public final class SpeakerModelStore {
  public static final String REVISION = "a9cb9321b07b4ee5b0ea47fdd25242d9cacd824a";
  static final String[] NAMES = {"ecapa-speaker-v1.onnx", "fbank-80x201-f32.bin"};
  static final long[] SIZES = {83476039, 64320};
  static final String[] HASHES = {
    "f46380bbaeddb929fb3a10ab63a4b1877a50e3d1e5fdd55a1b618d5651d3f64e",
    "024e5073b7cfedee84408dc68dd6bafa02808fc786e67f1314e9c918297f5a63"
  };
  public final File root;

  public SpeakerModelStore(File files) {
    root = new File(files, "speaker-ecapa-" + REVISION.substring(0, 12));
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
      progress.update("Speaker encoder · READY OFFLINE");
      return;
    }
    if (root.getUsableSpace() < 180_000_000)
      throw new IOException("Free 180 MB to install the speaker encoder.");
    for (int i = 0; i < NAMES.length; i++) {
      File dest = new File(root, NAMES[i]), receipt = new File(root, NAMES[i] + ".receipt");
      if (InstallReceipt.ready(dest, receipt, HASHES[i], REVISION)) continue;
      if (dest.isFile() && InstallReceipt.verify(dest, receipt, HASHES[i], REVISION)) continue;
      File part = new File(root, NAMES[i] + ".download");
      new ResumableDownload()
          .fetch(
              "https://huggingface.co/vedk00/ecapa-voxceleb-speaker-embedding-onnx/resolve/"
                  + REVISION
                  + "/model/"
                  + NAMES[i],
              part,
              SIZES[i],
              HASHES[i],
              cancel,
              (bytes, total) ->
                  progress.update("Downloading speaker encoder · " + bytes * 100 / total + "%"));
      if (cancel.get()) throw new java.util.concurrent.CancellationException();
      progress.update("Verifying speaker encoder…");
      FileIntegrity.promote(part, dest);
      if (!InstallReceipt.verify(dest, receipt, HASHES[i], REVISION))
        throw new IOException("Speaker encoder integrity failure");
    }
  }
}
