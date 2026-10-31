#!/usr/bin/env python3
"""Static wiring checks only; these do not run Minecraft or certify game behavior."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SMOKE = ROOT / "src/clientSmoke/java/enchantment_fusion/smoke"


class AcceptancePlanTest(unittest.TestCase):
    def test_all_exercised_fixtures_are_planned_once(self):
        plan = (SMOKE / "AcceptanceReport.java").read_text(encoding="utf-8")
        client = (SMOKE / "ClientSmoke.java").read_text(encoding="utf-8")
        planned = re.findall(r'\bplan\("([^"]+)"', plan)
        self.assertEqual(len(planned), len(set(planned)), "duplicate result IDs")
        exercised = re.findall(r'acceptance\.(?:run|passed|failed)\("([^"]+)"', client)
        self.assertFalse(set(exercised) - set(planned), "unplanned fixture IDs")
        self.assertTrue({"05-mending-unbreaking", "05-unbreaking-mending",
                         "09-efficiency-silk", "09-silk-efficiency", "11-multishot-piercing",
                         "11-piercing-multishot"}.issubset(exercised))
        self.assertIn('"SKIP"', plan)
        self.assertIn('"FAIL"', plan)
        self.assertIn('"PASS"', plan)
        self.assertIn('acceptance.hasFailures()', client)
        self.assertIn('acceptance-report.md', client)
        self.assertIn('run-client-smoke/acceptance-report.md',
                      (ROOT / '.github/workflows/build.yml').read_text(encoding='utf-8'))
        fallback = re.findall(r'plan\("([^"\\]+)", "([^"\\]+)",', plan)
        self.assertEqual(len(fallback), len(planned), 'startup fallback must enumerate every planned fixture')
        script = (ROOT / 'scripts/client_smoke.py').read_text(encoding='utf-8')
        self.assertIn('subprocess.TimeoutExpired', script)
        self.assertIn('Game did not initialize the report', script)

    def test_prepared_fixtures_do_not_depend_on_gui_success(self):
        client = (SMOKE / 'ClientSmoke.java').read_text(encoding='utf-8')
        initial = client.index('case 0 ->')
        after = client.index('case 1 ->', initial)
        self.assertIn('runIndependentFixtures(sp)', client[initial:after])
        self.assertIn('full redesign acceptance remains incomplete', client)


if __name__ == '__main__':
    unittest.main()
