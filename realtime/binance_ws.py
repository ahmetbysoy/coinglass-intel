"""Binance USD-M Futures WebSocket — 2026 /public + /market split.

Docs:
  https://developers.binance.com/docs/derivatives/usds-margined-futures/websocket-market-streams/Important-WebSocket-Change-Notice

2026-03-05: /public /market /private eklendi.
2026-04-23: legacy wss://fstream.binance.com/ws ve /stream KAPANDI.
            Unrouted baglanti SADECE /public stream basar.

Canli test (proje notu):
  @trade  /market altinda SUS, /public altinda akar.
  depth20 /public.
  kline / markPrice / forceOrder  /market.

Resmi excerpt @trade'i hic listelemez (USD-M resmi olarak @aggTrade /market).
@trade icin kaynak: canli gozlem + bu router.
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Awaitable, Callable, Dict, Iterable, List, Optional, Tuple
from urllib.parse import quote

import orjson
import websockets

from .models import StreamEvent, normalize_symbol

log = logging.getLogger("realtime.binance_ws")

PUBLIC_BASE = "wss://fstream.binance.com/public"
MARKET_BASE = "wss://fstream.binance.com/market"
PRIVATE_BASE = "wss://fstream.binance.com/private"

OnEvent = Callable[[StreamEvent], Awaitable[None]]


def _kind(stream: str) -> str:
    s = stream.lower()
    if "@trade" in s and "@aggtrade" not in s:
        return "trade"
    if "@aggtrade" in s:
        return "aggTrade"
    if "@depth" in s:
        return "depth"
    if "@bookticker" in s:
        return "bookTicker"
    if "@kline" in s:
        return "kline"
    if "@markprice" in s:
        return "markPrice"
    if "@forceorder" in s:
        return "forceOrder"
    if "@miniticker" in s:
        return "miniTicker"
    if "@ticker" in s:
        return "ticker"
    return "other"


def route_lane(stream: str) -> str:
    """Return 'public' or 'market' for a stream name like allousdt@trade."""
    k = _kind(stream)
    if k in ("trade", "depth", "bookTicker"):
        return "public"
    return "market"


def default_streams(symbol: str) -> Dict[str, List[str]]:
    s = normalize_symbol(symbol).lower()
    return {
        "public": [
            f"{s}@trade",
            f"{s}@depth20@100ms",
        ],
        "market": [
            f"{s}@kline_1m",
            f"{s}@kline_5m",
            f"{s}@markPrice@1s",
            f"{s}@forceOrder",
        ],
    }


def combined_url(base: str, streams: Iterable[str]) -> str:
    joined = "/".join(streams)
    return f"{base}/stream?streams={quote(joined, safe='@/_')}"


def _parse_payload(stream: str, data: dict) -> Optional[StreamEvent]:
    kind = _kind(stream)
    symbol = normalize_symbol(str(data.get("s") or stream.split("@", 1)[0]))
    ts_ms = data.get("E") or data.get("T") or 0
    try:
        ts = float(ts_ms) / 1000.0 if float(ts_ms) > 1e12 else time.time()
    except (TypeError, ValueError):
        ts = time.time()

    price = 0.0
    size = 0.0
    side = ""
    extra: dict = {"stream": stream, "lane": route_lane(stream)}

    if kind in ("trade", "aggTrade"):
        price = float(data.get("p") or 0)
        size = float(data.get("q") or 0)
        # m=true => buyer is maker => this trade is a sell (taker sell)
        side = "sell" if data.get("m") else "buy"
        extra["trade_id"] = data.get("t") or data.get("a")
    elif kind == "depth":
        bids = data.get("b") or data.get("bids") or []
        asks = data.get("a") or data.get("asks") or []
        extra["bids"] = bids[:20]
        extra["asks"] = asks[:20]
        if bids and asks:
            try:
                bb, ba = float(bids[0][0]), float(asks[0][0])
                price = (bb + ba) / 2
            except (TypeError, ValueError, IndexError):
                pass
    elif kind == "kline":
        k = data.get("k") or {}
        price = float(k.get("c") or 0)
        extra.update({
            "o": k.get("o"), "h": k.get("h"), "l": k.get("l"), "c": k.get("c"),
            "v": k.get("v"), "i": k.get("i"), "closed": bool(k.get("x")),
        })
    elif kind == "markPrice":
        price = float(data.get("p") or 0)
        extra["funding"] = float(data.get("r") or 0)
        extra["index"] = float(data.get("i") or 0)
    elif kind == "forceOrder":
        o = data.get("o") or {}
        symbol = normalize_symbol(str(o.get("s") or symbol))
        price = float(o.get("ap") or o.get("p") or 0)
        size = float(o.get("q") or 0)
        side = str(o.get("S") or "").lower()
        extra["status"] = o.get("X")
    elif kind in ("ticker", "miniTicker", "bookTicker"):
        price = float(data.get("c") or data.get("b") or data.get("a") or 0)

    return StreamEvent(
        channel=stream,
        kind=kind,
        symbol=symbol,
        exchange="Binance",
        timestamp=ts,
        price=price,
        size_usd=size * price if size and price else size,
        side=side,
        extra=extra,
    )


class _Lane:
    def __init__(self, name: str, url: str, on_event: OnEvent) -> None:
        self.name = name
        self.url = url
        self.on_event = on_event
        self.frames = 0
        self.last_error = ""
        self.connected = False
        self._task: Optional[asyncio.Task] = None
        self._running = False

    def start(self) -> None:
        self._running = True
        self._task = asyncio.create_task(self._loop(), name=f"bn-{self.name}")

    async def stop(self) -> None:
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except (asyncio.CancelledError, Exception):
                pass
            self._task = None

    async def _loop(self) -> None:
        attempt = 0
        while self._running:
            try:
                log.info("Binance %s baglaniyor: %s", self.name, self.url)
                async with websockets.connect(
                    self.url,
                    ping_interval=20,
                    ping_timeout=20,
                    close_timeout=5,
                    max_size=8 * 1024 * 1024,
                ) as ws:
                    self.connected = True
                    attempt = 0
                    async for raw in ws:
                        if not self._running:
                            break
                        await self._handle(raw)
            except asyncio.CancelledError:
                break
            except Exception as exc:
                self.last_error = str(exc)
                log.warning("Binance %s kopuk: %s", self.name, exc)
            finally:
                self.connected = False
            if not self._running:
                break
            attempt += 1
            await asyncio.sleep(min(2 * attempt, 20))

    async def _handle(self, raw) -> None:
        try:
            msg = orjson.loads(raw)
        except Exception:
            return
        if not isinstance(msg, dict):
            return
        stream = str(msg.get("stream") or "")
        data = msg.get("data") if "data" in msg else msg
        if not isinstance(data, dict):
            return
        ev = _parse_payload(stream or str(data.get("e") or ""), data)
        if not ev:
            return
        self.frames += 1
        if self.frames <= 5:
            log.info("BN %s #%s %s px=%s", self.name, self.frames, ev.kind, ev.price)
        try:
            await self.on_event(ev)
        except Exception as exc:
            log.debug("on_event: %s", exc)


class BinanceFuturesWS:
    """Iki baglanti: public (trade+depth) ve market (kline+mark+liq)."""

    def __init__(self, symbol: str, on_event: OnEvent, streams: Optional[Dict[str, List[str]]] = None) -> None:
        plan = streams or default_streams(symbol)
        self.public = _Lane("public", combined_url(PUBLIC_BASE, plan["public"]), on_event)
        self.market = _Lane("market", combined_url(MARKET_BASE, plan["market"]), on_event)
        self.plan = plan

    def start(self) -> None:
        self.public.start()
        self.market.start()

    async def stop(self) -> None:
        await asyncio.gather(self.public.stop(), self.market.stop())

    def stats(self) -> dict:
        return {
            "public": {"connected": self.public.connected, "frames": self.public.frames, "err": self.public.last_error, "url": self.public.url},
            "market": {"connected": self.market.connected, "frames": self.market.frames, "err": self.market.last_error, "url": self.market.url},
            "plan": self.plan,
        }


async def _demo(symbol: str, seconds: float = 20.0) -> None:
    counts: Dict[str, int] = {}

    async def on_event(ev: StreamEvent) -> None:
        counts[ev.kind] = counts.get(ev.kind, 0) + 1

    ws = BinanceFuturesWS(symbol, on_event)
    ws.start()
    await asyncio.sleep(seconds)
    await ws.stop()
    print(orjson.dumps(ws.stats(), option=orjson.OPT_INDENT_2).decode())
    print("counts", counts)


if __name__ == "__main__":
    import sys
    logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(name)s | %(message)s", datefmt="%H:%M:%S")
    sym = sys.argv[1] if len(sys.argv) > 1 else "BTCUSDT"
    asyncio.run(_demo(sym, 15))
