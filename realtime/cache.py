"""In-memory live cache with optional Redis fan-out."""
from __future__ import annotations

import json
import logging
import time
from collections import defaultdict, deque
from typing import Any, Deque, Dict, List, Optional

from .models import StreamEvent

log = logging.getLogger("realtime.cache")


class LiveCache:
    def __init__(self, maxlen: int = 5_000, redis_url: str = "") -> None:
        self._events: Dict[str, Deque[StreamEvent]] = defaultdict(lambda: deque(maxlen=maxlen))
        self._ticker: Dict[str, StreamEvent] = {}
        self._counts: Dict[str, int] = defaultdict(int)
        self._started = time.time()
        self._redis = None
        self._redis_url = redis_url

    async def connect_redis(self) -> None:
        if not self._redis_url:
            return
        try:
            import redis.asyncio as redis  # type: ignore
            self._redis = redis.from_url(self._redis_url, decode_responses=True)
            await self._redis.ping()
            log.info("Redis baglandi: %s", self._redis_url)
        except Exception as exc:
            log.warning("Redis yok, bellek cache kullanilacak: %s", exc)
            self._redis = None

    async def close(self) -> None:
        if self._redis is not None:
            try:
                await self._redis.close()
            except Exception:
                pass

    async def push(self, event: StreamEvent) -> None:
        key = event.symbol or event.channel
        self._events[key].append(event)
        self._counts[event.kind] += 1
        if event.kind == "ticker" and event.symbol:
            self._ticker[event.symbol] = event
        if self._redis is not None:
            try:
                await self._redis.xadd(
                    f"cg:{event.kind}",
                    {"json": json.dumps(event.to_dict(), default=str)},
                    maxlen=2_000,
                    approximate=True,
                )
            except Exception as exc:
                log.debug("redis xadd: %s", exc)

    def ticker(self, symbol: str) -> Optional[StreamEvent]:
        return self._ticker.get(symbol)

    def recent(self, symbol: str, kind: Optional[str] = None, seconds: float = 30.0) -> List[StreamEvent]:
        cutoff = time.time() - seconds
        out = [e for e in self._events.get(symbol, ()) if e.timestamp >= cutoff]
        if kind:
            out = [e for e in out if e.kind == kind]
        return out

    def series(self, symbol: str, kind: str, field: str = "price", limit: int = 200) -> List[float]:
        evs = [e for e in self._events.get(symbol, ()) if e.kind == kind]
        vals = []
        for e in evs[-limit:]:
            if field == "price" and e.price:
                vals.append(e.price)
            elif field == "size_usd" and e.size_usd:
                vals.append(e.size_usd)
            elif field in e.extra:
                try:
                    vals.append(float(e.extra[field]))
                except (TypeError, ValueError):
                    continue
        return vals

    def symbols(self) -> List[str]:
        return sorted({s for s in self._ticker} | {s for s in self._events if s})

    def stats(self) -> Dict[str, Any]:
        return {
            "uptime_sec": round(time.time() - self._started, 1),
            "symbols": len(self.symbols()),
            "tickers": len(self._ticker),
            "counts": dict(self._counts),
            "buffers": {k: len(v) for k, v in self._events.items()},
        }
