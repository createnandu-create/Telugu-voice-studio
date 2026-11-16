package in.aitechapp.teluguoffline;

import static org.junit.Assert.*;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Lifecycle/state test with a shadow recorder; does not certify real microphone audio. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecordingServiceTest {
  @Test
  public void deniedMicrophoneDoesNotStartOrLoseActionableError() {
    VoiceRecordingService service =
        Robolectric.buildService(VoiceRecordingService.class).create().get();
    org.robolectric.Shadows.shadowOf(service.getApplication())
        .denyPermissions(android.Manifest.permission.RECORD_AUDIO);
    service.onStartCommand(new Intent().setAction("start"), 0, 1);
    assertFalse(VoiceRecordingService.active);
    assertTrue(VoiceRecordingService.status.contains("Allow microphone access"));
    service.onDestroy();
  }

  @Test
  public void repeatedPauseResumeCommandsDoNotEndRecording() throws Exception {
    VoiceRecordingService service =
        Robolectric.buildService(VoiceRecordingService.class).create().get();
    org.robolectric.Shadows.shadowOf((android.app.Application) service.getApplication())
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    try {
      service.onStartCommand(
          new Intent()
              .setAction("start")
              .putExtra("name", "Lifecycle fixture")
              .putExtra("label", "Custom"),
          0,
          1);
      assertTrue(VoiceRecordingService.status, VoiceRecordingService.active);
      service.onStartCommand(new Intent().setAction("pause"), 0, 2);
      service.onStartCommand(new Intent().setAction("pause"), 0, 3);
      assertTrue(VoiceRecordingService.active);
      assertEquals(RecordingClock.State.PAUSED, VoiceRecordingService.clock.state());
      service.onStartCommand(new Intent().setAction("resume"), 0, 4);
      service.onStartCommand(new Intent().setAction("resume"), 0, 5);
      assertTrue(VoiceRecordingService.active);
      assertEquals(RecordingClock.State.RECORDING, VoiceRecordingService.clock.state());
    } finally {
      service.onDestroy();
    }
    long deadline = System.nanoTime() + 6_000_000_000L;
    while (VoiceRecordingService.active && System.nanoTime() < deadline) Thread.sleep(10);
    assertFalse(VoiceRecordingService.status, VoiceRecordingService.active);
    assertEquals(RecordingClock.State.STOPPED, VoiceRecordingService.clock.state());
  }
}
