"""Score history + real-price outcome matching (t+5m / 15m / 1h)."""
from __future__ import annotations

import csv
import statistics
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Tuple

from .curves import COMPONENT_KEYS, HORIZONS_SEC, forecast_side

HISTORY_HEADERS = [
    "ts", "symbol", "price", "score", "direction",
    "ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence",
]
OUTCOME_HEADERS = [
    "ts", "symbol", "price", "score", "direction",
    "horizon", "horizon_sec", "future_ts", "future_price",
    "fwd_return_pct", "win", "settled",
    "ob", "tf", "oi", "funding", "liq", "vol", "mom",
]

PriceFn = Callable[[str, datetime], Optional[float]]


def _parse_ts(raw: str) -> datetime:
    try:
        dt = datetime.fromisoformat(raw)
    except ValueError:
        dt = datetime.fromtimestamp(float(raw), tz=timezone.utc)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def _to_float(v, default: float = 0.0) -> float:
    if v is None or v == "":
        return default
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


@dataclass
class SignalRow:
    ts: datetime
    symbol: str
    price: float
    score: float
    direction: str
    components: Dict[str, float]


@dataclass
class OutcomeRow:
    ts: datetime
    symbol: str
    price: float
    score: float
    direction: str
    horizon: str
    horizon_sec: int
    future_ts: datetime
    future_price: Optional[float]
    fwd_return_pct: Optional[float]
    win: Optional[bool]
    settled: bool
    components: Dict[str, float]


@dataclass
class ComponentStats:
    key: str
    n: int
    win_rate: float
    avg_fwd_return: float
    avg_when_bet: float


