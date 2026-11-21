/* Test APK only. Feed known H.264 frames to the decoder shipped in the game APK. */
#include "video-decoder.h"
#include <dlfcn.h>
#include <stdint.h>
#include <stdlib.h>

typedef struct {
    void *library;
    ChiakiLog quiet;
    AndroidChiakiVideoDecoder decoder;
    __typeof__(android_chiaki_video_decoder_video_sample) *feed;
    __typeof__(android_chiaki_video_decoder_fini) *finish;
} Probe;

JNIEXPORT jlong JNICALL Java_cz_titulky_tv_StickProbe_open(
    JNIEnv *env, jobject unused, jobject surface, jint width, jint height)
{
    Probe *p = calloc(1, sizeof(*p));
    if (!p) return 0;
    p->library = dlopen("libchiaki-hra.so", RTLD_NOW);
    if (!p->library) goto failed;
    __typeof__(android_chiaki_video_decoder_init) *init = dlsym(p->library, "android_chiaki_video_decoder_init");
    __typeof__(android_chiaki_video_decoder_set_surface) *attach = dlsym(p->library, "android_chiaki_video_decoder_set_surface");
    p->feed = dlsym(p->library, "android_chiaki_video_decoder_video_sample");
    p->finish = dlsym(p->library, "android_chiaki_video_decoder_fini");
    if (!init || !attach || !p->feed || !p->finish) goto failed;
    if (init(&p->decoder, &p->quiet, width, height, CHIAKI_CODEC_H264) != CHIAKI_ERR_SUCCESS) goto failed;
    attach(&p->decoder, env, surface);
    if (p->decoder.codec) return (jlong)(intptr_t)p;
    p->finish(&p->decoder);
failed:
    if (p->library) dlclose(p->library);
    free(p);
    return 0;
}

JNIEXPORT jboolean JNICALL Java_cz_titulky_tv_StickProbe_feed(
    JNIEnv *env, jobject unused, jlong ptr, jbyteArray sample)
{
    Probe *p = (Probe *)(intptr_t)ptr;
    jbyte *data = (*env)->GetByteArrayElements(env, sample, NULL);
    if (!data) return JNI_FALSE;
    bool ok = p->feed((uint8_t *)data, (*env)->GetArrayLength(env, sample), &p->decoder);
    (*env)->ReleaseByteArrayElements(env, sample, data, JNI_ABORT);
    return ok;
}

JNIEXPORT void JNICALL Java_cz_titulky_tv_StickProbe_close(JNIEnv *env, jobject unused, jlong ptr)
{
    Probe *p = (Probe *)(intptr_t)ptr;
    p->finish(&p->decoder);
    dlclose(p->library);
    free(p);
}
