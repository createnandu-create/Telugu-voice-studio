package in.aitechapp.teluguoffline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * A single foreground worker owns the native engine. Cancel never frees a model during JNI
 * inference.
 */
public final class StudioJobService extends Service {
  public static volatile boolean busy = false;
  public static volatile int downloadingVoice = -1;
  public static volatile boolean enhancing = false;
  public static volatile String runId = "", resultRunId = "";
  public static volatile String status = "Ready · all generation is local";
  public static volatile File result;
  public static final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private PowerManager.WakeLock wake;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public IBinder onBind(Intent i) {
    return null;
  }

  public static void cancel() {
    cancelled.set(true);
    status = enhancing ? "Stopping local processing…" : "Stopping after the current sentence…";
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (running.getAndSet(true)) return START_NOT_STICKY;
    busy = true;
    cancelled.set(false);
    status = "Preparing…";
    runId = intent.getStringExtra("runId");
    if (runId == null) runId = "";
    boolean cloneInstall = "clone-install".equals(intent.getAction());
    boolean cloneCreate = "clone-create".equals(intent.getAction());
    boolean speakerInstall = "speaker-install".equals(intent.getAction());
    boolean enroll = "speaker-enroll".equals(intent.getAction());
    boolean install = "install".equals(intent.getAction()) || speakerInstall || cloneInstall;
    enhancing =
        "enhance".equals(intent.getAction())
            || enroll
            || speakerInstall
            || cloneCreate
            || cloneInstall;
    downloadingVoice =
        install && !speakerInstall && !cloneInstall ? intent.getIntExtra("voice", 0) : -1;
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "render", "Voice studio progress", NotificationManager.IMPORTANCE_LOW));
    Notification notification =
        new Notification.Builder(this, "render")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Telugu Voice Studio")
            .setContentText(
                install ? "Installing offline voice" : "Rendering locally · tap to view progress")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(this, StudioActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .build();
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(
          41,
          notification,
          install || Build.VERSION.SDK_INT < 35
              ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
              : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
    else startForeground(41, notification);
    wake =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeluguVoice:Render");
    wake.acquire(2 * 60 * 60 * 1000L);
    worker.execute(
        () -> {
          try {
            VoiceModelStore store = new VoiceModelStore(this);
            if (cloneInstall) {
              new OpenVoiceModelStore(getFilesDir())
                  .install(cancelled, message -> status = message);
              status = "Completed · OpenVoice ready. Create a consented voice preview in Voices.";
            } else if (cloneCreate) {
              if (!intent.getBooleanExtra("consent", false))
                throw new IOException("Speaker consent is required");
              CloneProfiles.create(
                  this,
                  intent.getStringExtra("profile"),
                  intent.getIntExtra("voice", 0),
                  cancelled,
                  message -> status = message);
              status =
                  "Completed · local voice preview created. Listen in Voices before selecting it.";
            } else if (speakerInstall) {
              new SpeakerModelStore(getFilesDir()).install(cancelled, message -> status = message);
              status =
                  "Completed · speaker encoder READY OFFLINE. Enroll a consented reference in"
                      + " Voices.";
            } else if (enroll) {
              if (!intent.getBooleanExtra("consent", false))
                throw new IOException("Confirm speaker consent before enrollment.");
              SpeakerEnrollment.run(
                  this, intent.getStringExtra("profile"), cancelled, message -> status = message);
              status =
                  "Completed · 192-D speaker fingerprint saved privately. Cloning remains"
                      + " unavailable.";
            } else if (install) {
              int voice = intent.getIntExtra("voice", 0);
              store.install(ModelCatalog.VOICES[voice], cancelled, message -> status = message);
              status = ModelCatalog.VOICES[voice].name + " installed · ready offline";
            } else if (enhancing) {
              ReferenceEnhancement.run(
                  this,
                  intent.getStringExtra("profile"),
                  intent.getBooleanExtra("roomTail", false),
                  cancelled,
                  message -> status = message);
              status = "Completed · cleaned recording saved in Voices. Original kept.";
            } else renderBatch(store);
          } catch (CancellationException e) {
            status =
                speakerInstall
                    ? "Download paused · partial bytes kept. Tap Download speaker encoder to"
                        + " resume."
                    : enhancing
                        ? "Cleanup stopped · original kept. Tap Enhance or Enroll to retry."
                        : install
                            ? "Download paused · partial bytes saved. Tap Resume download in"
                                + " Settings."
                            : "Stopped · completed chunks saved. Tap Generate / Resume to"
                                + " continue.";
          } catch (Exception | LinkageError e) {
            android.util.Log.e("StudioJob", "Offline job failed", e);
            status =
                cancelled.get()
                    ? "Cleanup stopped · original kept. Retry when ready."
                    : JobFailure.message(e);
          } finally {
            new Handler(Looper.getMainLooper())
                .post(
                    () -> {
                      if (wake != null && wake.isHeld()) wake.release();
                      running.set(false);
                      busy = false;
                      downloadingVoice = -1;
                      enhancing = false;
                      stopForeground(STOP_FOREGROUND_REMOVE);
                      stopSelf(startId);
                    });
          }
        });
    return "render".equals(intent.getAction()) ? START_REDELIVER_INTENT : START_NOT_STICKY;
  }

  private void renderBatch(VoiceModelStore store) throws Exception {
    UUID.fromString(runId);
    File state = new File(getFilesDir(), "batch-" + runId + ".json");
    if (!state.isFile()) { status = "Queue finished"; return; }
    JSONObject batch = new JSONObject(FileIntegrity.read(state));
    org.json.JSONArray plans = batch.getJSONArray("plans");
    SpeechSettings speech = SpeechSettings.fromJson(batch.getJSONObject("speech"));
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel("completed", "Saved audio",
        NotificationManager.IMPORTANCE_DEFAULT));
    try {
      for (int n = batch.getInt("next"); n < plans.length(); n++) {
        check();
        PlanStorage plan = PlanStorage.parse(plans.getJSONObject(n).toString());
        String name = ModelCatalog.VOICES[plan.voice].name;
        try {
          if (!plan.cloneProfile.isEmpty())
            name = new VoiceProfiles(this).get(plan.cloneProfile).optString("name", "Cloned voice");
          String progress = "Generating audio " + (n + 1) + " of " + plans.length() + ": " + name;
          post(manager, null, 41, message("render", progress, true));
          render(store, plan, speech);
          post(manager, runId, n + 1, message("completed", "Audio ready: " + AudioNames.forAudio(result), false));
        } catch (CancellationException e) {
          throw e;
        } catch (Exception e) {
          if (cancelled.get()) throw new CancellationException();
          batch.put("failure", name + " · " + JobFailure.message(e));
          post(manager, runId, n + 1, message("completed", batch.getString("failure"), false));
        }
        batch.put("next", n + 1);
        FileIntegrity.write(state, batch.toString());
      }
      if (batch.has("failure")) status = "Queue finished · " + batch.getString("failure");
      state.delete();
    } catch (CancellationException e) {
      state.delete();
      throw e;
    }
  }

  private void post(NotificationManager manager, String tag, int id, Notification notification) {
    if (Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED)
      manager.notify(tag, id, notification);
  }

  private Notification message(String channel, String text, boolean ongoing) {
    Intent open = new Intent(this, StudioActivity.class);
    if (!ongoing) open.setAction("open-projects");
    PendingIntent tap = PendingIntent.getActivity(this, ongoing ? 0 : 1, open,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, channel)
        .setContentTitle("Telugu Voice Studio").setContentText(text)
        .setStyle(new Notification.BigTextStyle().bigText(text))
        .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(tap)
        .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing).build();
  }

  private void check() {
    if (cancelled.get()) throw new CancellationException();
  }

  private void render(VoiceModelStore store, PlanStorage plan, SpeechSettings speech) throws Exception {
    ModelCatalog.Voice voice = ModelCatalog.VOICES[plan.voice];
    status = "Checking voice integrity…";
    if (!store.verified(voice))
      throw new IOException("Install " + voice.name + " in Offline Models first.");
    if (!voice.tiny()) store.prepareAssets(cancelled);
    check();
    DynamicEnglishPhonetizer.load(getAssets().open("english-cmudict.bin"));
    boolean cloned = !plan.cloneProfile.isEmpty();
    JSONObject clone = null;
    File cloneDirectory = null;
    if (cloned) {
      VoiceProfiles profiles = new VoiceProfiles(this);
      clone = profiles.get(plan.cloneProfile);
      cloneDirectory = CloneProfiles.directory(profiles, clone);
      if (!new OpenVoiceModelStore(getFilesDir()).ready())
        throw new IOException("Install OpenVoice before generating a cloned voice");
    }
    List<NarrationDirector.Segment> renderSegments =
        DeliveryControls.expand(plan.segments, speech.commaBuffer, speech.carefulDiction);
    renderSegments = speech.audible(renderSegments);
    boolean soft = voice.tiny() && speech.femaleSoftening && voice.gender.equals("Female");
    boolean waveformDelivery = speech.youtuber;
    boolean waveformQuestions = speech.questionLift;
    StringBuilder spokenIdentity =
        new StringBuilder("delivery-v081-native/")
            .append(plan.cloneProfile).append("/").append(voice.id).append("/").append(speech.warmthEq).append("/").append(speech.youtuber)
            .append(speech.durationGuard)
            .append(speech.questionLift)
            .append('/')
            .append(soft)
            .append('/')
            .append(speech.commaBuffer)
            .append('/')
            .append(speech.carefulDiction);
    if (cloned)
      spokenIdentity
          .append("/openvoice/")
          .append(OpenVoiceModelStore.REVISION)
          .append(clone.getString("cloneToneSha"));
    org.json.JSONArray inputAudit = new org.json.JSONArray();
    for (NarrationDirector.Segment segment : renderSegments) {
      String spoken = speech.spoken(segment.text);
      if (voice.tiny()) TinyVoice.tokens(spoken);
      spokenIdentity.append(spoken.length()).append(':').append(spoken);
      inputAudit.put(new JSONObject().put("original", segment.text).put("spoken", spoken));
    }
    RenderJournal journal =
        new RenderJournal(
            new File(getFilesDir(), "renders"),
            voice,
            renderSegments,
            FileIntegrity.sha256(spokenIdentity.toString()));
    FileIntegrity.write(new File(journal.directory, "speech-inputs.json"), inputAudit.toString(2));
    FileIntegrity.write(new File(journal.directory, "plan.json"), plan.json());
    JSONObject identity =
        new JSONObject()
            .put("voice", cloned ? "clone-" + plan.cloneProfile : voice.id)
            .put("cloned", cloned)
            .put("voice_name", cloned ? clone.getString("name") : voice.name)
            .put(
                "engine",
                cloned
                    ? "OpenVoice V2 tone conversion · " + voice.name
                    : voice.tiny() ? "Indic Tiny / Vocos" : "Piper / sherpa-onnx")
            .put("model_sha", voice.installedSha)
            .put("question_lift", speech.questionLift)
            .put("youtuber_controls", speech.youtuber)
            .put("tiny_duration_guard", speech.durationGuard)
            .put("female_softening", soft)
            .put("comma_buffer_ms", speech.commaBuffer ? DeliveryControls.COMMA_MS : 0)
            .put("careful_diction", speech.carefulDiction);
    FileIntegrity.write(new File(journal.directory, "render-identity.json"), identity.toString(2));
    AudioNames.project(journal.directory, voice.name);
    long need = 0;
    for (NarrationDirector.Segment s : renderSegments) need += s.text.length() * 7000L + 100000;
    if (getFilesDir().getUsableSpace() < need + 30_000_000)
      throw new IOException(
          "Not enough storage for chunks and WAV. Free approximately "
              + (need / 1_000_000)
              + " MB.");
    OfflineTts tts = null;
    TinyVoice tiny = null;
    long start = SystemClock.elapsedRealtime();
    int peakPss = 0, maxThermal = 0;
    float maxBattery = Float.NaN;
    List<File> chunks = new ArrayList<>();
    int reused = 0;
    try {
      for (int i = 0; i < renderSegments.size(); i++) {
        check();
        NarrationDirector.Segment s = renderSegments.get(i);
        status = "Rendering " + (i + 1) + " / " + renderSegments.size() + " · " + voice.name;
        if (!journal.reusable(i)) {
          if (voice.tiny()) {
            if (tiny == null) tiny = new TinyVoice(new TinyModelStore(store.root).root, cancelled);
            float[] samples =
                tiny.generate(
                    speech.spoken(s.text),
                    voice.gender.equals("Male"),
                    s.rate,
                    s.text.hashCode(),
                    false,
                    speech.durationGuard);
            check();
            PcmWave.write(journal.chunk(i), samples, 24000);
          } else {
            if (tts == null) {
              status = "Loading local neural voice…";
              OfflineTtsVitsModelConfig vits =
                  OfflineTtsVitsModelConfig.builder()
                      .setModel(store.model(voice).getPath())
                      .setTokens(new File(store.root, voice.id + "-tokens.txt").getPath())
                      .setDataDir(new File(store.root, "espeak-ng-data").getPath())
                      .build();
              tts =
                  new OfflineTts(
                      OfflineTtsConfig.builder()
                          .setModel(
                              OfflineTtsModelConfig.builder()
                                  .setVits(vits)
                                  .setNumThreads(2)
                                  .setDebug(false)
                                  .build())
                          .setMaxNumSentences(1)
                          .build());
            }
            GeneratedAudio audio = tts.generate(PronunciationLexicon.piperInput(speech.spoken(s.text)), 0, s.rate);
            check();
            if (audio.getSampleRate() != 22050)
              throw new IOException(
                  "Piper returned an unexpected sample rate: " + audio.getSampleRate());
            PcmWave.write(journal.chunk(i), audio.getSamples(), audio.getSampleRate());
          }
          journal.complete(i);
        } else reused++;
        chunks.add(journal.chunk(i));
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        peakPss = Math.max(peakPss, memory.getTotalPss());
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
          float temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
          maxBattery = Float.isNaN(maxBattery) ? temp : Math.max(maxBattery, temp);
        }
        if (Build.VERSION.SDK_INT >= 29) {
          int thermal = ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
          maxThermal = Math.max(maxThermal, thermal);
          if (thermal >= PowerManager.THERMAL_STATUS_SEVERE)
            throw new IOException(
                "Phone is hot. Let it cool, then Resume. Completed chunks are saved.");
        }
        if (SystemClock.elapsedRealtime() - start > 110 * 60 * 1000L)
          throw new IOException("Render paused after 110 minutes. Tap Resume to continue.");
      }
    } finally {
      if (tts != null) tts.release();
      if (tiny != null) tiny.close();
    }
    check();
    if (cloned) {
      File converted = new File(journal.directory, "cloned");
      converted.mkdirs();
      List<File> convertedChunks = new ArrayList<>();
      OpenVoiceModelStore pack = new OpenVoiceModelStore(getFilesDir());
      try (OpenVoiceEngine engine = new OpenVoiceEngine(pack.root, cancelled)) {
        float[] target = OpenVoiceEngine.loadEmbedding(new File(cloneDirectory, "tone256.bin"));
        File reference = CloneProfiles.baseReference(this, plan.voice);
        float[] source =
            engine.embedding(OpenVoiceEngine.read(reference), PcmWave.sampleRate(reference));
        for (int i = 0; i < chunks.size(); i++) {
          check();
          status = "Converting your voice · " + (i + 1) + " / " + chunks.size();
          File file = new File(converted, i + ".wav"), receipt = new File(converted, i + ".sha256");
          boolean ready =
              receipt.isFile()
                  && file.isFile()
                  && FileIntegrity.matches(file, FileIntegrity.read(receipt));
          if (!ready) {
            float[] pcm =
                engine.convert(
                    OpenVoiceEngine.read(chunks.get(i)),
                    PcmWave.sampleRate(chunks.get(i)),
                    source,
                    target);
            PcmWave.write(file, pcm, 22050);
            FileIntegrity.write(receipt, FileIntegrity.sha256(file));
          }
          PcmWave.validate(file);
          if (PcmWave.sampleRate(file) != 22050)
            throw new IOException("Converted voice has an invalid sample rate");
          convertedChunks.add(file);
        }
      }
      chunks = convertedChunks;
    }
    status = "Joining audio…";
    File output = new File(journal.directory, "narration.wav");
    PcmWave.mergeRaw(chunks, renderSegments, output, cancelled);
    double seconds = PcmWave.validate(output) / (PcmWave.sampleRate(output) * 2.0);
    // Publish the untouched raw render first. A failed/cancelled master never loses the narration.
    result = output;
    resultRunId = runId;
    FileIntegrity.write(new File(getFilesDir(), "last-result.txt"), output.getPath());
    File master = new File(journal.directory, "narration-studio.wav");
    StudioMastering.Report mastered;
    if (speech.warmthEq && (!voice.tiny() || cloned)) {
      status = "Creating optional native-rate EQ comparison…";
      PcmWave.merge(
          chunks,
          renderSegments,
          new File(journal.directory, "narration-native-warmth.wav"),
          cancelled,
          true);
    }
    if (voice.tiny() && !cloned) {
      status = "Preparing native-rate playback audio…";
      output = new File(journal.directory, "narration-playback.wav");
      PcmWave.merge(chunks, renderSegments, output, cancelled);
      seconds = PcmWave.validate(output) / (24000.0 * 2);
    }
    int lifted = 0, requested = 0;
    org.json.JSONArray controlAudit = new org.json.JSONArray();
    {
      List<File> controlled = new ArrayList<>();
      File controlledDir = new File(journal.directory, "delivery");
      controlledDir.mkdirs();
      for (int i = 0; i < chunks.size(); i++) {
        check();
        boolean question =
            waveformQuestions && DeliveryControls.question(renderSegments.get(i).text);
        if (question) requested++;
        {
          status = "Applying offline voice controls · " + (i + 1) + " / " + chunks.size();
          File f = new File(controlledDir, i + ".wav");
          VoiceDeliveryDsp.Result applied =
              VoiceDeliveryDsp.process(
                  chunks.get(i),
                  f,
                  question,
                  soft,
                  waveformDelivery ? renderSegments.get(i) : null,
                  cancelled, true, voice.tiny());
          if (question && applied.lifted) lifted++;
          controlAudit.put(
              new JSONObject()
                  .put("segment", i)
                  .put("question_requested", question)
                  .put("pitch_applied", applied.lifted)
                  .put("emotion", renderSegments.get(i).emotion.name())
                  .put("voiced_start_frame", applied.start)
                  .put("voiced_end_frame", applied.end));
          controlled.add(f);
        }
      }
      {
        File adjusted = new File(journal.directory, "narration-directed.wav");
        PcmWave.merge(controlled, renderSegments, adjusted, cancelled);
        File directedRaw = new File(journal.directory, "narration-directed-raw.wav");
        PcmWave.mergeRaw(controlled, renderSegments, directedRaw, cancelled);
        mastered =
            StudioMastering.process(
                directedRaw, master, cancelled, message -> status = message, speech.warmthEq);
        FileIntegrity.write(new File(journal.directory, "mastering.json"), mastered.json());
        output = master;
        seconds = mastered.frames / (double) StudioMastering.RATE;
      }
    }
    FileIntegrity.write(
        new File(journal.directory, "delivery-audit.json"), controlAudit.toString(2));
    FileIntegrity.write(new File(journal.directory, "preferred-output.txt"), output.getName());
    long elapsed = SystemClock.elapsedRealtime() - start;
    JSONObject stats =
        new JSONObject()
            .put("device", Build.MODEL)
            .put(
                "engine",
                cloned
                    ? "OpenVoice V2 tone conversion / " + voice.name
                    : voice.tiny()
                        ? "Indic Tiny / ONNX Runtime 1.31 / Vocos"
                        : "sherpa-onnx 1.12.26 / Piper")
            .put("voice", cloned ? "clone-" + plan.cloneProfile : voice.id)
            .put("voice_name", cloned ? clone.getString("name") : voice.name)
            .put("cloned", cloned)
            .put("base_voice", voice.id)
            .put("warmth_eq", speech.warmthEq)
            .put("native_warmth_comparison", speech.warmthEq)
            .put("head_padding_ms", PcmWave.HEAD_PADDING_MS)
            .put("tail_padding_ms", PcmWave.TAIL_PADDING_MS)
            .put("elapsed_ms", elapsed)
            .put("audio_seconds", seconds)
            .put("mastering", mastered == null ? JSONObject.NULL : new JSONObject(mastered.json()))
            .put("reused_chunks", reused)
            .put("total_chunks", chunks.size())
            .put("sampled_peak_pss_kib", peakPss)
            .put("max_thermal_status", maxThermal)
            .put(
                "max_battery_temperature_c", Float.isNaN(maxBattery) ? JSONObject.NULL : maxBattery)
            .put("question_tails_requested", requested)
            .put("question_tails_lifted", lifted)
            .put("female_softening", soft)
            .put("comma_buffer_ms", speech.commaBuffer ? DeliveryControls.COMMA_MS : 0)
            .put("output_file", output.getName())
            .put("quality_score", JSONObject.NULL);
    FileIntegrity.write(new File(journal.directory, "metrics.json"), stats.toString(2));
    FileIntegrity.write(new File(getFilesDir(), "last-result.txt"), output.getPath());
    result = output;
    resultRunId = runId;
    status =
        String.format(
            Locale.US,
            "Ready · %.1f min audio · %.1f s render · %d MB sampled PSS · %d reused chunks",
            seconds / 60,
            elapsed / 1000.0,
            peakPss / 1024,
            reused);
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    cancel();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    cancelled.set(true);
    worker.shutdown();
    super.onDestroy();
  }
}
