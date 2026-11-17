/* Exercise the actual upstream decoder and Chiaki pthread code, not a rewritten shutdown. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include "audio-decoder.c"

struct AMediaCodec {
    pthread_mutex_t mutex;
    pthread_cond_t changed;
    bool eos;
    uint8_t input[2];
};
struct AMediaFormat { int unused; };
static struct AMediaCodec codec;
static struct AMediaFormat format;
static int input_calls, fail_at, queued_bytes, deleted;
static atomic_int released;

void chiaki_log(ChiakiLog *log, ChiakiLogLevel level, const char *fmt, ...) {
    (void)log; (void)level; (void)fmt;
}
AMediaCodec *AMediaCodec_createDecoderByType(const char *mime) {
    (void)mime;
    assert(!pthread_mutex_init(&codec.mutex, NULL));
    assert(!pthread_cond_init(&codec.changed, NULL));
    codec.eos = false;
    return &codec;
}
media_status_t AMediaCodec_configure(AMediaCodec *c, const AMediaFormat *f, void *w, void *s, uint32_t flags) {
    (void)c; (void)f; (void)w; (void)s; (void)flags; return 0;
}
media_status_t AMediaCodec_start(AMediaCodec *c) { (void)c; return 0; }
AMediaFormat *AMediaFormat_new(void) { return &format; }
void AMediaFormat_delete(AMediaFormat *f) { (void)f; }
void AMediaFormat_setString(AMediaFormat *f, const char *k, const char *v) { (void)f; (void)k; (void)v; }
void AMediaFormat_setInt32(AMediaFormat *f, const char *k, int32_t v) { (void)f; (void)k; (void)v; }

ssize_t AMediaCodec_dequeueInputBuffer(AMediaCodec *c, int64_t timeout) {
    (void)c; (void)timeout;
    return ++input_calls == fail_at ? -1 : 0;
}
uint8_t *AMediaCodec_getInputBuffer(AMediaCodec *c, size_t index, size_t *size) {
    (void)index; *size = sizeof(c->input); return c->input;
}
media_status_t AMediaCodec_queueInputBuffer(AMediaCodec *c, size_t index, off_t offset,
        size_t size, uint64_t time, uint32_t flags) {
    (void)index; (void)offset; (void)time;
    queued_bytes += size;
    if (flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
        pthread_mutex_lock(&c->mutex);
        c->eos = true;
        pthread_cond_signal(&c->changed);
        pthread_mutex_unlock(&c->mutex);
    }
    return 0;
}
ssize_t AMediaCodec_dequeueOutputBuffer(AMediaCodec *c, AMediaCodecBufferInfo *info, int64_t timeout) {
    (void)timeout;
    pthread_mutex_lock(&c->mutex);
    while (!c->eos) pthread_cond_wait(&c->changed, &c->mutex);
    pthread_mutex_unlock(&c->mutex);
    memset(info, 0, sizeof(*info));
    info->flags = AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
    return 0;
}
uint8_t *AMediaCodec_getOutputBuffer(AMediaCodec *c, size_t index, size_t *size) {
    return AMediaCodec_getInputBuffer(c, index, size);
}
media_status_t AMediaCodec_releaseOutputBuffer(AMediaCodec *c, size_t index, bool render) {
    (void)c; (void)index; (void)render;
    atomic_fetch_add(&released, 1);
    return 0;
}
media_status_t AMediaCodec_delete(AMediaCodec *c) {
    assert(atomic_load(&released) > deleted);
    assert(!pthread_cond_destroy(&c->changed));
    assert(!pthread_mutex_destroy(&c->mutex));
    deleted++;
    return 0;
}
static const char *scenario;
static void *frame(void *user) {
    AndroidChiakiAudioDecoder *decoder = user;
    uint8_t data[] = {1, 2, 3, 4, 5};
    if (!strcmp(scenario, "header-failure")) {
        ChiakiAudioHeader header = {.channels = 2, .rate = 48000};
        android_chiaki_audio_decoder_header(&header, decoder);
    } else {
        android_chiaki_audio_decoder_frame(data, sizeof(data), decoder);
        if (!strcmp(scenario, "retry"))
            android_chiaki_audio_decoder_frame(data, sizeof(data), decoder);
    }
    return NULL;
}

int main(int argc, char **argv) {
    assert(argc == 2);
    scenario = argv[1];
    fail_at = !strcmp(scenario, "partial-failure") ? 2 :
        (!strcmp(scenario, "success") || !strcmp(scenario, "no-codec") ? -1 : 1);
    AndroidChiakiAudioDecoder decoder;
    assert(android_chiaki_audio_decoder_init(&decoder, NULL) == CHIAKI_ERR_SUCCESS);
    if (strcmp(scenario, "no-codec") && strcmp(scenario, "header-failure")) {
        decoder.codec = AMediaCodec_createDecoderByType("audio/opus");
        assert(chiaki_thread_create(&decoder.output_thread,
            android_chiaki_audio_decoder_output_thread_func, &decoder) == CHIAKI_ERR_SUCCESS);
    }
    // A recursive mutex can hide a leaked lock on the same thread: use a different owner.
    pthread_t worker;
    assert(!pthread_create(&worker, NULL, frame, &decoder));
    assert(!pthread_join(worker, NULL));
    assert(chiaki_mutex_trylock(&decoder.codec_mutex) == CHIAKI_ERR_SUCCESS &&
        "audio input failure left codec_mutex locked by another thread");
    assert(chiaki_mutex_unlock(&decoder.codec_mutex) == CHIAKI_ERR_SUCCESS);
    if (!strcmp(scenario, "success") || !strcmp(scenario, "retry")) assert(queued_bytes == 5);
    if (!strcmp(scenario, "partial-failure")) assert(queued_bytes == 2);
    if (!strcmp(scenario, "input-failure")) assert(queued_bytes == 0);
    android_chiaki_audio_decoder_fini(&decoder);
    assert(!decoder.codec);
    assert(deleted == (strcmp(scenario, "no-codec") ? 1 : 0));
    puts(scenario);
    return 0;
}
