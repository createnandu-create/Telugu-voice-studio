package in.aitechapp.teluguoffline;

import android.content.Context;
import android.media.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** App-private references and metadata. Never interpreted as trained/cloned voices. */
public final class VoiceProfiles {
  private final File root;

  public VoiceProfiles(Context context) {
    root = new File(context.getFilesDir(), "voice-profiles");
    root.mkdirs();
  }

  public File root() {
    return root;
  }

  public File audio(String id) {
    validateId(id);
    return new File(root, id + ".m4a");
  }

  private void validateId(String id) {
    if (id == null || !id.matches("[a-zA-Z0-9-]+"))
      throw new IllegalArgumentException("Invalid voice profile.");
  }

  public synchronized JSONObject get(String id) throws Exception {
    validateId(id);
    return new JSONObject(FileIntegrity.read(new File(root, id + ".json")));
  }

  public synchronized List<JSONObject> list() throws Exception {
    List<JSONObject> list = new ArrayList<>();
    File[] files =
        root.listFiles((d, n) -> n.endsWith(".json") && !n.equals("pending-recording.json"));
    if (files != null)
      for (File f : files)
        try {
          JSONObject p = new JSONObject(FileIntegrity.read(f));
          String id = p.getString("id");
          validateId(id);
          if (p.has("durationMs") && p.has("name") && audio(id).isFile()) list.add(p);
        } catch (Exception ignored) {
          /* A damaged sidecar never deletes the audio. */
        }
    list.sort((a, b) -> Long.compare(b.optLong("created"), a.optLong("created")));
    return list;
  }

  public synchronized JSONObject save(
      String id, String name, String label, String description, boolean interrupted)
      throws Exception {
    File file = audio(id);
    long duration = duration(file);
    if (duration <= 0)
      throw new IOException("Recording is incomplete or not decodable. Original bytes were kept.");
    JSONObject p =
        new JSONObject()
            .put("id", id)
            .put("name", clean(name))
            .put("label", clean(label))
            .put("description", description)
            .put("durationMs", duration)
            .put("created", System.currentTimeMillis())
            .put("interrupted", interrupted)
            .put(
                "status",
                interrupted
                    ? "Interrupted reference"
                    : duration < 15000
                        ? "Short reference · under 15 seconds"
                        : "Recorded reference only — cloning not installed");
    FileIntegrity.write(new File(root, id + ".json"), p.toString());
    return p;
  }

  public synchronized void rename(String id, String name, String label, String description)
      throws Exception {
    JSONObject p = get(id);
    p.put("name", clean(name)).put("label", clean(label)).put("description", description);
    FileIntegrity.write(new File(root, id + ".json"), p.toString());
  }

  private String clean(String s) {
    String v = s == null ? "" : s.trim();
    if (v.length() > 100) throw new IllegalArgumentException("Use at most 100 characters.");
    return v.isEmpty() ? "My voice" : v;
  }

  public synchronized void select(String id) throws Exception {
    get(id);
    FileIntegrity.write(new File(root, "selected.txt"), id);
  }

  public String selected() {
    try {
      return FileIntegrity.read(new File(root, "selected.txt"));
    } catch (IOException e) {
      return "";
    }
  }

  public synchronized void delete(String id) throws Exception {
    get(id);
    java.nio.file.Files.deleteIfExists(audio(id).toPath());
    java.nio.file.Files.deleteIfExists(new File(root, id + ".json").toPath());
    if (selected().equals(id)) new File(root, "selected.txt").delete();
  }

  public static long duration(File f) throws Exception {
    if (!f.isFile() || f.length() < 100) throw new IOException("Recording file is empty.");
    MediaMetadataRetriever r = new MediaMetadataRetriever();
    try {
      r.setDataSource(f.getPath());
      String s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      return s == null ? 0 : Long.parseLong(s);
    } finally {
      r.release();
    }
  }

  /** Non-destructive migration; the user's original file is always retained. */
  public synchronized void migrateLegacy(Context c) throws Exception {
    File old = new File(c.getFilesDir(), "my-voice-reference.m4a");
    if (old.isFile() && !new File(root, "legacy.json").exists()) {
      if (!audio("legacy").exists())
        java.nio.file.Files.copy(old.toPath(), audio("legacy").toPath());
      save("legacy", "Previous recording", "Custom", "Imported from version 0.3", false);
    }
  }

  /**
   * Lossless packet trim into a new profile, original kept. AAC boundaries are rounded to frames.
   */
  public synchronized String trim(String id, long fromMs, long toMs) throws Exception {
    long length = duration(audio(id));
    if (fromMs < 0 || toMs <= fromMs || toMs > length)
      throw new IllegalArgumentException("Choose a valid start/end within the recording.");
    String next = UUID.randomUUID().toString();
    MediaExtractor e = new MediaExtractor();
    MediaMuxer mux = null;
    try {
      e.setDataSource(audio(id).getPath());
      int track = -1;
      for (int i = 0; i < e.getTrackCount(); i++)
        if (e.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          track = i;
          break;
        }
      if (track < 0) throw new IOException("No audio track.");
      e.selectTrack(track);
      e.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
      mux = new MediaMuxer(audio(next).getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      int dest = mux.addTrack(e.getTrackFormat(track));
      mux.start();
      java.nio.ByteBuffer data = java.nio.ByteBuffer.allocate(1024 * 1024);
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long first = -1;
      while (e.getSampleTime() >= 0 && e.getSampleTime() < toMs * 1000) {
        int n = e.readSampleData(data, 0);
        if (n < 0) break;
        if (e.getSampleTime() >= fromMs * 1000) {
          if (first < 0) first = e.getSampleTime();
          int sampleFlags = e.getSampleFlags();
          if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0)
            throw new IOException("Encrypted audio cannot be trimmed.");
          int flags =
              (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                  ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                  : 0;
          if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0)
            flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
          info.set(0, n, e.getSampleTime() - first, flags);
          mux.writeSampleData(dest, data, info);
        }
        e.advance();
      }
      mux.stop();
      mux.release();
      mux = null;
      JSONObject p = get(id);
      save(
          next,
          p.getString("name") + " · trimmed",
          p.optString("label", "Custom"),
          "Trimmed copy; original retained",
          false);
      return next;
    } finally {
      e.release();
      if (mux != null)
        try {
          mux.release();
        } catch (Exception ignored) {
        }
    }
  }
}
