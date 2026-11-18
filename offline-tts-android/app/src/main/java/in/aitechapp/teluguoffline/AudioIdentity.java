package in.aitechapp.teluguoffline;

import java.io.*;
import org.json.JSONObject;

/** Identity belongs to the completed file, never the currently selected narrator. */
public final class AudioIdentity {
  public final String id, name, engine;

  private AudioIdentity(String id, String name, String engine) {
    this.id = id;
    this.name = name;
    this.engine = engine;
  }

  public static AudioIdentity read(File audio) {
    for (String file : new String[] {"render-identity.json", "metrics.json"})
      try {
        JSONObject value =
            new JSONObject(FileIntegrity.read(new File(audio.getParentFile(), file)));
        String id = value.optString("voice", "");
        if (value.optBoolean("cloned", false))
          return new AudioIdentity(
              id,
              value.optString("voice_name", "MyVoice") + " · Cloned",
              value.optString("engine", "OpenVoice V2"));
        for (ModelCatalog.Voice v : ModelCatalog.VOICES)
          if (v.id.equals(id))
            return new AudioIdentity(id, v.name, value.optString("engine", "Engine not recorded"));
      } catch (Exception ignored) {
      }
    return new AudioIdentity("", "Unknown voice", "Engine not recorded");
  }

  public String exportLabel() {
    return id.isEmpty() ? "Telugu-narration" : "Telugu-" + id;
  }
}
