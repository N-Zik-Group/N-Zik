import json
import re
from os import environ

from bs4 import BeautifulSoup as BSoup, Tag
from requests import get, Response


"""
Append more keys if needed, refrain from taking all.
More elements mean more loading time.
"""
contributors_keys = ['login', 'id', 'name', 'avatar_url', 'html_url', 'contributions']
def filter_keys(entry: dict) -> None:
    """
    Extract needed keys from json file
    """
    # Keys must be converted to list to
    # prevent "modify-during-loop"
    for key in list(entry.keys()):
        if key not in contributors_keys:
            del entry[key]


contributors_url = f'https://api.github.com/repos/{environ["GITHUB_REPOSITORY"]}/contributors'
def get_latest_contributors() -> list[dict]:
    """
    Gets all the contributors of this repository via GitHub API.

    Requires env GITHUB_REPOSITORY. A string in format {OWNER}/{REPO}, i.e. N-Zik-Group/N-Zik
    """

    response: Response = get(contributors_url)
    # Convert JSON string returned from GitHub API
    # to dict-like object (easier to work on).
    latest_contributors = json.loads(response.text)

    # Filter out unwanted keys of each dev to save some space
    # Each 'entry' represents a developer
    for entry in latest_contributors:
        filter_keys(entry)

    return latest_contributors


#
#    It STARTS here
#
if __name__ == '__main__':
    # Get latest contributors
    contributors: list[dict] = get_latest_contributors()

    #
    #   Write contributors to a file named `contributors.json`
    #
    json_format: str = json.dumps(contributors)
    with open('contributors.json', 'w') as file:
        file.write(json_format)
