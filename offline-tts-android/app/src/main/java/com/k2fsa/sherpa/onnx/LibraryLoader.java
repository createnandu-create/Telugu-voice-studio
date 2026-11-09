package com.k2fsa.sherpa.onnx;
/** Android loader adapted for APK-bundled native libraries. */
public final class LibraryLoader {
    private static boolean loaded;
    static synchronized void maybeLoad() {
        if (!loaded) {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("sherpa-onnx-jni");
            loaded = true;
        }
    }
}
