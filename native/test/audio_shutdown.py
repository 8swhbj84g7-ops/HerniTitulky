#!/usr/bin/env python3
"""Run real Chiaki audio/frame/shutdown with pthreads and a controllable MediaCodec double."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

source = Path(sys.argv[1]).resolve()  # pinned Chiaki checkout, patched by build_chiaki.py
here = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix="playcaption-audio-test-") as temporary:
    work = Path(temporary)
    (work / "jni.h").write_text("/* No JNI calls are made by audio-decoder.c. */\n")
    binary = work / "audio-test"
    subprocess.run([
        os.environ.get("CC", "cc"), "-std=c11", "-pthread", "-Wall", "-Wextra",
        "-Wno-unused-parameter", "-I" + str(work), "-I" + str(here),
        "-I" + str(source / "android/app/src/main/cpp"), "-I" + str(source / "lib/include"),
        str(here / "audio_shutdown.c"), str(source / "lib/src/thread.c"),
        str(source / "lib/src/time.c"), "-o", str(binary)
    ], check=True)
    for scenario in ["success", "no-codec", "input-failure", "partial-failure", "retry", "header-failure"]:
        subprocess.run([str(binary), scenario], check=True, timeout=10)
    (work / "android").mkdir()
    (work / "android/log.h").write_text("/* CircularBuffer does not use Android logging here. */\n")
    playback = work / "playback-test"
    subprocess.run([
        os.environ.get("CXX", "c++"), "-std=c++14", "-pthread", "-Wall", "-Wextra",
        "-Wno-unused-parameter", "-I" + str(work), "-I" + str(here),
        "-I" + str(source / "android/app/src/main/cpp"), "-I" + str(source / "lib/include"),
        str(here / "audio_output.cpp"), "-o", str(playback)
    ], check=True)
    scenarios = ["audible-pcm", "output-unavailable"] if "--game" in sys.argv else ["no-device-playback", "no-pcm-playback"]
    for scenario in scenarios:
        subprocess.run([str(playback), scenario], check=True, timeout=10)
