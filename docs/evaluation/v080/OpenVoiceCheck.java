package in.aitechapp.teluguoffline;
import java.io.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
public class OpenVoiceCheck {public static void main(String[] a)throws Exception {
 File src=new File(a[1]),ref=new File(a[2]);AtomicBoolean cancel=new AtomicBoolean();
 try(OpenVoiceEngine e=new OpenVoiceEngine(new File(a[0]),cancel)) {
  float[] x=Arrays.copyOf(OpenVoiceEngine.read(src),22050*5),r=OpenVoiceEngine.read(ref);
  long start=System.nanoTime();float[] target=e.embedding(r,22050);float[] source=e.embedding(x,22050);
  float[] out=e.convert(x,22050,source,target);PcmWave.write(new File(a[3]),out,22050);
  System.out.println("Java OpenVoice: "+out.length+" frames, "+((System.nanoTime()-start)/1e9)+" host seconds; 256-D target; finite output");
 }
}}
