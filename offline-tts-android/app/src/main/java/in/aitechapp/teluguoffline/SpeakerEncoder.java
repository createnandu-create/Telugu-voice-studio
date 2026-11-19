package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real 192-dimensional ECAPA fingerprint. It is NOT compatible with fixed-speaker TTS conditioning.
 */
public final class SpeakerEncoder {
  public static float[] encode(File root, float[] audio16k, AtomicBoolean cancel) throws Exception {
    float[][] features =
        SpeakerFeatures.compute(audio16k, new File(root, "fbank-80x201-f32.bin"), cancel);
    OrtEnvironment env = LocalOnnx.environment();
    try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
      opts.setIntraOpNumThreads(2);
      opts.setInterOpNumThreads(1);
      opts.setMemoryPatternOptimization(false);
      try (OrtSession session =
              env.createSession(new File(root, "ecapa-speaker-v1.onnx").getPath(), opts);
          OnnxTensor input = OnnxTensor.createTensor(env, new float[][][] {features});
          OnnxTensor lengths = OnnxTensor.createTensor(env, new float[] {1f});
          OrtSession.RunOptions run = new OrtSession.RunOptions()) {
        OfflineDenoiser.check(cancel);
        // terminate an active native run when foreground Cancel is pressed; no leaked polling
        // thread.
        java.util.concurrent.atomic.AtomicBoolean finished =
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread watcher =
            new Thread(
                () -> {
                  while (!finished.get()) {
                    if (cancel.get()) {
                      try {
                        run.setTerminate(true);
                      } catch (OrtException ignored) {
                      }
                      return;
                    }
                    try {
                      Thread.sleep(50);
                    } catch (InterruptedException e) {
                      return;
                    }
                  }
                },
                "speaker-cancel");
        watcher.setDaemon(true);
        watcher.start();
        try (OrtSession.Result result =
            session.run(Map.of("features", input, "feature_lens", lengths), run)) {
          OfflineDenoiser.check(cancel);
          float[][] values = (float[][]) result.get(0).getValue();
          if (values.length != 1 || values[0].length != 192)
            throw new IOException("Speaker model output is not 192-dimensional");
          float[] vector = values[0].clone();
          double norm = 0;
          for (float v : vector) {
            if (!Float.isFinite(v)) throw new IOException("Invalid speaker fingerprint");
            norm += v * v;
          }
          if (norm < 1e-12) throw new IOException("Speaker fingerprint is empty");
          norm = Math.sqrt(norm);
          for (int i = 0; i < vector.length; i++) vector[i] /= norm;
          return vector;
        } finally {
          finished.set(true);
          watcher.interrupt();
          watcher.join();
        }
      }
    }
  }

  private SpeakerEncoder() {}
}
