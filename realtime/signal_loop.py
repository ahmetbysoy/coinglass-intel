"""Scan the live cache every few seconds and emit anomalies."""
from __future__ import annotations

import asyncio
import logging
import time
from typing import List

from config import CFG
from .cache import LiveCache
from .models import Anomaly, StreamEvent, normalize_symbol
from .telegram_push import TelegramPush

log = logging.getLogger("realtime.signal")


class SignalLoop:
    def __init__(self, cache: LiveCache, telegram: TelegramPush, symbols: List[str]) -> None:
        self.cache = cache
        self.telegram = telegram
        self.symbols = [normalize_symbol(s) for s in symbols]
        self._task = None
        self.emitted = 0

    def start(self) -> None:
        import asyncio
        if self._task:
            return
        self._task = asyncio.create_task(self._run(), name="cg-signals")

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except (asyncio.CancelledError, Exception):
                pass
            self._task = None

    async def _run(self) -> None:
        import asyncio
        interval = CFG.daemon.signal_interval_sec
        while True:
            try:
                for anomaly in self.scan():
                    self.emitted += 1
                    await self.telegram.send(anomaly)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                log.exception("signal scan: %s", exc)
            await asyncio.sleep(interval)

    def scan(self) -> List[Anomaly]:
        found: List[Anomaly] = []
        window = max(CFG.daemon.signal_interval_sec * 3, 15.0)
        for symbol in self.symbols:
            found.extend(self._liq(symbol, window))
            found.extend(self._whale(symbol, window))
            found.extend(self._ticker(symbol, window))
            found.extend(self._funding(symbol, window))
            found.extend(self._oi(symbol, window))
        return found

    def _liq(self, symbol: str, window: float) -> List[Anomaly]:
        evs = self.cache.recent(symbol, "liquidation", window)
        if not evs:
            return []
        total = sum(e.size_usd for e in evs)
        longs = sum(e.size_usd for e in evs if e.side == "long")
        shorts = sum(e.size_usd for e in evs if e.side == "short")
        biggest = max(evs, key=lambda e: e.size_usd)
        out = []
        if biggest.size_usd >= CFG.daemon.liq_alert_usd:
            out.append(
                Anomaly(
                    kind="liquidation",
                    symbol=symbol,
                    severity="high" if biggest.size_usd >= CFG.daemon.liq_alert_usd * 4 else "medium",
                    score=min(biggest.size_usd / (CFG.daemon.liq_alert_usd * 4), 1.0),
                    title=f"Likidasyon {biggest.side.upper()} ${biggest.size_usd:,.0f}",
                    body=(
                        f"{biggest.exchange} {symbol} {biggest.side} liq "
                        f"${biggest.size_usd:,.0f} @ ${biggest.price:,.2f}\n"
                        f"Son {window:.0f}s toplam ${total:,.0f}  L ${longs:,.0f} / S ${shorts:,.0f}"
                    ),
                    payload={"usd": biggest.size_usd, "side": biggest.side},
                )
            )
        if total >= CFG.daemon.liq_alert_usd * 3 and len(evs) >= 4:
            side = "long" if longs > shorts else "short"
            out.append(
                Anomaly(
                    kind="liq_cluster",
                    symbol=symbol,
                    severity="high",
                    score=min(total / (CFG.daemon.liq_alert_usd * 8), 1.0),
                    title=f"Likidasyon kumes {side} ${total:,.0f}",
                    body=f"{len(evs)} emir / {window:.0f}s  dominant={side}",
                    payload={"usd": total, "count": len(evs)},
                )
            )
        return out

    def _whale(self, symbol: str, window: float) -> List[Anomaly]:
        evs = [e for e in self.cache.recent(symbol, "trade", window) if e.size_usd >= CFG.daemon.whale_alert_usd]
        out = []
        for e in evs[-3:]:
            out.append(
                Anomaly(
                    kind="whale",
                    symbol=symbol,
                    severity="high" if e.size_usd >= CFG.daemon.whale_alert_usd * 3 else "medium",
                    score=min(e.size_usd / (CFG.daemon.whale_alert_usd * 5), 1.0),
                    title=f"Balina {e.side.upper()} ${e.size_usd:,.0f}",
                    body=f"{e.exchange} {symbol} {e.side} ${e.size_usd:,.0f} @ ${e.price:,.2f}",
                    payload={"usd": e.size_usd, "side": e.side},
                )
            )
        return out

    def _ticker(self, symbol: str, window: float) -> List[Anomaly]:
        prices = self.cache.series(symbol, "ticker", "price", limit=40)
        if len(prices) < 4:
            return []
        last, first = prices[-1], prices[0]
        if first <= 0:
            return []
        pct = (last - first) / first * 100.0
        if abs(pct) < CFG.daemon.price_move_pct:
            return []
        direction = "UP" if pct > 0 else "DOWN"
        return [
            Anomaly(
                kind="price_move",
                symbol=symbol,
                severity="medium" if abs(pct) < CFG.daemon.price_move_pct * 3 else "high",
                score=min(abs(pct) / 2.0, 1.0),
                title=f"Fiyat {direction} {pct:+.3f}%",
                body=f"{symbol} ${first:,.2f} -> ${last:,.2f}  ({window:.0f}s pencere)",
                payload={"pct": pct, "price": last},
            )
        ]

    def _funding(self, symbol: str, window: float) -> List[Anomaly]:
        evs = self.cache.recent(symbol, "funding", window * 4) or [
            e for e in self.cache.recent(symbol, "ticker", window) if e.extra.get("funding")
        ]
        if not evs:
            return []
        last = evs[-1]
        rate = float(last.extra.get("funding") or 0)
        if abs(rate) < CFG.daemon.funding_extreme:
            return []
        return [
            Anomaly(
                kind="funding",
                symbol=symbol,
                severity="medium",
                score=min(abs(rate) / 0.002, 1.0),
                title=f"Funding ekstrem {rate*100:+.4f}%",
                body=f"{symbol} funding {rate:.6f}  ({'long crowded' if rate>0 else 'short crowded'})",
                payload={"rate": rate},
            )
        ]

    def _oi(self, symbol: str, window: float) -> List[Anomaly]:
        vals = self.cache.series(symbol, "oi", "size_usd", limit=20)
        if not vals:
            tick = self.cache.ticker(symbol)
            if tick and tick.extra.get("oi"):
                vals = [float(tick.extra["oi"])]
        if len(vals) < 2 or vals[0] <= 0:
            return []
        pct = (vals[-1] - vals[0]) / vals[0] * 100.0
        if abs(pct) < CFG.daemon.oi_jump_pct:
            return []
        return [
            Anomaly(
                kind="oi_jump",
                symbol=symbol,
                severity="medium",
                score=min(abs(pct) / 8.0, 1.0),
                title=f"OI {pct:+.2f}%",
                body=f"{symbol} OI ${vals[0]:,.0f} -> ${vals[-1]:,.0f}",
                payload={"pct": pct, "oi": vals[-1]},
            )
        ]
