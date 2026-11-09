package in.aitechapp.teluguoffline;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native, English-first studio. Model capabilities are visible at the point of use. */
public final class StudioActivity extends Activity {
  private static final int INK = 0xff20251f,
      MUTED = 0xff657063,
      YELLOW = 0xffffd33d,
      RED = 0xffbe482d,
      PAPER = 0xfffafbf7;
  private LinearLayout body, root;
  private TextView live;
  private EditText editor;
  private int tab = 0, step = 0, voice = 0;
  private String draft =
      "నమస్కారం ఫ్రెండ్స్! ఇప్పుడు ఇక్కడ క్లిక్ చేయండి. ఇది చాలా ముఖ్యమైన విషయం. జాగ్రత్తగా"
          + " వినండి.";
  private List<NarrationDirector.Segment> plan = new ArrayList<>();
  private String plannedScript = "";
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private MediaPlayer player;
  private MediaRecorder recorder;
  private boolean recording = false;
  private File reference;
  private boolean lastBusy;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          boolean running = StudioJobService.busy;
          if (lastBusy && !running && (tab == 2 || (tab == 0 && step == 3))) show();
          lastBusy = running;
          if (live != null) live.setText(StudioJobService.status);
          ui.postDelayed(this, 700);
        }
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    restore();
    show();
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(20));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private TextView text(String s, int size, int color) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setPadding(0, dp(5), 0, dp(7));
    v.setLineSpacing(dp(2), 1);
    return v;
  }

  private void title(String s, String sub) {
    TextView h = text(s, 30, INK);
    h.setTypeface(Typeface.create("serif", Typeface.BOLD));
    body.addView(h);
    body.addView(text(sub, 14, MUTED));
  }

  private Button button(LinearLayout parent, String label, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(INK);
    b.setTextSize(15);
    b.setMinHeight(dp(50));
    b.setBackground(shape(primary ? YELLOW : Color.WHITE, primary ? 0 : 0xffdce1d7));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(6), 0, dp(6));
    parent.addView(b, lp);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private LinearLayout card() {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(16), dp(12), dp(16), dp(12));
    c.setBackground(shape(Color.WHITE, 0xffe2e6dc));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(12), 0, dp(8));
    body.addView(c, lp);
    return c;
  }

  private void capture() {
    if (editor != null) {
      draft = editor.getText().toString();
    }
  }

  private void show() {
    capture();
    editor = null;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(PAPER);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets b =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
          } else
            v.setPadding(
                0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
          return insets;
        });
    TextView brand = text("  ●  TELUGU VOICE STUDIO", 16, INK);
    brand.setTypeface(null, Typeface.BOLD);
    brand.setPadding(dp(18), dp(14), dp(18), dp(12));
    root.addView(brand);
    live = text(StudioJobService.status, 12, MUTED);
    live.setPadding(dp(22), 0, dp(22), dp(8));
    live.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    root.addView(live);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(22), dp(12), dp(22), dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (tab == 0) studio();
    else if (tab == 1) voices();
    else if (tab == 2) models();
    else settings();
    LinearLayout nav = new LinearLayout(this);
    nav.setPadding(dp(8), dp(6), dp(8), dp(6));
    nav.setBackgroundColor(Color.WHITE);
    String[] names = {"Studio", "Voices", "Models", "Settings"};
    for (int i = 0; i < names.length; i++) {
      final int target = i;
      Button b = new Button(this);
      b.setText(names[i]);
      b.setTextSize(12);
      b.setAllCaps(false);
      b.setTextColor(tab == i ? INK : MUTED);
      b.setBackground(shape(tab == i ? YELLOW : Color.WHITE, 0));
      nav.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
      b.setOnClickListener(
          v -> {
            capture();
            tab = target;
            show();
          });
    }
    root.addView(nav);
    setContentView(root);
    root.requestApplyInsets();
  }

  private boolean available() {
    if (StudioJobService.busy) {
      toast("A job is running. Cancel or wait before changing the render.");
      return false;
    }
    return true;
  }

  private void studio() {
    String[] names = {"Your script", "Choose a voice", "Direct the delivery", "Your narration"};
    body.addView(text("STEP " + (step + 1) + " / 4  ·  TELUGU  ·  OFFLINE", 12, RED));
    title(
        names[step],
        step == 0
            ? "Paste your complete script. Your factual words stay intact."
            : step == 1
                ? "Two downloadable Telugu neural voices."
                : step == 2
                    ? "Review pacing before you render."
                    : "Preview and export the latest completed narration. A new result replaces it"
                        + " only after a successful render.");
    if (step == 0) {
      editor = new EditText(this);
      editor.setText(draft);
      editor.setTextColor(INK);
      editor.setTextSize(17);
      editor.setGravity(Gravity.TOP);
      editor.setMinLines(8);
      editor.setMaxLines(18);
      editor.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
              | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
      editor.setHint("Write or paste your Telugu script…");
      editor.setBackground(shape(Color.WHITE, 0xffdce1d7));
      editor.setPadding(dp(16), dp(16), dp(16), dp(16));
      body.addView(editor, new LinearLayout.LayoutParams(-1, -2));
      body.addView(
          text(
              "Up to 60,000 characters · no subscription or daily quota. Telugu language is fixed"
                  + " for these models.",
              12,
              MUTED));
      button(
          body,
          "Next · Choose voice →",
          true,
          () -> {
            capture();
            if (draft.trim().isEmpty()) {
              toast("Paste a script first.");
              show();
              return;
            }
            step = 1;
            saveDraft();
            show();
          });
    } else if (step == 1) {
      voiceChoices();
      button(
          body,
          "Next · Expression director →",
          true,
          () -> {
            if (!available()) return;
            ensurePlan();
            step = 2;
            show();
          });
      back(0);
    } else if (step == 2) {
      ensurePlan();
      body.addView(
          text(
              "Heuristic suggestions · no semantic LLM loaded. Piper applies rate and pauses only."
                  + " Emotion labels are editing notes; pitch, emphasis and energy need an"
                  + " expressive model.",
              14,
              MUTED));
      LinearLayout controls = card();
      controls.addView(
          text(plan.size() + " segments · " + ModelCatalog.VOICES[voice].name, 17, INK));
      button(controls, "Edit a sentence", false, () -> pickSegment(0));
      button(
          controls,
          "Use neutral delivery for all",
          false,
          () -> {
            if (!available()) return;
            List<NarrationDirector.Segment> neutral = new ArrayList<>();
            for (NarrationDirector.Segment s : plan)
              neutral.add(
                  new NarrationDirector.Segment(
                      s.text, NarrationDirector.Emotion.NEUTRAL, 1, 1, 220));
            plan = neutral;
            saveDraft();
            show();
          });
      for (int i = 0; i < Math.min(plan.size(), 4); i++) {
        final int n = i;
        NarrationDirector.Segment s = plan.get(i);
        LinearLayout c = card();
        c.addView(text((i + 1) + " · " + s.label() + " · " + s.rate + "×", 12, RED));
        c.addView(text(s.text, 16, INK));
        button(c, "Edit rate / pause", false, () -> editSegment(n));
      }
      if (plan.size() > 4)
        body.addView(
            text("Use ‘Edit a sentence’ to review all " + plan.size() + " segments.", 13, MUTED));
      button(body, "Generate / Resume →", true, this::generate);
      back(1);
    } else {
      LinearLayout c = card();
      c.addView(text(StudioJobService.busy ? "Rendering on your phone" : "Local audio", 20, INK));
      c.addView(
          text(
              "Progress appears above. Rendering can continue with the screen off. If interrupted,"
                  + " keep the same script, voice and controls and tap Generate / Resume.",
              14,
              MUTED));
      button(
          c,
          "Cancel current job",
          false,
          () -> {
            if (StudioJobService.busy) StudioJobService.cancel();
          });
      button(body, "Generate / Resume", true, this::generate);
      button(body, "Play / Pause narration", false, this::play);
      button(body, "Export WAV", true, this::export);
      button(body, "View render measurements", false, this::metrics);
      back(2);
    }
  }

  private void back(int target) {
    button(
        body,
        "← Back",
        false,
        () -> {
          step = target;
          show();
        });
  }

  private void voiceChoices() {
    for (int i = 0; i < ModelCatalog.VOICES.length; i++) {
      final int index = i;
      ModelCatalog.Voice v = ModelCatalog.VOICES[i];
      Button b =
          button(
              body,
              (voice == i ? "✓  " : "") + v.name + "  ·  " + v.gender,
              voice == i,
              () -> {
                if (!available()) return;
                voice = index;
                saveDraft();
                show();
              });
      b.setTextColor(i == 0 ? 0xff264f67 : RED);
    }
    body.addView(
        text(
            "Piper · 22.05 kHz · CC BY 4.0\n"
                + "Basic neural narration; these voices do not clone your voice or reproduce human"
                + " emotion.",
            13,
            MUTED));
    button(
        body,
        "Install or remove voices",
        false,
        () -> {
          tab = 2;
          show();
        });
  }

  private void voices() {
    title("Voice library", "One narrator per render keeps speaker identity stable.");
    voiceChoices();
    LinearLayout c = card();
    c.addView(text("Your voice · reference recording", 19, INK));
    c.addView(
        text(
            "Save your own reference locally. Cloning is not available in this build; recording"
                + " alone does not train a model.",
            14,
            MUTED));
    reference = new File(getFilesDir(), "my-voice-reference.m4a");
    c.addView(
        text(
            reference.exists()
                ? "Reference saved · " + (reference.length() / 1024) + " KB"
                : "No reference recorded",
            12,
            MUTED));
    button(
        c,
        recording ? "Stop recording" : "Record my own voice",
        false,
        () -> {
          if (recording) {
            stopRecording();
            show();
          } else consent();
        });
    button(
        c,
        "Delete reference",
        false,
        () -> {
          if (recording) {
            toast("Stop recording first.");
            return;
          }
          new AlertDialog.Builder(this)
              .setMessage("Delete the local reference recording?")
              .setNegativeButton("Keep", null)
              .setPositiveButton(
                  "Delete",
                  (d, w) -> {
                    reference.delete();
                    show();
                  })
              .show();
        });
  }

  private void models() {
    title("Offline models", "Download once. Generate without Wi-Fi or mobile data.");
    body.addView(
        text(
            "Shared phonemes and the ARM64 runtime are included. Each voice downloads 63.5 MB and"
                + " is verified before use.",
            14,
            MUTED));
    VoiceModelStore store = new VoiceModelStore(this);
    for (int i = 0; i < ModelCatalog.VOICES.length; i++) {
      final int index = i;
      ModelCatalog.Voice v = ModelCatalog.VOICES[i];
      LinearLayout c = card();
      c.addView(text(v.name + " · " + v.gender, 20, INK));
      TextView state = text("Checking installation…", 13, MUTED);
      c.addView(state);
      io.execute(
          () -> {
            String s;
            try {
              s =
                  store.verified(v)
                      ? "Verified · ready offline · 63.5 MB"
                      : "Not installed or damaged";
            } catch (Exception e) {
              s = "Could not verify local files";
            }
            final String status = s;
            ui.post(() -> state.setText(status));
          });
      button(
          c,
          "Download / Repair",
          true,
          () -> {
            if (!available()) return;
            startJob(
                new Intent(this, StudioJobService.class)
                    .setAction("install")
                    .putExtra("voice", index));
          });
      button(
          c,
          "Remove voice",
          false,
          () -> {
            if (!available()) return;
            new AlertDialog.Builder(this)
                .setMessage("Remove " + v.name + "? Your exported audio is kept.")
                .setNegativeButton("Keep", null)
                .setPositiveButton(
                    "Remove",
                    (d, w) -> {
                      try {
                        store.remove(v);
                        show();
                      } catch (Exception e) {
                        error(e);
                      }
                    })
                .show();
          });
    }
    button(
        body,
        "Cancel download",
        false,
        () -> {
          if (StudioJobService.busy) StudioJobService.cancel();
        });
    body.addView(
        text(
            "CC BY 4.0 · PravalX / AI4Bharat IndicVoices-R. Model revision "
                + ModelCatalog.REVISION.substring(0, 12)
                + ". Downloads are from Hugging Face file storage; inference never uses a server.",
            12,
            MUTED));
  }

  private void settings() {
    title("Settings", "Simple controls. Honest capabilities.");
    LinearLayout c = card();
    c.addView(text("On this device", 18, INK));
    c.addView(
        text(
            "English interface · Telugu speech\nARM64 CPU · 2 inference threads\nFree storage: "
                + (getFilesDir().getUsableSpace() / 1_000_000)
                + " MB\n"
                + "Piper: installed separately\n"
                + "Semantic LLM: not integrated\n"
                + "Expressive cloning: not integrated",
            14,
            MUTED));
    button(
        body,
        "Open system TTS fallback",
        false,
        () -> {
          if (available()) startActivity(new Intent(this, MainActivity.class));
        });
    button(
        body,
        "Clear render cache",
        false,
        () -> {
          if (!available()) return;
          new AlertDialog.Builder(this)
              .setMessage(
                  "Delete local chunks and previews? Exported WAV files remain. Resume will no"
                      + " longer be possible.")
              .setNegativeButton("Keep", null)
              .setPositiveButton(
                  "Clear",
                  (d, w) -> {
                    releasePlayer();
                    io.execute(
                        () -> {
                          deleteTree(new File(getFilesDir(), "renders"));
                          new File(getFilesDir(), "last-result.txt").delete();
                          StudioJobService.result = null;
                          ui.post(
                              () -> {
                                toast("Render cache cleared");
                                show();
                              });
                        });
                  })
              .show();
        });
    button(
        body,
        "Licenses & source",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Open source credits")
                .setMessage(
                    "Studio: GPL-3.0-or-later. sherpa-onnx 1.12.26: Apache-2.0. ONNX Runtime: MIT."
                        + " eSpeak NG: GPL-3.0-or-later. Telugu voice weights: CC BY 4.0, PravalX /"
                        + " AI4Bharat IndicVoices-R. Full notices and corresponding-source"
                        + " references are in the source ZIP and APK assets/licenses.")
                .setPositiveButton(
                    "Source repository",
                    (d, w) ->
                        startActivity(
                            new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/createnandu-create/Telugu-voice-studio/tree/telugu-voice-testing"))))
                .setNegativeButton("Close", null)
                .show());
    body.addView(
        text(
            "Quality remains under evaluation. No 90% naturalness, emotion, pronunciation or phone"
                + " speed score has been established. Battery temperature and Android thermal"
                + " status are recorded separately from CPU temperature.",
            13, MUTED));
  }

  private void ensurePlan() {
    if (!plannedScript.equals(draft) || plan.isEmpty()) {
      plan = NarrationDirector.planExact(draft, NarrationDirector.Style.AUTO);
      plannedScript = draft;
    }
  }

  private void pickSegment(int page) {
    if (plan.isEmpty()) return;
    int start = page * 40, end = Math.min(start + 40, plan.size());
    String[] items = new String[end - start + (end < plan.size() ? 1 : 0)];
    for (int i = start; i < end; i++) {
      String s = plan.get(i).text;
      items[i - start] = (i + 1) + " · " + s.substring(0, Math.min(s.length(), 60));
    }
    if (end < plan.size()) items[items.length - 1] = "Next 40 sentences →";
    new AlertDialog.Builder(this)
        .setTitle("Select a sentence")
        .setItems(
            items,
            (d, w) -> {
              if (start + w == end) pickSegment(page + 1);
              else editSegment(start + w);
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void editSegment(int index) {
    if (!available()) return;
    NarrationDirector.Segment s = plan.get(index);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20), dp(8), dp(20), dp(8));
    box.addView(text(s.text, 16, INK));
    Spinner emotion = new Spinner(this);
    emotion.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            NarrationDirector.Emotion.values()));
    emotion.setSelection(s.emotion.ordinal());
    box.addView(text("Direction note (Piper does not synthesize emotions)", 12, MUTED));
    box.addView(emotion);
    EditText rate = new EditText(this);
    rate.setInputType(8194);
    rate.setText(String.valueOf(s.rate));
    box.addView(text("Speaking rate · 0.75–1.25", 13, MUTED));
    box.addView(rate);
    EditText pause = new EditText(this);
    pause.setInputType(2);
    pause.setText(String.valueOf(s.pauseAfterMs));
    box.addView(text("Pause after sentence · 0–2000 ms", 13, MUTED));
    box.addView(pause);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Sentence " + (index + 1))
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        List<NarrationDirector.Segment> copy = new ArrayList<>(plan);
                        copy.set(
                            index,
                            new NarrationDirector.Segment(
                                s.text,
                                (NarrationDirector.Emotion) emotion.getSelectedItem(),
                                Float.parseFloat(rate.getText().toString()),
                                s.pitch,
                                Integer.parseInt(pause.getText().toString())));
                        DirectionContract.validate(draft, copy);
                        plan = copy;
                        saveDraft();
                        dialog.dismiss();
                        show();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
  }

  private void generate() {
    capture();
    if (!available()) return;
    try {
      if (draft.length() > 60000)
        throw new IllegalArgumentException(
            "This build supports up to 60,000 characters per render. Your full script is preserved;"
                + " split it into parts.");
      ensurePlan();
      PlanStorage p = new PlanStorage(draft, voice, plan);
      FileIntegrity.write(new File(getFilesDir(), "render-plan.json"), p.json());
      saveDraft();
      step = 3;
      tab = 0;
      startJob(new Intent(this, StudioJobService.class).setAction("render"));
      show();
    } catch (Exception e) {
      error(e);
    }
  }

  private void startJob(Intent intent) {
    if (recording) {
      toast("Stop recording before starting a job.");
      return;
    }
    releasePlayer();
    try {
      StudioJobService.busy = true;
      lastBusy = true;
      StudioJobService.status = "Starting…";
      startForegroundService(intent);
    } catch (Exception e) {
      StudioJobService.busy = false;
      error(e);
    }
  }

  private File result() throws IOException {
    File f = StudioJobService.result;
    if (f == null) {
      File saved = new File(getFilesDir(), "last-result.txt");
      if (saved.isFile()) f = new File(FileIntegrity.read(saved));
    }
    if (f == null || !f.isFile())
      throw new IOException("No completed narration yet. Generate first.");
    if (!f.getCanonicalPath()
        .startsWith(new File(getFilesDir(), "renders").getCanonicalPath() + File.separator))
      throw new IOException("Invalid result path.");
    return f;
  }

  private void play() {
    try {
      if (player != null) {
        if (player.isPlaying()) player.pause();
        else player.start();
        return;
      }
      File f = result();
      player = new MediaPlayer();
      player.setDataSource(f.getPath());
      player.prepare();
      player.setOnCompletionListener(p -> releasePlayer());
      player.start();
      toast("Playing latest completed narration");
    } catch (Exception e) {
      releasePlayer();
      error(e);
    }
  }

  private void releasePlayer() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

  private void export() {
    try {
      FileIntegrity.write(
          new File(getFilesDir(), "pending-export.txt"), result().getCanonicalPath());
      Intent i =
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .setType("audio/wav")
              .addCategory(Intent.CATEGORY_OPENABLE)
              .putExtra(
                  Intent.EXTRA_TITLE, "Telugu-narration-" + System.currentTimeMillis() + ".wav");
      startActivityForResult(i, 501);
    } catch (Exception e) {
      error(e);
    }
  }

  @Override
  protected void onActivityResult(int request, int code, Intent data) {
    super.onActivityResult(request, code, data);
    if (request == 501 && code == RESULT_OK && data != null && data.getData() != null) {
      Uri uri = data.getData();
      try {
        File f = new File(FileIntegrity.read(new File(getFilesDir(), "pending-export.txt")));
        if (!f.getCanonicalPath()
                .startsWith(new File(getFilesDir(), "renders").getCanonicalPath() + File.separator)
            || !f.isFile())
          throw new IOException("The selected audio is no longer available. Export again.");
        io.execute(
            () -> {
              try (InputStream in = new FileInputStream(f);
                  OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Cannot open destination.");
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                ui.post(() -> toast("WAV exported"));
              } catch (Exception e) {
                ui.post(() -> error(e));
              }
            });
      } catch (Exception e) {
        error(e);
      }
    }
  }

  private void metrics() {
    try {
      File f = new File(result().getParentFile(), "metrics.json");
      new AlertDialog.Builder(this)
          .setTitle("Actual render measurements")
          .setMessage(FileIntegrity.read(f))
          .setPositiveButton("Close", null)
          .show();
    } catch (Exception e) {
      error(e);
    }
  }

  private void consent() {
    if (!available()) return;
    new AlertDialog.Builder(this)
        .setTitle("Record your voice")
        .setMessage(
            "Record only your own voice or a speaker who explicitly consents. The recording stays"
                + " on this phone. Maximum 60 seconds. This build cannot use it for cloning.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "I have consent · Record",
            (d, w) -> {
              if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                  != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 601);
              else record();
            })
        .show();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] p, int[] results) {
    super.onRequestPermissionsResult(request, p, results);
    if (request == 601 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
      record();
    else if (request == 601) toast("Microphone access is needed to record your reference.");
  }

  private void record() {
    try {
      reference = new File(getFilesDir(), "my-voice-reference.m4a");
      recorder = new MediaRecorder();
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      recorder.setAudioSamplingRate(44100);
      recorder.setAudioEncodingBitRate(128000);
      recorder.setOutputFile(reference.getPath());
      recorder.setMaxDuration(60000);
      recorder.setOnInfoListener(
          (r, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
              stopRecording();
              show();
            }
          });
      recorder.prepare();
      recorder.start();
      recording = true;
      show();
    } catch (Exception e) {
      stopRecording();
      error(e);
    }
  }

  private void stopRecording() {
    if (recorder != null) {
      try {
        recorder.stop();
      } catch (RuntimeException e) {
        if (reference != null) reference.delete();
      }
      recorder.release();
      recorder = null;
    }
    recording = false;
  }

  private void saveDraft() {
    try {
      getPreferences(0).edit().putString("script", draft).putInt("voice", voice).apply();
      if (plannedScript.equals(draft) && !plan.isEmpty())
        FileIntegrity.write(
            new File(getFilesDir(), "draft-plan.json"), new PlanStorage(draft, voice, plan).json());
    } catch (Exception e) {
      toast("Could not save draft: " + e.getMessage());
    }
  }

  private void restore() {
    draft = getPreferences(0).getString("script", draft);
    voice = getPreferences(0).getInt("voice", 0);
    try {
      PlanStorage p =
          PlanStorage.parse(FileIntegrity.read(new File(getFilesDir(), "draft-plan.json")));
      if (p.script.equals(draft)) {
        plan = p.segments;
        plannedScript = draft;
      }
    } catch (Exception ignored) {
      /* Missing or incompatible draft: regenerate from unchanged script. */
    }
  }

  private void deleteTree(File f) {
    File[] children = f.listFiles();
    if (children != null) for (File c : children) deleteTree(c);
    f.delete();
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void error(Exception e) {
    new AlertDialog.Builder(this)
        .setTitle("Please check")
        .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  public void onBackPressed() {
    capture();
    if (tab != 0) {
      tab = 0;
      show();
    } else if (step > 0) {
      step--;
      show();
    } else super.onBackPressed();
  }

  @Override
  protected void onResume() {
    super.onResume();
    ui.post(ticker);
  }

  @Override
  protected void onPause() {
    capture();
    saveDraft();
    ui.removeCallbacks(ticker);
    if (recording) stopRecording();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    releasePlayer();
    stopRecording();
    io.shutdown();
    super.onDestroy();
  }
}