class HistoryStore:
    def __init__(self, data_dir: Optional[Path] = None) -> None:
        root = Path(data_dir or (Path(__file__).resolve().parents[1] / "data"))
        root.mkdir(parents=True, exist_ok=True)
        self.history_path = root / "score_history.csv"
        self.outcomes_path = root / "score_outcomes.csv"
        self._ensure(self.history_path, HISTORY_HEADERS)
        self._ensure(self.outcomes_path, OUTCOME_HEADERS)

    @staticmethod
    def _ensure(path: Path, headers: List[str]) -> None:
        if not path.exists() or path.stat().st_size == 0:
            with path.open("w", newline="", encoding="utf-8") as fh:
                csv.writer(fh).writerow(headers)

    def save(
        self,
        symbol: str,
        price: float,
        score: float,
        direction: str,
        components: Dict[str, float],
        ts: Optional[datetime] = None,
    ) -> SignalRow:
        ts = ts or datetime.now(timezone.utc)
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)
        comps = {k: float(components.get(k.lower(), components.get(k, 0.0)) or 0.0) for k in
                 ("ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence")}
        row = [
            ts.isoformat(), symbol, price, score, direction,
            comps["ob"], comps["tf"], comps["oi"], comps["funding"],
            comps["liq"], comps["vol"], comps["mom"], comps["confluence"],
        ]
        with self.history_path.open("a", newline="", encoding="utf-8") as fh:
            csv.writer(fh).writerow(row)
        return SignalRow(ts, symbol, float(price), float(score), direction, comps)

    def load_signals(self, symbol: Optional[str] = None, days: int = 365) -> List[SignalRow]:
        if not self.history_path.exists():
            return []
        cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        out: List[SignalRow] = []
        with self.history_path.open(encoding="utf-8") as fh:
            for raw in csv.DictReader(fh):
                try:
                    ts = _parse_ts(raw["ts"])
                except Exception:
                    continue
                if ts < cutoff:
                    continue
                if symbol and raw.get("symbol") != symbol:
                    continue
                comps = {k: _to_float(raw.get(k)) for k in
                         ("ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence")}
                out.append(SignalRow(
                    ts=ts, symbol=raw.get("symbol") or "",
                    price=_to_float(raw.get("price")), score=_to_float(raw.get("score")),
                    direction=raw.get("direction") or "NEUTRAL", components=comps,
                ))
        return out

    def load_outcomes(self) -> List[OutcomeRow]:
        if not self.outcomes_path.exists():
            return []
        out: List[OutcomeRow] = []
        with self.outcomes_path.open(encoding="utf-8") as fh:
            for raw in csv.DictReader(fh):
                try:
                    ts = _parse_ts(raw["ts"])
                    fts = _parse_ts(raw["future_ts"]) if raw.get("future_ts") else ts
                except Exception:
                    continue
                fp = raw.get("future_price")
                fr = raw.get("fwd_return_pct")
                win_raw = (raw.get("win") or "").strip().lower()
                win: Optional[bool]
                if win_raw in ("1", "true", "yes"):
                    win = True
                elif win_raw in ("0", "false", "no"):
                    win = False
                else:
                    win = None
                comps = {k: _to_float(raw.get(k)) for k in
                         ("ob", "tf", "oi", "funding", "liq", "vol", "mom")}
                out.append(OutcomeRow(
                    ts=ts, symbol=raw.get("symbol") or "",
                    price=_to_float(raw.get("price")), score=_to_float(raw.get("score")),
                    direction=raw.get("direction") or "NEUTRAL",
                    horizon=raw.get("horizon") or "",
                    horizon_sec=int(_to_float(raw.get("horizon_sec"))),
                    future_ts=fts,
                    future_price=_to_float(fp) if fp not in (None, "") else None,
                    fwd_return_pct=_to_float(fr) if fr not in (None, "") else None,
                    win=win,
                    settled=(raw.get("settled") or "").strip().lower() in ("1", "true", "yes"),
                    components=comps,
                ))
        return out

    def settle(self, get_price: PriceFn, now: Optional[datetime] = None) -> List[OutcomeRow]:
        """For every history signal, fill t+5m/15m/1h future price + win flag."""
        now = now or datetime.now(timezone.utc)
        if now.tzinfo is None:
            now = now.replace(tzinfo=timezone.utc)
        existing = {(o.ts.isoformat(), o.symbol, o.horizon): o for o in self.load_outcomes()}
        written: List[OutcomeRow] = []
        for sig in self.load_signals():
            for horizon, sec in HORIZONS_SEC.items():
                key = (sig.ts.isoformat(), sig.symbol, horizon)
                prev = existing.get(key)
                if prev and prev.settled:
                    written.append(prev)
                    continue
                future_ts = sig.ts + timedelta(seconds=sec)
                future_price: Optional[float] = None
                fwd: Optional[float] = None
                win: Optional[bool] = None
                settled = False
                if now >= future_ts:
                    future_price = get_price(sig.symbol, future_ts)
                    if future_price is not None and sig.price > 0:
                        fwd = (future_price - sig.price) / sig.price * 100.0
                        side = forecast_side(sig.direction)
                        if side == 0:
                            win = abs(fwd) < 0.15
                        else:
                            win = (fwd * side) > 0
                        settled = True
                row = OutcomeRow(
                    ts=sig.ts, symbol=sig.symbol, price=sig.price, score=sig.score,
                    direction=sig.direction, horizon=horizon, horizon_sec=sec,
                    future_ts=future_ts, future_price=future_price,
                    fwd_return_pct=fwd, win=win, settled=settled,
                    components={k: sig.components.get(k.lower(), 0.0) for k in
                                ("ob", "tf", "oi", "funding", "liq", "vol", "mom")},
                )
                written.append(row)
        self._rewrite_outcomes(written)
        return written

    def _rewrite_outcomes(self, rows: Iterable[OutcomeRow]) -> None:
        with self.outcomes_path.open("w", newline="", encoding="utf-8") as fh:
            w = csv.writer(fh)
            w.writerow(OUTCOME_HEADERS)
            for r in rows:
                w.writerow([
                    r.ts.isoformat(), r.symbol, r.price, r.score, r.direction,
                    r.horizon, r.horizon_sec, r.future_ts.isoformat(),
                    "" if r.future_price is None else r.future_price,
                    "" if r.fwd_return_pct is None else r.fwd_return_pct,
                    "" if r.win is None else int(r.win),
                    int(r.settled),
                    r.components.get("ob", 0), r.components.get("tf", 0),
                    r.components.get("oi", 0), r.components.get("funding", 0),
                    r.components.get("liq", 0), r.components.get("vol", 0),
                    r.components.get("mom", 0),
                ])

    def component_performance(
        self,
        horizon: str = "15m",
        min_abs: float = 1.0,
    ) -> Dict[str, ComponentStats]:
        """Win-rate + mean forward-return per component sign vs realized move."""
        settled = [o for o in self.load_outcomes() if o.settled and o.horizon == horizon
                   and o.fwd_return_pct is not None]
        out: Dict[str, ComponentStats] = {}
        mapping = {
            "OB": "ob", "TF": "tf", "OI": "oi", "Funding": "funding",
            "Liq": "liq", "Vol": "vol", "Mom": "mom",
        }
        for label, key in mapping.items():
            wins = 0
            n = 0
            rets: List[float] = []
            aligned: List[float] = []
            for o in settled:
                val = o.components.get(key, 0.0)
                if abs(val) < min_abs:
                    continue
                n += 1
                fwd = o.fwd_return_pct or 0.0
                rets.append(fwd)
                side = 1 if val > 0 else -1
                aligned.append(fwd * side)
                if fwd * side > 0:
                    wins += 1
            out[label] = ComponentStats(
                key=label,
                n=n,
                win_rate=(wins / n) if n else 0.0,
                avg_fwd_return=(statistics.mean(rets) if rets else 0.0),
                avg_when_bet=(statistics.mean(aligned) if aligned else 0.0),
            )
        return out

    def format_performance(self, horizon: str = "15m") -> str:
        stats = self.component_performance(horizon=horizon)
        lines = [f"component win-rate @ {horizon}"]
        for k in COMPONENT_KEYS:
            s = stats[k]
            lines.append(
                f"  {k:8s}  n={s.n:3d}  wr={s.win_rate*100:5.1f}%  "
                f"avg_fwd={s.avg_fwd_return:+.3f}%  aligned={s.avg_when_bet:+.3f}%"
            )
        return "\n".join(lines)
