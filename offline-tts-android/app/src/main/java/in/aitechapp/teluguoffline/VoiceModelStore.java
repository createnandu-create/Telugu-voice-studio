package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.JSONObject;

/** Only this class accesses the network, exclusively from an explicit Install action. */
public final class VoiceModelStore {
  public interface Progress {
    void update(String message);
  }

  private final Context context;
  public final File root;

  public VoiceModelStore(Context c) {
    context = c.getApplicationContext();
    root = new File(c.getFilesDir(), "neural-v1");
    root.mkdirs();
  }

  public File model(ModelCatalog.Voice voice) {
    return new File(root, voice.id + ".onnx");
  }

  private File receipt(ModelCatalog.Voice voice) {
    return new File(root, voice.id + ".installed.properties");
  }

  public boolean ready(ModelCatalog.Voice voice) throws IOException {
    return InstallReceipt.ready(
        model(voice), receipt(voice), voice.installedSha, ModelCatalog.REVISION);
  }

  /** Legacy migration happens once, locally. A damaged receipt never triggers a network request. */
  public boolean verified(ModelCatalog.Voice voice) throws IOException {
    if (ready(voice)) return true;
    if (model(voice).isFile() && !receipt(voice).exists()) return verify(voice);
    return false;
  }

  public boolean verify(ModelCatalog.Voice voice) throws IOException {
    return InstallReceipt.verify(
        model(voice), receipt(voice), voice.installedSha, ModelCatalog.REVISION);
  }

  public void remove(ModelCatalog.Voice voice) throws IOException {
    java.nio.file.Files.deleteIfExists(model(voice).toPath());
    java.nio.file.Files.deleteIfExists(receipt(voice).toPath());
  }

  private void check(AtomicBoolean cancelled) {
    if (cancelled.get() || Thread.currentThread().isInterrupted())
      throw new CancellationException("Paused; completed files retained.");
  }

  public void install(ModelCatalog.Voice voice, AtomicBoolean cancel, Progress progress)
      throws Exception {
    if (verified(voice) || (model(voice).isFile() && verify(voice))) {
      progress.update(voice.name + " · READY OFFLINE · no download needed");
      return;
    }
    if (root.getUsableSpace() < ModelCatalog.BYTES * 2 + 25_000_000)
      throw new IOException("Free 155 MB to safely install or replace a voice.");
    File part = new File(root, voice.id + ".download"),
        staged = new File(root, voice.id + ".installing");
    new ResumableDownload()
        .fetch(
            voice.url(),
            part,
            ModelCatalog.BYTES,
            voice.originalSha,
            cancel,
            (bytes, total) ->
                progress.update(
                    "Downloading "
                        + voice.name
                        + " · "
                        + (bytes * 100 / total)
                        + "% · "
                        + (bytes / 1_000_000)
                        + " / "
                        + (total / 1_000_000)
                        + " MB"));
    check(cancel);
    progress.update("Verifying " + voice.name + "…");
    java.nio.file.Files.copy(
        part.toPath(), staged.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    try (InputStream in = context.getAssets().open("piper-metadata.bin");
        FileOutputStream out = new FileOutputStream(staged, true)) {
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      out.getFD().sync();
    }
    if (!FileIntegrity.matches(staged, voice.installedSha))
      throw new IOException("Installed checksum mismatch; previous model kept.");
    prepareAssets(cancel);
    check(cancel);
    FileIntegrity.promote(staged, model(voice));
    InstallReceipt.verify(model(voice), receipt(voice), voice.installedSha, ModelCatalog.REVISION);
    part.delete();
    new File(part.getPath() + ".properties").delete();
  }

  /** Metadata-only request, invoked exclusively by Check Updates. Never replaces pinned weights. */
  public String checkUpdates() throws Exception {
    HttpURLConnection c =
        (HttpURLConnection)
            new URL("https://huggingface.co/api/models/rhasspy/piper-voices").openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(15000);
    try {
      if (c.getResponseCode() != 200) throw new IOException("Could not check upstream revision.");
      try (InputStream in = c.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
          if (out.size() > 4_000_000) throw new IOException("Unexpected update response.");
          out.write(b, 0, n);
        }
        String revision = new JSONObject(out.toString("UTF-8")).getString("sha");
        String message =
            ModelCatalog.REVISION.equals(revision)
                ? "Pinned release is current."
                : "Upstream revision changed. Installed voices remain ready. A reviewed app update"
                      + " is required before installing new weights.";
        FileIntegrity.write(new File(root, "last-update-check.txt"), message);
        return message;
      }
    } finally {
      c.disconnect();
    }
  }

  public void prepareAssets(AtomicBoolean cancel) throws Exception {
    // Verify every runtime data file, including after an interrupted install. No trusted marker
    // file.
    JSONObject hashes;
    try (InputStream in = context.getAssets().open("phonemes-sha256.json")) {
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) data.write(buf, 0, n);
      hashes = new JSONObject(data.toString("UTF-8"));
    }
    boolean valid = true;
    for (java.util.Iterator<String> it = hashes.keys(); it.hasNext(); ) {
      check(cancel);
      String name = it.next();
      if (!FileIntegrity.matches(new File(root, name), hashes.getString(name))) {
        valid = false;
        break;
      }
    }
    if (!valid)
      try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("phonemes.zip"))) {
        ZipEntry entry;
        byte[] buf = new byte[65536];
        while ((entry = zip.getNextEntry()) != null) {
          check(cancel);
          File f = new File(root, entry.getName());
          if (!f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)
              || !hashes.has(entry.getName())) throw new IOException("Invalid phoneme archive.");
          f.getParentFile().mkdirs();
          File tmp = new File(f.getPath() + ".tmp");
          try (FileOutputStream out = new FileOutputStream(tmp)) {
            int n;
            long size = 0;
            while ((n = zip.read(buf)) != -1) {
              check(cancel);
              size += n;
              if (size > 32_000_000) throw new IOException("Invalid phoneme file size.");
              out.write(buf, 0, n);
            }
          }
          if (!FileIntegrity.matches(tmp, hashes.getString(entry.getName())))
            throw new IOException("Phoneme integrity check failed.");
          FileIntegrity.promote(tmp, f);
        }
      }
    for (ModelCatalog.Voice voice : ModelCatalog.VOICES) {
      File tmp = new File(root, voice.id + "-tokens.txt.tmp");
      try (InputStream in = context.getAssets().open(voice.id + "-tokens.txt");
          FileOutputStream out = new FileOutputStream(tmp)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
      FileIntegrity.promote(tmp, new File(root, voice.id + "-tokens.txt"));
    }
  }
}
