# Repeat Java Indic Tiny inference on a host

Download the pinned Telugu package explicitly (revision in `TinyModelStore.java`) and ONNX Runtime's host Java 1.31.0 jar from Maven Central. Keep all three model graphs under one local directory. No inference network requests are made. With Java 17:

```sh
# Run from offline-tts-android. Set ORT_HOST to the unmodified host jar.
src=app/src/main/java/in/aitechapp/teluguoffline
mkdir -p /tmp/tiny-classes /tmp/tiny-audio
javac -cp "$ORT_HOST" -d /tmp/tiny-classes "$src"/{TinyVoice,PronunciationLexicon,PcmWave,WarmthEq,NarrationDirector,FileIntegrity}.java tools/TinyCheck.java
java -cp "$ORT_HOST:/tmp/tiny-classes" in.aitechapp.teluguoffline.TinyCheck "$TINY_MODEL_DIR/onnx" /tmp/tiny-audio
```

The helper emits four cases for each speaker, plus `zero-java.f32`. The fourth word deliberately lacks the initial Telugu న as a negative control; it is not an automatic pronunciation correction. Compare the zero-noise file against upstream `TinyTTSOnnx.synthesize('నమస్కారం', voice='male', noise=lambda shape: np.zeros(shape,np.float32))`. Host timings must not be described as phone benchmarks.
