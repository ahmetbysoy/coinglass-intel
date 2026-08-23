"""Same fixture as android LockstepTest — engines must match."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from engine.curves import ls_score, oi_score, risk_score, rsi_signal  # noqa: E402
from engine.market_score import mom_from_rsi_and_ret  # noqa: E402

FIX = Path(__file__).resolve().parents[1] / "engine" / "fixtures" / "lockstep.json"


class LockstepTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(FIX.read_text())

    def test_rsi(self):
        for row in self.data["rsi"]:
            self.assertAlmostEqual(rsi_signal(row["in"]), row["out"], places=6)

    def test_oi(self):
        for row in self.data["oi"]:
            self.assertAlmostEqual(oi_score(row["oi"], row["chg"]), row["out"], places=6)

    def test_ls(self):
        for row in self.data["ls"]:
            self.assertAlmostEqual(ls_score(row["ls"]), row["out"], places=6)

    def test_risk(self):
        for row in self.data["risk"]:
            self.assertEqual(risk_score(row["atr"], row["fund"], row["ls"], row["vol"]), row["out"])

    def test_mom(self):
        for row in self.data["mom"]:
            self.assertAlmostEqual(mom_from_rsi_and_ret(row["rsi"], row["ret3"]), row["out"], places=6)


if __name__ == "__main__":
    unittest.main()
