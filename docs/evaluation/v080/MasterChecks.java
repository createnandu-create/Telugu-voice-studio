package in.aitechapp.teluguoffline;
import java.io.*;import java.util.concurrent.atomic.AtomicBoolean;
public class MasterChecks {public static void main(String[] args)throws Exception {
 File dir=new File(args[0]);for(File f:dir.listFiles((d,n)->n.endsWith("-raw.wav"))){
  String base=f.getName().replace("-raw.wav","");File out=new File(dir,base+"-studio.wav");
  StudioMastering.Report r=StudioMastering.process(f,out,new AtomicBoolean(),m->{},false);
  FileIntegrity.write(new File(dir,base+"-mastering.json"),r.json());System.out.println(base+" "+r.json());
 }
}}
