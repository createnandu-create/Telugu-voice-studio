package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Telugu INT8 autoregressive speech with native speaker and reference-frame conditioning. */
final class HimaVoice extends AlternativeVoice {
  private static final int WIDTH=576, LATENT=32, CACHES=24, LIMIT=375;
  private final Map<String,float[]> weights, voices;
  private final OrtSession step, frame, decoder;
  private final HimaTokenizer tokenizer;
  private final String name;
  private final HimaCondition condition;

  HimaVoice(File dir, String name, AtomicBoolean cancel) throws Exception {
    this(dir,dir,name,null,cancel);
  }

  HimaVoice(File dir, File graphDir, String name, HimaCondition condition, AtomicBoolean cancel) throws Exception {
    super(cancel);
    this.condition=condition;
    this.name = name;
    weights = tensors(new File(dir,"prefix.safetensors"));
    voices = condition == null ? tensors(new File(dir,"voices.safetensors")) : Collections.emptyMap();
    tokenizer = new HimaTokenizer(new File(dir,"tokenizer.model"));
    try {
      step=session(new File(graphDir,"step_int8.onnx"));
      frame=session(new File(graphDir,"frame_int8.onnx"));
      decoder=session(new File(dir,"decoder_int8.onnx"));
    } catch (OrtException e) { close(); throw e; }
  }

  private static Map<String,float[]> tensors(File file) throws Exception {
    ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
    int header = Math.toIntExact(data.getLong());
    if (header <= 0 || header > data.remaining()) throw new IOException("Invalid tensor header");
    byte[] json = new byte[header];
    data.get(json);
    JSONObject info = new JSONObject(new String(json,StandardCharsets.UTF_8));
    Map<String,float[]> result = new HashMap<>();
    for (Iterator<String> it=info.keys(); it.hasNext();) {
      String key=it.next();
      if (key.equals("__metadata__")) continue;
      JSONObject meta=info.getJSONObject(key);
      JSONArray range=meta.getJSONArray("data_offsets");
      int start=range.getInt(0), end=range.getInt(1);
      if (!meta.getString("dtype").equals("F32") || start<0 || end<start
          || end>data.limit()-8-header || (end-start)%4!=0) throw new IOException("Invalid tensor "+key);
      data.position(8+header+start);
      float[] values=new float[(end-start)/4];
      data.asFloatBuffer().get(values);
      result.put(key,values);
    }
    return result;
  }

  private float[] project(String key, float[] input, int offset, int size, String biasKey) {
    float[] w=weights.get(key), bias=biasKey==null?null:weights.get(biasKey);
    float[] y=new float[WIDTH];
    for (int i=0;i<WIDTH;i++) {
      float sum=bias==null?0:bias[i];
      for (int j=0;j<size;j++) sum+=input[offset+j]*w[i*size+j];
      y[i]=sum;
    }
    return y;
  }

  private float[] prefix(int[] ids, float[] vec, float[] ref) {
    int refs=ref.length/LATENT, n=ids.length+refs+3;
    float[] data=new float[2*n*WIDTH], embeddings=weights.get("text_embed.weight");
    System.arraycopy(weights.get("bos_text"),0,data,0,WIDTH);
    for (int i=0;i<ids.length;i++)
      System.arraycopy(project("text_up.weight",embeddings,ids[i]*256,256,null),0,data,(1+i)*WIDTH,WIDTH);
    int at=(1+ids.length)*WIDTH;
    System.arraycopy(project("spk_in.weight",vec,0,192,"spk_in.bias"),0,data,at,WIDTH);
    float[] normalized=new float[LATENT], mean=weights.get("emb_mean"), std=weights.get("emb_std");
    for (int i=0;i<refs;i++) {
      for (int j=0;j<LATENT;j++) normalized[j]=(ref[i*LATENT+j]-mean[j])/std[j];
      at+=WIDTH;
      System.arraycopy(project("ref_in.weight",normalized,0,LATENT,"ref_tag"),0,data,at,WIDTH);
    }
    System.arraycopy(project("frame_in.weight",weights.get("bos_frame"),0,LATENT,null),0,data,at+WIDTH,WIDTH);
    System.arraycopy(data,0,data,n*WIDTH,n*WIDTH);
    float[] blank=project("text_up.weight",embeddings,16000*256,256,null);
    for (int i=0;i<ids.length;i++) System.arraycopy(blank,0,data,(n+1+i)*WIDTH,WIDTH);
    return data;
  }

  private OrtSession.Result advance(float[] rows, OnnxTensor empty, OrtSession.Result previous) throws OrtException {
    try (OnnxTensor input=floats(rows,2,rows.length/(2*WIDTH),WIDTH)) {
      Map<String,OnnxTensor> feed=new HashMap<>();
      feed.put("rows",input);
      for(int i=0;i<CACHES;i++) feed.put("past_"+i,previous==null?empty:(OnnxTensor)previous.get(i+1));
      return step.run(feed);
    }
  }

