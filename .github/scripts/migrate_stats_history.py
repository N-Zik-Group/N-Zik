"""Convert legacy metrics history to explicit UTC without changing aware records."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from zoneinfo import ZoneInfo


def normalize_history(records, legacy_zone, utc_prefix=0):
    """Preserve row order and fields, assigning a zone only to naive timestamps."""
    if not 0 <= utc_prefix <= len(records):
        raise ValueError("UTC prefix must be between zero and the record count")
    result = []
    for index, record in enumerate(records):
        point = dict(record)
        instant = datetime.fromisoformat(point["time"])
        if instant.tzinfo is None:
            zone = timezone.utc if index < utc_prefix else legacy_zone
            point["time"] = instant.replace(tzinfo=zone).astimezone(timezone.utc).isoformat(timespec="seconds")
        result.append(point)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("history", type=Path)
    parser.add_argument("--legacy-zone", required=True, help="IANA zone used by the legacy writer")
    parser.add_argument("--utc-prefix", type=int, required=True, help="Number of initial records originally written in UTC")
    args = parser.parse_args()
    records = json.loads(args.history.read_text(encoding="utf-8"))
    migrated = normalize_history(records, ZoneInfo(args.legacy_zone), args.utc_prefix)
    if migrated != records:
        args.history.write_text(json.dumps(migrated, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
