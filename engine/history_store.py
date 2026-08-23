"""Score history + validate (skor vs sonraki fiyat)."""
from __future__ import annotations

import csv
import statistics
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional

from config import DATA_DIR
from .util import to_float

HISTORY_FILE = DATA_DIR / "score_history.csv"
HEADERS = ["ts", "symbol", "price", "score", "direction", "ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence"]


class HistoryStore:
    def __init__(self, path: Optional[Path] = None) -> None:
        self.path = Path(path or HISTORY_FILE)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            with self.path.open("w", newline="", encoding="utf-8") as fh:
                csv.writer(fh).writerow(HEADERS)

    def save(self, symbol: str, price: float, score: float, direction: str, components: Dict[str, float]) -> None:
        row = [datetime.now().isoformat(), symbol, price, score, direction]
        row.extend(components.get(k, 0) for k in ("ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence"))
        with self.path.open("a", newline="", encoding="utf-8") as fh:
            csv.writer(fh).writerow(row)

    def load(self, symbol: Optional[str] = None, days: int = 30) -> List[dict]:
        if not self.path.exists():
            return []
        cutoff = datetime.now() - timedelta(days=days)
        out = []
        with self.path.open(encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                try:
                    ts = datetime.fromisoformat(row["ts"])
                except Exception:
                    continue
                if ts < cutoff:
                    continue
                if symbol and row.get("symbol") != symbol:
                    continue
                out.append({
                    "ts": ts, "symbol": row.get("symbol"),
                    "price": to_float(row.get("price")), "score": to_float(row.get("score")),
                    "direction": row.get("direction"),
                    "components": {k: to_float(row.get(k)) for k in ("ob", "tf", "oi", "funding", "liq", "vol", "mom", "confluence")},
                })
        return out

    def validate(self, symbol: str) -> str:
        data = self.load(symbol, days=30)
        if len(data) < 2:
            return f"{symbol}: history yetersiz (en az 2 run)"
        scores, changes = [], []
        for i in range(len(data) - 1):
            scores.append(data[i]["score"])
            a, b = data[i]["price"], data[i + 1]["price"]
            changes.append((b - a) / a * 100 if a else 0)
        try:
            corr = statistics.correlation(scores, changes) if len(scores) > 1 else 0
        except Exception:
            corr = 0
        tag = "guclu" if abs(corr) > 0.5 else "orta" if abs(corr) > 0.3 else "zayif"
        return f"{symbol}: skor-fiyat korelasyon {corr:+.3f} ({tag}, n={len(scores)})"
