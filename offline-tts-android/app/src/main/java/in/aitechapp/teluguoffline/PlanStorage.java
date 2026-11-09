package in.aitechapp.teluguoffline;

import java.util.*;
import org.json.*;

public final class PlanStorage {
  public final String script;
  public final int voice;
  public final List<NarrationDirector.Segment> segments;

  public PlanStorage(String script, int voice, List<NarrationDirector.Segment> segments) {
    if (voice < 0 || voice >= ModelCatalog.VOICES.length)
      throw new IllegalArgumentException("Unknown voice.");
    DirectionContract.validate(script, segments);
    this.script = script;
    this.voice = voice;
    this.segments = new ArrayList<>(segments);
  }

  public String json() throws JSONException {
    JSONArray rows = new JSONArray();
    for (NarrationDirector.Segment s : segments)
      rows.put(
          new JSONObject()
              .put("text", s.text)
              .put("emotion", s.emotion.name())
              .put("rate", s.rate)
              .put("pitch", s.pitch)
              .put("pause", s.pauseAfterMs));
    return new JSONObject()
        .put("schema", 1)
        .put("script", script)
        .put("voice", voice)
        .put("segments", rows)
        .toString();
  }

  public static PlanStorage parse(String data) throws JSONException {
    JSONObject o = new JSONObject(data);
    if (o.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported saved plan.");
    JSONArray rows = o.getJSONArray("segments");
    List<NarrationDirector.Segment> segments = new ArrayList<>();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject s = rows.getJSONObject(i);
      segments.add(
          new NarrationDirector.Segment(
              s.getString("text"),
              NarrationDirector.Emotion.valueOf(s.getString("emotion")),
              (float) s.getDouble("rate"),
              (float) s.getDouble("pitch"),
              s.getInt("pause")));
    }
    return new PlanStorage(o.getString("script"), o.getInt("voice"), segments);
  }
}
