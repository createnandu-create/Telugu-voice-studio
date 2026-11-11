package in.aitechapp.teluguoffline;
import java.io.*;import java.util.concurrent.atomic.AtomicBoolean;
public class TinyCheck {
 public static void main(String[] args)throws Exception{
  try(TinyVoice t=new TinyVoice(new File(args[0]),new AtomicBoolean())){
   String[] texts={"నమస్కారం","నమస్కారం ఫ్రెండ్స్!","మీ అందరికీ నమస్కారం.","అమస్కారం"};
   for(boolean male:new boolean[]{true,false})for(int i=0;i<texts.length;i++){
    long at=System.nanoTime();float[] a=t.generate(texts[i],male,1f,0,false);
    String name=(male?"male":"female")+"-"+i;
    PcmWave.write(new File(args[1],name+".wav"),a,24000);
    System.out.println(name+" "+texts[i]+" "+a.length/24000.0+" sec, render "+(System.nanoTime()-at)/1e9);
   }
   float[] a=t.generate("నమస్కారం",true,1f,0,true);
   try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]+"/zero-java.f32"))){for(float x:a)out.writeInt(Integer.reverseBytes(Float.floatToIntBits(x)));}
  }
 }
}
