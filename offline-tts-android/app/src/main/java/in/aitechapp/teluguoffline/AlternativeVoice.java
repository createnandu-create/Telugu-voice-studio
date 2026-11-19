package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owned by a single render job. Graph outputs stay alive until their consumer finishes. */
abstract class AlternativeVoice implements AutoCloseable {
  final OrtEnvironment env = LocalOnnx.environment();
  final AtomicBoolean cancel;
  final List<OrtSession> sessions = new ArrayList<>();

  AlternativeVoice(AtomicBoolean cancel) { this.cancel = cancel; }

  final OrtSession session(File file) throws OrtException {
    try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
      opts.setIntraOpNumThreads(2);
      opts.setInterOpNumThreads(1);
      OrtSession s = env.createSession(file.getPath(), opts);
      sessions.add(s);
      return s;
    }
  }

  final OnnxTensor floats(float[] values, long... shape) throws OrtException {
    return OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape);
  }

  static float[] array(OnnxValue value) {
    FloatBuffer data = ((OnnxTensor)value).getFloatBuffer();
    float[] out = new float[data.remaining()];
    data.get(out);
    return out;
  }

  float[] render(String text) throws Exception { return render(text,1f); }
  abstract float[] render(String text, float rate) throws Exception;

  public void close() throws OrtException {
    try {
      for (OrtSession s : sessions) s.close();
    } finally { sessions.clear(); }
  }
}
