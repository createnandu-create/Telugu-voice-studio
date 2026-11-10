package in.aitechapp.teluguoffline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.*;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** Microphone foreground service owns recording; Activity navigation never stops it. */
public final class VoiceRecordingService extends Service {
  public static final RecordingClock clock = new RecordingClock();
  public static volatile boolean active = false;
  public static volatile String status = "Ready to record", profileId = "";
  public static volatile long elapsedMs = 0;
  private MediaRecorder recorder;
  private Handler handler;
  private VoiceProfiles profiles;
  private String name, label;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          elapsedMs = clock.elapsed(SystemClock.elapsedRealtime());
          if (clock.limit(SystemClock.elapsedRealtime())) {
            finish(false);
            return;
          }
          handler.postDelayed(this, 200);
        }
      };

  public IBinder onBind(Intent i) {
    return null;
  }

  public void onCreate() {
    super.onCreate();
    handler = new Handler(Looper.getMainLooper());
    profiles = new VoiceProfiles(this);
  }

  public int onStartCommand(Intent i, int flags, int id) {
    if (i == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    String action = i.getAction();
    try {
      if ("start".equals(action)) {
        if (active) return START_NOT_STICKY;
        name = i.getStringExtra("name");
        label = i.getStringExtra("label");
        start();
      } else if ("pause".equals(action)
          && active
          && clock.state() == RecordingClock.State.RECORDING) {
        recorder.pause();
        clock.pause(SystemClock.elapsedRealtime());
        status = "Paused · tap Resume";
      } else if ("resume".equals(action)
          && active
          && clock.state() == RecordingClock.State.PAUSED) {
        recorder.resume();
        clock.resume(SystemClock.elapsedRealtime());
        status = "Recording · microphone active";
      } else if ("stop".equals(action) && active) finish(false);
    } catch (Exception e) {
      status = "Recording interrupted: " + e.getMessage();
      finish(true);
    }
    return START_NOT_STICKY;
  }

  private void start() throws Exception {
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "voice-recording", "Voice recording", NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop =
        PendingIntent.getService(
            this,
            72,
            new Intent(this, VoiceRecordingService.class).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n =
        new Notification.Builder(this, "voice-recording")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording your voice")
            .setContentText("Microphone active · maximum 60 seconds")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    71,
                    new Intent(this, StudioActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null, "Stop and save", stop).build())
            .build();
    if (Build.VERSION.SDK_INT >= 30)
      startForeground(72, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    else startForeground(72, n);
    profileId = UUID.randomUUID().toString();
    FileIntegrity.write(
        new File(profiles.root(), "pending-recording.json"),
        new JSONObject()
            .put("id", profileId)
            .put("name", name == null ? "My voice" : name)
            .put("label", label == null ? "Custom" : label)
            .toString());
    recorder = new MediaRecorder();
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setAudioSamplingRate(44100);
    recorder.setAudioEncodingBitRate(128000);
    recorder.setOutputFile(profiles.audio(profileId).getPath());
    recorder.setMaxDuration(60000);
    recorder.setOnInfoListener(
        (r, what, extra) -> {
          if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish(false);
        });
    recorder.setOnErrorListener(
        (r, what, extra) -> {
          status = "Microphone interrupted (" + what + ")";
          finish(true);
        });
    recorder.prepare();
    recorder.start();
    clock.start(SystemClock.elapsedRealtime());
    elapsedMs = 0;
    active = true;
    status = "Recording · microphone active";
    handler.post(tick);
  }

  private void finish(boolean interrupted) {
    handler.removeCallbacks(tick);
    if (recorder == null) {
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return;
    }
    clock.stop(SystemClock.elapsedRealtime());
    elapsedMs = clock.elapsed(SystemClock.elapsedRealtime());
    try {
      recorder.stop();
      profiles.save(
          profileId,
          name == null ? "My voice" : name,
          label == null ? "Custom" : label,
          "",
          interrupted);
      status =
          interrupted
              ? "Interrupted recording saved; check duration and playback."
              : "Reference saved · cloning not installed";
      new File(profiles.root(), "pending-recording.json").delete();
    } catch (Exception e) {
      status = "Incomplete recording retained: " + e.getMessage();
    } finally {
      recorder.release();
      recorder = null;
      active = false;
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
    }
  }

  @Override
  public void onDestroy() {
    if (recorder != null) finish(true);
    if (handler != null) handler.removeCallbacks(tick);
    super.onDestroy();
  }
}
