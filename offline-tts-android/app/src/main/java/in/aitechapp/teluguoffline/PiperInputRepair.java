package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Restores the PAD after BOS omitted by sherpa 1.12.26's Piper frontend. Small protobuf-preserving
 * ONNX adapter, not a weight edit. Original graph inputs/API stay intact. Pinned hashes gate both
 * ends. Four standard ops insert [0] at input[:,1] and add 1 to lengths.
 */
public final class PiperInputRepair {
  private static final String P = "tvs_bos_pad_v1_";

  private static final class Field {
    int number, wire, start, end, valueStart, valueEnd;
    byte[] source;
  }

  private static long var(byte[] b, int[] cursor) throws IOException {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (cursor[0] >= b.length) throw new IOException("Truncated ONNX protobuf");
      int c = b[cursor[0]++] & 255;
      value |= (long) (c & 127) << shift;
      if ((c & 128) == 0) return value;
    }
    throw new IOException("Invalid ONNX protobuf integer");
  }

  private static List<Field> fields(byte[] b, int start, int end) throws IOException {
    List<Field> result = new ArrayList<>();
    int[] c = {start};
    while (c[0] < end) {
      Field f = new Field();
      f.source = b;
      f.start = c[0];
      long tag = var(b, c);
      f.number = (int) (tag >>> 3);
      f.wire = (int) (tag & 7);
      if (f.number <= 0) throw new IOException("Invalid ONNX field");
      if (f.wire == 2) {
        long size = var(b, c);
        if (size < 0 || size > end - c[0]) throw new IOException("Invalid ONNX length");
        f.valueStart = c[0];
        c[0] += (int) size;
        f.valueEnd = c[0];
      } else if (f.wire == 0) var(b, c);
      else if (f.wire == 1) c[0] += 8;
      else if (f.wire == 5) c[0] += 4;
      else throw new IOException("Unsupported ONNX wire type");
      if (c[0] > end) throw new IOException("Truncated ONNX field");
      f.end = c[0];
      result.add(f);
    }
    return result;
  }

  private static void raw(OutputStream out, Field f) throws IOException {
    out.write(f.source, f.start, f.end - f.start);
  }

  private static String string(Field f) {
    return new String(f.source, f.valueStart, f.valueEnd - f.valueStart, StandardCharsets.UTF_8);
  }

  private static void var(ByteArrayOutputStream out, long n) {
    while ((n & ~127L) != 0) {
      out.write((int) n & 127 | 128);
      n >>>= 7;
    }
    out.write((int) n);
  }

  private static void append(ByteArrayOutputStream out, byte[] value) {
    out.write(value, 0, value.length);
  }

  private static byte[] scalar(int field, long n) {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    var(o, field * 8L);
    var(o, n);
    return o.toByteArray();
  }

  private static byte[] bytes(int field, byte[] data) {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    var(o, field * 8L + 2);
    var(o, data.length);
    o.write(data, 0, data.length);
    return o.toByteArray();
  }

  private static byte[] text(int field, String s) {
    return bytes(field, s.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] join(byte[]... a) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] x : a) out.write(x, 0, x.length);
    return out.toByteArray();
  }

  private static byte[] tensor(String name, long value, long... dims) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (long d : dims) append(out, scalar(1, d));
    append(out, scalar(2, 7));
    append(out, scalar(7, value));
    append(out, text(8, P + name));
    return bytes(5, out.toByteArray());
  }

  private static byte[] node(String op, String name, String[] inputs, byte[] attr) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (String input : inputs) append(out, text(1, input));
    append(out, text(2, P + name));
    append(out, text(3, P + name));
    append(out, text(4, op));
    if (attr != null) append(out, bytes(5, attr));
    return bytes(1, out.toByteArray());
  }

  static byte[] wrap(byte[] source) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(source.length + 2048);
    wrapTo(source, out);
    return out.toByteArray();
  }

  private static void wrapTo(byte[] source, OutputStream model) throws IOException {
    int graphs = 0;
    for (Field field : fields(source, 0, source.length)) {
      if (field.number != 7 || field.wire != 2) {
        raw(model, field);
        continue;
      }
      graphs++;
      byte[] prefix =
          join(
              node("Slice", "bos", new String[] {"input", P + "zero", P + "one", P + "axis"}, null),
              node("Slice", "rest", new String[] {"input", P + "one", P + "end", P + "axis"}, null),
              node(
                  "Concat",
                  "input",
                  new String[] {P + "bos", P + "blank", P + "rest"},
                  join(text(1, "axis"), scalar(3, 1), scalar(20, 2))),
              node("Add", "lengths", new String[] {"input_lengths", P + "one"}, null));
      byte[] suffix =
          join(
              tensor("zero", 0, 1),
              tensor("one", 1, 1),
              tensor("end", Integer.MAX_VALUE, 1),
              tensor("axis", 1, 1),
              tensor("blank", 0, 1, 1));
      List<Field> graph = fields(source, field.valueStart, field.valueEnd);
      Map<Field, byte[]> changed = new IdentityHashMap<>();
      int graphSize = field.valueEnd - field.valueStart + prefix.length + suffix.length,
          tokens = 0,
          lengths = 0;
      for (Field f : graph) {
        if (f.number != 1 || f.wire != 2) continue;
        ByteArrayOutputStream node = new ByteArrayOutputStream(f.end - f.start + 128);
        for (Field part : fields(source, f.valueStart, f.valueEnd)) {
          String str = part.wire == 2 && (part.number == 1 || part.number == 3) ? string(part) : "";
          if (str.startsWith(P)) throw new IOException("Piper model is already adapted.");
          if (part.number == 1 && str.equals("input")) {
            append(node, text(1, P + "input"));
            tokens++;
          } else if (part.number == 1 && str.equals("input_lengths")) {
            append(node, text(1, P + "lengths"));
            lengths++;
          } else raw(node, part);
        }
        byte[] value = bytes(1, node.toByteArray());
        changed.put(f, value);
        graphSize += value.length - (f.end - f.start);
      }
      if (tokens == 0 || lengths == 0) throw new IOException("Unexpected Piper input graph.");
      ByteArrayOutputStream header = new ByteArrayOutputStream();
      var(header, 7 * 8 + 2);
      var(header, graphSize);
      model.write(header.toByteArray());
      model.write(prefix);
      for (Field f : graph) {
        byte[] value = changed.get(f);
        if (value == null) raw(model, f);
        else model.write(value);
      }
      model.write(suffix);
    }
    if (graphs != 1) throw new IOException("Unexpected Piper graph count.");
  }

  public static void install(File source, File destination, String before, String after)
      throws IOException {
    if (source.length() > 70_000_000 || !FileIntegrity.matches(source, before))
      throw new IOException("Piper source integrity check failed.");
    byte[] original = Files.readAllBytes(source.toPath());
    File part = new File(destination + ".repairing");
    try {
      try (FileOutputStream out = new FileOutputStream(part)) {
        wrapTo(original, out);
        out.getFD().sync();
      }
      if (!FileIntegrity.matches(part, after))
        throw new IOException("Piper input repair integrity check failed. Existing voice kept.");
      FileIntegrity.promote(part, destination);
    } finally {
      part.delete();
    }
  }

  private PiperInputRepair() {}
}
