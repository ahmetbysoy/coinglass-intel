"""Official REST snapshots when an API key is present. Lightweight, no browser."""
from __future__ import annotations

import logging
import time
from typing import List, Optional

import aiohttp
import orjson

from config import CFG
from .cache import LiveCache
from .models import StreamEvent, base_asset, normalize_symbol

log = logging.getLogger("realtime.rest")


class RestPump:
    def __init__(self, cache: LiveCache, symbols: List[str], api_key: str = "") -> None:
        self.cache = cache
        self.symbols = symbols
        self.api_key = api_key
        self._task = None
        self.ok = 0
        self.fail = 0

    def start(self, interval: float = 15.0) -> None:
        import asyncio
        if self._task or not self.api_key:
            if not self.api_key:
                log.info("REST pump kapali — API key yok")
            return
        self._task = asyncio.create_task(self._loop(interval), name="cg-rest")

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except Exception:
                pass
            self._task = None

    async def _loop(self, interval: float) -> None:
        import asyncio
        while True:
            try:
                await self.tick()
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.fail += 1
                log.debug("REST tick: %s", exc)
            await asyncio.sleep(interval)

    async def tick(self) -> None:
        headers = {"CG-API-KEY": self.api_key, "Accept": "application/json"}
        timeout = aiohttp.ClientTimeout(total=12)
        async with aiohttp.ClientSession(headers=headers, timeout=timeout) as session:
            for raw in self.symbols:
                base = base_asset(raw)
                pair = normalize_symbol(raw)
                await self._oi(session, base, pair)
                await self._funding(session, pair)

    async def _get(self, session: aiohttp.ClientSession, path: str, params: dict) -> Optional[dict]:
        url = f"{CFG.websocket.rest_base}{path}"
        try:
            async with session.get(url, params=params) as resp:
                body = await resp.read()
                if resp.status != 200:
                    self.fail += 1
                    return None
                data = orjson.loads(body)
                self.ok += 1
                return data
        except Exception as exc:
            self.fail += 1
            log.debug("GET %s: %s", path, exc)
            return None

    async def _oi(self, session: aiohttp.ClientSession, base: str, pair: str) -> None:
        data = await self._get(session, "/api/futures/open-interest/exchange-list", {"symbol": base})
        rows = (data or {}).get("data") or []
        if not isinstance(rows, list):
            return
        total = 0.0
        price = 0.0
        for row in rows:
            if not isinstance(row, dict):
                continue
            total += float(row.get("open_interest_usd") or row.get("oi_usd") or 0)
            price = price or float(row.get("price") or 0)
        if total <= 0:
            return
        await self.cache.push(
            StreamEvent(
                channel="rest.oi",
                kind="oi",
                symbol=pair,
                exchange="AGG",
                timestamp=time.time(),
                price=price,
                size_usd=total,
                extra={"oi": total},
            )
        )

    async def _funding(self, session: aiohttp.ClientSession, pair: str) -> None:
        data = await self._get(
            session,
            "/api/futures/funding-rate/exchange-list",
            {"symbol": base_asset(pair)},
        )
        rows = (data or {}).get("data") or []
        if not isinstance(rows, list) or not rows:
            return
        rates = []
        for row in rows:
            if isinstance(row, dict):
                try:
                    rates.append(float(row.get("funding_rate") or row.get("rate") or 0))
                except (TypeError, ValueError):
                    continue
        if not rates:
            return
        avg = sum(rates) / len(rates)
        await self.cache.push(
            StreamEvent(
                channel="rest.funding",
                kind="funding",
                symbol=pair,
                exchange="AGG",
                timestamp=time.time(),
                extra={"funding": avg},
            )
        )
