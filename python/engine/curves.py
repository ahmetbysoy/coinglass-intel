"""Continuous scoring curves — shared contract with Kotlin Curves.kt."""
from __future__ import annotations

import math
from typing import Dict, Tuple

HORIZONS_SEC: Dict[str, int] = {"5m": 300, "15m": 900, "1h": 3600}
COMPONENT_KEYS = ("OB", "TF", "OI", "Funding", "Liq", "Vol", "Mom")

# Risk raw caps (legacy steps). Sum = 65 → scale to 100.
RISK_ATR_MAX = 20.0
RISK_FUND_MAX = 10.0
RISK_LS_MAX = 15.0
RISK_VOL_MAX = 20.0
RISK_RAW_MAX = RISK_ATR_MAX + RISK_FUND_MAX + RISK_LS_MAX + RISK_VOL_MAX  # 65


def tanh(x: float) -> float:
    # math.tanh is fine; wrapper keeps the contract obvious
    return math.tanh(x)


def rsi_signal(rsi: float) -> float:
    """Continuous mean-reversion around 50. Saturates near ±30.

    Old steps: >70=-30, >60=+10, <30=+30, <40=-10. Those stairs are gone.
    """
    return 30.0 * tanh((50.0 - rsi) / 12.0)


def oi_score(oi_chg_pct: float, chg24: float) -> float:
    """Quadrant-aware but continuous in magnitude (tanh).

    OI↑ price↑ → +60, OI↑ price↓ → -40, OI↓ price↑ → +30, OI↓ price↓ → -60
    at large |moves|; near zero the score fades to 0.
    """
    up_oi = max(oi_chg_pct, 0.0)
    dn_oi = max(-oi_chg_pct, 0.0)
    up_px = max(chg24, 0.0)
    dn_px = max(-chg24, 0.0)
    mag_oi_up = tanh(up_oi / 3.0)
    mag_oi_dn = tanh(dn_oi / 3.0)
    mag_px_up = tanh(up_px / 2.0)
    mag_px_dn = tanh(dn_px / 2.0)
    return (
        60.0 * mag_oi_up * mag_px_up
        + (-40.0) * mag_oi_up * mag_px_dn
        + 30.0 * mag_oi_dn * mag_px_up
        + (-60.0) * mag_oi_dn * mag_px_dn
    )


def ls_score(ls_avg: float) -> float:
    """Crowded longs (ls>>1) → negative. Crowded shorts (ls<<1) → positive."""
    return -40.0 * tanh((ls_avg - 1.0) / 0.55)


def risk_raw(atr_pct: float, funding: float, ls_avg: float, vol24: float) -> float:
    raw = 0.0
    if atr_pct > 4:
        raw += RISK_ATR_MAX
    elif atr_pct > 2:
        raw += RISK_ATR_MAX / 2.0
    if abs(funding) > 0.01:
        raw += RISK_FUND_MAX
    if ls_avg > 2 or ls_avg < 0.5:
        raw += RISK_LS_MAX
    if vol24 < 1_000_000:
        raw += RISK_VOL_MAX
    elif vol24 < 10_000_000:
        raw += RISK_VOL_MAX / 2.0
    return raw


def risk_score(atr_pct: float, funding: float, ls_avg: float, vol24: float) -> int:
    """Normalize the 0–65 raw risk onto 0–100."""
    raw = risk_raw(atr_pct, funding, ls_avg, vol24)
    return int(round(min(raw, RISK_RAW_MAX) / RISK_RAW_MAX * 100.0))


def sl_tp(price: float, direction: str, atr_pct: float, total_score: float) -> Tuple[float, float, float, float]:
    """SL/TP from |total_score|. Low confidence → tighter SL, not a wide TP.

    Returns (sl, tp, sl_pct, tp_pct).
    """
    if price <= 0:
        return 0.0, 0.0, 0.0, 0.0
    conf = min(abs(total_score) / 100.0, 1.0)
    atr = max(atr_pct, 0.0)
    # low conf: ~0.6·ATR (min 0.35%), high conf: ~1.5·ATR
    sl_pct = max(0.35, atr * (0.6 + 0.9 * conf)) if atr > 0 else max(0.35, 1.0 * (0.6 + 0.4 * conf))
    rr = 1.0 + 1.5 * conf  # 1.0R … 2.5R
    tp_pct = sl_pct * rr
    if "BULL" in direction:
        return price * (1 - sl_pct / 100), price * (1 + tp_pct / 100), sl_pct, tp_pct
    if "BEAR" in direction:
        return price * (1 + sl_pct / 100), price * (1 - tp_pct / 100), sl_pct, tp_pct
    return price * (1 - sl_pct / 100), price * (1 + sl_pct / 100), sl_pct, sl_pct


def direction_of(total: float) -> str:
    if total > 30:
        return "BULLISH"
    if total > 10:
        return "HAFIF BULLISH"
    if total < -30:
        return "BEARISH"
    if total < -10:
        return "HAFIF BEARISH"
    return "NEUTRAL"


def forecast_side(direction: str) -> int:
    if "BULL" in direction:
        return 1
    if "BEAR" in direction:
        return -1
    return 0
