"""Regrese balení IPK pro starší i novější webOS; bez TV a npm."""
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

import build


class IpkBuildTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "receiver.ipk"
        self.expected = {
            "usr/palm/applications/cz.playcaption.overlaytest/appinfo.json": b'{"version":"0.0.1"}',
            "usr/palm/applications/cz.playcaption.overlaytest/index.html": b"subtitle receiver",
        }

    def package(self, control_time, data_time):
        data_files = dict(self.expected)
        data_files["usr/palm/packages/cz.playcaption.overlaytest/packageinfo.json"] = b'{"version":"0.0.1"}'
        result = bytearray(b"!<arch>\n")
        for name, files, mtime in [
            ("control.tar.gz", {"control": b"Package: cz.playcaption.overlaytest\n"}, control_time),
            ("data.tar.gz", data_files, data_time),
        ]:
            output = io.BytesIO()
            with tarfile.open(fileobj=output, mode="w:gz") as archive:
                for filename, content in files.items():
                    member = tarfile.TarInfo(filename)
                    member.size = len(content)
                    member.mtime = mtime
                    member.mode = 0o644
                    member.uid = member.gid = 501
                    member.pax_headers = {"mtime": str(mtime)}
                    archive.addfile(member, io.BytesIO(content))
            payload = output.getvalue()
            result.extend((name.ljust(16) + str(mtime).ljust(12) + "501".ljust(6) + "501".ljust(6) +
                           "100644".ljust(8) + str(len(payload)).ljust(10) + "`\n").encode("ascii"))
            result.extend(payload)
            if len(payload) % 2:
                result.extend(b"\n")
        self.path.write_bytes(result)

    def test_verifier_rejects_old_dates_in_either_archive(self):
        for control_time, data_time in [(0, 946684800), (946684800, 0)]:
            with self.subTest(control_time=control_time, data_time=data_time):
                self.package(control_time, data_time)
                with self.assertRaisesRegex(ValueError, "čas"):
                    build.verify_ipk(self.path, self.expected)

    def test_normalization_preserves_content_and_permissions_with_safe_dates(self):
        self.package(0, 0)
        build.normalize_ipk(self.path)
        build.verify_ipk(self.path, self.expected)
        data = self.path.read_bytes()
        offset = 8
        while offset < len(data):
            header = data[offset:offset + 60]
            size = int(header[48:58])
            self.assertEqual(int(header[16:28]), 946684800)
            with tarfile.open(fileobj=io.BytesIO(data[offset + 60:offset + 60 + size])) as archive:
                for member in archive.getmembers():
                    self.assertEqual(member.mtime, 946684800)
                    self.assertEqual(member.mode, 0o644)
                    self.assertEqual((member.uid, member.gid), (0, 0))
            offset += 60 + size + size % 2

    def test_normalization_is_reproducible_and_idempotent(self):
        self.package(0, 0)
        build.normalize_ipk(self.path)
        first = self.path.read_bytes()
        self.package(1700000000, 1700000000)
        build.normalize_ipk(self.path)
        self.assertEqual(self.path.read_bytes(), first)
        build.normalize_ipk(self.path)
        self.assertEqual(self.path.read_bytes(), first)

    def test_verifier_still_rejects_changed_content(self):
        self.package(946684800, 946684800)
        wrong_content = dict(self.expected)
        wrong_content["usr/palm/applications/cz.playcaption.overlaytest/index.html"] = b"changed"
        with self.assertRaisesRegex(ValueError, "Obsah IPK"):
            build.verify_ipk(self.path, wrong_content)


if __name__ == "__main__":
    unittest.main()
