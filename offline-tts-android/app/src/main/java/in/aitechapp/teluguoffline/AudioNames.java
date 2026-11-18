package in.aitechapp.teluguoffline;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

/** One persisted display stem shared by library rows and device export filenames. */
public final class AudioNames {
  public static String safe(String value) {
    String s =
        value == null
            ? ""
            : value.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", " ").trim().replaceAll("\\s+", "_");
    return s.isEmpty() ? "MyVoice" : s.substring(0, Math.min(s.length(), 100));
  }

  public static String stem(String voice, String role, long created) {
    return safe(voice)
        + (role == null || role.isEmpty() ? "" : "_" + safe(role))
        + "_"
        + new SimpleDateFormat("ddMMMyyyy_hhmma", Locale.ENGLISH).format(new Date(created));
  }

  public static synchronized String project(File folder, String voice) throws IOException {
    File named = new File(folder, "project-name.txt");
    if (named.isFile()) {
      String s = FileIntegrity.read(named).trim();
      if (!s.isEmpty() && !s.matches("Narration · [a-f0-9]+")) return s;
    }
    File created = new File(folder, "created-at.txt");
    long when;
    if (created.isFile())
      try {
        when = Long.parseLong(FileIntegrity.read(created));
      } catch (NumberFormatException e) {
        when = folder.lastModified();
      }
    else {
      File audio = new File(folder, "narration.wav");
      when = audio.isFile() ? audio.lastModified() : System.currentTimeMillis();
      FileIntegrity.write(created, Long.toString(when));
    }
    String role = "";
    try {
      JSONObject identity =
          new JSONObject(FileIntegrity.read(new File(folder, "render-identity.json")));
      voice = identity.optString("voice_name", voice);
      role = identity.optBoolean("cloned", false) ? "Cloned" : "";
    } catch (Exception ignored) {
    }
    String name = stem(voice, role, when);
    FileIntegrity.write(named, name);
    return name;
  }

  public static String forAudio(File file) {
    File folder = file.getParentFile();
    if (new File(folder, "plan.json").isFile() || new File(folder, "render-identity.json").isFile())
      try {
        return safe(project(folder, AudioIdentity.read(file).name));
      } catch (IOException ignored) {
      }
    File[] metadata = folder.listFiles((d, n) -> n.endsWith(".json"));
    if (metadata != null)
      for (File f : metadata)
        try {
          JSONObject p = new JSONObject(FileIntegrity.read(f));
          String id = p.optString("id");
          if (file.getName().equals(id + ".wav")
              || file.getName().equals(id + ".m4a")
              || file.getName().equals(p.optString("activeAudio")))
            return p.optString(
                "displayStem",
                stem(
                    p.optString("name", "MyVoice"),
                    p.optInt("cleanCount", 0) > 0 ? "Cleaned" : "",
                    p.optLong("created", file.lastModified())));
        } catch (Exception ignored) {
        }
    return stem("Recording", "", file.lastModified());
  }

  private AudioNames() {}
}
