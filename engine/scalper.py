"""Scalper extras: Wilder-adjacent helpers, CVD divergence, 25bps book, EV bands."""
from __future__ import annotations

from typing import Dict, List, Optional, Tuple

from .util import to_float


def wilder_rsi(closes: List[float], period: int = 14) -> float:
    if len(closes) < period + 1:
        return 50.0
    deltas = [closes[i] - closes[i - 1] for i in range(1, len(closes))]
    gains = [max(d, 0.0) for d in deltas]
    losses = [max(-d, 0.0) for d in deltas]
    avg_gain = sum(gains[:period]) / period
    avg_loss = sum(losses[:period]) / period
    for g, loss in zip(gains[period:], losses[period:]):
        avg_gain = (avg_gain * (period - 1) + g) / period
        avg_loss = (avg_loss * (period - 1) + loss) / period
    if avg_gain == 0 and avg_loss == 0:
        return 50.0
    if avg_loss == 0:
        return 100.0
    if avg_gain == 0:
        return 0.0
    rs = avg_gain / avg_loss
    return 100.0 - (100.0 / (1.0 + rs))


def linear_slope(xs: List[float]) -> float:
    n = len(xs)
    if n < 2:
        return 0.0
    x_mean = sum(xs) / n
    y_mean = (n - 1) / 2
    num = sum((xs[i] - x_mean) * i for i in range(n))
    den = sum((i - y_mean) ** 2 for i in range(n))
    return num / den if den else 0.0


def detect_cvd_divergence(price_series: List[float], cvd_series: List[float], lookback: int = 20) -> dict:
    if len(price_series) < lookback or len(cvd_series) < lookback:
        return {"divergence": False, "type": None, "strength": 0.0}
    ps = linear_slope(price_series[-lookback:])
    cs = linear_slope(cvd_series[-lookback:])
    if ps > 0 and cs < 0:
        return {"divergence": True, "type": "bearish", "strength": abs(ps) + abs(cs)}
    if ps < 0 and cs > 0:
        return {"divergence": True, "type": "bullish", "strength": abs(ps) + abs(cs)}
    return {"divergence": False, "type": None, "strength": 0.0}


def build_cvd_series(taker_hist, current_price: float = 1.0) -> List[float]:
    if not isinstance(taker_hist, list) or not taker_hist:
        return []
    try:
        hist = sorted(taker_hist, key=lambda x: int(x.get("timestamp") or 0))
    except Exception:
        hist = taker_hist
    running = 0.0
    out = []
    for bar in hist:
        buy = to_float(bar.get("sumTakerBuyVolume") or bar.get("buyVol") or 0)
        sell = to_float(bar.get("sumTakerSellVolume") or bar.get("sellVol") or 0)
        running += (buy - sell) * current_price
        out.append(running)
    return out


def ev_and_band(momentum: dict, price: float) -> dict:
    biases, bands = {}, {}
    if price <= 0:
        return {"bias": biases, "bands": bands}
    for tf, mm in (momentum or {}).items():
        if not mm:
            continue
        rsi = mm.get("rsi", 50.0)
        ret_3 = mm.get("ret_3", 0.0)
        if rsi < 30:
            bias = ret_3 + 0.5
        elif rsi > 70:
            bias = ret_3 - 0.5
        else:
            bias = ret_3
        atr_pct = max(mm.get("atr_pct", 0.0), 0.0)
        half = (atr_pct / 100.0) * 1.5
        biases[tf] = bias
        bands[tf] = {
            "lower": price * (1.0 - half),
            "upper": price * (1.0 + half),
            "half_width_pct": atr_pct * 1.5,
        }
    return {"bias": biases, "bands": bands}


def depth_25bps(bids, asks, mid, bps: float = 25.0) -> Tuple[float, float, float]:
    if not mid or not bids or not asks:
        return 0.0, 0.0, 0.0
    lim = bps / 10000.0
    lo, hi = mid * (1.0 - lim), mid * (1.0 + lim)
    bid_d = sum(q for p, q in bids if p >= lo)
    ask_d = sum(q for p, q in asks if p <= hi)
    tot = bid_d + ask_d
    imb = (bid_d - ask_d) / tot * 100 if tot else 0.0
    return bid_d, ask_d, imb


def quality_weights(quality: Dict[str, float]) -> Dict[str, float]:
    w = {"OB": 20.0, "TF": 20.0, "OI": 15.0, "Funding": 10.0, "Liq": 15.0, "Vol": 10.0, "Mom": 10.0}
    mapping = {
        "order_book_quality": "OB", "trade_flow_quality": "TF", "oi_quality": "OI",
        "funding_quality": "Funding", "ls_ratio_quality": "Liq",
        "volume_quality": "Vol", "momentum_quality": "Mom",
    }
    for qk, fk in mapping.items():
        q = quality.get(qk, 50) / 100.0
        w[fk] = w[fk] * (0.5 + 0.5 * q)
    tot = sum(w.values()) or 1
    return {k: v / tot * 100 for k, v in w.items()}
