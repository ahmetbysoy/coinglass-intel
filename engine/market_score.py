"""v4.3 scoring: confluence, dynamic weights, spoof, strategy, SL/TP."""
from __future__ import annotations

import statistics
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from .indicators import candle_metrics
from .scalper import build_cvd_series, detect_cvd_divergence, depth_25bps, ev_and_band, quality_weights
from .util import fmt_price, safe_path, to_float
from pipeline.exchange_feed import (
    analyze_funding,
    analyze_ls,
    analyze_oi_change,
    analyze_order_book,
    analyze_trades_binance,
)


@dataclass
class SimpleSignal:
    directional_score: float
    signal_strength: float
    current_price: float = 0.0
    narrative: str = ""


@dataclass
class V4Report:
    symbol: str
    price: float
    chg24: float
    vol24: float
    direction: str
    total_score: float
    confluence: float
    risk: int
    spoof: int
    strategy: str
    strategy_warnings: List[str]
    forecasts: Dict[str, float]
    component: Dict[str, float]
    signals: Dict[str, SimpleSignal]
    text: str
    warnings: List[str] = field(default_factory=list)
    divergence: Optional[dict] = None
    coverage: float = 0.0


def _ob_from_sources(src: dict, warnings: list) -> Dict[str, Any]:
    out = {}
    bf = safe_path(src, "binance_fut", "orderbook", warnings=warnings)
    if bf and isinstance(bf, dict) and "bids" in bf:
        out["binance"] = analyze_order_book(bf)
    by = safe_path(src, "bybit", "orderbook", warnings=warnings)
    if by:
        out["bybit"] = analyze_order_book(by)
    ok = safe_path(src, "okx", "orderbook", warnings=warnings)
    if ok:
        out["okx"] = analyze_order_book(ok)
    return {k: v for k, v in out.items() if v}


def analyze_spoof(ob_metrics: dict, ls_avg: float, agg_imbalance: float) -> int:
    spoof = 0
    bob = ob_metrics.get("binance") if ob_metrics else None
    if bob and bob.get("bids") and bob.get("asks"):
        med_b = statistics.median([b[1] for b in bob["bids"]]) or 1
        med_a = statistics.median([a[1] for a in bob["asks"]]) or 1
        mid = bob.get("mid") or 0
        for p, q in bob["bids"]:
            if mid and (mid - p) / mid * 100 > 0.5 and q > med_b * 10:
                spoof += 25
                break
        for p, q in bob["asks"]:
            if mid and (p - mid) / mid * 100 > 0.5 and q > med_a * 10:
                spoof += 25
                break
        if abs(agg_imbalance) > 30:
            spoof += 20
    if ls_avg > 2.5:
        spoof += 15
    return min(spoof, 100)


def generate_strategy(price: float, direction: str, atr_pct: float, funding: float, ls_avg: float, imb: float) -> dict:
    sl_pct = max(0.5, atr_pct * 1.5) if atr_pct else 1.0
    tp_pct = sl_pct * 2
    if "BULL" in direction:
        strategy = (
            f"LONG entry ~{fmt_price(price)}  SL {fmt_price(price * (1 - sl_pct / 100))} (-{sl_pct:.2f}%)  "
            f"TP {fmt_price(price * (1 + tp_pct / 100))} (+{tp_pct:.2f}%)"
        )
    elif "BEAR" in direction:
        strategy = (
            f"SHORT entry ~{fmt_price(price)}  SL {fmt_price(price * (1 + sl_pct / 100))} (+{sl_pct:.2f}%)  "
            f"TP {fmt_price(price * (1 - tp_pct / 100))} (-{tp_pct:.2f}%)"
        )
    else:
        strategy = f"NEUTRAL — range. Destek {fmt_price(price * 0.99)} / Direnc {fmt_price(price * 1.01)}"
    warns = []
    if funding < -0.0005:
        warns.append("Funding negatif — short squeeze potansiyeli")
    if funding > 0.0005:
        warns.append("Funding pozitif — long crowded, squeeze riski")
    if ls_avg > 2:
        warns.append(f"L/S {ls_avg:.2f} yuksek — long cascade riski")
    if imb > 30:
        warns.append(f"OB bid agirligi +{imb:.1f}%")
    if imb < -30:
        warns.append(f"OB ask agirligi {imb:.1f}%")
    return {"strategy": strategy, "warnings": warns, "sl_pct": sl_pct, "tp_pct": tp_pct}


