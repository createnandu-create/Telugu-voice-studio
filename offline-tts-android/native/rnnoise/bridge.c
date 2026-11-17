#include <jni.h>
#include <stdint.h>
#include "rnnoise.h"
JNIEXPORT jlong JNICALL Java_in_aitechapp_teluguoffline_OfflineDenoiser_create(JNIEnv *e,jclass c) {
  (void)e; (void)c; return (jlong)(intptr_t)rnnoise_create(NULL);
}
JNIEXPORT void JNICALL Java_in_aitechapp_teluguoffline_OfflineDenoiser_destroy(JNIEnv *e,jclass c,jlong h) {
  (void)e; (void)c; if(h)rnnoise_destroy((DenoiseState*)(intptr_t)h);
}
JNIEXPORT jfloat JNICALL Java_in_aitechapp_teluguoffline_OfflineDenoiser_frame(JNIEnv *e,jclass c,jlong h,jfloatArray a) {
  (void)c;
  if(!h || (*e)->GetArrayLength(e,a)!=480) {
    (*e)->ThrowNew(e,(*e)->FindClass(e,"java/lang/IllegalArgumentException"),"RNNoise requires 480 samples"); return 0;
  }
  float x[480]; (*e)->GetFloatArrayRegion(e,a,0,480,x);
  float vad=rnnoise_process_frame((DenoiseState*)(intptr_t)h,x,x);
  (*e)->SetFloatArrayRegion(e,a,0,480,x); return vad;
}
