#!/usr/bin/env python3
"""Test, sestavení a kontrola distribučního balíčku LG bez knihoven navíc."""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent


def verify_ipk(path, expected):
    data = path.read_bytes()
    if data[:8] != b"!<arch>\n":
        raise ValueError("Neplatný IPK archiv")
    offset = 8
    found = {}
    while offset < len(data):
        header = data[offset:offset + 60]
        size = int(header[48:58])
        if header[:16].strip().startswith(b"data.tar"):
            with tarfile.open(fileobj=io.BytesIO(data[offset + 60:offset + 60 + size])) as archive:
                for item in archive.getmembers():
                    if item.isfile():
                        name = item.name.removeprefix("./")
                        if name in found:
                            raise ValueError("Duplicitní soubor v IPK: " + name)
                        found[name] = archive.extractfile(item).read()
        offset += 60 + size + size % 2
    metadata = "usr/palm/packages/cz.playcaption.overlaytest/packageinfo.json"
    package = json.loads(found.pop(metadata))
    if package["version"] != json.loads(expected["usr/palm/applications/cz.playcaption.overlaytest/appinfo.json"])["version"]:
        raise ValueError("Nesouhlasí verze IPK")
    if found != expected:
        raise ValueError("Obsah IPK neodpovídá zdrojům nebo obsahuje soubory navíc")


def main():
    app = json.loads((HERE / "app/appinfo.json").read_text(encoding="utf8"))
    service = json.loads((HERE / "service/package.json").read_text(encoding="utf8"))
    assert app["version"] == service["version"], "Nesouhlasí verze UI a služby"
    assert service["name"] == app["id"] + ".receiver", "Nesouhlasí ID služby"
    for test in ["test.cjs", "test-udp.cjs", "test-input.cjs", "test-receiver.cjs"]:
        subprocess.run(["node", test], cwd=HERE, check=True)
    for source in ["app/app.js", "app/receiver-ui.js", "service/protocol.js", "service/service.js", "service/switch-input.js"]:
        subprocess.run(["node", "--check", source], cwd=HERE, check=True)
    (HERE / "dist").mkdir(exist_ok=True)
    # Pevný příkaz bez interpolace vstupu; shell podporuje také npx.cmd na Windows.
    subprocess.run("npx --yes --package @webos-tools/cli@3.2.6 ares-package --no-minify app service --outdir dist",
                   shell=True, cwd=HERE, check=True)
    ipk = HERE / "dist" / (app["id"] + "_" + app["version"] + "_all.ipk")
    expected = {}
    for directory, prefix in [("app", "applications/" + app["id"]), ("service", "services/" + service["name"])]:
        for source in (HERE / directory).iterdir():
            if not source.is_file():
                raise ValueError("Neočekávaný podadresář: " + str(source))
            expected["usr/palm/" + prefix + "/" + source.name] = source.read_bytes()
    verify_ipk(ipk, expected)
    files = {ipk.name: ipk.read_bytes(), "LICENSE": (ROOT / "LICENSE").read_bytes()}
    files["NAVOD-LG.md"] = (HERE / "NAVOD-LG.md").read_bytes()
    files["SHODA-ANDROID.md"] = (HERE / "SHODA-ANDROID.md").read_bytes()
    files["INSTALACE-Z-ANDROIDU.md"] = (HERE / "INSTALACE-Z-ANDROIDU.md").read_bytes()
    files["source/LICENSE"] = (ROOT / "LICENSE").read_bytes()
    files["source/BACKLOG.md"] = (ROOT / "BACKLOG.md").read_bytes()
    sources = ["README.md", "NAVOD-LG.md", "TESTOVANI.md", "SHODA-ANDROID.md", "INSTALACE-Z-ANDROIDU.md", "test.cjs", "test-udp.cjs", "test-input.cjs", "test-receiver.cjs", "build.py"]
    sources += [str(p.relative_to(HERE)) for folder in ["app", "service"] for p in sorted((HERE / folder).iterdir())]
    for name in sources:
        files["source/webos/" + name] = (HERE / name).read_bytes()
    files["SHA256SUMS"] = "".join(hashlib.sha256(data).hexdigest() + "  " + name + "\n" for name, data in sorted(files.items())).encode()
    output = HERE / "dist" / ("PlayCaption-LG-" + app["version"] + ".zip")
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None, "Poškozený ZIP"
        assert set(archive.namelist()) == set(files), "Nesouhlasí obsah ZIPu"
    print("OK: IPK odpovídá zdrojům; ZIP obsahuje návod, licenci, zdroje a SHA256SUMS.")
    print(output)


if __name__ == "__main__":
    main()
