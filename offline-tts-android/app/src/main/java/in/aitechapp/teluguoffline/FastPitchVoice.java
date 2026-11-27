package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class FastPitchVoice extends AlternativeVoice {
  static final String TOPOLOGY_SHA="0b25a3e0ac90d65e74915fd6bf25221f9e5f31bf1362e185aa331550fe4b558e";
  // VitsCharacters: pad, punctuation, graphemes, blank. Duplicate symbols use the last ID.
  private static final String SYMBOLS = "!¡'(),-.:;¿?  !',.:;?oంఃఅఆఇఈఉఊఋఎఏఐఒఓఔకఖగఘఙచఛజఝఞటఠడఢణతథదధనపఫబభమయరఱలళవశషసహాిీుూృెేైొోౌ్”\uFEFF";
  private final OrtSession acoustic, vocoder;
  private final long speaker;

  FastPitchVoice(File folder, boolean male, AtomicBoolean cancel) throws OrtException {
    super(cancel);
    speaker = male ? 1 : 0;
    try {
      acoustic = session(new File(folder, "fastpitch-pace.pb"));
      vocoder = session(new File(folder, "hifigan.onnx"));
    } catch (OrtException e) { close(); throw e; }
  }

  private static Map<String,OnnxTensor> inputs(OnnxTensor text, OnnxTensor speaker, OnnxTensor pace) {
    Map<String,OnnxTensor> input = new HashMap<>();
    input.put("length_scale",pace); input.put("text", text); input.put("speaker_id", speaker); return input;
  }

  float[] render(String text,float rate) throws Exception {
    String clean = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace("‌", "").replace("‍", "").replace('।', '.').replace('–', '-').replace('’', '\'');
    // Keep the brand in one acoustic request without a punctuation pause.
    String channel = speaker == 1 ? "ఏఐటెక్ యాప్" : "ఏ ఐ టెక్యాప్";
    clean = clean.replace("ఏఐ టెక్ యాప్", channel).replace("ఏ ఐ టెక్ యాప్", channel);
    long[] ids = new long[clean.length()];
    for (int i=0; i<ids.length; i++) {
      int index = SYMBOLS.lastIndexOf(clean.charAt(i));
      if (index < 0) throw new IOException("Unsupported Telugu narrator character: " + clean.charAt(i));
      ids[i] = index + 1;
    }
    if (ids.length == 0) throw new IOException("Empty spoken phrase");
    try (OnnxTensor tokens = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), new long[]{1,ids.length});
         OnnxTensor voice = OnnxTensor.createTensor(env, new long[]{speaker});
         OnnxTensor pace=floats(new float[]{1f/rate});
         OrtSession.Result mel = acoustic.run(inputs(tokens,voice,pace))) {
      OfflineDenoiser.check(cancel);
      try (OrtSession.Result out = vocoder.run(Collections.singletonMap("mel",(OnnxTensor)mel.get(0)))) {
        return array(out.get(0));
      }
    }
  }
}
