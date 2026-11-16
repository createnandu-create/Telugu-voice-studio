package in.aitechapp.teluguoffline;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Atomic publication into Downloads; cancellation removes only our pending entry. */
public final class AudioDownloads {
  public interface Progress {
    void update(long bytes, long total);
  }

  public static Uri save(
      Context context, File source, String name, AtomicBoolean cancel, Progress progress)
      throws IOException {
    if (Build.VERSION.SDK_INT < 29)
      throw new IOException("Choose a folder on this Android version.");
    ContentResolver resolver = context.getContentResolver();
    ContentValues values = new ContentValues();
    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
    values.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".mp3") ? "audio/mpeg" : "audio/wav");
    values.put(
        MediaStore.Downloads.RELATIVE_PATH,
        Environment.DIRECTORY_DOWNLOADS + "/Telugu Voice Studio");
    values.put(MediaStore.Downloads.IS_PENDING, 1);
    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) throw new IOException("Downloads is unavailable.");
    boolean complete = false;
    try {
      try (InputStream in = new FileInputStream(source);
          OutputStream out = resolver.openOutputStream(uri, "w")) {
        if (out == null) throw new IOException("Cannot open Downloads file.");
        byte[] buffer = new byte[65536];
        long count = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
          if (cancel.get()) throw new java.util.concurrent.CancellationException();
          out.write(buffer, 0, n);
          count += n;
          progress.update(count, source.length());
        }
        out.flush();
      }
      if (cancel.get()) throw new java.util.concurrent.CancellationException();
      values.clear();
      values.put(MediaStore.Downloads.IS_PENDING, 0);
      if (resolver.update(uri, values, null, null) != 1)
        throw new IOException("Could not publish downloaded audio.");
      complete = true;
      return uri;
    } finally {
      if (!complete) resolver.delete(uri, null, null);
    }
  }

  private AudioDownloads() {}
}
