package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SpeechSettings {
  public final Map<String, String> dictionary = new LinkedHashMap<>();
  public boolean youtuber = true;
  public boolean durationGuard = true;
  public boolean questionLift = true;
  public boolean commaBuffer = true;
  public boolean femaleSoftening = false;
  public boolean carefulDiction = false;
  public boolean readDigits = false;
  public boolean warmthEq = false; // Explicit opt-in only; raw audio is default.

  public static SpeechSettings load(Context c) throws Exception {
    SpeechSettings s = new SpeechSettings();
    File file = new File(c.getFilesDir(), "pronunciation.json");
    if (!file.exists()) return s;
    JSONObject o = new JSONObject(FileIntegrity.read(file));
    s.youtuber = o.optBoolean("youtuber", true);
    s.durationGuard = o.optBoolean("durationGuard", true);
    s.questionLift = o.optBoolean("questionLift", true);
    s.commaBuffer = o.optBoolean("commaBuffer", true);
    s.femaleSoftening = o.optBoolean("femaleSoftening", false);
    s.carefulDiction = o.optBoolean("carefulDiction", false);
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
            .put("youtuber", youtuber)
            .put("durationGuard", durationGuard)
            .put("questionLift", questionLift)
            .put("commaBuffer", commaBuffer)
            .put("femaleSoftening", femaleSoftening)
            .put("carefulDiction", carefulDiction)
            .put("readDigits", readDigits)
            .put("warmthEq", warmthEq)
            .toString());
  }

  public List<NarrationDirector.Segment> audible(List<NarrationDirector.Segment> segments) {
    List<NarrationDirector.Segment> out = new ArrayList<>();
    for (NarrationDirector.Segment segment : segments)
      if (!spoken(segment.text).trim().isEmpty()) out.add(segment);
    if (out.isEmpty())
      throw new IllegalArgumentException("Add spoken words; this script contains formatting only.");
    return out;
  }

  public String spoken(String original) {
    return PronunciationLexicon.apply(original, dictionary, readDigits);
  }
}
