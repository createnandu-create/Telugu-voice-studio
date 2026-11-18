package in.aitechapp.teluguoffline;
import java.io.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
public class MixedCheck {
 public static void main(String[] a)throws Exception {
  DynamicEnglishPhonetizer.load(new FileInputStream(a[1]));
  String text="నమస్కారం! AiTechApp లో PixelLab. 100% క్లారిటీ. √25 = 5. 16:9, 4K, 192-D Vocal DNA. శ్రావ్యమైన స్వరం సులభమా?";
  String spoken=PronunciationLexicon.apply(text,Collections.emptyMap(),false);
  File out=new File(a[2]);out.mkdirs();FileIntegrity.write(new File(out,"script.txt"),text+"\n\nSpeech-only input:\n"+spoken+"\n");
  System.out.println(spoken);
  for(boolean male:new boolean[]{false,true}) {
   try(TinyVoice tiny=new TinyVoice(new File(a[0]),new AtomicBoolean())) {
    long start=System.nanoTime();float[] x=tiny.generate(spoken,male,1,42,false,true);
    String name=male?"IndicTinyMale":"IndicTinyFemale";PcmWave.write(new File(out,name+"-raw.wav"),x,24000);
    System.out.println(name+" "+x.length/24000.0+" s audio / "+(System.nanoTime()-start)/1e9+" host seconds");
   }
  }
 }
}