  static String spokenForm(String text) {
    // Telugu shaping controls render a glyph correctly in the editor, but the
    // acoustic tokenizer may fall back to UTF-8 bytes for them.
    return text.replace("‌","").replace("‍","")
        .replace("సబ్స్క్రైబ్","సబ్ స్క్రైబ్").replace("ఆఫ్లైన్","ఆఫ్ లైన్")
        .replace("ఎక్స్పోర్ట్","ఎక్స్ పోర్ట్").replace("ఎక్స్ప్లనేషన్","ఎక్స్ ప్లనేషన్")
        .replace("ఏఐ టెక్","ఏ ఐ టెక్");
  }

  float[] render(String text, float rate) throws Exception {
    text=spokenForm(text);
    // Karthik tends to omit the initial consonants of this isolated app name in lists.
    if (name.equals("Karthik") && text.trim().equals("పిక్సెల్ ల్యాబ్,")) text="పిక్సెల్ ల్యాబ్!";
    // The planner inserts the following breath when merging segments. A standalone
    // prompt must also tell the autoregressive model where the utterance ends.
    String prompt=text.trim().replaceFirst("[,;:，；：]+$", ".");
    if (!prompt.matches("(?s).*[.!?।！？…]$")) prompt+=".";
    int[] ids=tokenizer.encode(prompt);
    float[] vec=condition==null?voices.get("vec_"+name):condition.speaker;
    float[] ref=condition==null?voices.get("ref_"+name):condition.ref;
    if (vec==null || ref==null) throw new IOException("Missing Hima speaker "+name);
    float[] rows=prefix(ids,vec,ref), head=project("head_voice.weight",vec,0,192,null);
    int chars=(int)text.codePoints().filter(c -> Character.isLetterOrDigit(c) || (c>=0xc00 && c<=0xc7f)).count();
    int limit=Math.min(LIMIT,Math.max(40,(int)Math.ceil((.184*chars+1)*12.5)));
    Random random=new Random(31L*text.hashCode()+name.hashCode()+Float.floatToIntBits(rate)-Float.floatToIntBits(1f));
    for(int attempt=0;attempt<6;attempt++) {
      float[] pcm=take(rows,head,Math.min(LIMIT,limit + attempt * 12),random);
      if (pcm != null) {
        float[] spoken = ConversationTiming.trimPadding(pcm, 24000);
        if (audible(spoken)) return spoken;
      }
    }
    throw new ConversationTiming.UnusableTake("Hima could not finish this phrase clearly. Shorten the sentence and retry.");
  }

  private static boolean audible(float[] pcm) {
    int silent=0; boolean speech=false;
    for(int i=0;i<pcm.length;i+=2400) {
      int end=Math.min(i+2400,pcm.length); double energy=0;
      for(int j=i;j<end;j++) energy+=pcm[j]*pcm[j];
      if (energy/(end-i)>0.0000031623) {speech=true; silent=0;}
      else if(speech && ++silent>=30) return false;
    }
    return speech;
  }

  private float[] take(float[] rows, float[] head, int limit, Random rng) throws Exception {
    float[] latents=new float[limit*LATENT], mean=weights.get("emb_mean"), std=weights.get("emb_std");
    try (OnnxTensor empty=floats(new float[0],2,9,0,64);
         OnnxTensor speaker=floats(head,1,WIDTH); OnnxTensor guidance=floats(new float[]{1.25f},1)) {
      OrtSession.Result past=advance(rows,empty,null);
      try {
        for(int t=0;t<limit;t++) {
          OfflineDenoiser.check(cancel);
          float[] noise=new float[LATENT];
          for(int i=0;i<LATENT;i++) noise[i]=(float)(.54772256*rng.nextGaussian());
          try (OnnxTensor eps=floats(noise,1,LATENT)) {
            Map<String,OnnxTensor> feed=new HashMap<>();
            feed.put("z",(OnnxTensor)past.get(0)); feed.put("guidance",guidance);
            feed.put("head",speaker); feed.put("noise",eps);
            try(OrtSession.Result next=frame.run(feed)) {
              if(array(next.get(1))[0]>-1) {
                if(t==0) return null;
                try(OnnxTensor frames=floats(Arrays.copyOf(latents,t*LATENT),1,t,LATENT);
                    OrtSession.Result wav=decoder.run(Collections.singletonMap("frames",frames))) {
                  return array(wav.get(0));
                }
              }
              float[] x=array(next.get(0));
              for(int j=0;j<LATENT;j++) latents[t*LATENT+j]=x[j]*std[j]+mean[j];
              float[] row=array(next.get(2)), pair=new float[2*WIDTH];
              System.arraycopy(row,0,pair,0,WIDTH); System.arraycopy(row,0,pair,WIDTH,WIDTH);
              OrtSession.Result updated=advance(pair,empty,past);
              past.close(); past=updated;
            }
          }
        }
        return null;
      } finally { past.close(); }
    }
  }
}
