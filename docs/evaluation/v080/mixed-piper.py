import sherpa_onnx,numpy as np,wave,json,pathlib,time,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument("--output",required=True);p.add_argument("--models",required=True);p.add_argument("--espeak-data",required=True);a0=p.parse_args()
root=pathlib.Path(a0.output);text=(root/'script.txt').read_text().split('Speech-only input:\n')[1].strip();rows=[]
for key,name in [('padmavathi','Padmavathi'),('venkatesh','Venkatesh')]:
 cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(pathlib.Path(a0.models)/(key+'-fixed.onnx')),tokens='offline-tts-android/app/src/main/assets/'+key+'-tokens.txt',data_dir=a0.espeak_data),num_threads=2,debug=False),max_num_sentences=1)
 engine=sherpa_onnx.OfflineTts(cfg);start=time.monotonic();a=engine.generate(text,sid=0,speed=1);elapsed=time.monotonic()-start
 assert a.sample_rate==22050 and np.isfinite(a.samples).all()
 p=root/(name+'-raw.wav')
 with wave.open(str(p),'wb') as w:w.setparams((1,2,a.sample_rate,0,'NONE',''));w.writeframes((np.clip(a.samples,-1,1)*32767).astype('<i2').tobytes())
 rows.append(dict(voice=name,native_rate=a.sample_rate,seconds=len(a.samples)/a.sample_rate,host_seconds=elapsed,peak=float(np.abs(a.samples).max()),clipped_samples=int((np.abs(a.samples)>=1).sum())))
 print(rows[-1],flush=True);del engine
(root/'piper-metrics.json').write_text(json.dumps(rows,indent=2))
