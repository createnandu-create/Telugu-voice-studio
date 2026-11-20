package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SpeechSettings {
  public final Map<String, String> dictionary = new LinkedHashMap<>();
  public boolean youtuber = true;
  public boolean durationGuard = false;
  public boolean questionLift = true;
  public boolean commaBuffer = true;
  public boolean femaleSoftening = false;
  public boolean synthesisCleanup = false;
  public boolean carefulDiction = true;
  public boolean explainTechnical = true;
  public boolean readDigits = false;
  public boolean warmthEq = false; // Explicit opt-in only; raw audio is default.

  public static SpeechSettings load(Context c) throws Exception {
    SpeechSettings s = new SpeechSettings();
    File file = new File(c.getFilesDir(), "pronunciation.json");
    if (!file.exists()) return s;
    return fromJson(new JSONObject(FileIntegrity.read(file)));
  }

  static SpeechSettings fromJson(JSONObject o) throws JSONException {
    SpeechSettings s = new SpeechSettings();
    s.youtuber = o.optBoolean("youtuber", true);
    s.durationGuard = false;
    s.questionLift = o.optBoolean("questionLift", true);
    s.commaBuffer = o.optBoolean("commaBuffer", true);
    s.femaleSoftening = o.optBoolean("femaleSoftening", false);
    s.synthesisCleanup = o.optBoolean("synthesisCleanup", false);
    s.carefulDiction = o.optBoolean("carefulDiction", true);
    s.explainTechnical = o.optBoolean("explainTechnical", true);
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
    FileIntegrity.write(new File(c.getFilesDir(), "pronunciation.json"), json().toString());
  }

  public JSONObject json() throws JSONException {
    JSONObject entries = new JSONObject();
    for (Map.Entry<String, String> e : dictionary.entrySet()) entries.put(e.getKey(), e.getValue());
    return new JSONObject()
            .put("entries", entries)
            .put("youtuber", youtuber)
            .put("durationGuard", durationGuard)
            .put("questionLift", questionLift)
            .put("commaBuffer", commaBuffer)
            .put("femaleSoftening", femaleSoftening)
            .put("synthesisCleanup", synthesisCleanup)
            .put("carefulDiction", carefulDiction)
            .put("explainTechnical", explainTechnical)
            .put("readDigits", readDigits)
            .put("warmthEq", warmthEq);
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
    return PronunciationLexicon.apply(original, dictionary, readDigits, explainTechnical);
  }
}
