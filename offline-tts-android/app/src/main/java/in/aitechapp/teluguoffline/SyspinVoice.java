package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Character-input Coqui VITS exported by bolo. Each gender has its own vocabulary and blank ID. */
final class SyspinVoice extends AlternativeVoice {
  private final Map<Integer, Long> ids = new HashMap<>();
  private final OrtSession model;
  private final long blank;
  SyspinVoice(File pack, AtomicBoolean cancel) throws Exception {
    super(cancel);
    for (String line : Files.readAllLines(new File(pack,"tokens.txt").toPath())) {
      int split = line.lastIndexOf(' ');
      if (split > 0 && line.codePointCount(0,split) == 1)
        ids.put(line.codePointAt(0), Long.parseLong(line.substring(split+1)));
    }
    model = session(new File(pack,"model.onnx"));
    try {
      blank = Long.parseLong(model.getMetadata().getCustomMetadata().get("blank_id"));
    } catch (Exception e) { close(); throw e; }
  }

  @Override float[] render(String text, float rate) throws Exception {
    OfflineDenoiser.check(cancel);
    String spoken = text.replace("\u200c","").replace("\u200d","").replaceAll("\\s+", " ").trim();
    List<Long> tokens = new ArrayList<>();
    tokens.add(blank);
    for (int cp : spoken.codePoints().toArray()) {
      Long id = ids.get(cp);
      if (id == null) {
        int type = Character.getType(cp);
        if (Character.isLetterOrDigit(cp) || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK)
          throw new IOException("Add a pronunciation for " + new String(Character.toChars(cp)));
        id = ids.get((int)' ');
      }
      tokens.add(id);
      tokens.add(blank);
    }
    long[] data = new long[tokens.size()];
    for (int i=0;i<data.length;i++) data[i]=tokens.get(i);
    try (OnnxTensor chars=OnnxTensor.createTensor(env,LongBuffer.wrap(data),new long[]{1,data.length});
         OnnxTensor count=OnnxTensor.createTensor(env,new long[]{data.length});
         OnnxTensor scales=floats(new float[]{.667f,1f/rate,.8f},3);
         OrtSession.Result result=model.run(Map.of("input",chars,"input_lengths",count,"scales",scales))) {
      OfflineDenoiser.check(cancel);
      return array(result.get(0));
    }
  }
}
