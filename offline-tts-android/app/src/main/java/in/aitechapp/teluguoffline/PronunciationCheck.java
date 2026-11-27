package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;

/** Independent Telugu CTC check. Selects complete native takes; never edits their waveform. */
final class PronunciationCheck implements AutoCloseable {
  private final OrtEnvironment env=LocalOnnx.environment();
  private final OrtSession session;
  private final String[] vocabulary;
  private final AtomicBoolean cancel;
  // Each checker belongs to one sequential render job. The journal records this with its chunk.
  String reviewWord = "";
  private static final int N=512, BANDS=80;
  private final double[][] bank=new double[BANDS][N/2+1];
  private final int[] firstBin=new int[BANDS], endBin=new int[BANDS];
  private final double[] window=new double[N];

  PronunciationCheck(File dir,AtomicBoolean cancel) throws Exception {
    this.cancel=cancel;
    JSONArray words=new JSONArray(new String(Files.readAllBytes(new File(dir,"vocab.json").toPath()),StandardCharsets.UTF_8));
    vocabulary=new String[words.length()];
    for(int i=0;i<vocabulary.length;i++) vocabulary[i]=words.getString(i);
    double[] edge=new double[BANDS+2];
    double maximum=15+27*Math.log(8)/Math.log(6.4);
    for(int i=0;i<edge.length;i++) {
      double mel=maximum*i/(BANDS+1);
      edge[i]=mel<15 ? mel*200/3 : 1000*Math.pow(6.4,(mel-15)/27);
    }
    for(int b=0;b<BANDS;b++) for(int k=0;k<=N/2;k++) {
      double f=k*16000.0/N;
      bank[b][k]=2*Math.max(0,Math.min((f-edge[b])/(edge[b+1]-edge[b]),
          (edge[b+2]-f)/(edge[b+2]-edge[b+1])))/(edge[b+2]-edge[b]);
    }
    for(int b=0;b<BANDS;b++) {
      int start=0, end=N/2+1;
      while(start<end && bank[b][start]==0) start++;
      while(end>start && bank[b][end-1]==0) end--;
      firstBin[b]=start; endBin[b]=end;
    }
    for(int i=0;i<400;i++) window[56+i]=.5-.5*Math.cos(2*Math.PI*i/400);
    try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()) {
      opts.setIntraOpNumThreads(2); opts.setInterOpNumThreads(1);
      session=env.createSession(new File(dir,"indicconformer_int8.onnx").getPath(),opts);
    }
  }

  String recognize(float[] pcm,int hz) throws Exception {
    float[] x=OfflineDenoiser.resample(pcm,hz,16000,cancel);
    if(x.length<320) return "";
    for(int i=x.length-1;i>0;i--) x[i]-=.97f*x[i-1];
    int frames=x.length/160+1;
    float[] data=new float[BANDS*frames];
    double[] re=new double[N], im=new double[N], power=new double[N/2+1];
    for(int t=0;t<frames;t++) {
      OfflineDenoiser.check(cancel);
      for(int j=0;j<N;j++) {
        int i=Math.abs(t*160+j-N/2);
        if(i>=x.length) i=2*x.length-2-i;
        re[j]=window[j]*x[i]; im[j]=0;
      }
      LateReverb.fft(re,im,false);
      for(int k=0;k<power.length;k++) power[k]=re[k]*re[k]+im[k]*im[k];
      for(int b=0;b<BANDS;b++) {
        double e=0;
        for(int k=firstBin[b];k<endBin[b];k++) e+=bank[b][k]*power[k];
        data[b*frames+t]=(float)Math.log(Math.max(1e-10,e));
      }
    }
    for(int b=0;b<BANDS;b++) {
      int off=b*frames; double mean=0,squares=0;
      for(int t=0;t<frames;t++) mean+=data[off+t];
      mean/=frames;
      for(int t=0;t<frames;t++) squares+=Math.pow(data[off+t]-mean,2);
      double sd=Math.max(1e-5,Math.sqrt(squares/(frames-1)));
      for(int t=0;t<frames;t++) data[off+t]=(float)((data[off+t]-mean)/sd);
    }
    try(OnnxTensor features=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,BANDS,frames});
        OnnxTensor length=OnnxTensor.createTensor(env,new long[]{frames});
        OrtSession.Result r=session.run(Map.of("audio_signal",features,"length",length))) {
      OnnxTensor scores=(OnnxTensor)r.get(0);
      long[] shape=scores.getInfo().getShape();
      int classes=Math.toIntExact(shape[2]), last=-1;
      FloatBuffer logits=scores.getFloatBuffer();
      StringBuilder decoded=new StringBuilder();
      for(int t=0;t<shape[1];t++) {
        int best=0; float max=logits.get();
        for(int i=1;i<classes;i++){float value=logits.get();if(value>max){best=i;max=value;}}
        if(best!=last && best<vocabulary.length) decoded.append(vocabulary[best]);
        last=best;
      }
      return decoded.toString().replace('▁',' ').trim();
    }
  }

  private static String letters(String text) {
    return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFC).replaceAll("[^\\p{L}\\p{M}\\p{N}]","");
  }

  private static boolean hasFullBrand(String heard) {
    String transcript = letters(heard);
    int start = transcript.indexOf("ఏఐటె");
    if (start < 0) return false;
    int app = transcript.indexOf("యాప్", start);
    return app >= 0 && app - start <= 14;
  }

  interface PhraseSynthesizer {
    float[] generate(String text, float rate) throws Exception;
  }

  private static final class Take {
    final float[] pcm;
    final WordCoverage coverage;
    final boolean missingBrand;
    final double loss;

    Take(float[] pcm, String text, String heard) {
      this.pcm = pcm;
      coverage = WordCoverage.compare(text, heard);
      String target = letters(text);
      missingBrand = target.contains("ఏఐటెక్") && !hasFullBrand(heard);
      double penalty = missingBrand ? .45 : 0;
      if (target.contains("వాట్ఏపర్ఫెక్ట్") && !letters(heard).contains("వాట్ఏ")) penalty += .25;
      loss = coverage.characterError + .28 * Math.min(3, coverage.missing) + penalty;
    }
  }

  static float[] render(PronunciationCheck checker, String text, float rate, int hz,
                        boolean directed, PhraseSynthesizer synth) throws Exception {
    float[] original = ConversationTiming.render(text, rate, hz, directed, speed -> synth.generate(text, speed));
    if (checker == null) return original;
    checker.reviewWord = "";
    if (original.length > hz * 30) {
      checker.reviewWord = "Long phrase outside the recognition window";
      return original;
    }
    Take best = new Take(original, text, checker.recognize(original, hz));
    boolean sensitive = best.missingBrand || letters(text).contains("వాట్ఏపర్ఫెక్ట్");
    float[] rates = sensitive ? new float[]{1.02f, rate*.95f, rate*1.04f, 1f} : new float[]{rate*.97f, rate*1.03f};
    for (float nextRate : rates) {
      if (best.loss < .22) break;
      OfflineDenoiser.check(checker.cancel);
      try {
        float[] pcm = synth.generate(text, Math.max(.5f, Math.min(2f, nextRate)));
        if (pcm.length > hz * 30 || pcm.length < hz / 5) continue;
        pcm = ConversationTiming.trimPadding(pcm, hz);
        Take next = new Take(pcm, text, checker.recognize(pcm, hz));
        if (next.loss + .015 < best.loss) best = next;
      } catch (ConversationTiming.UnusableTake rejected) {
        // Compare the remaining candidates with the intact full take.
      }
    }
    int cut = retryBoundary(text, best);
    if (cut < 0) return checker.finish(text, best);
    try {
      String left = text.substring(0, cut).trim(), right = text.substring(cut).trim();
      float[] first = ConversationTiming.trimPadding(synth.generate(left, rate), hz);
      float[] last = ConversationTiming.trimPadding(synth.generate(right, rate), hz);
      if (first.length < hz / 8 || last.length < hz / 8 || first.length + last.length > hz * 30)
        return checker.finish(text, best);
      float[] joined = Arrays.copyOf(first, first.length + last.length);
      System.arraycopy(last, 0, joined, first.length, last.length);
      Take next = new Take(joined, text, checker.recognize(joined, hz));
      if (next.loss + .015 < best.loss) best = next;
    } catch (ConversationTiming.UnusableTake rejected) {
      // Both halves must be available before replacing the full take.
    }
    return checker.finish(text, best);
  }

  private float[] finish(String text, Take selected) {
    int first = selected.coverage.firstMissingOffset;
    if (first >= 0) {
      String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
      int end = first;
      while (end < normalized.length() && !Character.isWhitespace(normalized.charAt(end))) end++;
      reviewWord = normalized.substring(first, end);
    } else if (selected.missingBrand) reviewWord = "AiTechApp";
    return selected.pcm;
  }

  private static int retryBoundary(String text, Take best) {
    java.util.regex.Matcher brand = java.util.regex.Pattern.compile("ఏ\\s*ఐ టెక్ యాప్(?:స్)?").matcher(text);
    boolean named = brand.find();
    if (best.missingBrand && named && brand.end() < text.length()
        && Character.isWhitespace(text.charAt(brand.end())) && !text.substring(brand.end()).trim().isEmpty())
      return brand.end();
    if (best.coverage.missing == 0) return -1;
    int target = best.coverage.firstMissingOffset, bestCut = -1;
    for (int i = 1; i < text.length() - 1; i++) {
      if (!Character.isWhitespace(text.charAt(i)) || Character.isWhitespace(text.charAt(i-1))) continue;
      if (named && i > brand.start() && i < brand.end()) continue;
      if (bestCut < 0 || Math.abs(i - target) < Math.abs(bestCut - target)) bestCut = i;
    }
    return bestCut;
  }

  static float[] renderNamed(PronunciationCheck checker, String text, float rate, int hz,
                             boolean directed, AlternativeVoice voice) throws Exception {
    return render(checker, text, rate, hz, directed, voice::render);
  }

  public void close() throws OrtException {session.close();}
}
