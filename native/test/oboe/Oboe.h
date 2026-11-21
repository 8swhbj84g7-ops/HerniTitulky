/* Host-only playback spy; production builds use the pinned upstream Oboe. */
#pragma once
#include <cstdint>
#include <memory>

namespace oboe {
extern int device_opens, stream_starts;
extern bool refuse;
enum class AudioFormat { I16 };
enum class Result { Error, OK };
enum class DataCallbackResult { Continue, Stop };
enum class PerformanceMode { LowLatency };
enum class SharingMode { Shared, Exclusive };
class AudioStream {
public:
    AudioFormat getFormat() { return AudioFormat::I16; }
    int32_t getBytesPerFrame() { return 4; }
    Result start() { stream_starts++; return Result::OK; }
};
class AudioStreamCallback {
public:
    virtual ~AudioStreamCallback() = default;
    virtual DataCallbackResult onAudioReady(AudioStream *, void *, int32_t) = 0;
    virtual void onErrorBeforeClose(AudioStream *, Result) = 0;
    virtual void onErrorAfterClose(AudioStream *, Result) = 0;
};
using ManagedStream = std::shared_ptr<AudioStream>;
class AudioStreamBuilder {
public:
    AudioStreamBuilder *setPerformanceMode(PerformanceMode) { return this; }
    AudioStreamBuilder *setSharingMode(SharingMode) { return this; }
    AudioStreamBuilder *setFormat(AudioFormat) { return this; }
    AudioStreamBuilder *setChannelCount(uint32_t) { return this; }
    AudioStreamBuilder *setSampleRate(uint32_t) { return this; }
    AudioStreamBuilder *setCallback(AudioStreamCallback *) { return this; }
    Result openManagedStream(ManagedStream &stream) {
        device_opens++;
        if (refuse) return Result::Error;
        stream = std::make_shared<AudioStream>();
        return Result::OK;
    }
};
inline const char *convertToText(Result) { return "test"; }
}
