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

  public boolean verified(ModelCatalog.Voice voice) throws IOException {
    return FileIntegrity.matches(model(voice), voice.installedSha);
  }

  public void remove(ModelCatalog.Voice voice) throws IOException {
    java.nio.file.Files.deleteIfExists(model(voice).toPath());
  }

  private void check(AtomicBoolean cancelled) {
    if (cancelled.get() || Thread.currentThread().isInterrupted())
      throw new CancellationException("Cancelled. Completed render chunks are kept.");
  }

  public void install(ModelCatalog.Voice voice, AtomicBoolean cancel, Progress progress)
      throws Exception {
    if (root.getUsableSpace() < ModelCatalog.BYTES + 40_000_000)
      throw new IOException("Free at least 105 MB before installing a voice.");
    File part = new File(root, voice.id + ".download");
    HttpURLConnection conn = null;
    try {
      URL url = new URL(voice.url());
      for (int redirects = 0; ; redirects++) {
        if (!url.getProtocol().equals("https")) throw new IOException("Download requires HTTPS.");
        conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept-Encoding", "identity");
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400 && redirects < 5) {
          URL next = new URL(url, conn.getHeaderField("Location"));
          conn.disconnect();
          url = next;
          continue;
        }
        if (code != 200)
          throw new IOException("Model server returned HTTP " + code + ". Try again when online.");
        break;
      }
      long count = 0;
      byte[] buffer = new byte[65536];
      long last = 0;
      try (InputStream in = conn.getInputStream();
          FileOutputStream out = new FileOutputStream(part)) {
        int n;
        while ((n = in.read(buffer)) != -1) {
          check(cancel);
          count += n;
          if (count > ModelCatalog.BYTES) throw new IOException("Unexpected model size.");
          out.write(buffer, 0, n);
          if (count - last > 1_000_000) {
            progress.update(
                "Downloading " + voice.name + " · " + (count * 100 / ModelCatalog.BYTES) + "%");
            last = count;
          }
        }
        out.getFD().sync();
      }
      if (count != ModelCatalog.BYTES || !FileIntegrity.matches(part, voice.originalSha))
        throw new IOException("Download failed integrity check. Please retry.");
      check(cancel);
      progress.update("Verifying voice and preparing local phonemes…");
      // ONNX protobuf metadata only; graph/weights unchanged. Both input and output hashes are
      // pinned.
      try (InputStream in = context.getAssets().open("piper-metadata.bin");
          FileOutputStream out = new FileOutputStream(part, true)) {
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        out.getFD().sync();
      }
      if (!FileIntegrity.matches(part, voice.installedSha))
        throw new IOException("Installed model checksum mismatch.");
      prepareAssets(cancel);
      check(cancel);
      FileIntegrity.promote(part, model(voice));
    } finally {
      if (conn != null) conn.disconnect();
      part.delete();
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
