package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

/** Materialize a small graph that references the verified upstream weights beside it. */
final class PackTopology {
  static void prepare(Context context,File file,String digest) throws IOException {
    if(FileIntegrity.matches(file,digest)) return;
    File staged=new File(file+".staging");
    try(InputStream in=new GZIPInputStream(context.getAssets().open(file.getName()+".bin"));
        OutputStream out=new FileOutputStream(staged)) {
      byte[] b=new byte[32768]; int n;
      while((n=in.read(b))!=-1) out.write(b,0,n);
    }
    if(!FileIntegrity.matches(staged,digest)) throw new IOException("Invalid acoustic model topology");
    FileIntegrity.promote(staged,file);
  }
  private PackTopology() {}
}
