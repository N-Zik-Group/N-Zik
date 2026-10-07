import json
from os import environ

from requests import get, Response


"""
The translators shown in the About screen are everyone on the Crowdin
project: every project member, plus anyone who translated there. For each
person the `languages` field lists the languages they actually have a
current translation in (aggregated from the per-language translation
history); members with an explicit per-language access but no current
translation get that explicit list. The script writes `translators.json`
in the same shape the app's loader expects:

    [{"username": ..., "displayName": ..., "languages": "A, B",
      "avatarUrl": ..., "profileUrl": ...}, ...]

Any failure fails the workflow, so a bad run never overwrites the
committed list.

Requires env CROWDIN_PROJECT_ID and CROWDIN_PERSONAL_TOKEN.
"""
crowdin_base = 'https://api.crowdin.com/api/v2'
project_id = environ['CROWDIN_PROJECT_ID']
token = environ['CROWDIN_PERSONAL_TOKEN']
headers = {'Authorization': f'Bearer {token}'}

page_limit = 500


def get_json(url: str, params: dict) -> dict:
    """
    One GET against the Crowdin API. Fails the workflow on any non-2xx
    (an empty response is never written over the committed list).
    """
    response: Response = get(url, params=params, headers=headers, timeout=60)
    response.raise_for_status()
    return response.json()


def get_all_items(path: str, params: dict) -> list[dict]:
    """
    Every item of a paginated endpoint, following the pagination
    (limit/offset) until a short page comes back. Items may come wrapped
    in a nested `data` object — unwrap to the plain object.
    """
    items: list[dict] = []
    offset = 0
    while True:
        body = get_json(f'{crowdin_base}{path}', {**params, 'limit': page_limit, 'offset': offset})
        page = body['data']
        items.extend(
            item.get('data') if isinstance(item, dict) and 'data' in item else item
            for item in page
        )
        if len(page) < page_limit:
            break
        offset += page_limit
    return items


#
#    It STARTS here
#
if __name__ == '__main__':
    # Language code -> display name, for this project's target languages
    project = get_json(f'{crowdin_base}/projects/{project_id}', {})['data']
    language_names: dict[str, str] = {}
    target_languages: list[tuple[str, str]] = []
    for lang in project.get('targetLanguages', []):
        code = lang.get('code') or lang.get('id') or lang.get('twoLettersCode')
        if code:
            name = lang.get('name') or code
            language_names[code] = name
            target_languages.append((code, name))

    users: dict[str, dict] = {}

    def note_user(username: str, full_name: str = None, avatar_url: str = None, language: str = None) -> None:
        entry = users.setdefault(
            username, {'fullName': None, 'avatarUrl': None, 'languages': set()}
        )
        if language:
            entry['languages'].add(language)
        # Keep the first non-empty values seen across that user's records
        if not entry['fullName'] and full_name:
            entry['fullName'] = full_name
        if not entry['avatarUrl'] and avatar_url:
            entry['avatarUrl'] = avatar_url

    # 1. Everyone with a current translation, per language
    for code, name in target_languages:
        for translation in get_all_items(
            f'/projects/{project_id}/languages/{code}/translations', {}
        ):
            user = translation.get('user') or {}
            username = user.get('username')
            if not username:
                continue
            note_user(username, user.get('fullName'), user.get('avatarUrl'), name)

    # 2. Every project member (explicit per-language access fills in gaps)
    for member in get_all_items(f'/projects/{project_id}/members', {'role': 'all'}):
        username = member.get('username')
        if not username:
            continue
        note_user(username, member.get('fullName'), member.get('avatarUrl'))
        for role in member.get('roles') or []:
            permissions = role.get('permissions') or {}
            for code in permissions.get('languagesAccess') or []:
                note_user(username, language=language_names.get(code, code))

    translators = []
    for username, entry in users.items():
        translators.append({
            'username': username,
            'displayName': entry['fullName'] or username,
            'languages': ', '.join(sorted(entry['languages'])),
            'avatarUrl': entry['avatarUrl'] or '',
            'profileUrl': f"https://crowdin.com/profile/{username}",
        })

    # The app's loader sorts by display name anyway — deterministic order
    translators.sort(key=lambda t: t['displayName'].lower())

    #
    #   Write translators to a file named `translators.json`
    #
    json_format: str = json.dumps(translators)
    with open('translators.json', 'w') as file:
        file.write(json_format)

    print(f'Wrote {len(translators)} translators across {len(target_languages)} languages')
