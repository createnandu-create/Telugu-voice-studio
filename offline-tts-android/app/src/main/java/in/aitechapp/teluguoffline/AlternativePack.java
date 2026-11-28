package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Installs shared speaker packs from immutable upstream files, on explicit request. */
final class AlternativePack {
  private static final class Asset {
    final String remote, file, sha;
    final long size;
    Asset(String remote, long size, String sha) {
      this.remote=remote; file=new File(remote).getName(); this.size=size; this.sha=sha;
    }
  }
  static final String HIMA_REV="8fd92e3956163815892f6adcf7e3aae91e70272b", INDIC_REV="78f9e2ce6fc3a508e4d68a67146579adfd7add42";
  private static final Asset[] HIMA = {
    new Asset("onnx/voices/step_int8.onnx",48565039L,"a2d0dbbac3cd39f86d7b3125c405418a02d0cf1132b9e3cf64546ca4f3487f22"),
    new Asset("onnx/voices/frame_int8.onnx",11622260L,"d0b5158a3740427aa406ab2cc1691a15c29dbc9318020fcbfe650dfa71b476f3"),
    new Asset("onnx/decoder_int8.onnx",23482392L,"ca22d2751cbff909a0d98783fcc5df3315351000a60219ef6bb221b7261596fb"),
    new Asset("prefix.safetensors",18020039L,"b46229533f19d41cbcd6c294aa1ab28b9e44c37920d682e25b1b2a043a1f13cc"),
    new Asset("tokenizer.model",618690L,"3e870fd3437b3616810c66a6834dd582d55300f106bc63dec3d3aec0e53f151f"),
    new Asset("voices/voices.safetensors",50688L,"217d6836ca534cadcb82b89748e0816b14f05bb69754b05af2997014049d34fc")
  };
  private static final Asset[] INDIC = {
    new Asset("tts/fastpitch.onnx",108842190L,"37942087c650689e67c5aef4362da21a0a65ccc238b341541002e87dda5db1dd"),
    new Asset("tts/hifigan.onnx",27910570L,"81e03a670def1b06cacecd008a4c81ed3d93bc1b2dc62510c4ec69540173ca1d")
  };
  private static final Asset[] CHECKER = {
    new Asset("stt/indicconformer_int8.onnx",197021597L,"cb60980a93a6ed8fdcd875a4be1dfa5fd050eee95a6db94bfd461feb45cabdfc"),
    new Asset("stt/vocab.json",68706L,"69fb197067f139f7961e70fed986274f366c7d1c26f777aada635f6237b75d43")
  };
  private static final Asset ARCHIVE = new Asset("te-IN-1.0.0.itantra",280580175L,"79fa256cac907c2e86a8093f9c1d1bcab14d9ee1c7cc1f0b4b578386c07bf68b");

  final File root;
  private final boolean direct;
  private final String revision, url;
  private final Asset[] assets;
  AlternativePack(File parent, ModelCatalog.Voice v) {
    this(parent,v,false);
  }
  static AlternativePack pronunciation(File parent) { return new AlternativePack(parent,null,true); }

  private AlternativePack(File parent, ModelCatalog.Voice voice, boolean check) {
    boolean hima = !check && voice.hima();
    direct = hima || (!check && voice.syspin());
    if (!check && voice.syspin()) {
      boolean male = voice.gender.equals("Male");
      revision = male ? "6650e4cb5e553e80af13e5b079ffaf86c30ac19b" : "24682cdcc245b8930f177cc57108baca7103e0fb";
      assets = new Asset[]{new Asset("model.onnx", male ? 114206547 : 114221140, voice.installedSha),
          new Asset("tokens.txt", male ? 676 : 832, male ? "d451ccb72770d6bf15094a27a5eeee566799fb1ac8162663875d1286026c0c0e" : "05f3a237c7b2710bfb51fff70cc4281e0e5edc30a97dda34516a1655405c19fc")};
      root = new File(parent, voice.id + "-" + revision.substring(0,12));
      url = "https://huggingface.co/codebucketsolutions/bolo-syspin-telugu-" + (male ? "male" : "female") + "/resolve/" + revision + "/";
      return;
    }
    revision=hima?HIMA_REV:INDIC_REV;
    assets=check?CHECKER:hima?HIMA:INDIC;
    root=new File(parent,(check?"indic-check-":hima?"hima-":"indic-fastpitch-")+revision.substring(0,12));
    url="https://huggingface.co/"+(hima?"Prahaladha/hima-tts-nano-te-ONNX":"helo-ayush/itantra-models")+"/resolve/"+revision+"/";
  }

