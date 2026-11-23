"""Regression coverage for dev build identity, history, content and failure policy."""

import contextlib
import datetime
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "dev_build_guard.py"
SPEC = importlib.util.spec_from_file_location("dev_build_guard", SCRIPT)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)
NOW = datetime.datetime(2026, 11, 23, 12, tzinfo=datetime.timezone.utc)


def commit_info(sha="a" * 40, author="NEVARLeVrai", committer="NEVARLeVrai", kind="User"):
    return {
        "sha": sha,
        "author": {"login": author, "type": "User"} if author else None,
        "committer": {"login": committer, "type": kind} if committer else None,
        "commit": {"author": {"name": author, "email": "author@example.test"},
                   "committer": {"name": committer, "email": "committer@example.test"}},
    }


class PolicyTests(unittest.TestCase):
    def test_identity_requires_account_and_rejects_bots(self):
        self.assertTrue(guard.personal_commit(commit_info()))
        for value in (commit_info(author="Other"), commit_info(author=None),
                      commit_info(committer="github-actions[bot]"),
                      commit_info(committer="automation", kind="Bot")):
            with self.subTest(value=value):
                self.assertFalse(guard.personal_commit(value))
        raw_bot = commit_info(committer=None)
        raw_bot["commit"]["committer"]["email"] = "123+dependabot[bot]@users.noreply.github.com"
        self.assertFalse(guard.personal_commit(raw_bot))

    def test_repository_specific_inputs_and_exclusions(self):
        common_excluded = ("README.md", ".github/workflows/build-dev.yml", ".github/tests/test_guard.py",
                           "fastlane/source/title.txt", "Updater/changelogs/dev.txt", "assets/notes/Done.txt",
                           "assets/stats/history.json", "ComposeN-Zik/src/test/kotlin/Test.kt")
        for profile in ("mobile", "desktop"):
            self.assertTrue(guard.qualifying_path("gradle/gradle-daemon-jvm.properties", profile))
            for path in common_excluded:
                self.assertFalse(guard.qualifying_path(path, profile), path)
        for source in ("androidMain", "main"):
            for path in (f"ComposeN-Zik/src/{source}/localization/source/strings.xml",
                         f"ComposeN-Zik/src/{source}/localization/translations/fr/strings.xml",
                         f"ComposeN-Zik/src/{source}/resources/contributors.json",
                         f"ComposeN-Zik/src/{source}/res/raw/translators.json",
                         f"ComposeN-Zik/src/{source}/res/raw/release_notes.txt"):
                for profile in ("mobile", "desktop"):
                    self.assertFalse(guard.qualifying_path(path, profile), path)
        for path in ("ComposeN-Zik/src/androidMain/kotlin/App.kt", "ComposeN-Zik/src/commonMain/kotlin/App.kt",
                     "ComposeN-Zik/src/androidMain/assets/po_token.html",
                     "ComposeN-Zik/src/androidMain/jni/module_loader.c", "ComposeN-Zik/src/androidMain/jni/CMakeLists.txt",
                     "ComposeN-Zik/src/main/proto/listentogether.proto", "ComposeN-Zik/src/androidMain/res/raw/input.txt",
                     "ComposeN-Zik/src/debug/AndroidManifest.xml", "extensions/lrclib/src/main/kotlin/Client.kt",
                     "modules/betterlyrics", "ComposeN-Zik/proguard-rules.pro", "gradle/libs.versions.toml"):
            self.assertTrue(guard.qualifying_path(path, "mobile"), path)
        for path in ("ComposeN-Zik/src/main/kotlin/App.kt", "ComposeN-Zik/src/main/composeResources/drawable/icon.png",
                     "ComposeN-Zik/packaging/windows/installer.nsi", "packaging/flatpak/manifest.json",
                     "tools/nsis/nsis.zip", "assets/design/icon.ico", "build.sh"):
            self.assertTrue(guard.qualifying_path(path, "desktop"), path)
        self.assertFalse(guard.qualifying_path("ComposeN-Zik/packaging/windows/localization/source/installer.nsh", "desktop"))

    def test_paginated_api_checks_all_pages(self):
        with patch.object(guard, "command", side_effect=[json.dumps([{}] * 100).encode(), b'[{"sha":"last"}]']) as call:
            self.assertEqual(len(list(guard.api_pages("repos/example/repo/commits", {"author": guard.MAINTAINER}))), 101)
        self.assertIn("page=2", call.call_args.args[-1])
        self.assertIn("author=NEVARLeVrai", call.call_args.args[-1])

    def test_malformed_api_fails(self):
        for response in (b"{}", b"not json", b"[null]"):
            with self.subTest(response=response), patch.object(guard, "command", return_value=response):
                with self.assertRaises(ValueError):
                    list(guard.api_pages("repos/example/repo/commits"))

    def test_nightly_checks_older_personal_work_after_excluded_head(self):
        values = [commit_info("b" * 40), commit_info("a" * 40), commit_info(author="Other")]
        with patch.object(guard, "api_pages", side_effect=[iter([]), iter(values)]), patch.object(guard, "substantive_commit", side_effect=[False, True]) as inspect:
            self.assertTrue(guard.eligible("mobile", "schedule", "scheduler", "owner/repo", "c" * 40, NOW))
        self.assertEqual(inspect.call_count, 2)

    def test_manual_owner_only_and_automatic_push_disabled(self):
        with patch.object(guard, "api_pages", return_value=iter([])), patch.object(guard, "substantive_commit") as inspect:
            self.assertTrue(guard.eligible("desktop", "workflow_dispatch", "NEVARLeVrai", "owner/repo", "a" * 40, NOW))
            self.assertFalse(guard.eligible("desktop", "workflow_dispatch", "Other", "owner/repo", "a" * 40, NOW))
            self.assertFalse(guard.eligible("desktop", "push", "NEVARLeVrai", "owner/repo", "a" * 40, NOW))
            inspect.assert_not_called()

    def test_nightly_api_parameters_limit_cutoff_revision_and_author(self):
        revision = "c" * 40
        stale = (commit_info("d" * 40), "2026-11-22T11:59:59Z", revision)
        other_revision = (commit_info("e" * 40), "2026-11-23T11:00:00Z", "f" * 40)
        other_author = (commit_info("b" * 40, author="Other"), "2026-11-23T11:00:00Z", revision)
        current = (commit_info("a" * 40), "2026-11-22T12:00:00Z", revision)
        for records, expected in (([stale, other_revision, other_author], False),
                                  ([stale, other_revision, other_author, current], True)):
            def api_command(*args):
                self.assertEqual(args[:2], ("gh", "api"))
                endpoint = urlsplit(args[2])
                params = parse_qs(endpoint.query)
                if endpoint.path == "repos/owner/repo/releases":
                    return b"[]"
                self.assertEqual(endpoint.path, "repos/owner/repo/commits")
                self.assertEqual(params, {"sha": [revision], "since": ["2026-11-22T12:00:00Z"],
                                          "author": ["NEVARLeVrai"], "per_page": ["100"], "page": ["1"]})
                return json.dumps([info for info, date, ref in records
                                   if date >= params["since"][0] and ref == params["sha"][0]
                                   and info["author"]["login"] == params["author"][0]]).encode()

            with self.subTest(expected=expected), patch.object(guard, "command", side_effect=api_command), patch.object(guard, "substantive_commit", return_value=True) as inspect:
                self.assertEqual(guard.eligible("mobile", "schedule", "scheduler", "owner/repo", revision, NOW), expected)
                self.assertEqual(inspect.call_args_list, [unittest.mock.call("a" * 40, "mobile")] if expected else [])

    def test_daily_limit_still_applies_to_manual(self):
        today = NOW.astimezone().strftime("%Y%m%d")
        with patch.object(guard, "api_pages", return_value=iter([{"tag_name": f"v8.7-dev-{today}"}])):
            self.assertFalse(guard.eligible("mobile", "workflow_dispatch", guard.MAINTAINER, "owner/repo", "a" * 40, NOW))

    def test_daily_limit_ignores_near_matching_tag_suffixes(self):
        today = NOW.astimezone().strftime("%Y%m%d")
        for tag in (f"v8.7-dev-{today}-retry", f"v8.7-dev-{today}0", f"v8.7-dev-{today}beta"):
            with self.subTest(tag=tag), patch.object(guard, "api_pages", return_value=iter([{"tag_name": tag}])):
                self.assertTrue(guard.eligible("mobile", "workflow_dispatch", guard.MAINTAINER, "owner/repo", "a" * 40, NOW))

    def test_daily_release_on_second_page_blocks_build(self):
        today = NOW.astimezone().strftime("%Y%m%d")
        pages = [json.dumps([{"tag_name": "v1.0-stable"}] * 100).encode(),
                 json.dumps([{"tag_name": f"v8.7-dev-{today}"}]).encode()]
        with patch.object(guard, "command", side_effect=pages) as request:
            self.assertFalse(guard.eligible("mobile", "workflow_dispatch", guard.MAINTAINER, "owner/repo", "a" * 40, NOW))
        self.assertEqual(request.call_count, 2)
        self.assertEqual(parse_qs(urlsplit(request.call_args.args[-1]).query)["page"], ["2"])

    def test_later_release_page_failure_blocks_build(self):
        first_page = json.dumps([{"tag_name": "v1.0-stable"}] * 100).encode()
        with patch.object(guard, "command", side_effect=[first_page, subprocess.CalledProcessError(1, ("gh", "api"))]) as request:
            with self.assertRaises(subprocess.CalledProcessError):
                guard.eligible("mobile", "workflow_dispatch", guard.MAINTAINER, "owner/repo", "a" * 40, NOW)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(parse_qs(urlsplit(request.call_args.args[-1]).query)["page"], ["2"])

    def test_nightly_without_personal_work_is_ineligible(self):
        for commits in ([], [commit_info(committer="github-actions[bot]")], [commit_info()]):
            with self.subTest(commits=commits), patch.object(guard, "api_pages", side_effect=[iter([]), iter(commits)]), patch.object(guard, "substantive_commit", return_value=False):
                self.assertFalse(guard.eligible("mobile", "schedule", "scheduler", "owner/repo", "a" * 40, NOW))

    def test_author_phase_outputs_owner_dispatch_and_schedule_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "output"
            for event, actor, allowed in (("schedule", "scheduler", True), ("workflow_dispatch", "NEVARLeVrai", True), ("workflow_dispatch", "Other", False), ("push", "NEVARLeVrai", False)):
                target.write_text("")
                with patch.dict(os.environ, {"GITHUB_OUTPUT": str(target), "GITHUB_EVENT_NAME": event, "GITHUB_ACTOR": actor}), patch("sys.argv", [str(SCRIPT), "author", "--profile", "desktop"]):
                    self.assertEqual(guard.main(), 0)
                self.assertEqual(target.read_text().splitlines()[-1], f"should-build={str(allowed).lower()}")

    def test_later_api_failure_overrides_earlier_match(self):
        def commits():
            yield commit_info()
            raise subprocess.CalledProcessError(1, "gh")
        with patch.object(guard, "api_pages", side_effect=[iter([]), commits()]), patch.object(guard, "substantive_commit", return_value=True):
            with self.assertRaises(subprocess.CalledProcessError):
                guard.eligible("mobile", "schedule", "scheduler", "owner/repo", "a" * 40, NOW)

    def test_main_failure_writes_false_and_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "output"
            env = {"GITHUB_OUTPUT": str(target), "GITHUB_EVENT_NAME": "schedule", "GITHUB_ACTOR": "scheduler",
                   "GITHUB_REPOSITORY": "owner/repo", "GITHUB_SHA": "a" * 40}
            for error in (subprocess.CalledProcessError(1, "gh"), subprocess.CalledProcessError(1, "git"), ValueError("bad JSON")):
                target.write_text("")
                with patch.dict(os.environ, env), patch("sys.argv", [str(SCRIPT), "changes", "--profile", "mobile"]), patch.object(guard, "eligible", side_effect=error), contextlib.redirect_stderr(io.StringIO()) as errors:
                    self.assertEqual(guard.main(), 1)
                self.assertEqual(target.read_text(), "changed=false\n")
                self.assertIn("::error::", errors.getvalue())


class GitHistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous = Path.cwd()
        os.chdir(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.addCleanup(os.chdir, self.previous)
        self.git("init", "-q")
        self.git("config", "user.name", "Maintainer")
        self.git("config", "user.email", "maintainer@example.test")
        self.write("README.md", "baseline\n")
        self.commit()

    def git(self, *args):
        return guard.command("git", *args).decode().strip()

    def write(self, path, value):
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(value, encoding="utf-8")

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD")

    def test_translation_and_version_notes_do_not_qualify(self):
        self.write("ComposeN-Zik/src/androidMain/localization/source/strings.xml", "translation")
        self.write("Updater/changelogs/dev.txt", "notes")
        self.assertFalse(guard.substantive_commit(self.commit(), "mobile"))
        self.write(guard.CATALOG, '[versions]\nnzikVersionCode = "1"\nnzikVersionName = "1.0"\nkotlin = "2.0"\n')
        self.commit()
        self.write(guard.CATALOG, '# new comment\n[versions]\nnzikVersionCode="2"\nnzikVersionName="2.0"\nkotlin="2.0" # unchanged\n')
        self.assertFalse(guard.substantive_commit(self.commit(), "mobile"))
        self.write(guard.CATALOG, '[versions]\nnzikVersionCode="3"\nkotlin="2.1"\n')
        self.assertTrue(guard.substantive_commit(self.commit(), "mobile"))

    def test_added_or_deleted_version_only_catalog_does_not_qualify(self):
        self.write(guard.CATALOG, '[versions]\nnzikVersionCode="1"\nnzikVersionName="1.0"\n')
        self.assertFalse(guard.substantive_commit(self.commit(), "mobile"))
        Path(guard.CATALOG).unlink()
        self.assertFalse(guard.substantive_commit(self.commit(), "mobile"))

    def test_qualifying_and_excluded_root_commits(self):
        excluded_root = self.git("rev-list", "--max-parents=0", "HEAD")
        self.assertFalse(guard.substantive_commit(excluded_root, "mobile"))
        self.git("checkout", "--orphan", "root-inputs")
        self.git("rm", "-f", "README.md")
        self.write("ComposeN-Zik/src/androidMain/jni/module_loader.c", "int load(void) { return 0; }\n")
        self.assertTrue(guard.substantive_commit(self.commit(), "mobile"))

    def test_code_mixed_with_localization_rename_and_delete_qualify(self):
        path = "ComposeN-Zik/src/main/kotlin/App.kt"
        self.write(path, "fun main() = Unit\n")
        self.write("ComposeN-Zik/src/main/localization/source/strings.xml", "translation")
        self.assertTrue(guard.substantive_commit(self.commit(), "desktop"))
        self.git("mv", path, "README.kt")
        self.assertTrue(guard.substantive_commit(self.commit(), "desktop"))
        self.write(path, "fun main() = Unit\n")
        self.commit()
        Path(path).unlink()
        self.assertTrue(guard.substantive_commit(self.commit(), "desktop"))

    def test_merge_alone_does_not_qualify_but_underlying_work_can(self):
        self.git("checkout", "-qb", "side")
        self.write("ComposeN-Zik/src/main/kotlin/App.kt", "fun main() = Unit\n")
        personal = self.commit()
        self.git("checkout", "-")
        self.write("README.md", "main branch\n")
        self.commit()
        self.git("merge", "--no-ff", "-m", "merge fixture", "side")
        self.assertFalse(guard.substantive_commit(self.git("rev-parse", "HEAD"), "desktop"))
        self.assertTrue(guard.substantive_commit(personal, "desktop"))

    def test_invalid_or_missing_git_revision_fails(self):
        with self.assertRaises(ValueError):
            guard.substantive_commit("--help", "mobile")
        with self.assertRaises(subprocess.CalledProcessError):
            guard.substantive_commit("a" * 40, "mobile")


if __name__ == "__main__":
    unittest.main()
