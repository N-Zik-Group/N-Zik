"""Fail closed when stable APK provenance or an existing release tag disagrees."""

import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen
from zipfile import ZipFile


VCS_PATH = "META-INF/version-control-info.textproto"
ABIS = {
    "release64": {"arm64-v8a", "x86_64"},
    "minified64": {"arm64-v8a", "x86_64"},
    "release32": {"armeabi-v7a", "x86"},
    "minified32": {"armeabi-v7a", "x86"},
    "foss": {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"},
}


def full_sha(value):
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError("Expected a full lowercase Git commit SHA")
    return value


def verify_apk(path, sha, variant):
    full_sha(sha)
    with ZipFile(path) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"{path}: duplicate ZIP entries")
        metadata = [name for name in names if name.endswith("/version-control-info.textproto")]
        if metadata != [VCS_PATH]:
            raise ValueError(f"{path}: missing or ambiguous embedded VCS metadata: {metadata}")
        revisions = re.findall(r'\brevision\s*:\s*"([^"\n]+)"',
                               apk.read(VCS_PATH).decode("utf-8"))
        if revisions != [sha]:
            raise ValueError(f"{path}: embedded revision {revisions!r} differs from {sha}")
        libraries = {
            name.split("/")[1]: name for name in names
            if re.fullmatch(r"lib/[^/]+/libmodule_loader\.so", name)
        }
        if set(libraries) != ABIS[variant]:
            raise ValueError(f"{path}: module_loader ABIs {sorted(libraries)}; "
                             f"expected {sorted(ABIS[variant])}")
        if any(apk.getinfo(name).file_size == 0 for name in libraries.values()):
            raise ValueError(f"{path}: empty module_loader library")


def github_get(path):
    request = Request("https://api.github.com" + path, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def verify_tag(repository, tag, sha, get=github_get):
    full_sha(sha)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid GitHub repository")
    prefix = f"/repos/{repository}/git/"
    try:
        obj = get(prefix + "ref/tags/" + quote(tag, safe=""))["object"]
    except HTTPError as error:
        if error.code == 404:
            return  # A missing tag will be created at target_commitish by the release action.
        raise
    seen = set()
    while obj["type"] == "tag":
        tag_sha = full_sha(obj["sha"])
        if tag_sha in seen or len(seen) >= 10:
            raise ValueError("Cyclic or excessively nested annotated tag")
        seen.add(tag_sha)
        obj = get(prefix + "tags/" + tag_sha)["object"]
    if obj["type"] != "commit" or obj["sha"] != sha:
        raise ValueError(f"Existing tag {tag} targets {obj!r}, expected commit {sha}; "
                         "refusing to publish or move the tag")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    apk = commands.add_parser("apk")
    apk.add_argument("--sha", required=True, type=full_sha)
    apk.add_argument("--variant", required=True, choices=ABIS)
    apk.add_argument("--directory", required=True, type=Path)
    tag = commands.add_parser("tag")
    tag.add_argument("--sha", required=True, type=full_sha)
    tag.add_argument("--repository", required=True)
    tag.add_argument("--tag", required=True)
    args = parser.parse_args()
    if args.command == "apk":
        paths = sorted(args.directory.glob("*.apk"))
        if not paths:
            raise ValueError(f"No APKs found in {args.directory}")
        for path in paths:
            verify_apk(path, args.sha, args.variant)
            print(f"Verified {path}: {args.sha}, {sorted(ABIS[args.variant])}")
    else:
        verify_tag(args.repository, args.tag, args.sha)
        print(f"Release tag {args.tag} is absent or agrees with {args.sha}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        sys.exit(str(error))
