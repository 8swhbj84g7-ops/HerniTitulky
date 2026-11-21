#!/usr/bin/env python3
"""Fail the release if packaged Android metadata, code, or native provenance drifts."""
import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "cz.siller.hernititulky.stick"
LAUNCHER = "cz.titulky.tv.stick.StickHome"
ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def verify(apk, sdk):
    subprocess.run([sdk / "build-tools/34.0.0/apksigner", "verify", "--verbose", apk], check=True)
    manifest_xml = subprocess.check_output([
        sdk / "cmdline-tools/latest/bin/apkanalyzer", "manifest", "print", apk
    ])
    manifest = ET.fromstring(manifest_xml)
    require(manifest.get("package") == PACKAGE, "Unexpected application ID")
    app = manifest.find("application")
    require(app is not None, "Application manifest missing")
    for flag in ("debuggable", "allowBackup", "fullBackupContent", "usesCleartextTraffic"):
        require(app.get(ANDROID + flag) == "false", f"{flag} must be explicitly false")

    names = []
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for component in app.findall(tag):
            name = component.get(ANDROID + "name")
            expected = "true" if tag == "activity" and name == LAUNCHER else "false"
            require(component.get(ANDROID + "exported") == expected, f"Unexpected export state: {name}")
            require(not component.findall("./intent-filter/data"), f"Unexpected deep link: {name}")
            if name.startswith("cz.titulky.tv."):
                names.append(name)
    require(set(names) == {LAUNCHER, "cz.titulky.tv.stick.StickGame", "cz.titulky.tv.stick.StickPair"},
            f"Unexpected app components: {names}")
    require(not manifest.findall("instrumentation"), "Instrumentation in release package")

    provenance = json.loads((ROOT / "native/chiaki-stick-build.json").read_text())
    require(provenance.get("openssl") == "3.5.9", "Unexpected native OpenSSL version")
    with ZipFile(apk) as archive:
        files = archive.namelist()
        require(not any(name.startswith(("assets/lg/", "assets/samsung/")) for name in files),
                "Legacy receiver assets in release")
        native = {name for name in files if name.startswith("lib/") and
                  "chiaki" in name.rsplit("/", 1)[-1]}
        require(native == {"lib/arm64-v8a/libchiaki-hra.so", "lib/armeabi-v7a/libchiaki-hra.so"},
                f"Unexpected Chiaki libraries: {native}")
        require(not any("libstickprobe" in name or "libpcobraz" in name or "libjingle" in name for name in files),
                "Legacy or test library in release")
        for abi, libraries in provenance["libraries"].items():
            for name, digest in libraries.items():
                packaged = archive.read(f"lib/{abi}/{name}")
                require(hashlib.sha256(packaged).hexdigest() == digest, f"Native hash mismatch: {abi}/{name}")
        code = [archive.read(name) for name in files if name.startswith("classes") and name.endswith(".dex")]
        require(code, "No executable DEX")
        for name in ("PsnPrihlaseni", "PrihlaseniTelefonem", "PsnPokus", "RemotePlayUdaje", "RemotePlayService",
                     "Propojeni", "Predavani", "AutoStart", "PrijemService", "MainActivity", "XboxUcet"):
            descriptor = f"Lcz/titulky/tv/{name};".encode()
            require(not any(descriptor in dex for dex in code), f"Legacy class in release: {name}")
        for marker in (b"auth.api.sonyentertainmentnetwork.com", b"remoteplay.dl.playstation.net/remoteplay/redirect"):
            require(not any(marker in dex for dex in code), "Sony login endpoint in release")
    print("Stick release manifest, signature, isolation and native hashes verified.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"), required="ANDROID_HOME" not in os.environ)
    args = parser.parse_args()
    verify(args.apk, args.sdk)
