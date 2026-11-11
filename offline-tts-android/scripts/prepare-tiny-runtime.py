#!/usr/bin/env python3
"""Install ORT 1.31 side by side with sherpa's existing 1.17.1 dependency.
Usage: python prepare-tiny-runtime.py onnxruntime-android-1.31.0.aar
Only library names change, not code/ELF layout. Never overwrite Piper's libonnxruntime.so.
"""
import io,pathlib,struct,sys,zipfile,hashlib
root=pathlib.Path(__file__).resolve().parents[1]/'app'
blob=pathlib.Path(sys.argv[1]).read_bytes()
# Maven Central: com.microsoft.onnxruntime:onnxruntime-android:1.31.0
expected='72683787a80d0608a3121b967d8e1239aef48286826661c953211626690540a7'
assert hashlib.sha256(blob).hexdigest()==expected, 'Unexpected upstream runtime archive'
with zipfile.ZipFile(io.BytesIO(blob)) as z:
 for original,new in [('libonnxruntime.so','libtinyort.so'),('libonnxruntime4j_jni.so','libonnxruntime4j_jni.so')]:
  data=z.read('jni/arm64-v8a/'+original)
  old=b'libonnxruntime.so\0'; replacement=b'libtinyort.so\0'.ljust(len(old),b'\0')
  assert data.count(old)==1, (original,'unexpected ELF string table')
  data=data.replace(old,replacement)
  (root/'src/main/jniLibs/arm64-v8a'/new).write_bytes(data)
 with zipfile.ZipFile(io.BytesIO(z.read('classes.jar'))) as jar, zipfile.ZipFile(root/'libs/onnxruntime-1.31.0.jar','w',zipfile.ZIP_DEFLATED) as out:
  for item in jar.infolist():
   data=jar.read(item.filename)
   if item.filename=='ai/onnxruntime/OnnxRuntime.class':
    old=b'\x01\x00\x0bonnxruntime'; new=b'\x01\x00\x07tinyort'
    assert data.count(old)==1, 'Unexpected class constant pool'
    data=data.replace(old,new)
   out.writestr(item,data)
print('Installed isolated ARM64 Tiny runtime; Piper runtime untouched.')
