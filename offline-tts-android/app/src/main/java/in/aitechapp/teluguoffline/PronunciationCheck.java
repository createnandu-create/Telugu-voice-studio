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

  static double error(String expected,String heard) {
    String a=letters(expected), b=letters(heard);
    int[] row=new int[b.length()+1];
    for(int j=0;j<row.length;j++) row[j]=j;
    for(int i=1;i<=a.length();i++) {
      int prev=row[0]; row[0]=i;
      for(int j=1;j<=b.length();j++) {
        int old=row[j];
        row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),prev+(a.charAt(i-1)==b.charAt(j-1)?0:1));
        prev=old;
      }
    }
    double cost=row[b.length()]/(double)Math.max(1,a.length());
    if(a.contains("ఏఐటెక్") && !b.contains("ఏఐటె")) cost+=.45;
    if(a.contains("వాట్ఏపర్ఫెక్ట్") && !b.contains("వాట్ఏ")) cost+=.25;
    return cost;
  }

  static float[] render(PronunciationCheck checker,String text,float rate,int hz,boolean directed,
                        ConversationTiming.Synthesizer synth) throws Exception {
    float[] best=ConversationTiming.render(text,rate,hz,directed,synth);
    if(checker==null || best.length<hz/3 || best.length>hz*30) return best;
    double loss=error(text,checker.recognize(best,hz));
    // Do not spend extra inference on minor spelling variations in recognized Telugu.
    for(float ratio:new float[]{.95f,1.04f}) {
      if(loss<.22) break;
      OfflineDenoiser.check(checker.cancel);
      float[] other;
      try {other=synth.generate(Math.max(.5f,Math.min(2f,rate*ratio)));}
      catch(ConversationTiming.UnusableTake rejected){continue;}
      if(other.length<hz/3 || other.length>hz*30) continue;
      double next=error(text,checker.recognize(other,hz));
      if(next+.015<loss){best=ConversationTiming.trimPadding(other,hz);loss=next;}
    }
    return best;
  }

  public void close() throws OrtException {session.close();}
}
