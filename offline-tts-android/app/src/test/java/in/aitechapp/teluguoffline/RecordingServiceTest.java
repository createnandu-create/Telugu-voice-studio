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
  public void repeatedPauseResumeCommandsDoNotEndRecording() {
    VoiceRecordingService service =
        Robolectric.buildService(VoiceRecordingService.class).create().get();
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
    assertFalse(VoiceRecordingService.active);
    assertEquals(RecordingClock.State.STOPPED, VoiceRecordingService.clock.state());
  }
}