class MarketScorer:
    def score(self, feed: Dict[str, Any], symbol: str) -> V4Report:
        warnings: List[str] = []
        src = feed.get("sources") or {}
        pair = feed.get("symbol") or symbol
        prices = []
        chg24 = vol24 = 0.0
        last_bn = safe_path(src, "binance_fut", "ticker_24h", "lastPrice", warnings=warnings)
        if last_bn:
            prices.append(("Binance", to_float(last_bn)))
            chg24 = to_float(safe_path(src, "binance_fut", "ticker_24h", "priceChangePercent"))
            vol24 = to_float(safe_path(src, "binance_fut", "ticker_24h", "quoteVolume"))
        bt = safe_path(src, "bybit", "ticker_linear", "result", "list", 0, warnings=warnings)
        if bt and bt.get("lastPrice"):
            prices.append(("Bybit", to_float(bt["lastPrice"])))
        ot = safe_path(src, "okx", "swap_ticker", "data", 0, warnings=warnings)
        if ot and ot.get("last"):
            prices.append(("OKX", to_float(ot["last"])))
        prices = [(n, p) for n, p in prices if p > 0]
        price = statistics.median(p for _, p in prices) if prices else 0.0

        oi_bn = to_float(safe_path(src, "binance_fut", "open_interest", "openInterest"))
        obs = _ob_from_sources(src, warnings)
        valid_obs = [m for m in obs.values() if m]
        agg_imb = 0.0
        if valid_obs:
            bid = ask = 0.0
            for m in valid_obs:
                if m.get("bids") and m.get("asks") and m.get("mid"):
                    b25, a25, imb25 = depth_25bps(m["bids"], m["asks"], m["mid"])
                    bid += b25
                    ask += a25
                else:
                    bid += m.get("bid_vol", 0)
                    ask += m.get("ask_vol", 0)
            if bid + ask:
                agg_imb = (bid - ask) / (bid + ask) * 100

        tf = analyze_trades_binance(safe_path(src, "binance_fut", "trades"))
        cvd_pct = tf["cvd_pct"] if tf else 0.0
        fund = analyze_funding(safe_path(src, "binance_fut", "funding"))
        funding_avg = fund["current"] if fund else 0.0
        ls = analyze_ls(safe_path(src, "binance_fut", "ls_account"), "binance")
        ls_avg = ls["current"] if ls else 1.0
        oi_chg = analyze_oi_change(safe_path(src, "binance_fut", "oi_history"))
        oi_chg_pct = oi_chg["change_pct"] if oi_chg else 0.0

        mom = {}
        bn = src.get("binance_fut") or {}
        for key, lab in (("klines_5m", "5m"), ("klines_15m", "15m"), ("klines_1h", "1h")):
            if bn.get(key):
                mom[lab] = candle_metrics(bn[key], lab, warnings)

        tf_w = {"5m": 0.25, "15m": 0.35, "1h": 0.40}
        wv = tw = 0.0
        for tf_name, w in tf_w.items():
            mm = mom.get(tf_name)
            if mm:
                vote = 1 if mm["ret"] > 0 else (-1 if mm["ret"] < 0 else 0)
                wv += w * vote
                tw += w
        confluence = (wv / tw) * 10 if tw else 0.0

        atr_pct = (mom.get("1h") or {}).get("atr_pct", 0) or 0
        quality = {
            "order_book_quality": 80.0 if obs.get("binance") or obs else 20.0,
            "trade_flow_quality": 70.0 if tf else 10.0,
            "oi_quality": 60.0 if oi_bn else 10.0,
            "funding_quality": 50.0 if fund else 10.0,
            "ls_ratio_quality": 40.0 if ls else 10.0,
            "volume_quality": 90.0 if vol24 > 1_000_000 else 30.0,
            "momentum_quality": 75.0 if mom.get("5m") else 10.0,
        }
        weights = quality_weights(quality)
        if vol24 < 10_000_000:
            weights["OB"] += 3
            weights["TF"] += 3
        if atr_pct > 4:
            weights["Vol"] += 3
            weights["Mom"] += 3
        tws = sum(weights.values()) or 1
        weights = {k: v / tws * 100 for k, v in weights.items()}

        closes_5m = []
        k5 = (src.get("binance_fut") or {}).get("klines_5m")
        if isinstance(k5, list):
            closes_5m = [to_float(r[4]) for r in sorted(k5, key=lambda x: to_float(x[0]))]
        cvd_series = build_cvd_series((src.get("binance_fut") or {}).get("taker_buysell"), price or 1.0)
        divergence = detect_cvd_divergence(closes_5m, cvd_series) if closes_5m and cvd_series else {"divergence": False, "type": None, "strength": 0.0}
        ev = ev_and_band(mom, price)

        if oi_chg_pct > 0 and chg24 > 0:
            oi_score = 60
        elif oi_chg_pct > 0 and chg24 < 0:
            oi_score = -40
        elif oi_chg_pct < 0 and chg24 > 0:
            oi_score = 30
        elif oi_chg_pct < 0 and chg24 < 0:
            oi_score = -60
        else:
            oi_score = 0

        funding_score = max(min(-funding_avg * 10000, 100), -100)
        if ls_avg > 2:
            liq_score = -40
        elif ls_avg > 1.5:
            liq_score = -20
        elif ls_avg < 0.5:
            liq_score = 40
        elif ls_avg < 0.7:
            liq_score = 20
        else:
            liq_score = 0

        vol_score = 0.0
        m5 = mom.get("5m")
        if m5 and m5.get("vol_med", 0) > 0:
            vr = m5["vol_last"] / m5["vol_med"]
            vol_score = min(vr * 15, 100) if m5["ret_3"] > 0 else -min(vr * 15, 100)
        elif m5 and mom.get("1h") and mom["1h"]["vol_total"] > 0:
            vr = m5["vol_total"] / (mom["1h"]["vol_total"] / 12)
            vol_score = min(vr * 15, 100) if m5["ret_3"] > 0 else -min(vr * 15, 100)

        mom_parts = []
        for tf_name in ("5m", "15m", "1h"):
            mm = mom.get(tf_name)
            if not mm:
                continue
            rsi_sig = -30 if mm["rsi"] > 70 else 10 if mm["rsi"] > 60 else 30 if mm["rsi"] < 30 else (-10 if mm["rsi"] < 40 else 0)
            mom_parts.append((rsi_sig + max(min(mm["ret_3"] * 10, 50), -50)) / 2)
        mom_score = (statistics.mean(mom_parts) if mom_parts else 0) + confluence * 0.5

        scores = {
            "OB": (weights["OB"], agg_imb if valid_obs else None),
            "TF": (weights["TF"], cvd_pct if tf else None),
            "OI": (weights["OI"], oi_score if oi_bn or oi_chg else None),
            "Funding": (weights["Funding"], funding_score if fund else None),
            "Liq": (weights["Liq"], liq_score if ls else None),
            "Vol": (weights["Vol"], vol_score if m5 else None),
            "Mom": (weights["Mom"], mom_score if mom else None),
        }
        available = [(w, s) for w, s in scores.values() if s is not None]
        if available:
            total = sum(w * s for w, s in available) / sum(w for w, _ in available)
            coverage = sum(w for w, _ in available)
        else:
            total = 0.0
            coverage = 0.0
        total = max(min(total, 100), -100)
        if total > 30:
            direction = "BULLISH"
        elif total > 10:
            direction = "HAFIF BULLISH"
        elif total < -30:
            direction = "BEARISH"
        elif total < -10:
            direction = "HAFIF BEARISH"
        else:
            direction = "NEUTRAL"

        risk = 0
        if atr_pct > 4:
            risk += 20
        elif atr_pct > 2:
            risk += 10
        if abs(funding_avg) > 0.01:
            risk += 10
        if ls_avg > 2 or ls_avg < 0.5:
            risk += 15
        if vol24 < 1_000_000:
            risk += 20
        elif vol24 < 10_000_000:
            risk += 10
        spoof = analyze_spoof(obs, ls_avg, agg_imb)
        strat = generate_strategy(price, direction, atr_pct, funding_avg, ls_avg, agg_imb)
        forecasts = {
            "1m": mom_score * 0.03,
            "5m": mom_score * 0.05,
            "15m": mom_score * 0.1 + vol_score * 0.05,
        }

        def sig(raw: float) -> SimpleSignal:
            clipped = max(min(raw / 100.0, 1.0), -1.0)
            return SimpleSignal(directional_score=clipped, signal_strength=min(abs(clipped) * 1.4, 1.0), current_price=price)

        signals = {
            "oi_momentum": sig(oi_score),
            "funding_signal": sig(funding_score),
            "liq_pressure": sig(liq_score),
            "ob_imbalance": sig(agg_imb),
            "volume_signal": sig(vol_score),
            "whale_flow": sig(cvd_pct),
        }

        lines = [
            f"{pair}  {fmt_price(price)}  24h {chg24:+.2f}%  vol ${vol24:,.0f}",
            f"YON: {direction}   skor {total:+.1f}/100   confluence {confluence:+.1f}",
            f"OI {oi_bn:,.0f} ({oi_chg_pct:+.2f}%)  fund {funding_avg*100:.4f}%  L/S {ls_avg:.3f}  OB imb {agg_imb:+.1f}%",
            f"CVD {cvd_pct:+.2f}%  ATR% {atr_pct:.2f}  risk {min(risk,100)}/100  spoof {spoof}/100",
        ]
        if mom.get("5m"):
            m5r = mom["5m"]
            lines.append(f"5m WilderRSI {m5r['rsi']:.1f}  StochRSI {m5r['stoch_rsi']:.1f}  MACD {m5r['histogram']:+.5f}  VWAP {fmt_price(m5r['vwap'])}")
        if divergence.get("divergence"):
            lines.append(f"CVD DIVERGENCE {str(divergence.get('type')).upper()}  str={divergence.get('strength'):.4f}")
        for tf_name, band in (ev.get("bands") or {}).items():
            bias = (ev.get("bias") or {}).get(tf_name, 0)
            lines.append(f"  {tf_name} bias {bias:+.2f}%  band {fmt_price(band['lower'])}-{fmt_price(band['upper'])}")
        lines.append(f"coverage {coverage:.0f}%")
        lines.append(strat["strategy"])
        for w in strat["warnings"]:
            lines.append(f"  ! {w}")

        return V4Report(
            symbol=pair, price=price, chg24=chg24, vol24=vol24, direction=direction,
            total_score=total, confluence=confluence, risk=min(risk, 100), spoof=spoof,
            strategy=strat["strategy"], strategy_warnings=strat["warnings"],
            forecasts=forecasts,
            component={"ob": agg_imb, "tf": cvd_pct, "oi": oi_score, "funding": funding_score,
                       "liq": liq_score, "vol": vol_score, "mom": mom_score, "confluence": confluence},
            signals=signals, text="\n".join(lines), warnings=warnings,
        )
