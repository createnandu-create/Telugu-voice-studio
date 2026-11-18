package in.aitechapp.teluguoffline;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReleaseMp3 {
  public static void main(String[] args) throws Exception {
    File dir = new File(args[0]);
    for (File wav : dir.listFiles((folder,name) -> name.endsWith("_Clean.wav"))) {
      File mp3 = new File(dir, wav.getName().replace(".wav", ".mp3"));
      Mp3Export.encode(wav, mp3, new AtomicBoolean(), (done,total) -> {});
    }
  }
}
