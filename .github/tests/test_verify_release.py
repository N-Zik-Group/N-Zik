import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
from unittest.mock import Mock, patch
from urllib.error import HTTPError
from zipfile import ZipFile


SPEC = importlib.util.spec_from_file_location(
    "verify_release", Path(__file__).parents[1] / "scripts" / "verify_release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
SHA = "a" * 40
OTHER = "b" * 40


class ApkTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "test.apk"

    def apk(self, variant="foss", revision=SHA, abis=None, empty=False):
        with ZipFile(self.path, "w") as apk:
            if revision is not None:
                apk.writestr("META-INF/version-control-info.textproto",
                             f'repositories {{ revision: "{revision}" }}')
            for abi in release.ABIS[variant] if abis is None else abis:
                apk.writestr(f"lib/{abi}/libmodule_loader.so", b"" if empty else b"ELF")

    def test_all_five_variant_abi_sets(self):
        for variant in release.ABIS:
            with self.subTest(variant=variant):
                self.apk(variant)
                release.verify_apk(self.path, SHA, variant)

    def test_missing_wrong_or_short_revision_fails(self):
        for revision in (None, OTHER, SHA[:12]):
            with self.subTest(revision=revision):
                self.apk(revision=revision)
                with self.assertRaises(ValueError):
                    release.verify_apk(self.path, SHA, "foss")

    def test_missing_extra_or_empty_native_library_fails(self):
        for abis in ({"arm64-v8a"}, release.ABIS["foss"] | {"mips"}):
            self.apk(abis=abis)
            with self.assertRaises(ValueError):
                release.verify_apk(self.path, SHA, "foss")
        self.apk(empty=True)
        with self.assertRaises(ValueError):
            release.verify_apk(self.path, SHA, "foss")

    def test_no_apks_fails(self):
        with patch("sys.argv", ["verify_release.py", "apk", "--sha", SHA,
                                "--variant", "foss", "--directory", self.directory.name]):
            with self.assertRaisesRegex(ValueError, "No APKs"):
                release.main()

    def test_duplicate_zip_entry_fails(self):
        self.apk()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.path, "a") as apk:
                apk.writestr(release.VCS_PATH, f'revision: "{OTHER}"')
        with self.assertRaisesRegex(ValueError, "duplicate"):
            release.verify_apk(self.path, SHA, "foss")

    def test_multiple_revisions_fail(self):
        self.apk(revision=f'{SHA}" }} repositories {{ revision: "{OTHER}')
        with self.assertRaisesRegex(ValueError, "embedded revision"):
            release.verify_apk(self.path, SHA, "foss")

    def test_ambiguous_metadata_locations_fail(self):
        self.apk()
        with ZipFile(self.path, "a") as apk:
            apk.writestr("META-INF/com/android/build/gradle/version-control-info.textproto",
                         f'repositories {{ revision: "{SHA}" }}')
        with self.assertRaisesRegex(ValueError, "ambiguous"):
            release.verify_apk(self.path, SHA, "foss")


class TagTests(unittest.TestCase):
    def http_error(self, code):
        error = HTTPError("url", code, "error", {}, None)
        self.addCleanup(error.close)
        return error

    def check(self, get):
        release.verify_tag("owner/repo", "v1.0", SHA, get)

    def test_absent_tag_allowed(self):
        self.check(Mock(side_effect=self.http_error(404)))

    def test_http_errors_fail_closed(self):
        for code in (401, 403, 500):
            with self.subTest(code=code), self.assertRaises(HTTPError):
                self.check(Mock(side_effect=self.http_error(code)))

    def test_matching_lightweight_tag_allowed(self):
        self.check(Mock(return_value={"object": {"type": "commit", "sha": SHA}}))

    def test_nested_annotated_tags_peeled(self):
        get = Mock(side_effect=[
            {"object": {"type": "tag", "sha": OTHER}},
            {"object": {"type": "tag", "sha": "c" * 40}},
            {"object": {"type": "commit", "sha": SHA}},
        ])
        self.check(get)
        self.assertEqual(get.call_args_list[-1].args[0], "/repos/owner/repo/git/tags/" + "c" * 40)

    def test_mismatching_lightweight_and_annotated_tags_fail(self):
        for objects in ([{"type": "commit", "sha": OTHER}],
                        [{"type": "tag", "sha": "c" * 40},
                         {"type": "commit", "sha": OTHER}]):
            with self.subTest(objects=objects), self.assertRaisesRegex(ValueError, "refusing"):
                self.check(Mock(side_effect=[{"object": obj} for obj in objects]))

    def test_non_commit_and_cyclic_tags_fail(self):
        for obj in ({"type": "tree", "sha": SHA}, {"type": "tag", "sha": OTHER}):
            with self.subTest(obj=obj), self.assertRaises(ValueError):
                self.check(Mock(return_value={"object": obj}))

    def test_short_sha_fails_before_api_call(self):
        get = Mock()
        with self.assertRaises(ValueError):
            release.verify_tag("owner/repo", "v1", SHA[:12], get)
        get.assert_not_called()


if __name__ == "__main__":
    unittest.main()
