package in.aitechapp.teluguoffline;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Same 192-D speaker vector and 62 acoustic frames as the upstream recording conditioner. */
final class HimaCondition {
  static final int FRAMES=62, SAMPLES=FRAMES*1920;
  final float[] speaker, ref;
  HimaCondition(float[] speaker, float[] ref) {
    if (speaker.length!=192 || ref.length!=FRAMES*32) throw new IllegalArgumentException("Invalid voice dimensions");
    this.speaker=speaker; this.ref=ref;
  }

  static HimaCondition extract(File encoder, File speakerPack, float[] audio, AtomicBoolean cancel) throws Exception {
    if(audio.length<240000 || audio.length>360000) throw new IOException("Use a 10–15 second speech excerpt for Hima cloning");
    float[] vec=SpeakerEncoder.encode(speakerPack, OfflineDenoiser.resample(audio,24000,16000,cancel),cancel);
    // A contiguous window preserves the speaker's acoustic gestures.
    double energy=0,best=-1; int start=0;
    for(int i=0;i<audio.length;i++) {
      energy+=(double)audio[i]*audio[i];
      if(i>=SAMPLES) energy-=(double)audio[i-SAMPLES]*audio[i-SAMPLES];
      if(i>=SAMPLES-1 && energy>best) { best=energy; start=i-SAMPLES+1; }
    }
    OrtEnvironment env=OrtEnvironment.getEnvironment();
    try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()) {
      opts.setIntraOpNumThreads(2); opts.setInterOpNumThreads(1);
      try(OrtSession session=env.createSession(encoder.getPath(),opts);
          OnnxTensor samples=OnnxTensor.createTensor(env,FloatBuffer.wrap(Arrays.copyOfRange(audio,start,start+SAMPLES)),new long[]{1,1,SAMPLES});
          OrtSession.Result out=session.run(Collections.singletonMap("audio",samples))) {
        OfflineDenoiser.check(cancel);
        return new HimaCondition(vec,AlternativeVoice.array(out.get(0)));
      }
    }
  }

  void save(File dest) throws IOException {
    ByteBuffer b=ByteBuffer.allocate((192+FRAMES*32)*4).order(ByteOrder.LITTLE_ENDIAN);
    b.asFloatBuffer().put(speaker).put(ref);
    Files.write(dest.toPath(),b.array());
  }
  static HimaCondition read(File src) throws IOException {
    if(src.length()!=(192+FRAMES*32)*4) throw new IOException("Invalid private Hima voice");
    FloatBuffer b=ByteBuffer.wrap(Files.readAllBytes(src.toPath())).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    float[] vec=new float[192], ref=new float[FRAMES*32]; b.get(vec).get(ref);
    return new HimaCondition(vec,ref);
  }
}
