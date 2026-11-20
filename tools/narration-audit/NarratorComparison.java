package in.aitechapp.teluguoffline;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NarratorComparison {
  private static final String SCRIPT = "నమస్కారం ఫ్రెండ్స్! AiTechApp నుంచి ఒక mind-blowing tech update. Alight Motion లో ఎక్స్‌పోర్ట్ చేయాలా? VN, DNA అనే పదాలు ఇక్కడ వినండి. ఈ సంక్లిష్టమైన విషయం మీకు ఎలా అనిపించింది? వావ్! వాట్ ఏ పర్ఫెక్ట్ ఎక్స్‌ప్లనేషన్ కదా! AI Tech Apps ఛానల్‌కి స్వాగతం.";
  private static final String TECHNICAL = "క్వాలిటీ రేషియో 16:9 లేదా √25 = 5 అంత ఖచ్చితంగా 4K రెసొల్యూషన్‌లో రెండర్ అవుతుంది! The frame rate is 60 fps. What is the square root of 49?";
  public static void main(String[] args) throws Exception {
    File work=new File(args[0]), assets=new File(args[1]), out=new File(args[2]);
    AtomicBoolean cancel=new AtomicBoolean();
    try (InputStream in=new FileInputStream(new File(assets,"english-cmudict.bin"))) {
      DynamicEnglishPhonetizer.load(in);
    }
    boolean fullScript=args.length==4 && args[3].equals("--demo");
    boolean technical=args.length==4 && args[3].equals("--technical");
    String script=technical ? TECHNICAL : fullScript ? Files.readString(new File(assets,"studio-demo.txt").toPath()).trim() : SCRIPT;
    Files.writeString(new File(out,"script.txt").toPath(),script);
    List<NarrationDirector.Segment> plan=DeliveryControls.expand(
        NarrationDirector.planExact(script,NarrationDirector.Style.AUTO),true,true);
    plan = TechnicalTelugu.phrases(plan,true);
    plan.removeIf(segment -> PronunciationLexicon.apply(segment.text,Map.of(),false,true).isBlank());
    for(int i=4;i<ModelCatalog.VOICES.length;i++) {
      ModelCatalog.Voice voice=ModelCatalog.VOICES[i];
      if(technical && !(voice.name.equals("Srinivas") || voice.name.equals("Karthik") || voice.id.equals("fastpitch-male"))) continue;
      if(fullScript && i!=4 && i!=5 && !voice.name.equals("Karthik")) continue;
      List<File> pieces=new ArrayList<>();
      List<String> spoken=new ArrayList<>();
      RenderCleanup.Cleaner clean=null; // Current default: preserve the native tone.
      try(PronunciationCheck checker=new PronunciationCheck(new File(work,"stt"),cancel);
          AlternativeVoice engine=voice.hima() ? new HimaVoice(new File(work,"hima"),voice.name,cancel)
          : new FastPitchVoice(new File(work,"indic"),voice.gender.equals("Male"),cancel)) {
        for(NarrationDirector.Segment segment:plan) {
          String expanded=PronunciationLexicon.apply(segment.text,Map.of(),false,true);
          String input=voice.hima() ? HimaVoice.spokenForm(expanded) : expanded;
          System.out.println(voice.name+" · "+input);
          float[] pcm=PronunciationCheck.renderNamed(checker,input,segment.rate,voice.sampleRate(),true,engine);
          File raw=new File(work,"raw.wav"), processed=new File(work,voice.id+"-"+pieces.size()+".wav");
          PcmWave.write(raw,pcm,voice.sampleRate());
          VoiceDeliveryDsp.process(raw,processed,!technical && !fullScript && DeliveryControls.question(segment.text),false,null,cancel,false,false,clean);
          pieces.add(processed); spoken.add(input);
        }
      }
      File merged=new File(work,"merged.wav");
      PcmWave.mergeRaw(pieces,plan,merged,cancel);
      StudioMastering.Report report=StudioMastering.process(merged,new File(out,voice.id+".wav"),cancel,msg->{},false);
      Files.writeString(new File(out,voice.id+"-levels.json").toPath(),report.json());
      Files.writeString(new File(out,voice.id+"-spoken.txt").toPath(),String.join(" ",spoken));
    }
  }
}