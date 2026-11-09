package in.aitechapp.teluguoffline;
import java.util.Arrays;
public class AudioPolishTest {
  static void ok(boolean b,String description){if(!b)throw new AssertionError(description);}
  public static void main(String[] args) {
    for(int ch=1;ch<=2;ch++){
      final int frames=24000,channels=ch;
      byte[] input=new byte[frames*channels*2];
      for(int frame=0;frame<frames;frame++)for(int c=0;c<channels;c++){
        double y=Math.sin(2*Math.PI*440*frame/24000.0)*0.7;
        short s=(short)Math.round(y*32767);
        int i=frame*channels*2+c*2;input[i]=(byte)s;input[i+1]=(byte)(s>>8);
      }
      byte[] raw=input.clone();
      AudioPolish p=new AudioPolish(24000,ch);
      for(int from=0;from<input.length;from+=4800){int count=Math.min(4800,input.length-from);
        byte[] chunk=Arrays.copyOfRange(input,from,from+count);
        p.processInPlace(chunk,count,from,input.length);
        System.arraycopy(chunk,0,input,from,count);
      }
      ok(!Arrays.equals(input,raw),"modified");
      short first=(short)((input[0]&255)|((input[1]&255)<<8));
      ok(Math.abs(first)<500,"fades start");
      int clipped=0;for(int i=0;i<input.length;i+=2){short s=(short)((input[i]&255)|((input[i+1]&255)<<8));
        if(Math.abs((int)s)>31000)clipped++;
      }
      ok(clipped==0,"peak headroom");
      byte[] silence=new byte[4800];new AudioPolish(24000,ch).processInPlace(silence, silence.length,0,silence.length);
      for(byte b:silence)ok(b==0,"silence stays silent");
    }
    boolean threw=false;
    try{new AudioPolish(24000,1).processInPlace(new byte[7],7,0,7);}catch(IllegalArgumentException e){threw=true;}
    ok(threw,"reject malformed PCM");
    System.out.println("PASS AudioPolish: mono/stereo, streaming, fade, peak, silence, invalid PCM");
  }
}
