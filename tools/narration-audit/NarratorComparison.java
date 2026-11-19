package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NarratorComparison {
  private static final String SCRIPT = "నమస్కారం ఫ్రెండ్స్! AiTechApp నుంచి ఒక mind-blowing tech update. Alight Motion లో ఎక్స్‌పోర్ట్ చేయాలా? VN, DNA అనే పదాలు ఇక్కడ వినండి. ఈ సంక్లిష్టమైన విషయం మీకు ఎలా అనిపించింది?";
  public static void main(String[] args) throws Exception {
    File work=new File(args[0]), assets=new File(args[1]), out=new File(args[2]);
    AtomicBoolean cancel=new AtomicBoolean();
    try (InputStream in=new FileInputStream(new File(assets,"english-cmudict.bin"))) {
      DynamicEnglishPhonetizer.load(in);
    }
    Files.writeString(new File(out,"script.txt").toPath(),SCRIPT);
    List<NarrationDirector.Segment> plan=DeliveryControls.expand(
        NarrationDirector.planExact(SCRIPT,NarrationDirector.Style.AUTO),true,true);
    for(int i=4;i<ModelCatalog.VOICES.length;i++) {
      ModelCatalog.Voice voice=ModelCatalog.VOICES[i];
      List<File> pieces=new ArrayList<>();
      List<String> spoken=new ArrayList<>();
      RenderCleanup.Cleaner clean=RenderCleanup.forVoice(i,false,cancel);
      try(AlternativeVoice engine=voice.hima() ? new HimaVoice(new File(work,"hima"),voice.name,cancel)
          : new FastPitchVoice(new File(work,"indic"),voice.gender.equals("Male"),cancel)) {
        for(NarrationDirector.Segment segment:plan) {
          String input=PronunciationLexicon.apply(segment.text,Map.of(),false);
          float[] pcm=ConversationTiming.render(input,segment.rate,voice.sampleRate(),true,rate->engine.render(input,rate));
          File raw=new File(work,"raw.wav"), processed=new File(work,voice.id+"-"+pieces.size()+".wav");
          PcmWave.write(raw,pcm,voice.sampleRate());
          VoiceDeliveryDsp.process(raw,processed,DeliveryControls.question(segment.text),false,null,cancel,true,false,clean);
          pieces.add(processed); spoken.add(input);
        }
      }
      File merged=new File(work,"merged.wav");
      PcmWave.mergeRaw(pieces,plan,merged,cancel);
      StudioMastering.Report report=StudioMastering.process(merged,new File(out,voice.id+".wav"),cancel,msg->{},false);
      Files.writeString(new File(out,voice.id+"-levels.json").toPath(),report.json());
      Files.writeString(new File(out,"spoken.txt").toPath(),String.join(" ",spoken));
    }
  }
}