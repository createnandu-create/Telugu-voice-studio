package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SpeechSettings {
  public final Map<String, String> dictionary = new LinkedHashMap<>();
  public boolean readDigits = false;
  public boolean warmthEq = false; // Explicit opt-in only; raw audio is default.

  public static SpeechSettings load(Context c) throws Exception {
    SpeechSettings s = new SpeechSettings();
    File file = new File(c.getFilesDir(), "pronunciation.json");
    if (!file.exists()) return s;
    JSONObject o = new JSONObject(FileIntegrity.read(file));
    s.readDigits = o.optBoolean("readDigits", false);
    s.warmthEq = o.optBoolean("warmthEq", false);
    JSONObject entries = o.getJSONObject("entries");
    Iterator<String> it = entries.keys();
    while (it.hasNext()) {
      String key = it.next();
      s.dictionary.put(key, entries.getString(key));
    }
    return s;
  }

  public void save(Context c) throws Exception {
    JSONObject entries = new JSONObject();
    for (Map.Entry<String, String> e : dictionary.entrySet()) entries.put(e.getKey(), e.getValue());
    FileIntegrity.write(
        new File(c.getFilesDir(), "pronunciation.json"),
        new JSONObject()
            .put("entries", entries)
            .put("readDigits", readDigits)
            .put("warmthEq", warmthEq)
            .toString());
  }

  public String spoken(String original) {
    return PronunciationLexicon.apply(original, dictionary, readDigits);
  }
}
