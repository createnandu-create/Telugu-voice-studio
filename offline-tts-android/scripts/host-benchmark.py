import sys,pathlib,time,json,resource,wave,hashlib
import argparse
p=argparse.ArgumentParser()
p.add_argument("--original-models",type=pathlib.Path,required=True)
p.add_argument("--phonemes",type=pathlib.Path,required=True)
p.add_argument("--output",type=pathlib.Path,required=True)
a=p.parse_args()
import numpy as np
import sherpa_onnx
repo=pathlib.Path(__file__).resolve().parents[1]/'app/src/main'
work=a.output;work.mkdir(parents=True,exist_ok=True)
phrases=[
'నమస్కారం ఫ్రెండ్స్! ఈ రోజు మన ఫోన్ లో ఫోటోను అందంగా ఎలా మార్చాలో తెలుసుకుందాం.',
'ముందుగా పిక్సెల్ ల్యాబ్ యాప్ ఓపెన్ చేయండి. ఇప్పుడు ఇక్కడ ఉన్న టెక్స్ట్ ఆప్షన్ మీద క్లిక్ చేయండి.',
'మనకు కావలసిన తెలుగు పదాలను టైప్ చేయండి. తర్వాత మీకు నచ్చిన ఫాంట్ ని సెలెక్ట్ చేసుకోండి.',
'వావ్! ఎంత అందంగా వచ్చిందో చూడండి. రంగులు మార్చితే అక్షరాలు మరింత స్పష్టంగా కనిపిస్తాయి.',
'ఇది చాలా ముఖ్యమైన విషయం. జాగ్రత్తగా వినండి. మీ అసలు ఫోటోకు ముందు ఒక కాపీ సేవ్ చేసుకోండి.',
'అక్షరాలు చాలా చిన్నగా ఉంటే చదవడం కష్టంగా ఉంటుంది. అందుకే సరైన పరిమాణాన్ని ఎంచుకోండి.',
'ఫోటో వెనుక ఉన్న రంగును బట్టి తెలుపు లేదా నలుపు అక్షరాలు ఉపయోగించవచ్చు.',
'ఇప్పుడు చేసిన మార్పులను ఒకసారి పరిశీలించండి. ఏమైనా తప్పులు ఉంటే సరిచేసుకోండి.',
'చివరగా సేవ్ బటన్ నొక్కండి. మీ వీడియోలో ఉపయోగించడానికి చిత్రం సిద్ధంగా ఉంది.',
'ఈ విధంగా ఒక్కో దశను నెమ్మదిగా నేర్చుకుంటే పని సులభంగా పూర్తవుతుంది. ధన్యవాదాలు.'
]
results=[]
for name in ['venkatesh','padmavathi']:
 original=a.original_models/name/('te_IN-'+name+'-medium.onnx')
 model=work/(name+'.onnx');model.write_bytes(original.read_bytes()+(repo/'assets/piper-metadata.bin').read_bytes())
 cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(model),tokens=str(repo/'assets'/f'{name}-tokens.txt'),data_dir=str(a.phonemes)),num_threads=2,debug=False),max_num_sentences=1)
 load=time.monotonic();tts=sherpa_onnx.OfflineTts(cfg);load=time.monotonic()-load
 start=time.monotonic();samples=0;chunks=0;peak=0;output=work/(name+'-host.wav');text=[]
 with wave.open(str(output),'wb') as out:
  out.setnchannels(1);out.setsampwidth(2);out.setframerate(22050)
  # Male sustained 10-minute stress render; female covers all 10 distinct phrases.
  while samples/22050<(600 if name=='venkatesh' else 1) or chunks<10:
   line=phrases[chunks%len(phrases)];audio=tts.generate(line,sid=0,speed=1.0)
   assert audio.sample_rate==22050 and len(audio.samples)>0 and np.isfinite(audio.samples).all()
   peak=max(peak,float(np.max(np.abs(audio.samples))))
   out.writeframes((np.clip(audio.samples,-1,1)*32767).astype('<i2').tobytes());out.writeframes(bytes(22050//4*2));samples+=len(audio.samples)+22050//4;chunks+=1;text.append(line)
 duration=samples/22050;elapsed=time.monotonic()-start
 result=dict(voice=name,platform='Linux x86_64 CPU host; NOT Android phone',engine=sherpa_onnx.__version__,model_sha256=hashlib.sha256(model.read_bytes()).hexdigest(),load_seconds=load,render_seconds=elapsed,audio_seconds=duration,real_time_factor=elapsed/duration,chunks=chunks,process_peak_rss_kib=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,pcm_float_peak=peak,quality_score=None,sha256=hashlib.sha256(output.read_bytes()).hexdigest())
 results.append(result);print(json.dumps(result),flush=True);(work/(name+'-script.txt')).write_text('\n'.join(text));del tts
(work/'metrics.json').write_text(json.dumps(results,indent=2))
