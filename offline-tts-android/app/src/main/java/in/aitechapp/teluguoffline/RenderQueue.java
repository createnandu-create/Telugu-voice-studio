package in.aitechapp.teluguoffline;

import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;

/** Only the service main thread modifies admission order. Batch files hold the immutable plans. */
final class RenderQueue {
  private final SharedPreferences store;
  private final LinkedHashMap<String,String> jobs = new LinkedHashMap<>();

  RenderQueue(SharedPreferences store) {
    this.store = store;
    try {
      JSONArray saved = new JSONArray(store.getString("order", "[]"));
      for (int i=0; i<saved.length(); i++) {
        JSONArray row=saved.getJSONArray(i);
        jobs.put(row.getString(0),row.getString(1));
      }
    } catch (JSONException e) { throw new IllegalStateException("Cannot read the render queue", e); }
  }

  String first() { return jobs.isEmpty() ? null : jobs.keySet().iterator().next(); }

  void add(String id, String name) {
    UUID.fromString(id);
    if (!jobs.containsKey(id)) {
      jobs.put(id, name == null || name.isEmpty() ? "Narration" : name);
      save();
    }
  }

  void finish(String id) { if (jobs.remove(id) != null) save(); }

  String summary() {
    StringBuilder text=new StringBuilder();
    for (String name : jobs.values()) {
      if (text.length()==0) text.append("Rendering: ");
      else text.append(" · Queued: ");
      text.append(name);
    }
    return text.toString();
  }

  private void save() {
    JSONArray rows = new JSONArray();
    for (Map.Entry<String,String> entry : jobs.entrySet())
      rows.put(new JSONArray().put(entry.getKey()).put(entry.getValue()));
    if (!store.edit().putString("order",rows.toString()).commit())
      throw new IllegalStateException("Cannot save render queue");
  }
}
