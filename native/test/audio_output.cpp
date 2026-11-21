/* Actual playback callbacks with a device spy: no Android mixer or real audio output. */
#include <array>
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include "audio-output.cpp"

namespace oboe { int device_opens = 0, stream_starts = 0; bool refuse = false; }
extern "C" void chiaki_log(ChiakiLog *, ChiakiLogLevel, const char *, ...) {}

int main(int argc, char **argv) {
    assert(argc == 2);
    void *output = android_chiaki_audio_output_new(nullptr);
    assert(output);
    bool game = !std::strcmp(argv[1], "audible-pcm");
    if (!std::strcmp(argv[1], "output-unavailable")) {
        oboe::refuse = true;
        android_chiaki_audio_output_settings(2, 48000, output);
        assert(oboe::device_opens == 1 && oboe::stream_starts == 0);
        android_chiaki_audio_output_free(output);
        return 0;
    }
    if (!std::strcmp(argv[1], "no-device-playback")) {
        // Include reconfiguration: no default/exclusive output may replace the HDMI sound.
        android_chiaki_audio_output_settings(2, 48000, output);
        android_chiaki_audio_output_settings(2, 44100, output);
        assert(oboe::device_opens == 0 && oboe::stream_starts == 0);
    } else {
        assert(game || !std::strcmp(argv[1], "no-pcm-playback"));
        android_chiaki_audio_output_settings(2, 48000, output);
        std::array<int16_t, 1024> loud;
        loud.fill(16000); // Enough to fill the real upstream FIFO chunks.
        android_chiaki_audio_output_frame(loud.data(), loud.size(), output);
        android_chiaki_audio_output_frame(loud.data(), loud.size(), output);
        oboe::AudioStream probe;
        std::array<int16_t, 4> played{{1, 1, 1, 1}};
        auto *state = static_cast<AudioOutput *>(output);
        state->stream_callback.onAudioReady(&probe, played.data(), 2);
        for (int16_t sample : played) assert(sample == (game ? 16000 : 0));
    }
    android_chiaki_audio_output_free(output);
    android_chiaki_audio_output_free(nullptr);
    assert(oboe::device_opens == (game ? 1 : 0) && oboe::stream_starts == (game ? 1 : 0));
    std::puts(argv[1]);
}
