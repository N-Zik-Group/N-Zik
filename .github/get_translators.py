import json
from os import environ

from requests import get, Response


"""
The translators shown in the About screen are the translation members of the
Crowdin project. This script fetches them from the Crowdin API and writes
them to `translators.json` in the same shape the app's loader expects:

    [{"username": ..., "displayName": ..., "languages": "A, B",
      "avatarUrl": ..., "profileUrl": ...}, ...]

A member is listed when they translate or proofread at least one project
language (the per-language roles come from the member `permissions` map).
Any failure fails the workflow, so a bad run never overwrites the list.

Requires env CROWDIN_PROJECT_ID and CROWDIN_PERSONAL_TOKEN.
"""
crowdin_base = 'https://api.crowdin.com/api/v2'
project_id = environ['CROWDIN_PROJECT_ID']
token = environ['CROWDIN_PERSONAL_TOKEN']
headers = {'Authorization': f'Bearer {token}'}

# Roles that count as "worked on a translation" for the About screen.
translating_roles = ('translator', 'proofreader')
page_limit = 100


def get_json(url: str, params: dict) -> dict:
    """
    One GET against the Crowdin API. Fails the workflow on any non-2xx
    (an empty response is never written over the committed list).
    """
    response: Response = get(url, params=params, headers=headers, timeout=30)
    response.raise_for_status()
    return response.json()


def get_all_members(role: str) -> list[dict]:
    """
    All members carrying the given role, following the pagination
    (limit/offset) until a short page comes back.
    """
    members: list[dict] = []
    offset = 0
    while True:
        body = get_json(
            f'{crowdin_base}/projects/{project_id}/members',
            {'role': role, 'limit': page_limit, 'offset': offset},
        )
        page = body['data']
        members.extend(page)
        if len(page) < page_limit:
            break
        offset += page_limit
    return members


#
#    It STARTS here
#
if __name__ == '__main__':
    # Language code -> display name, for this project's target languages
    project = get_json(f'{crowdin_base}/projects/{project_id}', {})['data']
    language_names: dict[str, str] = {}
    for lang in project.get('targetLanguages', []):
        code = lang.get('code') or lang.get('id') or lang.get('twoLettersCode')
        if code:
            language_names[code] = lang.get('name') or code

    # Collect every member that translates or proofreads (merged by username)
    members: dict[str, dict] = {}
    for role in translating_roles:
        for member in get_all_members(role):
            members[member['username']] = member

    translators = []
    for member in members.values():
        permissions = member.get('permissions') or {}
        languages = sorted({
            language_names.get(code, code)
            for code, member_role in permissions.items()
            if member_role in translating_roles
        })
        if not languages:
            continue
        translators.append({
            'username': member['username'],
            'displayName': member.get('fullName') or member['username'],
            'languages': ', '.join(languages),
            'avatarUrl': member.get('avatarUrl') or '',
            'profileUrl': f"https://crowdin.com/profile/{member['username']}",
        })

    # The app's loader sorts by display name anyway — deterministic order
    translators.sort(key=lambda t: t['displayName'].lower())

    #
    #   Write translators to a file named `translators.json`
    #
    json_format: str = json.dumps(translators)
    with open('translators.json', 'w') as file:
        file.write(json_format)
