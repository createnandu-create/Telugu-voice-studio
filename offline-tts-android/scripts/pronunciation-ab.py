#!/usr/bin/env python3
"""Offline host A/B harness. Requires sherpa-onnx 1.12.26, numpy and onnxruntime 1.31.
Set TINY_ROOT, PIPER_MODELS (converted .onnx), PIPER_ORIGINALS (per-voice .onnx.json),
ESPEAK_PARENT (contains espeak-ng-data), and optional AB_OUTPUT. No files are uploaded.
Download pinned models explicitly before running; see docs/evaluation/COMPOSE_V04.md.
"""
import pathlib,sys,json,ctypes,glob,unicodedata,time,wave,hashlib,os
repo=pathlib.Path(__file__).resolve().parents[2]
tiny=os.environ['TINY_ROOT'];models=os.environ['PIPER_MODELS'];originals=os.environ['PIPER_ORIGINALS'];espeak=os.environ['ESPEAK_PARENT']
import numpy as np
import sherpa_onnx
sys.path.insert(0,tiny)
from indic_tiny_tts import TinyTTSOnnx
out=pathlib.Path(os.environ.get('AB_OUTPUT','pronunciation-ab'));out.mkdir(parents=True,exist_ok=True)
texts=['నమస్కారం','నమస్కారం ఫ్రెండ్స్!','ఇప్పుడు ఇక్కడ క్లిక్ చేయండి.','ఇది చాలా ముఖ్యమైన విషయం. జాగ్రత్తగా వినండి.']
lib=ctypes.CDLL(glob.glob(str(pathlib.Path(sherpa_onnx.__file__).parent/'lib/_sherpa_onnx*.so'))[0]);lib.espeak_Initialize.argtypes=[ctypes.c_int,ctypes.c_int,ctypes.c_char_p,ctypes.c_int];lib.espeak_Initialize(2,0,os.fsencode(espeak),0);lib.espeak_SetVoiceByName(b'te');lib.espeak_TextToPhonemes.argtypes=[ctypes.POINTER(ctypes.c_void_p),ctypes.c_int,ctypes.c_int];lib.espeak_TextToPhonemes.restype=ctypes.c_char_p
config=json.load(open(pathlib.Path(originals)/'venkatesh/te_IN-venkatesh-medium.onnx.json'));tokens=config['phoneme_id_map'];log=[]
for text in texts:
 buf=ctypes.create_string_buffer(text.encode());ptr=ctypes.cast(buf,ctypes.c_void_p);phones=[]
 while ptr.value:
  s=lib.espeak_TextToPhonemes(ctypes.byref(ptr),1,2)
  if s:phones.append(s.decode())
 log.append(dict(text=text,nfc=unicodedata.normalize('NFC',text),codepoints=[f'U+{ord(c):04X}' for c in text],phonemes=phones,token_ids=[[tokens.get(c,[]) for c in p] for p in phones],note='eSpeak API phonemes from same sherpa shared library; full Piper adds BOS/EOS/padding and punctuation'))
(out/'phoneme-audit.json').write_text(json.dumps(log,ensure_ascii=False,indent=2))
rows=[]
def save(engine,voice,index,samples,rate,elapsed):
 samples=np.asarray(samples,dtype=np.float32);name=f'{engine}-{voice}-{index+1}.wav';p=out/name
 with wave.open(str(p),'wb') as w:w.setparams((1,2,rate,0,'NONE',''));w.writeframes((np.clip(samples,-1,1)*32767).astype('<i2').tobytes())
 rms=[float(np.sqrt(np.mean(samples[i:i+int(rate*.01)]**2))) for i in range(0,min(len(samples),rate//2),int(rate*.01))]
 rows.append(dict(engine=engine,voice=voice,text=texts[index],file=name,rate=rate,seconds=len(samples)/rate,render_seconds=elapsed,first_500ms_rms_10ms=rms,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
for voice in ['venkatesh','padmavathi']:
 cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f'{models}/{voice}.onnx',tokens=str(repo/f'offline-tts-android/app/src/main/assets/{voice}-tokens.txt'),data_dir=str(pathlib.Path(espeak)/'espeak-ng-data')),num_threads=2,debug=False),max_num_sentences=1)
 tts=sherpa_onnx.OfflineTts(cfg)
 for i,text in enumerate(texts):
  start=time.monotonic();a=tts.generate(text,sid=0,speed=1);save('piper',voice,i,a.samples,a.sample_rate,time.monotonic()-start)
 del tts
print('Piper raw samples complete',flush=True)
tts=TinyTTSOnnx.from_pretrained(tiny,threads=2)
for voice in ['male','female']:
 for i,text in enumerate(texts):
  start=time.monotonic();a=tts.synthesize(text,voice=voice,seed=0);save('indic-tiny',voice,i,a,tts.sample_rate,time.monotonic()-start)
(out/'metrics.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));print('All 16 raw A/B WAVs generated',flush=True)
