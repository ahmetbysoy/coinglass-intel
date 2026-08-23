"""v4.3 component scoring using continuous curves."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Optional

from .curves import (
    direction_of,
    ls_score,
    oi_score,
    risk_score,
    rsi_signal,
    sl_tp,
)


@dataclass
class ComponentBundle:
    ob: Optional[float]
    tf: Optional[float]
    oi: Optional[float]
    funding: Optional[float]
    liq: Optional[float]
    vol: Optional[float]
    mom: Optional[float]
    confluence: float
    total: float
    coverage: float
    direction: str
    risk: int
    sl: float
    tp: float
    sl_pct: float
    tp_pct: float


def mom_from_rsi_and_ret(rsi: float, ret_3: float) -> float:
    """Continuous RSI + clipped 3-bar return. No 70/60/40/30 stairs."""
    ret_part = max(min(ret_3 * 10.0, 50.0), -50.0)
    return (rsi_signal(rsi) + ret_part) / 2.0


def score_components(
    *,
    price: float,
    chg24: float = 0.0,
    oi_chg_pct: Optional[float] = None,
    ls_avg: Optional[float] = None,
    funding: Optional[float] = None,
    agg_imb: Optional[float] = None,
    cvd_pct: Optional[float] = None,
    vol_score: Optional[float] = None,
    rsi: Optional[float] = None,
    ret_3: float = 0.0,
    confluence: float = 0.0,
    atr_pct: float = 0.0,
    vol24: float = 0.0,
    weights: Optional[Dict[str, float]] = None,
) -> ComponentBundle:
    w = dict(weights or {
        "OB": 20.0, "TF": 20.0, "OI": 15.0, "Funding": 10.0,
        "Liq": 15.0, "Vol": 10.0, "Mom": 10.0,
    })
    oi = None if oi_chg_pct is None else oi_score(oi_chg_pct, chg24)
    liq = None if ls_avg is None else ls_score(ls_avg)
    fund = None if funding is None else max(min(-funding * 10_000.0, 100.0), -100.0)
    mom = None
    if rsi is not None:
        mom = mom_from_rsi_and_ret(rsi, ret_3) + confluence * 0.5
    scores = {
        "OB": (w["OB"], agg_imb),
        "TF": (w["TF"], cvd_pct),
        "OI": (w["OI"], oi),
        "Funding": (w["Funding"], fund),
        "Liq": (w["Liq"], liq),
        "Vol": (w["Vol"], vol_score),
        "Mom": (w["Mom"], mom),
    }
    available = [(wt, s) for wt, s in scores.values() if s is not None]
    if available:
        total = sum(wt * s for wt, s in available) / sum(wt for wt, _ in available)
        coverage = sum(wt for wt, _ in available)
    else:
        total, coverage = 0.0, 0.0
    total = max(min(total, 100.0), -100.0)
    direction = direction_of(total)
    risk = risk_score(atr_pct, funding or 0.0, ls_avg if ls_avg is not None else 1.0, vol24)
    sl, tp, slp, tpp = sl_tp(price, direction, atr_pct, total)
    return ComponentBundle(
        ob=agg_imb, tf=cvd_pct, oi=oi, funding=fund, liq=liq,
        vol=vol_score, mom=mom, confluence=confluence,
        total=total, coverage=coverage, direction=direction, risk=risk,
        sl=sl, tp=tp, sl_pct=slp, tp_pct=tpp,
    )
