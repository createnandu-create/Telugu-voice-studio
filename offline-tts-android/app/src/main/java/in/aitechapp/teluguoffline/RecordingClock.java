package in.aitechapp.teluguoffline;

/** Monotonic active-recording clock; navigation and wall-clock changes have no effect. */
public final class RecordingClock {
  public enum State {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
  }

  private State state = State.IDLE;
  private long accumulated, start;

  public synchronized void start(long now) {
    if (state == State.RECORDING || state == State.PAUSED)
      throw new IllegalStateException("Recording already active.");
    accumulated = 0;
    start = now;
    state = State.RECORDING;
  }

  public synchronized void pause(long now) {
    if (state != State.RECORDING) throw new IllegalStateException("Not recording.");
    accumulated = elapsed(now);
    state = State.PAUSED;
  }

  public synchronized void resume(long now) {
    if (state != State.PAUSED) throw new IllegalStateException("Not paused.");
    start = now;
    state = State.RECORDING;
  }

  public synchronized long elapsed(long now) {
    return Math.min(60000, accumulated + (state == State.RECORDING ? Math.max(0, now - start) : 0));
  }

  public synchronized void stop(long now) {
    accumulated = elapsed(now);
    state = State.STOPPED;
  }

  public synchronized State state() {
    return state;
  }

  public synchronized boolean limit(long now) {
    return elapsed(now) >= 60000;
  }
}
