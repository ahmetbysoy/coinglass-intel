"""Candle metrics: RSI, ATR, BB, MACD, StochRSI, VWAP, S/R (v4.3)."""
from __future__ import annotations

import statistics
from typing import Any, Dict, List, Optional

from .util import to_float
from .scalper import wilder_rsi


def _ema(data: List[float], span: int) -> List[float]:
    if not data:
        return []
    alpha = 2 / (span + 1)
    out = [data[0]]
    for val in data[1:]:
        out.append(val * alpha + out[-1] * (1 - alpha))
    return out


def candle_metrics(klines: Any, label: str = "", warnings: Optional[List[str]] = None) -> Optional[Dict[str, Any]]:
    if not klines:
        return None
    if isinstance(klines, dict):
        raw = klines.get("data") or (klines.get("result") or {}).get("list") or []
    elif isinstance(klines, list):
        raw = klines
    else:
        return None
    if len(raw) < 5:
        return None

    rows = sorted(raw, key=lambda r: to_float(r[0]))
    open_times = [to_float(r[0]) for r in rows]
    if warnings is not None and len(open_times) != len(set(open_times)):
        warnings.append(f"candle_metrics({label}): duplicate open-time")
    if warnings is not None and len(rows) > 1:
        expected = to_float(rows[1][0]) - to_float(rows[0][0])
        for i in range(1, min(20, len(rows))):
            actual = to_float(rows[i][0]) - to_float(rows[i - 1][0])
            if expected > 0 and actual > 0 and abs(actual - expected) / expected > 0.1:
                warnings.append(f"candle_metrics({label}): gap ({actual:.0f} vs {expected:.0f})")
                break

    closes = [to_float(r[4]) for r in rows]
    highs = [to_float(r[2]) for r in rows]
    lows = [to_float(r[3]) for r in rows]
    vols = [to_float(r[5]) for r in rows]
    last, first = closes[-1], closes[0]
    if not first:
        return None

    ret = (last - first) / first * 100
    ret_3 = (closes[-1] - closes[-4]) / closes[-4] * 100 if len(closes) > 4 and closes[-4] else 0.0

    rsi = wilder_rsi(closes, 14)
    vol_last = vols[-1] if vols else 0.0
    vol_med = statistics.median(vols[:-1]) if len(vols) > 1 else 1.0

    trs = []
    for i, r in enumerate(rows):
        if i == 0:
            tr = to_float(r[2]) - to_float(r[3])
        else:
            pc = to_float(rows[i - 1][4])
            tr = max(to_float(r[2]) - to_float(r[3]), abs(to_float(r[2]) - pc), abs(to_float(r[3]) - pc))
        trs.append(tr)
    atr = statistics.mean(trs[-14:]) if len(trs) >= 14 else statistics.mean(trs)
    atr_pct = atr / last * 100 if last else 0.0

    if len(closes) >= 20:
        sma = statistics.mean(closes[-20:])
        sd = statistics.stdev(closes[-20:])
        bb_pct = (last - sma) / (2 * sd) * 100 if sd else 0.0
    else:
        bb_pct = 0.0

    if len(closes) >= 35:
        ema12 = _ema(closes, 12)
        ema26 = _ema(closes, 26)
        macd_series = [a - b for a, b in zip(ema12, ema26)]
        signal_series = _ema(macd_series[-26:] if len(macd_series) >= 26 else macd_series, 9)
        macd_line, signal_line = macd_series[-1], signal_series[-1]
        histogram = macd_line - signal_line
    else:
        macd_line = signal_line = histogram = 0.0

    if len(closes) >= 14:
        hh, ll = max(highs[-14:]), min(lows[-14:])
        stoch_rsi = (last - ll) / (hh - ll) * 100 if hh != ll else 50.0
    else:
        stoch_rsi = 50.0

    typical = [(to_float(r[2]) + to_float(r[3]) + to_float(r[4])) / 3 for r in rows]
    vol_sum = sum(vols)
    vwap = sum(t * v for t, v in zip(typical, vols)) / vol_sum if vol_sum else last

    support_tests = resistance_tests = 0
    if len(closes) > 5:
        for i in range(1, 5):
            if abs(lows[-i] - lows[-i - 1]) / (lows[-i] + 1e-6) < 0.01:
                support_tests += 1
            if abs(highs[-i] - highs[-i - 1]) / (highs[-i] + 1e-6) < 0.01:
                resistance_tests += 1

    return {
        "label": label, "ret": ret, "ret_3": ret_3, "rsi": rsi, "atr": atr, "atr_pct": atr_pct,
        "bb_pct": bb_pct, "last": last, "high": max(highs), "low": min(lows),
        "vol_total": sum(vols), "n": len(closes),
        "macd_line": macd_line, "signal_line": signal_line, "histogram": histogram,
        "stoch_rsi": stoch_rsi, "vwap": vwap,
        "support_tests": support_tests, "resistance_tests": resistance_tests,
    }
