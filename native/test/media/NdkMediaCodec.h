/* Host-only MediaCodec double; production builds use the Android NDK headers. */
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

typedef struct AMediaCodec AMediaCodec;
typedef struct AMediaFormat AMediaFormat;
typedef int media_status_t;
typedef struct { int32_t offset, size; int64_t presentationTimeUs; uint32_t flags; } AMediaCodecBufferInfo;
#define AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM 4
#define AMEDIAFORMAT_KEY_MIME "mime"
#define AMEDIAFORMAT_KEY_CHANNEL_COUNT "channel-count"
#define AMEDIAFORMAT_KEY_SAMPLE_RATE "sample-rate"

AMediaCodec *AMediaCodec_createDecoderByType(const char *mime);
media_status_t AMediaCodec_configure(AMediaCodec *, const AMediaFormat *, void *, void *, uint32_t);
media_status_t AMediaCodec_start(AMediaCodec *);
media_status_t AMediaCodec_delete(AMediaCodec *);
ssize_t AMediaCodec_dequeueInputBuffer(AMediaCodec *, int64_t);
ssize_t AMediaCodec_dequeueOutputBuffer(AMediaCodec *, AMediaCodecBufferInfo *, int64_t);
uint8_t *AMediaCodec_getInputBuffer(AMediaCodec *, size_t, size_t *);
uint8_t *AMediaCodec_getOutputBuffer(AMediaCodec *, size_t, size_t *);
media_status_t AMediaCodec_queueInputBuffer(AMediaCodec *, size_t, off_t, size_t, uint64_t, uint32_t);
media_status_t AMediaCodec_releaseOutputBuffer(AMediaCodec *, size_t, bool);
AMediaFormat *AMediaFormat_new(void);
void AMediaFormat_delete(AMediaFormat *);
void AMediaFormat_setString(AMediaFormat *, const char *, const char *);
void AMediaFormat_setInt32(AMediaFormat *, const char *, int32_t);
