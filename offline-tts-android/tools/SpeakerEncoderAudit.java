package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeakerEncoderAudit {
  public static void main(String[] args) throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(args[0]));
    FloatBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    float[] x = new float[b.remaining()];
    b.get(x);
    float[][] f =
        SpeakerFeatures.compute(x, new File(args[1], "fbank-80x201-f32.bin"), new AtomicBoolean());
    ByteBuffer data = ByteBuffer.allocate(f.length * 80 * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float[] row : f) for (float v : row) data.putFloat(v);
    Files.write(Path.of(args[2], "features-java.bin"), data.array());
    float[] v = SpeakerEncoder.encode(new File(args[1]), x, new AtomicBoolean());
    data = ByteBuffer.allocate(192 * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float a : v) data.putFloat(a);
    Files.write(Path.of(args[2], "vector-java.bin"), data.array());
    System.out.println("Real ECAPA output: " + v.length + " dimensions");
  }
}
