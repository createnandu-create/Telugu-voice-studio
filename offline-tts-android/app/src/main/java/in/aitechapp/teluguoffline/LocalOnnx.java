package in.aitechapp.teluguoffline;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

final class LocalOnnx {
  private static final OrtEnvironment ENV = create();

  private static OrtEnvironment create() {
    OrtEnvironment env = OrtEnvironment.getEnvironment();
    try {
      env.setTelemetry(false);
    } catch (OrtException e) {
      throw new IllegalStateException("Could not configure offline inference", e);
    }
    return env;
  }

  static OrtEnvironment environment() { return ENV; }

  private LocalOnnx() {}
}
