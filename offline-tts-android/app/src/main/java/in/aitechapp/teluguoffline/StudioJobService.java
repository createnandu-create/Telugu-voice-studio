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
    status = "Stopping after the current sentence…";
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
    boolean install = "install".equals(intent.getAction());
    downloadingVoice = install ? intent.getIntExtra("voice", 0) : -1;
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
            if (install) {
              int voice = intent.getIntExtra("voice", 0);
              store.install(ModelCatalog.VOICES[voice], cancelled, message -> status = message);
              status = ModelCatalog.VOICES[voice].name + " installed · ready offline";
            } else render(store);
          } catch (CancellationException e) {
            status =
                install
                    ? "Download paused · partial bytes saved. Tap Resume download in Settings."
                    : "Stopped · completed chunks saved. Tap Generate / Resume to continue.";
          } catch (Exception | LinkageError e) {
            status =
                "Could not complete: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
          } finally {
            new Handler(Looper.getMainLooper())
                .post(
                    () -> {
                      if (wake != null && wake.isHeld()) wake.release();
                      running.set(false);
                      busy = false;
                      downloadingVoice = -1;
                      stopForeground(STOP_FOREGROUND_REMOVE);
                      stopSelf(startId);
                    });
          }
        });
    return START_NOT_STICKY;
  }

  private void check() {
    if (cancelled.get()) throw new CancellationException();
  }

  private void render(VoiceModelStore store) throws Exception {
    File saved = new File(getFilesDir(), "render-plan.json");
    PlanStorage plan = PlanStorage.parse(FileIntegrity.read(saved));
    ModelCatalog.Voice voice = ModelCatalog.VOICES[plan.voice];
    status = "Checking voice integrity…";
    if (!store.verified(voice))
      throw new IOException("Install " + voice.name + " in Offline Models first.");
    store.prepareAssets(cancelled);
    check();
    SpeechSettings speech = SpeechSettings.load(this);
    StringBuilder spokenIdentity = new StringBuilder();
    org.json.JSONArray inputAudit = new org.json.JSONArray();
    for (NarrationDirector.Segment segment : plan.segments) {
      String spoken = speech.spoken(segment.text);
      spokenIdentity.append(spoken.length()).append(':').append(spoken);
      inputAudit.put(new JSONObject().put("original", segment.text).put("spoken", spoken));
    }
    RenderJournal journal =
        new RenderJournal(
            new File(getFilesDir(), "renders"),
            voice,
            plan.segments,
            FileIntegrity.sha256(spokenIdentity.toString()));
    FileIntegrity.write(new File(journal.directory, "speech-inputs.json"), inputAudit.toString(2));
    FileIntegrity.write(new File(journal.directory, "plan.json"), plan.json());
    long need = 0;
    for (NarrationDirector.Segment s : plan.segments) need += s.text.length() * 7000L + 100000;
    if (getFilesDir().getUsableSpace() < need + 30_000_000)
      throw new IOException(
          "Not enough storage for chunks and WAV. Free approximately "
              + (need / 1_000_000)
              + " MB.");
    OfflineTts tts = null;
    long start = SystemClock.elapsedRealtime();
    int peakPss = 0, maxThermal = 0;
    float maxBattery = Float.NaN;
    List<File> chunks = new ArrayList<>();
    int reused = 0;
    try {
      for (int i = 0; i < plan.segments.size(); i++) {
        check();
        NarrationDirector.Segment s = plan.segments.get(i);
        status = "Rendering " + (i + 1) + " / " + plan.segments.size() + " · " + voice.name;
        if (!journal.reusable(i)) {
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
          GeneratedAudio audio = tts.generate(speech.spoken(s.text), 0, s.rate);
          check();
          PcmWave.write(journal.chunk(i), audio.getSamples(), audio.getSampleRate());
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
    }
    check();
    status = "Joining audio…";
    File output = new File(journal.directory, "narration.wav");
    PcmWave.merge(chunks, plan.segments, output, cancelled);
    double seconds = PcmWave.validate(output) / (PcmWave.RATE * 2.0);
    long elapsed = SystemClock.elapsedRealtime() - start;
    JSONObject stats =
        new JSONObject()
            .put("device", Build.MODEL)
            .put("engine", "sherpa-onnx 1.12.26 / Piper")
            .put("voice", voice.id)
            .put("elapsed_ms", elapsed)
            .put("audio_seconds", seconds)
            .put("reused_chunks", reused)
            .put("total_chunks", chunks.size())
            .put("sampled_peak_pss_kib", peakPss)
            .put("max_thermal_status", maxThermal)
            .put(
                "max_battery_temperature_c", Float.isNaN(maxBattery) ? JSONObject.NULL : maxBattery)
            .put("quality_score", JSONObject.NULL);
    FileIntegrity.write(new File(journal.directory, "metrics.json"), stats.toString(2));
    FileIntegrity.write(new File(getFilesDir(), "last-result.txt"), output.getPath());
    result = output;
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
