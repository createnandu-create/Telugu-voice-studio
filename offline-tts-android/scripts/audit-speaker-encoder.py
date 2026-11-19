#!/usr/bin/env python3
"""Independent NumPy/ORT comparison. Requires separately downloaded pinned encoder and Java host ORT.
Usage: PYTHONPATH=<numpy/onnxruntime> python audit-speaker-encoder.py MODEL_DIR ORT_HOST_JAR OUTPUT_DIR
Only public synthetic TTS is used. No personal embeddings or recordings are published.
"""
import hashlib,json,os,pathlib,subprocess,sys,wave
import numpy as np,onnxruntime as ort
root=pathlib.Path(__file__).resolve().parents[2];model=pathlib.Path(sys.argv[1]);jar=pathlib.Path(sys.argv[2]);out=pathlib.Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256((model/'ecapa-speaker-v1.onnx').read_bytes()).hexdigest()=='f46380bbaeddb929fb3a10ab63a4b1877a50e3d1e5fdd55a1b618d5651d3f64e'
assert hashlib.sha256((model/'fbank-80x201-f32.bin').read_bytes()).hexdigest()=='024e5073b7cfedee84408dc68dd6bafa02808fc786e67f1314e9c918297f5a63'
with wave.open(str(root/'docs/evaluation/delivery-v07/samples/tiny-female-youtube-directed.wav')) as w:
    x=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2').astype(np.float32)/32768
# Identical fixed input for both implementations; interpolation here is a test-fixture operation only.
x=np.interp(np.arange(16000*12)*1.5,np.arange(len(x)),x).astype('<f4');x.tofile(out/'speaker-input.f32')
src=root/'offline-tts-android/app/src/main/java/in/aitechapp/teluguoffline'
subprocess.run(['bash',str(root/'offline-tts-android/native/rnnoise/build.sh'),'host',str(out/'librnnoise_offline.so')],check=True)
subprocess.run(['javac','-cp',str(jar),'-d',str(out),*[str(src/(n+'.java')) for n in ['OfflineDenoiser','SpeakerFeatures','LocalOnnx','SpeakerEncoder']],str(root/'offline-tts-android/tools/SpeakerEncoderAudit.java')],check=True)
subprocess.run(['java','-Djava.library.path='+str(out),'-cp',str(jar)+os.pathsep+str(out),'in.aitechapp.teluguoffline.SpeakerEncoderAudit',str(out/'speaker-input.f32'),str(model),str(out)],check=True)
bank=np.fromfile(model/'fbank-80x201-f32.bin',dtype='<f4').reshape(201,80)
frames=np.lib.stride_tricks.sliding_window_view(np.pad(x,(200,200)),400)[::160];window=.54-.46*np.cos(2*np.pi*np.arange(400)/400)
z=np.fft.rfft(frames*window,axis=1);f=10*np.log10(np.maximum((z.real*z.real+z.imag*z.imag)@bank,1e-10));f=np.maximum(f,f.max()-80);f-=f.mean(0);f=f.astype(np.float32)
j=np.fromfile(out/'features-java.bin',dtype='<f4').reshape(-1,80);error=float(abs(f-j).max());assert error<2e-4,error
session=ort.InferenceSession(str(model/'ecapa-speaker-v1.onnx'),providers=['CPUExecutionProvider']);v=session.run(None,{'features':f[None,:,:],'feature_lens':np.array([1],dtype=np.float32)})[0][0];v/=np.linalg.norm(v)
jv=np.fromfile(out/'vector-java.bin',dtype='<f4');cos=float(np.dot(v,jv));assert cos>.99999,cos
report={'dimensions':len(jv),'frontend_java_numpy_max_error':error,'java_python_embedding_cosine':cos,'vector_l2_norm':float(np.linalg.norm(jv)),'fixture':'12 seconds public pretrained Tiny Female synthetic speech','on_device_verified':False,'cloning_supported':False}
(out/'ecapa-audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
