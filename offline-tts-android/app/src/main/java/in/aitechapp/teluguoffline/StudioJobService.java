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
  public static volatile File activeProject;
  public static volatile String runId = "", resultRunId = "";
  public static volatile String status = "Ready · all generation is local";
  public static volatile File result;
  public static final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private PowerManager.WakeLock wake;
  private final AtomicBoolean running = new AtomicBoolean(false);

  private volatile boolean stopping;
  private int latestStart;
  private RenderQueue queue;
  public static volatile boolean rendering;
  public static volatile String queueSummary = "";

  @Override public void onCreate() {
    super.onCreate();
    queue = new RenderQueue(getSharedPreferences("pending-renders", MODE_PRIVATE));
  }

  public IBinder onBind(Intent i) {
    return null;
  }

  public static void cancel() {
    cancelled.set(true);
    status = enhancing ? "Stopping local processing…" : "Stopping after the current sentence…";
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    latestStart = startId;
    if (intent != null && "render".equals(intent.getAction())) {
      queue.add(intent.getStringExtra("runId"), intent.getStringExtra("label"));
      queueSummary = queue.summary();
    }
    if (running.get()) return queue.first() == null ? START_NOT_STICKY : START_REDELIVER_INTENT;
    if ((intent == null || "resume-renders".equals(intent.getAction()) || "render".equals(intent.getAction())) && queue.first() != null)
      intent = new Intent(this, StudioJobService.class).setAction("render").putExtra("runId", queue.first());
    if (intent == null || "resume-renders".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    return execute(intent);
  }

  private int execute(Intent intent) {
    running.set(true);
    rendering = "render".equals(intent.getAction());
    busy = true;
    cancelled.set(false);
    status = "Preparing…";
    runId = intent.getStringExtra("runId");
    if (runId == null) runId = "";
    boolean checkerInstall = "check-install".equals(intent.getAction());
    boolean himaInstall = "hima-install".equals(intent.getAction());
    boolean cloneInstall = himaInstall || "clone-install".equals(intent.getAction());
    boolean cloneCreate = "clone-create".equals(intent.getAction());
    boolean speakerInstall = "speaker-install".equals(intent.getAction());
    boolean enroll = "speaker-enroll".equals(intent.getAction());
    boolean install = checkerInstall || "install".equals(intent.getAction()) || speakerInstall || cloneInstall;
    enhancing =
        "enhance".equals(intent.getAction())
            || enroll
            || speakerInstall
            || cloneCreate
            || cloneInstall;
    downloadingVoice =
        install && !checkerInstall && !speakerInstall && !cloneInstall ? intent.getIntExtra("voice", 0) : -1;
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
            if (checkerInstall) {
              AlternativePack.pronunciation(store.root).install(cancelled,message -> status=message);
              status="Completed · offline pronunciation check installed";
            } else if (himaInstall) {
              new HimaClonePack(this).install(cancelled,message -> status=message);
              status="Completed · Hima cloning ready offline";
            } else if (cloneInstall) {
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
                      activeProject = null;
                      if (stopping) return;
                      if (rendering) queue.finish(runId);
                      queueSummary = queue.summary();
                      rendering = false;
                      running.set(false);
                      String next = queue.first();
                      if (next != null) {
                        execute(new Intent(this, StudioJobService.class).setAction("render").putExtra("runId", next));
                        return;
                      }
                      busy = false;
                      downloadingVoice = -1;
                      enhancing = false;
                      stopForeground(STOP_FOREGROUND_REMOVE);
                      stopSelf(latestStart);
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
    JSONObject legacySpeech = batch.optJSONObject("speech");
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
          post(manager, null, 41, message("render", progress + " · " + queueSummary, true));
          JSONObject settings = plans.getJSONObject(n).optJSONObject("speech");
          render(store, plan, SpeechSettings.fromJson(settings == null ? legacySpeech : settings));
          post(manager, runId, n + 1, message("completed", "Audio ready: " + AudioNames.forAudio(result), false));
        } catch (CancellationException e) {
          throw e;
        } catch (Exception e) {
          if (cancelled.get()) throw new CancellationException();
          batch.put("failure", name + " · " + JobFailure.message(e));
          post(manager, runId, n + 1, message("completed", batch.getString("failure"), false));
        } finally {
          activeProject = null;
        }
        batch.put("next", n + 1);
        FileIntegrity.write(state, batch.toString());
      }
      if (batch.has("failure")) status = "Failed · " + batch.getString("failure");
      state.delete();
    } catch (CancellationException e) {
      if (!stopping) state.delete();
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
    if (!voice.tiny() && !voice.alternative()) store.prepareAssets(cancelled);
    check();
    DynamicEnglishPhonetizer.load(getAssets().open("english-cmudict.bin"));
    boolean cloned = !plan.cloneProfile.isEmpty();
    JSONObject clone = null;
    File cloneDirectory = null;
    if (cloned) {
      VoiceProfiles profiles = new VoiceProfiles(this);
      clone = profiles.get(plan.cloneProfile);
      cloneDirectory = CloneProfiles.directory(profiles, clone);
      if (plan.voice != clone.optInt("cloneBase", 0)) throw new IOException("Reselect the cloned voice and generate again.");
      if (voice.hima() && !new HimaClonePack(this).ready()) throw new IOException("Install Hima cloning first");
      if (!voice.hima() && !new OpenVoiceModelStore(getFilesDir()).ready())
        throw new IOException("Install OpenVoice before generating a cloned voice");
    }
    List<NarrationDirector.Segment> renderSegments =
        speech.renderSegments(plan.segments);
    boolean soft = voice.tiny() && speech.femaleSoftening && voice.gender.equals("Female");
    boolean waveformQuestions = speech.questionLift;
    AlternativePack checkerPack=AlternativePack.pronunciation(store.root);
    boolean checkWords=getSharedPreferences("StudioActivity", 0).getBoolean("verifyWords", true) && checkerPack.ready();
    StringBuilder spokenIdentity =
        new StringBuilder((voice.hima() ? "hima-diction-v087/" : "delivery-v085-acoustic/"))
            .append("studio-v0815/").append(speech.synthesisCleanup).append('/').append(checkWords).append("/").append(plan.cloneProfile).append("/").append(voice.id).append("/").append(speech.warmthEq).append("/").append(speech.youtuber)
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
          .append("/clone/")
          .append(clone.getString("cloneModel"))
          .append(clone.getString("cloneToneSha"));
    org.json.JSONArray inputAudit = new org.json.JSONArray();
    List<String> inputs = new java.util.ArrayList<>(renderSegments.size());
    for (NarrationDirector.Segment segment : renderSegments) {
      String spoken = speech.spoken(segment.text);
      if (voice.hima()) spoken = HimaVoice.spokenForm(spoken);
      if (voice.tiny()) TinyVoice.tokens(spoken);
      inputs.add(spoken);
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
                    ? (voice.hima() ? "Hima native voice cloning" : "OpenVoice V2 tone conversion · " + voice.name)
                    : voice.engine())
            .put("model_sha", voice.installedSha)
            .put("question_lift", speech.questionLift)
            .put("youtuber_controls", speech.youtuber)
            .put("tiny_duration_guard", speech.durationGuard)
            .put("female_softening", soft)
            .put("synthesis_cleanup", speech.synthesisCleanup)
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
    AlternativeVoice alternative = null;
    long start = SystemClock.elapsedRealtime();
    int peakPss = 0, maxThermal = 0;
    float maxBattery = Float.NaN;
    List<File> chunks = new ArrayList<>();
    activeProject = journal.directory;
    int reused = 0;
    List<String> reviewLines = new ArrayList<>();
    try (PronunciationCheck checker=checkWords ? new PronunciationCheck(checkerPack.root,cancelled) : null) {
      for (int i = 0; i < renderSegments.size(); i++) {
        check();
        NarrationDirector.Segment s = renderSegments.get(i);
        status = "Rendering " + (i + 1) + " / " + renderSegments.size() + " · " + voice.name;
        post(getSystemService(NotificationManager.class), null, 41,
            message("render", status + " · " + queueSummary, true));
        File review = new File(journal.directory, i + "-word-review.txt");
        if (!journal.reusable(i)) {
          if (voice.alternative()) {
            if (alternative == null) {
              File pack = new AlternativePack(store.root,voice).root;
              if(voice.fastPitch()) PackTopology.prepare(this,new File(pack,"fastpitch-pace.pb"),FastPitchVoice.TOPOLOGY_SHA);
              alternative = cloned && voice.hima() ? new HimaClonePack(this).voice(cloneDirectory,cancelled)
                  : voice.hima() ? new HimaVoice(pack,voice.name,cancelled)
                  : voice.syspin() ? new SyspinVoice(pack,cancelled)
                  : new FastPitchVoice(pack,voice.gender.equals("Male"),cancelled);
            }
            AlternativeVoice engine = alternative;
            String spoken = inputs.get(i);
            float[] samples = PronunciationCheck.renderNamed(checker,spoken,s.rate,voice.sampleRate(),speech.youtuber,engine);
            check();
            if (speech.youtuber) ConversationTiming.shapeDelivery(samples, s.emotion);
            PcmWave.write(journal.chunk(i),samples,voice.sampleRate());
          } else if (voice.tiny()) {
            if (tiny == null) tiny = new TinyVoice(new TinyModelStore(store.root).root, cancelled);
            TinyVoice engine = tiny;
            String spoken = inputs.get(i);
            float[] samples = PronunciationCheck.render(checker,spoken, s.rate, 24000, speech.youtuber,
                (phrase, rate) -> {
                  check();
                  return engine.generate(phrase, voice.gender.equals("Male"), rate, s.text.hashCode(), false, false, speech.synthesisCleanup);
                });
            check();
            PcmWave.write(journal.chunk(i), samples, 24000);
          } else {
            if (tts == null) {
              status = "Loading local neural voice…";
              OfflineTtsVitsModelConfig vits =
                  OfflineTtsVitsModelConfig.builder()
                      .setModel(store.model(voice).getPath())
                      .setNoiseScale(.50f)
                      .setNoiseScaleW(.65f)
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
            OfflineTts engine = tts;
            String spoken = inputs.get(i);
            float[] samples = PronunciationCheck.render(checker,spoken, s.rate, 22050, speech.youtuber,
                (phrase, rate) -> {
                  check();
                  GeneratedAudio audio = engine.generate(PronunciationLexicon.piperInput(phrase), 0, rate);
                  if (audio.getSampleRate() != 22050) throw new IOException("Unexpected Piper sample rate");
                  return audio.getSamples();
                });
            check();
            PcmWave.write(journal.chunk(i), samples, 22050);
          }
          if (checker != null && !checker.reviewWord.isEmpty())
            FileIntegrity.write(review, checker.reviewWord);
          else java.nio.file.Files.deleteIfExists(review.toPath());
          journal.complete(i);
        } else reused++;
        if (review.isFile()) reviewLines.add("Phrase " + (i + 1) + " · " + FileIntegrity.read(review));
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
      if (alternative != null) alternative.close();
    }
    check();
    if (cloned && !voice.hima()) {
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
    // Keep the raw file in Projects for recovery; expose the master only when it is complete.
    File master = new File(journal.directory, "narration-studio.wav");
    StudioMastering.Report mastered;
    if (speech.warmthEq) {
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
      RenderCleanup.Cleaner enhancer = speech.synthesisCleanup
          ? RenderCleanup.forVoice(plan.voice, cloned, cancelled) : null;
      List<File> controlled = new ArrayList<>();
      File controlledDir = new File(journal.directory, "delivery");
      controlledDir.mkdirs();
      for (int i = 0; i < chunks.size(); i++) {
        check();
        boolean question =
            waveformQuestions && DeliveryControls.question(renderSegments.get(i).text)
                && !(speech.youtuber && renderSegments.get(i).emotion == NarrationDirector.Emotion.WRY);
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
                  speech.youtuber && (voice.hima() || voice.syspin()) && renderSegments.get(i).performanceCue() ? renderSegments.get(i) : null,
                  cancelled, speech.synthesisCleanup, voice.tiny() || cloned, enhancer);
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
                directedRaw, master, cancelled, message -> status = message, false);
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
                    ? (voice.hima() ? "Hima native voice cloning" : "OpenVoice V2 tone conversion / " + voice.name)
                    : voice.engine())
            .put("voice", cloned ? "clone-" + plan.cloneProfile : voice.id)
            .put("voice_name", cloned ? clone.getString("name") : voice.name)
            .put("cloned", cloned)
            .put("base_voice", voice.id)
            .put("warmth_eq", false)
            .put("speech_cleanup", speech.synthesisCleanup ? "speech-decay-v1" : "off")
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
    FileIntegrity.write(new File(journal.directory, "word-review.txt"), String.join("\n", reviewLines));
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
    stopping = true;
    cancel();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    stopping = true;
    cancelled.set(true);
    if (wake != null && wake.isHeld()) wake.release();
    rendering = false;
    busy = false;
    queueSummary = "";
    worker.shutdown();
    super.onDestroy();
  }
}
