package in.aitechapp.teluguoffline;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Android offline baseline + script-direction integration for the existing
 * telugu-voice-testing/offline-tts-android module.
 *
 * This is NOT a neural cloning engine and DOES NOT contain Indic-Mio weights.
 * It uses local Android TTS voices ONLY and deliberately refuses cloud voices.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
  private TextToSpeech tts;
  private Voice voice;
  private Spinner voicePicker;
  private CheckBox neutralMode;
  private final ArrayList<Voice> availableOfflineVoices = new ArrayList<>();
  private EditText input;
  private Spinner styleInput;
  private TextView status;
  private TextView planPreview;
  private boolean playing=false, exporting=false;
  private List<NarrationDirector.Segment> segments=Collections.emptyList();
  private int index=0;
  private final ArrayList<File> files=new ArrayList<>();
  private final Locale te=new Locale("te","IN");
  private final NarrationDirector.Style[] styles=NarrationDirector.Style.values();

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroller=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(30,32,30,44);
    root.setBackgroundColor(Color.rgb(20,25,39));
    scroller.addView(root);setContentView(scroller);
    text(root,"తెలుగు YouTuber Narrator",26,Color.WHITE);
    text(root,"100% offline script-direction preview. Uses an installed local Android Telugu voice. Neural cloning is not yet integrated.",14,Color.LTGRAY);
    input=new EditText(this);
    input.setTextColor(Color.WHITE);input.setHintTextColor(Color.GRAY);
    input.setTextSize(19);input.setMinLines(6);
    input.setGravity(Gravity.TOP);input.setHint("తెలుగు స్క్రిప్ట్ ఇక్కడ పెట్టండి");
    input.setText("నమస్కారం ఫ్రెండ్స్! ఇప్పుడు ఇక్కడ క్లిక్ చేయండి. వావ్! ఎంత అద్భుతం జరిగింది. జాగ్రత్తగా సెట్టింగ్స్ మార్చండి. వీడియో నచ్చితే సబ్‌స్క్రైబ్ చేయండి.");
    root.addView(input,new LinearLayout.LayoutParams(-1,-2));
    text(root,"Narration Style / వీడియో కేటగిరీ",15,Color.WHITE);
    styleInput=new Spinner(this);
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
        new String[]{"AUTO","TUTORIAL","STORY","NEWS","REVIEW"});
    styleInput.setAdapter(adapter);root.addView(styleInput);
    text(root,"Select OFFLINE Telugu voice (quality sorted)",15,Color.WHITE);
    voicePicker=new Spinner(this);
    voicePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
        new String[]{"Detecting installed offline Telugu voices..."}));
    root.addView(voicePicker);
    voicePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
      @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
        if(tts!=null && position>=0 && position<availableOfflineVoices.size())
          selectVoice(availableOfflineVoices.get(position));
      }
      @Override public void onNothingSelected(AdapterView<?> p){}
    });
    neutralMode=new CheckBox(this);
    neutralMode.setText("Natural baseline (pitch 1.0, rate 1.0) — recommended");
    neutralMode.setChecked(true);neutralMode.setTextColor(Color.WHITE);
    root.addView(neutralMode);
    button(root,"1. Analyze Script / Expression Plan",()->showPlan());
    button(root,"2. Find installed OFFLINE Telugu voice",()->checkVoice());
    button(root,"3. Install offline voice data",()->installVoice());
    button(root,"▶ Speak selected voice (baseline / mild expression)",()->startSpeaking());
    button(root,"■ Stop",()->stopAll());
    button(root,"4. Save directed narration as WAV",()->startExport());
    status=text(root,"Checking offline Telugu voices...",15,Color.rgb(164,221,190));
    planPreview=text(root,"",15,Color.rgb(206,216,239));
    tts=new TextToSpeech(this,this);
    showPlan();
  }

  private TextView text(LinearLayout root,String s,int size,int color){
    TextView t=new TextView(this); t.setText(s);t.setTextSize(size);t.setTextColor(color);
    t.setPadding(0,8,0,10);root.addView(t);return t;
  }
  private void button(LinearLayout root,String s,Runnable click){
    Button b=new Button(this);b.setAllCaps(false);b.setText(s);
    root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->click.run());
  }
  private void tell(String message){runOnUiThread(()->status.setText(message));}
  private NarrationDirector.Style style(){return styles[styleInput.getSelectedItemPosition()];}
  private List<NarrationDirector.Segment> getPlan(){
    return NarrationDirector.plan(input.getText().toString(),style());
  }
  private void showPlan(){
    List<NarrationDirector.Segment> result=getPlan();
    StringBuilder sb=new StringBuilder("Local Script Director — "+result.size()+" lines\n\n");
    for(int i=0;i<result.size();i++){
      NarrationDirector.Segment s=result.get(i);
      sb.append(i+1).append(". [").append(s.label()).append("] ")
          .append(s.text).append("\n")
          .append("   Speed ").append(s.rate).append(" / pitch ").append(s.pitch)
          .append(" / pause ").append(s.pauseAfterMs).append("ms\n\n");
    }
    planPreview.setText(sb.toString());
    tell("Heuristic plan prepared locally. Review emotion tags: this is not full AI topic comprehension.");
  }
  @Override public void onInit(int rc){
    if(rc!=TextToSpeech.SUCCESS){tell("Local Android TTS initialization failed.");return;}
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
      @Override public void onStart(String id){}
      @Override public void onDone(String id){
        runOnUiThread(()->{
          if(id.startsWith("SPEAK-")&&playing){
            int n=Integer.parseInt(id.substring(6));
            if(n!=index)return;
            int pause=segments.get(index).pauseAfterMs;
            if(pause>0){
              if(tts.playSilentUtterance(pause,TextToSpeech.QUEUE_ADD,"PAUSE-"+index)!=TextToSpeech.SUCCESS)
                speakAt(index+1);
            }else speakAt(index+1);
          } else if(id.startsWith("PAUSE-")&&playing){
            int n=Integer.parseInt(id.substring(6));
            if(n==index)speakAt(n+1);
          } else if(id.startsWith("FILE-")&&exporting){
            int n=Integer.parseInt(id.substring(5));
            if(n!=index)return;
            exportAt(n+1);
          }
        });
      }
      @Override public void onError(String id){
        playing=false;exporting=false;tell("Speech engine error at "+id);
      }
      @Override public void onError(String id,int code){onError(id);}
    });
    checkVoice();
  }
  private void checkVoice(){
    if(tts==null){tell("TTS service not ready");return;}
    String previous=voice==null?null:voice.getName();
    voice=null;
    availableOfflineVoices.clear();
    Set<Voice> voices=tts.getVoices();
    if(voices!=null)for(Voice v:voices){
      if(v.getLocale()!=null && "te".equals(v.getLocale().getLanguage()) &&
          !v.isNetworkConnectionRequired()) availableOfflineVoices.add(v);
    }
    availableOfflineVoices.sort((a,b)->{
      int quality=Integer.compare(b.getQuality(),a.getQuality());
      return quality!=0?quality:Integer.compare(a.getLatency(),b.getLatency());
    });
    if(availableOfflineVoices.isEmpty()){
      voicePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
          new String[]{"No installed OFFLINE Telugu voice"}));
      tell("No offline Telugu voice installed. Install local voice data, then check again.");
      return;
    }
    ArrayList<String> labels=new ArrayList<>();
    int selected=0;
    for(int i=0;i<availableOfflineVoices.size();i++){
      Voice v=availableOfflineVoices.get(i);
      labels.add(v.getName()+" — quality "+v.getQuality()+" / latency "+v.getLatency());
      if(previous!=null && previous.equals(v.getName()))selected=i;
    }
    voicePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    voicePicker.setSelection(selected);
    selectVoice(availableOfflineVoices.get(selected));
  }
  private void selectVoice(Voice chosen){
    if(tts==null || chosen==null || chosen.isNetworkConnectionRequired())return;
    if(tts.setVoice(chosen)==TextToSpeech.SUCCESS){
      voice=chosen;
      tell("Offline voice: "+chosen.getName()+" | quality "+chosen.getQuality()+
          " | latency "+chosen.getLatency()+". Rating is not a human-listening score.");
    }else{voice=null;tell("Could not activate selected offline Telugu voice.");}
  }
  private void installVoice(){
    try{startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));}
    catch(Exception e){tell("Open Android Settings → Text-to-speech → Install Telugu voice data.");}
  }
  private boolean ready(){
    if(voice==null || tts==null){tell("Install an offline Telugu voice first. No cloud fallback.");return false;}
    return true;
  }
  private void stopAll(){
    playing=false;exporting=false;
    if(tts!=null)tts.stop();tell("Stopped.");
  }
  private void startSpeaking(){
    if(!ready())return;
    stopAll();segments=getPlan();
    if(segments.isEmpty()){tell("Enter Telugu script.");return;}
    showPlan();playing=true;speakAt(0);
  }
  private void speakAt(int n){
    if(!playing)return;
    if(n>=segments.size()){playing=false;tell("Finished local narration preview.");return;}
    index=n;
    NarrationDirector.Segment s=segments.get(n);
    boolean neutral=neutralMode!=null && neutralMode.isChecked();
    tts.setSpeechRate(neutral?1.0f:Math.max(0.94f,Math.min(1.06f,s.rate)));
    tts.setPitch(neutral?1.0f:Math.max(0.96f,Math.min(1.05f,s.pitch)));
    tell("Speaking "+(n+1)+"/"+segments.size()+": "+s.label());
    int r=tts.speak(s.text,TextToSpeech.QUEUE_ADD,new Bundle(),"SPEAK-"+n);
    if(r!=TextToSpeech.SUCCESS){playing=false;tell("Local TTS refused segment "+(n+1));}
  }
  private void startExport(){
    if(!ready())return;
    stopAll();segments=getPlan();
    if(segments.isEmpty()){tell("Enter Telugu script.");return;}
    if(segments.size()>220){tell("Export at most 220 sentences at once; split longer scripts.");return;}
    files.clear();exporting=true;exportAt(0);
  }
  private void exportAt(int n){
    if(!exporting)return;
    if(n>=segments.size()){
      exporting=false;
      new Thread(this::mergeWav).start();return;
    }
    index=n;
    NarrationDirector.Segment s=segments.get(n);
    boolean neutral=neutralMode!=null && neutralMode.isChecked();
    tts.setPitch(neutral?1.0f:Math.max(0.96f,Math.min(1.05f,s.pitch)));
    tts.setSpeechRate(neutral?1.0f:Math.max(0.94f,Math.min(1.06f,s.rate)));
    File f=new File(getCacheDir(),"directed-"+n+".wav");files.add(f);
    tell("Exporting WAV chunk "+(n+1)+"/"+segments.size()+" ["+s.label()+"]");
    int r=tts.synthesizeToFile(s.text,new Bundle(),f,"FILE-"+n);
    if(r!=TextToSpeech.SUCCESS){exporting=false;tell("TTS export refused segment "+(n+1));}
  }
  private static int le16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
  private static int le32(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
  private static void write16(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>8)&255);}
  private static void write32(OutputStream out,long v)throws IOException{for(int n=0;n<4;n++)out.write((int)(v>>(8*n))&255);}
  private static final class WavePart {
    final File f;final long offset,size;final int rate,ch;
    WavePart(File f,long offset,long size,int rate,int ch){this.f=f;this.offset=offset;this.size=size;this.rate=rate;this.ch=ch;}
  }
  private WavePart inspectWave(File f)throws IOException{
    try(RandomAccessFile raf=new RandomAccessFile(f,"r")){
      if(raf.length()<44)throw new IOException("Empty WAV chunk");
      byte[] head=new byte[12];raf.readFully(head);
      if(!"RIFF".equals(new String(head,0,4,StandardCharsets.US_ASCII)) ||
         !"WAVE".equals(new String(head,8,4,StandardCharsets.US_ASCII)))throw new IOException("Invalid WAV");
      int rate=0,ch=0,bits=0,format=0;long off=-1,len=0;
      while(raf.getFilePointer()+8<=raf.length()){
        byte[] tag=new byte[8];raf.readFully(tag);
        String type=new String(tag,0,4,StandardCharsets.US_ASCII);
        long size=le32(tag,4)&0xffffffffL,pos=raf.getFilePointer();
        if(pos+size>raf.length())throw new IOException("Malformed WAV chunk");
        if("fmt ".equals(type)){
          if(size<16)throw new IOException("Invalid fmt chunk");
          byte[] fmt=new byte[16];raf.readFully(fmt);
          format=le16(fmt,0);ch=le16(fmt,2);rate=le32(fmt,4);bits=le16(fmt,14);
        } else if("data".equals(type)){off=pos;len=size;}
        raf.seek(pos+size+(size&1));
      }
      if(off<0 || format!=1 || bits!=16 || rate<=0 || ch<1)throw new IOException("Export requires PCM 16-bit WAV");
      return new WavePart(f,off,len,rate,ch);
    }
  }
  private void mergeWav(){
    Uri uri=null;
    try{
      ArrayList<WavePart> pieces=new ArrayList<>();long total=0;
      int rate=0,ch=0;
      for(int i=0;i<files.size();i++){
        WavePart p=inspectWave(files.get(i));
        if(i==0){rate=p.rate;ch=p.ch;}
        if(rate!=p.rate||ch!=p.ch)throw new IOException("Audio formats changed between chunks");
        total+=p.size;pieces.add(p);
        if(i<files.size()-1)total+=(long)Math.round(rate*segments.get(i).pauseAfterMs/1000.0)*ch*2;
      }
      if(total>0xffffffffL-44)throw new IOException("WAV exceeds 4 GB");
      ContentValues values=new ContentValues();
      values.put(MediaStore.Audio.Media.DISPLAY_NAME,"telugu-expressive-"+System.currentTimeMillis()+".wav");
      values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");
      values.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/TeluguVoiceStudio");
      values.put(MediaStore.Audio.Media.IS_PENDING,1);
      uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);
      if(uri==null)throw new IOException("Cannot open Music output");
      try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new IOException("Missing output stream");
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));write32(out,36+total);
        out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));write32(out,16);
        write16(out,1);write16(out,ch);write32(out,rate);
        write32(out,rate*ch*2L);write16(out,ch*2);write16(out,16);
        out.write("data".getBytes(StandardCharsets.US_ASCII));write32(out,total);
        byte[] buffer=new byte[32*1024];
        for(int i=0;i<pieces.size();i++){
          WavePart p=pieces.get(i);
          try(RandomAccessFile src=new RandomAccessFile(p.f,"r")){
            src.seek(p.offset);long remain=p.size;
            while(remain>0){int count=(int)Math.min(remain,buffer.length);src.readFully(buffer,0,count);out.write(buffer,0,count);remain-=count;}
          }
          if(i<pieces.size()-1){
            int silentBytes=(int)(Math.round(rate*segments.get(i).pauseAfterMs/1000.0)*ch*2);
            Arrays.fill(buffer,(byte)0);
            while(silentBytes>0){int count=Math.min(silentBytes,buffer.length);out.write(buffer,0,count);silentBytes-=count;}
          }
        }
      }
      values.clear();values.put(MediaStore.Audio.Media.IS_PENDING,0);
      getContentResolver().update(uri,values,null,null);
      for(File f:files)f.delete();
      tell("Offline WAV saved to Music/TeluguVoiceStudio. Note: quality is limited by installed Android voice.");
    }catch(Exception e){
      if(uri!=null)try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
      tell("WAV merge failed: "+e.getMessage());
    }
  }
  @Override protected void onDestroy(){playing=false;exporting=false;if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