  File model() { return new File(root,assets[0].file); }
  private File receipt(Asset a) { return new File(root,a.file+".receipt"); }
  private boolean installed(Asset a) throws IOException {
    return InstallReceipt.ready(new File(root,a.file),receipt(a),a.sha,revision);
  }
  boolean ready() throws IOException {
    for(Asset a:assets) if(!installed(a)) return false;
    return true;
  }
  boolean verify() throws IOException {
    boolean ok=true;
    for(Asset a:assets) ok=InstallReceipt.verify(new File(root,a.file),receipt(a),a.sha,revision)&&ok;
    return ok;
  }
  void remove() throws IOException {
    File[] files=root.listFiles();
    if(files!=null) for(File f:files) Files.deleteIfExists(f.toPath());
  }
  long partial() {
    long bytes=0;
    File[] files=root.listFiles((d,n)->n.endsWith(".download"));
    if(files!=null) for(File f:files) bytes+=f.length();
    return bytes;
  }
  void restart() throws IOException {
    File[] files=root.listFiles((d,n)->n.endsWith(".download") || n.endsWith(".download.properties"));
    if(files!=null) for(File f:files) Files.deleteIfExists(f.toPath());
  }
  private File fetch(Asset a, AtomicBoolean cancelled, VoiceModelStore.Progress progress) throws IOException {
    File part=new File(root,a.file+".download");
    new ResumableDownload().fetch(url+a.remote,part,a.size,a.sha,cancelled,
        (bytes,total)->progress.update("Downloading "+a.file+" · "+(bytes*100/total)+"%"));
    return part;
  }
  void install(AtomicBoolean cancel, VoiceModelStore.Progress progress) throws Exception {
    if (ready()) return;
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create model folder");
    long needed = assets == CHECKER ? 490_000_000L : 430_000_000L;
    if (direct) {
      needed = 4_000_000L;
      for (Asset a : assets) if (!installed(a))
        needed += Math.max(0, a.size - new File(root, a.file + ".download").length());
    }
    if (root.getUsableSpace() < needed) throw new IOException("Insufficient storage for the model pack");
    if (direct) {
      for(Asset a:assets) {
        if(installed(a)) continue;
        File part=fetch(a,cancel,progress);
        FileIntegrity.promote(part,new File(root,a.file));
        Files.deleteIfExists(new File(part+".properties").toPath());
      }
    } else {
      File archive=fetch(ARCHIVE,cancel,progress);
      try(ZipFile zip=new ZipFile(archive)) {
        for(Asset a:assets) {
          if(installed(a)) continue;
          ZipEntry entry=zip.getEntry(a.remote);
          if(entry==null || entry.getSize()!=a.size) throw new IOException("Unexpected model archive");
          File staged=new File(root,a.file+".staging");
          try(InputStream in=zip.getInputStream(entry); OutputStream out=new FileOutputStream(staged)) {
            byte[] buffer=new byte[65536]; int n; long written=0;
            while((n=in.read(buffer))!=-1) {
              if(cancel.get()) throw new java.util.concurrent.CancellationException();
              if((written+=n)>a.size) throw new IOException("Invalid model size");
              out.write(buffer,0,n);
            }
          }
          if(!FileIntegrity.matches(staged,a.sha)) throw new IOException("Incorrect model digest");
          FileIntegrity.promote(staged,new File(root,a.file));
        }
      }
      Files.deleteIfExists(archive.toPath());
      Files.deleteIfExists(new File(archive+".properties").toPath());
    }
    if(!verify()) throw new IOException("Model pack integrity check failed");
    progress.update(assets==CHECKER ? "Offline pronunciation check ready" : "Voice pack ready offline");
  }
}
