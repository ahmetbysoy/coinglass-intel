"""Outcome tracker: t+5m/15m/1h match + component win-rate."""
from __future__ import annotations

import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from engine.history_store import HistoryStore  # noqa: E402


class OutcomeTrackerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = HistoryStore(Path(self.tmp.name))
        self.t0 = datetime(2026, 8, 23, 12, 0, tzinfo=timezone.utc)

    def tearDown(self):
        self.tmp.cleanup()

    def _px(self, series):
        def get(symbol, ts):
            return series.get((symbol, ts))
        return get

    def test_settles_three_horizons_and_marks_wins(self):
        self.store.save(
            "ALLOUSDT", 1.00, 20.0, "HAFIF BULLISH",
            {"ob": 18, "tf": 4, "oi": 10, "funding": -2, "liq": 5, "vol": 3, "mom": 8},
            ts=self.t0,
        )
        series = {
            ("ALLOUSDT", self.t0 + timedelta(minutes=5)): 1.02,   # +2% win
            ("ALLOUSDT", self.t0 + timedelta(minutes=15)): 0.99,  # -1% lose
            ("ALLOUSDT", self.t0 + timedelta(hours=1)): 1.05,     # +5% win
        }
        rows = self.store.settle(self._px(series), now=self.t0 + timedelta(hours=2))
        by_h = {r.horizon: r for r in rows}
        self.assertEqual(len(rows), 3)
        self.assertTrue(by_h["5m"].settled and by_h["5m"].win is True)
        self.assertAlmostEqual(by_h["5m"].fwd_return_pct, 2.0, places=6)
        self.assertTrue(by_h["15m"].settled and by_h["15m"].win is False)
        self.assertTrue(by_h["1h"].settled and by_h["1h"].win is True)
        # persist + reload
        again = self.store.load_outcomes()
        self.assertEqual(len(again), 3)
        self.assertTrue(all(o.settled for o in again))

    def test_does_not_settle_future_horizon(self):
        self.store.save("X", 10, 40, "BULLISH", {"ob": 20}, ts=self.t0)
        rows = self.store.settle(lambda s, t: 11.0, now=self.t0 + timedelta(minutes=6))
        by_h = {r.horizon: r for r in rows}
        self.assertTrue(by_h["5m"].settled)
        self.assertFalse(by_h["15m"].settled)
        self.assertIsNone(by_h["15m"].win)

    def test_component_win_rate(self):
        # two signals, 15m later
        self.store.save(
            "AAAUSDT", 100, 15, "HAFIF BULLISH",
            {"ob": 20, "tf": -8, "oi": 12, "funding": 1, "liq": -2, "vol": 5, "mom": 4},
            ts=self.t0,
        )
        self.store.save(
            "BBBUSDT", 50, -18, "HAFIF BEARISH",
            {"ob": -15, "tf": -10, "oi": -6, "funding": 2, "liq": -4, "vol": -3, "mom": -5},
            ts=self.t0,
        )
        series = {
            ("AAAUSDT", self.t0 + timedelta(minutes=5)): 101,
            ("AAAUSDT", self.t0 + timedelta(minutes=15)): 103,  # +3%
            ("AAAUSDT", self.t0 + timedelta(hours=1)): 104,
            ("BBBUSDT", self.t0 + timedelta(minutes=5)): 49,
            ("BBBUSDT", self.t0 + timedelta(minutes=15)): 47,   # -6%
            ("BBBUSDT", self.t0 + timedelta(hours=1)): 46,
        }
        self.store.settle(self._px(series), now=self.t0 + timedelta(hours=3))
        stats = self.store.component_performance(horizon="15m", min_abs=1.0)
        # OB +20 vs +3% → win; OB -15 vs -6% → win (short)
        self.assertEqual(stats["OB"].n, 2)
        self.assertEqual(stats["OB"].win_rate, 1.0)
        self.assertGreater(stats["OB"].avg_when_bet, 0)
        text = self.store.format_performance("15m")
        self.assertIn("OB", text)
        self.assertIn("wr=", text)
        print("\n" + text)


if __name__ == "__main__":
    unittest.main()
