package in.aitechapp.teluguoffline;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** Keeps original model weights in app-private downloads and the encoder topology in the APK. */
final class HimaClonePack {
  static final String REV="hima-clone-8fd92e39-af61ec03-v1";
  private static final String[] NAMES={"step_int8.onnx","frame_int8.onnx","codec.safetensors","encoder.pb"};
  private static final String[] URL={"https://huggingface.co/Prahaladha/hima-tts-nano-te-ONNX/resolve/8fd92e3956163815892f6adcf7e3aae91e70272b/onnx/base/step_int8.onnx",
      "https://huggingface.co/Prahaladha/hima-tts-nano-te-ONNX/resolve/8fd92e3956163815892f6adcf7e3aae91e70272b/onnx/base/frame_int8.onnx",
      "https://huggingface.co/Prahaladha/hima-tts-nano-te/resolve/af61ec03fa408d48effdaa4ee0d5ad9e54e6e4fd/codec.safetensors"};
  private static final String[] SHA={"a431d78905787f474d050ef5f9ae2bdf87f77568a7478ab0f927ce2eb1fc38f4",
      "c08f72005beee3cabe489c331de2f87ce4ed29a38e3d08e4675c77320c79a152",
      "3f525a648a15a853adc3aa357ab164361c4a058ace68be9b342b8f81d94030ba",
      "a7015fdf0b32fcbc349266d42dde139538dca0e0741cc1fb540e7587ec1d896b"};
  private static final long[] SIZE={48565039,
      11622260,
      80227596,
      1137177};
  final Context context;
  final File root;
  final AlternativePack shared;
  final SpeakerModelStore speaker;

  HimaClonePack(Context c) {
    context=c; root=new File(c.getFilesDir(),REV);
    shared=new AlternativePack(new VoiceModelStore(c).root,ModelCatalog.VOICES[4]);
    speaker=new SpeakerModelStore(c.getFilesDir());
  }
  private File file(int i) { return new File(root,NAMES[i]); }
  private File receipt(int i) { return new File(root,NAMES[i]+".receipt"); }
  boolean ready() throws IOException {
    if(!shared.ready() || !speaker.ready()) return false;
    for(int i=0;i<NAMES.length;i++) if(!InstallReceipt.ready(file(i),receipt(i),SHA[i],REV)) return false;
    return true;
  }
  void remove() throws IOException {
    File[] contents=root.listFiles();
    if(contents!=null) for(File f:contents) Files.deleteIfExists(f.toPath());
  }
  void install(AtomicBoolean cancel, VoiceModelStore.Progress progress) throws Exception {
    shared.install(cancel,progress); speaker.install(cancel,progress);
    if(!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create cloning folder");
    for(int i=0;i<NAMES.length;i++) {
      if(InstallReceipt.ready(file(i),receipt(i),SHA[i],REV)) continue;
      File part=new File(root,NAMES[i]+".download");
      if(i<URL.length) {
        String name=NAMES[i];
        new ResumableDownload().fetch(URL[i],part,SIZE[i],SHA[i],cancel,
            (bytes,total)->progress.update("Downloading Hima cloning · "+name+" · "+(100*bytes/total)+"%"));
      } else {
        try(InputStream in=new GZIPInputStream(context.getAssets().open("hima-encoder.pb.bin"));
            OutputStream out=new FileOutputStream(part)) {
          byte[] buffer=new byte[32768]; int n;
          while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
        }
      }
      if(cancel.get()) throw new java.util.concurrent.CancellationException();
      if(!FileIntegrity.matches(part,SHA[i])) throw new IOException("Hima cloning checksum failed");
      FileIntegrity.promote(part,file(i));
      if(!InstallReceipt.verify(file(i),receipt(i),SHA[i],REV)) throw new IOException("Hima cloning installation failed");
      Files.deleteIfExists(new File(part+".properties").toPath());
    }
  }
  HimaVoice voice(File profile,AtomicBoolean cancel) throws Exception {
    return new HimaVoice(shared.root,root,"Personal Hima",HimaCondition.read(new File(profile,"hima.bin")),cancel);
  }
  void preview(File folder,float[] target, AtomicBoolean cancel) throws Exception {
    HimaCondition tone=HimaCondition.extract(file(3),speaker.root,target,cancel);
    tone.save(new File(folder,"hima.bin"));
    try(HimaVoice engine=voice(folder,cancel)) {
      PcmWave.write(new File(folder,"preview.wav"),engine.render("నమస్కారం! మీరు ఎలా ఉన్నారు? ఈ రోజు మనం ఒక కొత్త విషయం తెలుసుకుందాం."),24000);
    }
  }
}
