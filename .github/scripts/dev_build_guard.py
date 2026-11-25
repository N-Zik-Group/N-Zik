"""Authorize dev builds from substantive maintainer work, failing closed."""

import argparse
import datetime
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tomllib
from urllib.parse import urlencode


MAINTAINER = "NEVARLeVrai"
CATALOG = "gradle/libs.versions.toml"
SHA = re.compile(r"[0-9a-f]{40,64}\Z")


def command(*args):
    return subprocess.run(args, check=True, capture_output=True).stdout


def api_pages(endpoint, params=None):
    """Read every page and reject malformed responses or failed requests."""
    page = 1
    while True:
        query = urlencode({**(params or {}), "per_page": 100, "page": page})
        data = json.loads(command("gh", "api", f"{endpoint}?{query}"))
        if not isinstance(data, list) or any(not isinstance(row, dict) for row in data):
            raise ValueError("Expected a GitHub API list of objects")
        yield from data
        if len(data) < 100:
            return
        page += 1


def bot_identity(user, raw):
    if user and user.get("type", "").casefold() == "bot":
        return True
    identity = " ".join(str(value) for value in (
        (user or {}).get("login", ""), raw.get("name", ""), raw.get("email", "")
    )).casefold()
    return any(marker in identity for marker in ("[bot]", "github-actions", "dependabot", "renovate-bot", "crowdin"))


def personal_commit(commit):
    author = commit.get("author")
    metadata = commit["commit"]
    return (
        isinstance(author, dict)
        and author.get("login", "").casefold() == MAINTAINER.casefold()
        and not bot_identity(author, metadata["author"])
        and not bot_identity(commit.get("committer"), metadata["committer"])
    )


def qualifying_path(path, profile):
    parts = PurePosixPath(path).parts
    lower = path.casefold()
    if any(part.casefold() in {"localization", "translations", "test", "tests", "androidtest", "commontest", "androidunittest", "testfixtures", "build"} for part in parts):
        return False
    if lower.endswith(".md") or PurePosixPath(lower).name in {"contributors.json", "translators.json", "release_notes.txt"}:
        return False
    if "/res/values" in lower and PurePosixPath(lower).name.startswith(("strings", "plurals")):
        return False
    if lower.endswith((".gradle.kts", ".gradle")):
        return path.startswith(("ComposeN-Zik/", "extensions/", "buildSrc/")) or "/" not in path
    if path in {CATALOG, "gradle.properties", "gradle/gradle-daemon-jvm.properties", "gradlew", "gradlew.bat", "settings.gradle.kts", ".gitmodules"}:
        return True
    if path.startswith(("gradle/wrapper/", "buildSrc/src/")):
        return True
    if profile == "mobile":
        if path in {"modules/betterlyrics", "modules/discordrpc", "modules/nextvisualizer", "ComposeN-Zik/proguard-rules.pro"}:
            return True
        if path.startswith("extensions/") and "/src/" in path:
            return True
        if path.startswith("ComposeN-Zik/src/"):
            return any(marker in path for marker in ("/kotlin/", "/java/", "/jni/", "/res/", "/resources/", "/assets/", "/proto/")) or path.endswith("/AndroidManifest.xml")
    else:
        if path.startswith(("packaging/", "ComposeN-Zik/packaging/", "tools/nsis/", "assets/design/", "ComposeN-Zik/src/main/")):
            return True
        if path in {"build.sh", "scripts/build-vlc-linux-tarball.sh"}:
            return True
    return False


def catalog_value(revision):
    if revision is None:
        return {}
    value = tomllib.loads(command("git", "show", f"{revision}:{CATALOG}").decode("utf-8"))
    for key in ("nzikVersionCode", "nzikVersionName"):
        value.get("versions", {}).pop(key, None)
    if not value.get("versions"):
        value.pop("versions", None)
    return value


def substantive_commit(sha, profile):
    if not SHA.fullmatch(sha):
        raise ValueError("Invalid commit SHA from GitHub")
    parents = command("git", "rev-list", "--parents", "-n", "1", sha).decode().split()[1:]
    if len(parents) > 1:
        print(f"Skip {sha}: merge commit; inspect its underlying commits instead")
        return False
    parent = parents[0] if parents else None
    # No rename detection: both the removed and added input must be considered.
    args = ("git", "diff", "--no-renames", "--name-status", "-z", parent, sha) if parent else (
        "git", "diff-tree", "--root", "--no-commit-id", "--no-renames", "--name-status", "-r", "-z", sha
    )
    fields = command(*args).decode("utf-8").split("\0")
    for status, path in zip(fields[0::2], fields[1::2]):
        if not qualifying_path(path, profile):
            continue
        if path == CATALOG:
            before = {} if status == "A" else catalog_value(parent)
            after = {} if status == "D" else catalog_value(sha)
            if before == after:
                continue
        print(f"Eligible {sha}: changed application/build input {path}")
        return True
    print(f"Skip {sha}: only excluded or version-only changes")
    return False


def event_allowed(event, actor):
    return event == "schedule" or (event == "workflow_dispatch" and actor.casefold() == MAINTAINER.casefold())


def eligible(profile, event, actor, repository, revision, now=None):
    if not event_allowed(event, actor):
        print("Skip: unsupported event or manual dispatch by another account")
        return False
    now = (now or datetime.datetime.now(datetime.timezone.utc)).astimezone(datetime.timezone.utc)
    today = now.strftime("%Y%m%d")
    for release in api_pages(f"repos/{repository}/releases"):
        if release["tag_name"].endswith(f"-dev-{today}"):
            print("Skip: a dev release already exists today")
            return False
    if event == "workflow_dispatch":
        print("Eligible: explicit maintainer dispatch")
        return True
    since = (now - datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    found = False
    for commit in api_pages(f"repos/{repository}/commits", {"sha": revision, "since": since, "author": MAINTAINER}):
        if not personal_commit(commit):
            print(f"Skip {commit['sha']}: not personal maintainer authorship or bot identity")
            continue
        # Continue reading even after a match so a later API/Git failure fails closed.
        found = substantive_commit(commit["sha"], profile) or found
    if not found:
        print("Skip: no substantive personal application/build work in the last day")
    return found


def output(key, value):
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
        stream.write(f"{key}={str(value).lower()}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("author", "changes"))
    parser.add_argument("--profile", choices=("mobile", "desktop"), required=True)
    args = parser.parse_args()
    key = "should-build" if args.phase == "author" else "changed"
    output(key, False)
    try:
        event = os.environ["GITHUB_EVENT_NAME"]
        actor = os.environ["GITHUB_ACTOR"]
        if args.phase == "author":
            allowed = event_allowed(event, actor)
            print("Event authorized" if allowed else "Skip: manual dispatch requires NEVARLeVrai; only nightly/manual events are supported")
        else:
            allowed = eligible(args.profile, event, actor, os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"])
        output(key, allowed)
        return 0
    except (KeyError, OSError, ValueError, TypeError, subprocess.CalledProcessError) as error:
        detail = type(error).__name__
        if isinstance(error, subprocess.CalledProcessError):
            detail = f"{error.cmd[0]} exited with status {error.returncode}"
        print(f"::error::Dev build eligibility lookup failed ({detail}); build remains blocked", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
