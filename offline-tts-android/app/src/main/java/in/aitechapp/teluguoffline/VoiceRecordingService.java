package in.aitechapp.teluguoffline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
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
  private AudioRecord recorder;
  private Thread capture;
  private volatile boolean stopping;
  private volatile boolean discard;
  private boolean finishing;
  private String effects = "";
  private File raw;
  private Handler handler;
  private VoiceProfiles profiles;
  private String name, label;
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
        discard = true;
        clock.pause(SystemClock.elapsedRealtime());
        status = "Paused · tap Resume";
      } else if ("resume".equals(action)
          && active
          && clock.state() == RecordingClock.State.PAUSED) {
        discard = false;
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
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("Allow microphone access to record your voice.");
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
    int buffer =
        Math.max(
            4800,
            AudioRecord.getMinBufferSize(
                24000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
    recorder =
        new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            24000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer);
    if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
      throw new IOException("Microphone does not support 24 kHz PCM recording.");
    effects = "24 kHz PCM";
    raw = new File(profiles.root(), profileId + "-capture.wav");
    stopping = false;
    discard = false;
    finishing = false;
    recorder.startRecording();
    clock.start(SystemClock.elapsedRealtime());
    elapsedMs = 0;
    active = true;
    status = "Recording · " + effects;
    capture = new Thread(this::capturePcm, "voice-reference-capture");
    capture.start();

  }

  private void capturePcm() {
    try (RandomAccessFile output = new RandomAccessFile(raw, "rw")) {
      output.setLength(0);
      PcmWave.header(output, 0, 24000);
      output.seek(44);
      byte[] buffer = new byte[2400];
      long count = 0, lastMeter = 0;
      while (!stopping && count < 24000 * 2 * 60L) {
        int n = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
        if (n < 0) {
          if (!stopping) throw new IOException("Microphone read error " + n);
          break;
        }
        if (n == 0 || discard || stopping) continue;
        n = (int) Math.min(n & ~1, 24000 * 2 * 60L - count);
        output.seek(44 + count);
        output.write(buffer, 0, n);
        count += n;
        elapsedMs = count / 48;
        if (elapsedMs - lastMeter >= 250) {
          int peak = 0;
          for (int at = 0; at + 1 < n; at += 2)
            peak = Math.max(peak, Math.abs((int) (short) ((buffer[at] & 255) | buffer[at+1] << 8)));
          int db = (int) Math.round(20 * Math.log10(Math.max(1, peak) / 32768.0));
          if (!discard && !stopping) status = "Recording · mic peak " + db + " dBFS";
          lastMeter = elapsedMs;
        }
        // A recoverable WAV even if the OS interrupts this service.
        PcmWave.header(output, count, 24000);
      }
      output.getFD().sync();
      if (!stopping) handler.post(() -> finish(false));
    } catch (Exception e) {
      status = "Microphone interrupted: " + e.getMessage();
      if (!stopping) handler.post(() -> finish(true));
    }
  }

  private void finish(boolean interrupted) {
    if (finishing) return;
    if (recorder == null) {
      active = false;

      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return;
    }
    final String failure = interrupted ? status : "";
    finishing = true;

    stopping = true;
    clock.stop(SystemClock.elapsedRealtime());

    status = "Saving and cleaning reference…";
    new Thread(
            () -> {
              try {
                if (recorder != null) {
                  try {
                    recorder.stop();
                  } catch (RuntimeException ignored) {
                  }
                }
                if (capture != null) capture.join(5000);
                if (capture != null && capture.isAlive())
                  throw new IOException("Microphone did not stop. Capture retained for recovery.");
                if (raw != null && raw.length() > 44) {
                  RecordingCleanup.Result cleaned =
                      RecordingCleanup.file(raw, profiles.wave(profileId));
                  profiles.save(
                      profileId,
                      name == null ? "My voice" : name,
                      label == null ? "Custom" : label,
                      "Full-length leveled reference · " + effects + ". Original capture kept.",
                      interrupted);
                  new File(profiles.root(), "pending-recording.json").delete();
                  status =
                      "Reference saved · "
                          + String.format(
                              java.util.Locale.US,
                              "%02d:%02d",
                              cleaned.samples.length / 24000 / 60,
                              cleaned.samples.length / 24000 % 60)
                          + " · Audio cleaned";
                } else
                  status = failure.isEmpty() ? "No audio captured. Try recording again." : failure;
              } catch (Exception e) {
                status = "Capture kept for recovery: " + e.getMessage();
              } finally {
                if (recorder != null) recorder.release();
                recorder = null;
                active = false;
                handler.post(
                    () -> {
                      stopForeground(STOP_FOREGROUND_REMOVE);
                      stopSelf();
                    });
              }
            },
            "voice-reference-save")
        .start();
  }

  @Override
  public void onDestroy() {
    if (recorder != null && !finishing) finish(true);

    super.onDestroy();
  }
}
