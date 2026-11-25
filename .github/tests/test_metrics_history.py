"""Metrics migration and actual workflow-reader regressions."""

import ast
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import textwrap
import unittest
from unittest.mock import patch
from zoneinfo import ZoneInfo


UTC_PREFIX = 735
ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/migrate_stats_history.py"
SPEC = importlib.util.spec_from_file_location("migrate_stats_history", SCRIPT)
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class MigrationTests(unittest.TestCase):
    def test_utc_prefix_and_legacy_offsets_preserve_other_fields_and_order(self):
        records = [
            {"time": "2026-06-13 21:50", "downloads": 12467, "extra": {"source": "original"}},
            {"time": "2026-07-01 14:00", "downloads": 12480},
            {"time": "2026-11-01 14:00", "downloads": 12500},
        ]
        before = json.loads(json.dumps(records))
        migrated = migration.normalize_history(records, ZoneInfo("Europe/Brussels"), utc_prefix=1)
        self.assertEqual([row["time"] for row in migrated], [
            "2026-06-13T21:50:00+00:00", "2026-07-01T12:00:00+00:00", "2026-11-01T13:00:00+00:00",
        ])
        self.assertEqual(len(migrated), len(records))
        self.assertEqual([{key: value for key, value in row.items() if key != "time"} for row in migrated],
                         [{key: value for key, value in row.items() if key != "time"} for row in before])
        self.assertEqual(records, before)

    def test_aware_records_are_unchanged_and_migration_is_idempotent(self):
        records = [
            {"time": "2026-06-13T21:50:00Z", "downloads": 1},
            {"time": "2026-07-01T14:00:00+02:00", "downloads": 2},
            {"time": "2026-11-01 14:00", "downloads": 3},
        ]
        migrated = migration.normalize_history(records, ZoneInfo("Europe/Brussels"), 1)
        self.assertEqual(migrated[:2], records[:2])
        self.assertEqual(migration.normalize_history(migrated, ZoneInfo("Europe/Brussels"), 1), migrated)

    def test_cli_requires_explicit_policy_and_does_not_rewrite_aware_history(self):
        with tempfile.TemporaryDirectory() as directory:
            history = Path(directory) / "history.json"
            history.write_text('[{"time":"2026-06-13 21:50","downloads":1}]', encoding="utf-8")
            args = [str(SCRIPT), str(history), "--legacy-zone", "Europe/Brussels", "--utc-prefix", "1"]
            with patch("sys.argv", args):
                migration.main()
            first = history.read_bytes()
            self.assertEqual(json.loads(first)[0]["time"], "2026-06-13T21:50:00+00:00")
            with patch("sys.argv", args):
                migration.main()
            self.assertEqual(history.read_bytes(), first)


class ProductionReaderTests(unittest.TestCase):
    def test_actual_workflow_reader_accepts_real_migrated_and_future_utc_records(self):
        workflow = (ROOT / ".github/workflows/metrics.yml").read_text(encoding="utf-8")
        block = workflow.split("<< 'PYEOF'\n", 1)[1].split("\n          PYEOF", 1)[0]
        tree = ast.parse(textwrap.dedent(block))
        call = next(node.value for node in tree.body if isinstance(node, ast.Expr)
                    and isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name)
                    and node.value.func.id == "render_chart")
        reader = compile(ast.Expression(call.args[0]), str(ROOT / ".github/workflows/metrics.yml"), "eval")
        records = json.loads((ROOT / "assets/stats/history.json").read_text(encoding="utf-8"))
        future_time = datetime.fromisoformat(records[-1]["time"]).astimezone(timezone.utc) + timedelta(days=1)
        future = {"time": future_time.isoformat(), "downloads": records[-1]["downloads"] + 1}
        points = eval(reader, {"datetime": datetime, "history_zone": ZoneInfo("Europe/Brussels"), "downloads": records + [future]})
        self.assertEqual(len(points), len(records) + 1)
        self.assertEqual([point[1] for point in points], [row["downloads"] for row in records + [future]])
        self.assertEqual(points[-1], (future_time.timestamp(), future["downloads"]))
        self.assertEqual([point[0] for point in points], sorted(point[0] for point in points))
        if UTC_PREFIX:
            # Writer switched from date -u in d27bde4d808108bcaec80dd6e50be6fdedd1f1e2.
            # Its parent 6f42844b99727bdfd9422e543e76e4611dbeeb2b contains this exact UTC prefix.
            self.assertEqual(points[0], (1781387400, 12467))
            self.assertEqual(points[734], (1788937020, 34753))
            self.assertEqual(points[735], (1788957120, 34792))
            epochs = [int(point[0]) for point in points[:735]]
            digest = hashlib.sha256(json.dumps(epochs, separators=(",", ":")).encode()).hexdigest()
            self.assertEqual(digest, "5af7e0ba8935fea70e49addb2468933c0c5fe1ba53ad3d74380d9ca654f46e3c")
            self.assertEqual(points[1073], (1795565580, 75986))
        else:
            self.assertGreaterEqual(len(records), 134)
            self.assertEqual(points[0], (1791228240, 0))
            self.assertEqual(points[133], (1795491240, 49))


if __name__ == "__main__":
    unittest.main()
