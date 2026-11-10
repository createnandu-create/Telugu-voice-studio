package in.aitechapp.teluguoffline;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pinned HTTPS assets; Range/If-Range requests resume validated partial bytes. No inference uses
 * this.
 */
public final class ResumableDownload {
  public interface Progress {
    void update(long bytes, long total);
  }

  private final boolean allowLocalHttp;

  public ResumableDownload() {
    this(false);
  }

  ResumableDownload(boolean allowLocalHttp) {
    this.allowLocalHttp = allowLocalHttp;
  }

  private static void check(AtomicBoolean c) {
    if (c.get() || Thread.currentThread().isInterrupted())
      throw new CancellationException("Download paused; partial bytes saved.");
  }

  public void fetch(
      String address, File part, long size, String sha, AtomicBoolean cancel, Progress progress)
      throws IOException {
    File state = new File(part.getPath() + ".properties");
    Properties saved = InstallReceipt.load(state);
    if (!address.equals(saved.getProperty("url"))
        || !sha.equals(saved.getProperty("sha256"))
        || part.length() > size) {
      java.nio.file.Files.deleteIfExists(part.toPath());
      saved = new Properties();
    }
    if (part.isFile() && part.length() == size) {
      if (FileIntegrity.matches(part, sha)) {
        progress.update(size, size);
        return;
      }
      java.nio.file.Files.deleteIfExists(part.toPath());
    }
    long offset = part.isFile() ? part.length() : 0;
    String etag = saved.getProperty("etag", "");
    HttpURLConnection conn = null;
    try {
      URL url = new URL(address);
      for (int redirects = 0; ; redirects++) {
        check(cancel);
        if (!url.getProtocol().equals("https")
            && !(allowLocalHttp
                && url.getProtocol().equals("http")
                && (url.getHost().equals("127.0.0.1") || url.getHost().equals("localhost"))))
          throw new IOException("Model download requires HTTPS.");
        conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (offset > 0) {
          conn.setRequestProperty("Range", "bytes=" + offset + "-");
          if (!etag.isEmpty() && !etag.startsWith("W/")) conn.setRequestProperty("If-Range", etag);
        }
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400 && redirects < 5) {
          String location = conn.getHeaderField("Location");
          if (location == null) throw new IOException("Missing redirect location.");
          URL next = new URL(url, location);
          conn.disconnect();
          url = next;
          continue;
        }
        if (code != 200 && code != 206)
          throw new IOException(
              "Download server returned HTTP "
                  + code
                  + ". Partial data is kept; Retry when connected.");
        if (code == 206) {
          String range = conn.getHeaderField("Content-Range");
          if (range == null || !range.matches("bytes " + offset + "-[0-9]+/" + size))
            throw new IOException("Server returned an invalid resume range.");
          String nextTag = conn.getHeaderField("ETag");
          if (!etag.isEmpty() && nextTag != null && !etag.equals(nextTag))
            throw new IOException("Remote content changed. Use Restart download.");
        } else
          offset =
              0; // If-Range mismatch or Range unsupported: restart this asset, retain installed
                 // model.
        break;
      }
      saved.setProperty("url", address);
      saved.setProperty("sha256", sha);
      saved.setProperty("total", Long.toString(size));
      String tag = conn.getHeaderField("ETag");
      if (tag != null) saved.setProperty("etag", tag);
      saved.setProperty("state", "downloading");
      InstallReceipt.save(state, saved);
      long count = offset, last = offset;
      progress.update(count, size);
      byte[] buffer = new byte[65536];
      try (InputStream in = conn.getInputStream();
          FileOutputStream out = new FileOutputStream(part, offset > 0)) {
        try {
          int n;
          while ((n = in.read(buffer)) != -1) {
            check(cancel);
            count += n;
            if (count > size) throw new IOException("Unexpected model size.");
            out.write(buffer, 0, n);
            if (count - last >= 524288) {
              progress.update(count, size);
              last = count;
            }
          }
        } finally {
          out.getFD().sync();
        }
      }
      check(cancel);
      if (count != size) throw new IOException("Download interrupted. Tap Resume to continue.");
      if (!FileIntegrity.matches(part, sha)) {
        java.nio.file.Files.deleteIfExists(part.toPath());
        throw new IOException("Checksum mismatch. Invalid partial removed; Retry.");
      }
      saved.setProperty("state", "verified");
      saved.setProperty("downloaded", Long.toString(count));
      InstallReceipt.save(state, saved);
      progress.update(count, size);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }
}
