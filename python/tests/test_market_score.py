"""market_score.score_components wires the new curves."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from engine.market_score import confluence, mom_from_rsi_and_ret, score_components  # noqa: E402


class ScoreWireTests(unittest.TestCase):
    def test_bullish_stack(self):
        b = score_components(
            price=100,
            chg24=4,
            oi_chg_pct=8,
            ls_avg=0.55,
            funding=-0.0004,
            agg_imb=22,
            cvd_pct=15,
            vol_score=20,
            rsi=28,
            ret_3=1.2,
            atr_pct=2.5,
            vol24=50_000_000,
        )
        self.assertGreater(b.total, 10)
        self.assertIn("BULL", b.direction)
        self.assertGreater(b.oi, 0)
        self.assertGreater(b.liq, 0)
        self.assertGreater(b.mom, 0)
        self.assertLess(b.sl, 100)
        self.assertGreater(b.tp, 100)
        self.assertGreaterEqual(b.risk, 0)
        self.assertLessEqual(b.risk, 100)

    def test_mom_continuous_not_binned(self):
        a = mom_from_rsi_and_ret(69.5, 0)
        b = mom_from_rsi_and_ret(70.5, 0)
        self.assertLess(abs(a - b), 3.0)

    def test_confluence_uses_1m_3m(self):
        flat = {"5m": (0.0, 1.0), "15m": (0.0, 1.0)}
        fast = dict(flat, **{"1m": (4.0, 0.8), "3m": (3.0, 0.9)})
        self.assertGreater(confluence(fast), confluence(flat) + 1.0)
        self.assertGreater(confluence(fast), 0.0)
        # 1h must not participate
        with_1h = dict(flat, **{"1h": (9.0, 0.5)})
        self.assertAlmostEqual(confluence(flat), confluence(with_1h), places=6)

    def test_high_risk_hits_100(self):
        b = score_components(
            price=10, atr_pct=9, funding=0.02, ls_avg=3.0, vol24=100, rsi=50,
        )
        self.assertEqual(b.risk, 100)


if __name__ == "__main__":
    unittest.main()
