import copy
import unittest

from score import distance, score, text


class ScoreTest(unittest.TestCase):
    def setUp(self):
        self.reference = {"dataset": "test", "samples": [
            {"file": "a.png", "text": "Příběh pokračuje.", "lines": 1, "sha256": "a"},
            {"file": "b.png", "text": "Ano!", "lines": 1, "sha256": "b"}]}
        self.rows = [dict(file=s["file"], text=s["text"], mode="normal", scale=1,
                          ms=ms, heights=[20], sha256=s["sha256"])
                     for s, ms in zip(self.reference["samples"], [10, 90])]

    def test_edit_operations_and_empty_hypothesis(self):
        self.assertEqual(3, distance("kitten", "sitting"))
        self.assertEqual(3, distance("abc", ""))
        self.assertEqual(1, distance("", "x"))
        self.assertEqual(1, distance(["go", "home"], ["go", "back", "home"]))

    def test_unicode_whitespace_case_and_punctuation_policy(self):
        self.assertEqual("Příběh pokračuje.", text("  Při\u0301běh\n pokračuje. "))
        self.assertEqual(1, distance("a😀", "a"))
        self.assertNotEqual(text("Ano!"), text("ano"))

    def test_weighted_errors_not_mean_of_sample_percentages(self):
        self.rows[1]["text"] = "Aho!"
        report = score(self.reference, self.rows)
        v = report["variants"][0]
        self.assertEqual(1, v["char_errors"])
        self.assertEqual(1 / 21, v["cer"])
        self.assertEqual(1 / 3, v["wer"])
        self.assertEqual((10, 90), (v["ocr_ms_p50"], v["ocr_ms_p95"]))
        self.assertEqual(1, v["exact_samples"])
        self.assertTrue(report["input_sha256_verified"])

    def test_empty_read_is_scored_as_missing_content(self):
        self.rows[0].update(text="", heights=[])
        v = score(self.reference, self.rows)["variants"][0]
        self.assertEqual(17, v["char_errors"])
        self.assertEqual(1, v["line_count_matches"])

    def test_missing_duplicate_or_unknown_frames_are_rejected(self):
        for rows in [self.rows[:1], self.rows + self.rows[:1],
                     [dict(self.rows[0], file="unknown.png")]]:
            with self.assertRaises(ValueError):
                score(self.reference, rows)

    def test_different_variant_coverage_is_rejected(self):
        with self.assertRaises(ValueError):
            score(self.reference, self.rows + [dict(self.rows[0], scale=2)])

    def test_invalid_timing_and_mismatched_source_are_rejected(self):
        for changes in [dict(ms=-1), dict(ms=float("nan")), dict(sha256="other"), dict(scale=0)]:
            rows = copy.deepcopy(self.rows)
            rows[0].update(changes)
            with self.assertRaises(ValueError):
                score(self.reference, rows)

    def test_historical_results_without_hash_are_explicit(self):
        for row in self.rows:
            del row["sha256"]
        self.assertFalse(score(self.reference, self.rows)["input_sha256_verified"])


if __name__ == "__main__":
    unittest.main()
