import json
import os
from html import escape
from pathlib import Path
from urllib.request import Request, urlopen

ORDER = ["N-Zik", "N-Zik-Desktop-Compagnon", "N-Zik-Website", "Better-Lyrics", "DiscordRPC", "Next-Visualizer"]
headers = {"User-Agent": "nzik-contributors"}
if token := os.getenv("GH_TOKEN"):
    headers["Authorization"] = "Bearer " + token

def api(endpoint):
    url = "https://api.github.com/" + endpoint + "?per_page=100"
    while url:
        with urlopen(Request(url, headers=headers), timeout=25) as response:
            yield from json.load(response)
            url = next((part.split(">")[0].strip()[1:] for part in response.headers.get("Link", "").split(",") if 'rel="next"' in part), None)

def grid(rows):
    return '<p align="center">\n' + "\n".join(
        f'<a href="{escape(profile)}"><img src="{escape(avatar)}" width="54" height="54" alt="{escape(name)}" title="{escape(name)}" /></a>'
        for name, profile, avatar in rows
    ) + "\n</p>"

def replace(text, marker, content):
    start, end = f"<!-- {marker}:start -->", f"<!-- {marker}:end -->"
    before, rest = text.split(start, 1)
    _, after = rest.split(end, 1)
    return before + start + "\n" + content + "\n" + end + after

profile = Path("profile/README.md")
path = profile if profile.exists() else Path("README.md")
text = path.read_text(encoding="utf-8")
if profile.exists():
    available = {r["name"] for r in api("orgs/N-Zik-Group/repos") if not r["private"] and not r["name"].startswith(".")}
    repos = ["N-Zik-Group/" + name for name in [n for n in ORDER if n in available] + sorted(available - set(ORDER))]
    marker = "ecosystem-contributors"
else:
    repos = [os.environ["GITHUB_REPOSITORY"]]
    marker = "project-contributors"
seen = set()
rows = []
for repo in repos:
    for user in api(f"repos/{repo}/contributors"):
        if user["type"] == "User" and user["id"] not in seen:
            seen.add(user["id"])
            rows.append((user["login"], user["html_url"], user["avatar_url"]))
if not rows:
    raise SystemExit("No contributors returned")
text = replace(text, marker, grid(rows))
if "<!-- crowdin-contributors:start -->" in text:
    url = "https://raw.githubusercontent.com/N-Zik-Group/N-Zik/main/ComposeN-Zik/src/androidMain/res/raw/translators.json"
    with urlopen(url, timeout=25) as response:
        translators = json.load(response)
    rows = [(u.get("displayName") or u["username"], u["profileUrl"], u["avatarUrl"]) for u in translators if u.get("profileUrl") and u.get("avatarUrl")]
    if not rows:
        raise SystemExit("No Crowdin translators returned")
    text = replace(text, "crowdin-contributors", grid(rows))
path.write_text(text, encoding="utf-8", newline="\n")
