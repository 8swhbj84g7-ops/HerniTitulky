#!/usr/bin/env python3
"""Build the pinned Chiaki with the mutex fix and silent playback; leave app binaries untouched."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

REVISION = "f814cf69d636f149ad5a3cfffd19cb22da90beda"
NDK_REVISION = "21.4.7075529"
PLAYBACK_SOURCE_SHA256 = "9d20afbe898551159124f5f75397973e3e5ad48fab0e8bc7be7b5208006a5cc8"
HERE = Path(__file__).resolve().parent
REPO = HERE.parent


def output(*args):
    return subprocess.check_output(args, text=True).strip()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def main():
    game = sys.argv[4:] == ["--game"]
    if len(sys.argv) != 4 and not game:
        sys.exit("Usage: python3 native/build_chiaki.py <Chiaki-TV checkout> <NDK r21e> <build directory> [--game]")
    source, ndk, build = (Path(p).resolve() for p in sys.argv[1:4])
    if not shutil.which("cmake") or not shutil.which("ninja"):
        sys.exit("Install CMake 3.x and Ninja and add them to PATH")
    if output("git", "-C", str(source), "rev-parse", "HEAD") != REVISION:
        sys.exit("Unexpected upstream revision")
    if f"Pkg.Revision = {NDK_REVISION}" not in (ndk / "source.properties").read_text():
        sys.exit("Use NDK r21e (21.4.7075529)")
    if source == REPO or build.is_relative_to(source) or build.is_relative_to(REPO):
        sys.exit("Use a separate upstream checkout and build directory outside both repositories")
    audio = "android/app/src/main/cpp/audio-decoder.c"
    playback = "android/app/src/main/cpp/audio-output.cpp"
    changed = output("git", "-C", str(source), "diff", "HEAD", "--name-only").splitlines()
    log = "android/app/src/main/cpp/log.c"
    video = "android/app/src/main/cpp/video-decoder.c"
    allowed = {audio, playback, log, video} if game else {audio, playback}
    if set(changed) - allowed:
        sys.exit("Upstream checkout contains unrelated tracked changes")
    original = subprocess.check_output(["git", "-C", str(source), "show", f"HEAD:{audio}"])
    old = b'CHIAKI_LOGE(decoder->log, "Failed to get input audio buffer");\n\t\t\treturn;'
    if original.count(old) != 1:
        sys.exit("Unexpected upstream audio error path")
    fixed = original.replace(old, old.replace(b"return;", b"goto beach;"))
    if (source / audio).read_bytes() == original:
        subprocess.run(["git", "-C", str(source), "apply", "--unidiff-zero",
                        str(HERE / "chiaki-audio-unlock.patch")], check=True)
    if (source / audio).read_bytes() != fixed:
        sys.exit("Audio source differs from the reviewed one-line patch")
    original_playback = subprocess.check_output(["git", "-C", str(source), "show", f"HEAD:{playback}"])
    patch = HERE / ("chiaki-stick.patch" if game else "chiaki-silent-audio.patch")
    if (source / playback).read_bytes() == original_playback:
        subprocess.run(["git", "-C", str(source), "apply", "--unidiff-zero",
                        str(patch)], check=True)
    expected = "b45b4e9738f618e8647c928423f8fa6b06bf5c8443e99def072a4d8b069207c1" if game else PLAYBACK_SOURCE_SHA256
    if game and sha256((source / log).read_bytes()) != "af7d504f9fee08b5d40911cd44de6ba6b4d8737c274daa711ca82f983cc4b947":
        sys.exit("Unexpected native logging implementation")
    if game and sha256((source / video).read_bytes()) != "d736c13c4b152184f7010118e0b418d3ffb623cc0d126d60ed09b546525e21cf":
        sys.exit("Unexpected native video implementation")
    if sha256((source / playback).read_bytes()) != expected:
        sys.exit("Playback source differs from the reviewed silent callbacks")
    subprocess.run([sys.executable, str(HERE / "test/audio_shutdown.py"), str(source)] + (["--game"] if game else []), check=True)
    host = "darwin-x86_64" if sys.platform == "darwin" else "linux-x86_64"
    tools = ndk / "toolchains/llvm/prebuilt" / host / "bin"
    manifest = {"upstream": REVISION, "ndk": NDK_REVISION,
                "patchSha256": sha256((HERE / "chiaki-audio-unlock.patch").read_bytes()),
                "playbackPatchSha256": sha256(patch.read_bytes()),
                "libraries": {}}
    for abi in ["arm64-v8a", "armeabi-v7a"]:
        directory = build / ("build-" + abi)
        subprocess.run([
            "cmake", "-S", str(source), "-B", str(directory), "-G", "Ninja",
            "-DCMAKE_MAKE_PROGRAM=" + shutil.which("ninja"),
            "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
            "-DCMAKE_AR=" + str(tools / "llvm-ar"), "-DCMAKE_RANLIB=" + str(tools / "llvm-ranlib"),
            "-DPYTHON_EXECUTABLE=" + sys.executable,
            "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-21", "-DCMAKE_BUILD_TYPE=Release",
            "-DCHIAKI_ENABLE_TESTS=OFF", "-DCHIAKI_ENABLE_CLI=OFF", "-DCHIAKI_ENABLE_GUI=OFF",
            "-DCHIAKI_ENABLE_SETSU=OFF", "-DCHIAKI_ENABLE_ANDROID=ON", "-DCHIAKI_LIB_ENABLE_OPUS=OFF",
            "-DCHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT=ON", "-DCHIAKI_ENABLE_FFMPEG_DECODER=OFF",
            "-DCHIAKI_ENABLE_PI_DECODER=OFF", "-DCHIAKI_USE_SYSTEM_JERASURE=OFF",
            "-DCHIAKI_USE_SYSTEM_NANOPB=OFF"
        ], check=True)
        subprocess.run(["cmake", "--build", str(directory), "--target", "chiaki-jni", "-j", "4"], check=True)
        target = build / "jniLibs" / abi
        target.mkdir(parents=True, exist_ok=True)
        library = target / ("libchiaki-hra.so" if game else "libchiaki-jni.so")
        shutil.copyfile(directory / "android/app/libchiaki-jni.so", library)
        subprocess.run([str(tools / "llvm-strip"), "--strip-unneeded", str(library)], check=True)
        previous = REPO / "app/src/main/jniLibs" / abi / "libchiaki-jni.so"
        def jni_symbols(p):
            return set(re.findall(r"\bJava_\w+", output(str(tools / "llvm-readelf"), "--dyn-syms", "--wide", str(p))))
        def dependencies(p):
            return set(re.findall(r"\(NEEDED\).*?\[(.*?)\]", output(str(tools / "llvm-readelf"), "-d", str(p))))
        if jni_symbols(previous) != jni_symbols(library) or dependencies(previous) != dependencies(library):
            sys.exit(f"JNI API or shared dependencies changed for {abi}")
        data = library.read_bytes()
        if data.count(b"libmediandk.so") != 1:
            sys.exit("Unexpected MediaCodec dependency count")
        if game:
            # Tiny test-APK adapter for feeding recorded H.264 frames to this exact library.
            triple = "aarch64-linux-android" if abi == "arm64-v8a" else "armv7a-linux-androideabi"
            probe = build / "testJniLibs" / abi / "libstickprobe.so"
            probe.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run([str(tools / (triple + "21-clang")), "-shared", "-fPIC", "-O2",
                            "-I" + str(source / "android/app/src/main/cpp"), "-I" + str(source / "lib/include"),
                            "-I" + str(directory / "lib/include"), str(HERE / "test/stick_probe.c"),
                            "-ldl", "-o", str(probe)], check=True)
            manifest["libraries"][abi] = {library.name: sha256(data)}
            continue
        # Same dependency rename as the existing phone library; no decoder/codec changes.
        phone = target / "libchiaki-pc.so"
        phone.write_bytes(data.replace(b"libmediandk.so", b"libpcobraz1.so"))
        manifest["libraries"][abi] = {p.name: sha256(p.read_bytes()) for p in [library, phone]}
    (build / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Built libraries (not installed):", build / "jniLibs")


if __name__ == "__main__":
    main()
