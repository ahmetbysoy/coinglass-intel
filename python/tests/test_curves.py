"""Continuous curve contracts — no stair-steps, risk in 0..100."""
from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from engine.curves import (  # noqa: E402
    RISK_RAW_MAX,
    ls_score,
    oi_score,
    risk_raw,
    risk_score,
    rsi_signal,
    sl_tp,
)


class RsiCurveTests(unittest.TestCase):
    def test_smooth_around_50(self):
        self.assertAlmostEqual(rsi_signal(50), 0.0, places=6)
        # oversold → bullish, overbought → bearish
        self.assertGreater(rsi_signal(20), 20)
        self.assertLess(rsi_signal(80), -20)

    def test_no_stair_jump_across_old_bins(self):
        # old code jumped at 30/40/60/70. consecutive 0.5 RSI steps must be small
        xs = [rsi_signal(x / 2) for x in range(0, 201)]
        diffs = [abs(b - a) for a, b in zip(xs, xs[1:])]
        self.assertLess(max(diffs), 2.0)

    def test_saturates(self):
        self.assertLess(abs(rsi_signal(0)), 31)
        self.assertLess(abs(rsi_signal(100)), 31)


class OiLsCurveTests(unittest.TestCase):
    def test_oi_quadrants_at_large_moves(self):
        self.assertGreater(oi_score(20, 10), 40)    # +OI +px → ~+60
        self.assertLess(oi_score(20, -10), -25)     # +OI -px → ~-40
        self.assertGreater(oi_score(-20, 10), 18)   # -OI +px → ~+30
        self.assertLess(oi_score(-20, -10), -40)    # -OI -px → ~-60

    def test_oi_zero_when_flat(self):
        self.assertAlmostEqual(oi_score(0, 0), 0.0, places=6)
        self.assertAlmostEqual(oi_score(0, 5), 0.0, places=6)
        self.assertAlmostEqual(oi_score(5, 0), 0.0, places=6)

    def test_oi_monotone_in_magnitude(self):
        self.assertLess(abs(oi_score(1, 1)), abs(oi_score(8, 8)))

    def test_ls_crowding(self):
        self.assertLess(ls_score(2.5), -25)
        self.assertGreater(ls_score(0.4), 20)
        self.assertAlmostEqual(ls_score(1.0), 0.0, places=6)


class RiskNormalizeTests(unittest.TestCase):
    def test_raw_max_is_65(self):
        self.assertEqual(RISK_RAW_MAX, 65)
        raw = risk_raw(atr_pct=9, funding=0.02, ls_avg=3, vol24=100)
        self.assertEqual(raw, 65)

    def test_full_risk_is_100(self):
        self.assertEqual(risk_score(9, 0.02, 3, 100), 100)

    def test_zero_risk(self):
        self.assertEqual(risk_score(0.5, 0.0, 1.0, 50_000_000), 0)

    def test_partial_scales(self):
        # only vol<1M = 20 raw → 20/65*100 ≈ 31
        self.assertEqual(risk_score(0.5, 0.0, 1.0, 100), round(20 / 65 * 100))


class SlTpConfluenceTests(unittest.TestCase):
    def test_low_score_tighter_sl_not_wider_tp(self):
        sl_lo, tp_lo, slp_lo, tpp_lo = sl_tp(100, "BULLISH", atr_pct=2.0, total_score=5)
        sl_hi, tp_hi, slp_hi, tpp_hi = sl_tp(100, "BULLISH", atr_pct=2.0, total_score=80)
        self.assertLess(slp_lo, slp_hi)
        # RR grows with |score|
        self.assertLess(tpp_lo / slp_lo, tpp_hi / slp_hi)
        # low conf TP distance is NOT larger than high conf
        self.assertLess(tpp_lo, tpp_hi)

    def test_bear_side(self):
        sl, tp, slp, tpp = sl_tp(100, "BEARISH", 2.0, 50)
        self.assertGreater(sl, 100)
        self.assertLess(tp, 100)

    def test_rr_bounds(self):
        _, _, slp, tpp = sl_tp(100, "BULLISH", 2.0, 0)
        self.assertAlmostEqual(tpp / slp, 1.0, places=2)
        _, _, slp2, tpp2 = sl_tp(100, "BULLISH", 2.0, 100)
        self.assertAlmostEqual(tpp2 / slp2, 2.5, places=2)


if __name__ == "__main__":
    unittest.main()
