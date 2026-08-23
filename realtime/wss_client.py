"""Lightweight CoinGlass WSS client. Browser kapaninca bu yasamaya devam eder."""
from __future__ import annotations

import asyncio
import gzip
import logging
import time
from typing import Awaitable, Callable, List, Optional, Union

import orjson
import websockets

from config import CFG
from .models import SessionBundle, StreamEvent, base_asset, normalize_symbol

log = logging.getLogger("realtime.wss")

OnEvent = Callable[[StreamEvent], Awaitable[None]]

SITE_WSS = "wss://wss.coinglass.com/ws"


def is_site_wss(url: str) -> bool:
    return "wss.coinglass.com" in (url or "")


def build_site_subscribe(symbols: List[str]) -> list:
    """Frontend WSS protocol captured from /liquidations."""
    params = [{"channel": "liq", "type": "-1"}]
    for raw in symbols:
        params.append({"channel": "liq", "type": base_asset(raw)})
    return params


def build_channels(symbols: List[str]) -> List[str]:
    chans = ["liquidation_orders"]
    min_vol = CFG.websocket.min_trade_usd
    for raw in symbols:
        base = base_asset(raw)
        pair = normalize_symbol(raw)
        for ex in CFG.websocket.ticker_exchanges:
            chans.append(f"futures_ticker@{ex}_{pair}")
            chans.append(f"futures_trades@{ex}_{pair}@{min_vol}")
        chans.append(f"futures_ticker@Binance_{base}USDT")
    seen = set()
    out = []
    for c in chans:
        if c not in seen:
            seen.add(c)
            out.append(c)
    return out


def _side_name(kind: str, raw) -> str:
    try:
        n = int(raw)
    except (TypeError, ValueError):
        return str(raw or "")
    if kind == "liquidation":
        return "long" if n == 1 else "short" if n == 2 else str(n)
    return "sell" if n == 1 else "buy" if n == 2 else str(n)


def parse_message(msg: dict) -> List[StreamEvent]:
    channel = str(msg.get("channel") or msg.get("ch") or msg.get("topic") or "")
    data = msg.get("data") if "data" in msg else msg.get("params", msg.get("d"))
    if data is None:
        return []
    rows = data if isinstance(data, list) else [data]
    events: List[StreamEvent] = []
    if channel in ("liquidation_orders", "liq") or channel.startswith("liquidation"):
        kind = "liquidation"
    elif str(channel).startswith("futures_trades") or str(channel).startswith("spot_trades"):
        kind = "trade"
    elif str(channel).startswith("futures_ticker"):
        kind = "ticker"
    else:
        kind = "other"
    for row in rows:
        if not isinstance(row, dict):
            continue
        symbol = normalize_symbol(
            str(row.get("symbol") or row.get("base_asset") or row.get("baseAsset") or row.get("coin") or "")
        )
        exchange = str(row.get("exchange") or row.get("exchangeName") or row.get("exName") or row.get("ex_name") or row.get("ex") or "")
        ts_ms = row.get("time") or row.get("createTime") or row.get("turnoverTime") or row.get("update_time") or row.get("ts") or 0
        try:
            ts = float(ts_ms) / 1000.0 if float(ts_ms) > 1e12 else float(ts_ms or time.time())
        except (TypeError, ValueError):
            ts = time.time()
        price = float(row.get("price") or row.get("p") or 0)
        size = float(
            row.get("volume_usd")
            or row.get("volUsd")
            or row.get("vol_usd")
            or row.get("usd")
            or row.get("value")
            or 0
        )
        if kind == "ticker":
            size = float(row.get("volume_usd_24h") or size)
        events.append(
            StreamEvent(
                channel=channel,
                kind=kind,
                symbol=symbol,
                exchange=exchange,
                timestamp=ts,
                price=price,
                size_usd=size,
                side=_side_name(kind, row.get("side")),
                extra={
                    "oi": float(row.get("open_interest") or row.get("openInterest") or row.get("oi") or 0),
                    "funding": float(row.get("funding_rate") or row.get("fundingRate") or 0),
                    "index_price": float(row.get("index_price") or 0),
                    "raw_channel": channel,
                },
            )
        )
    return events


class CoinGlassWSS:
    def __init__(self, session: SessionBundle, symbols: List[str], on_event: OnEvent) -> None:
        self.session = session
        self.symbols = symbols
        self.on_event = on_event
        self.channels = session.subscribe or build_channels(symbols)
        self._running = False
        self._task: Optional[asyncio.Task] = None
        self.connected = False
        self.frames = 0
        self.reconnects = 0
        self.last_error = ""
        self.last_message_at = 0.0

    @property
    def url(self) -> str:
        if self.session.wss_url:
            return self.session.wss_url
        if self.session.api_key:
            return f"{CFG.websocket.official_url}?cg-api-key={self.session.api_key}"
        return ""

    def start(self) -> None:
        if self._task:
            return
        self._running = True
        self._task = asyncio.create_task(self._loop(), name="cg-wss")

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
        url = self.url
        if not url:
            self.last_error = "WSS url yok — COINGLASS_API_KEY veya hunt ile token gerekli"
            log.error(self.last_error)
            return
        attempt = 0
        headers = dict(self.session.wss_headers or {})
        headers.setdefault("Origin", "https://www.coinglass.com")
        headers.setdefault("User-Agent", CFG.browser.user_agent)
        while self._running:
            try:
                shown = url.split("cg-api-key=")[0] + ("cg-api-key=***" if "cg-api-key=" in url else "")
                log.info("WSS baglaniyor: %s", shown)
                async with websockets.connect(
                    url,
                    ping_interval=None,
                    close_timeout=5,
                    max_size=8 * 1024 * 1024,
                    additional_headers=headers,
                    origin="https://www.coinglass.com",
                ) as ws:
                    self.connected = True
                    attempt = 0
                    if is_site_wss(url):
                        payload = {"method": "subscribe", "params": build_site_subscribe(self.symbols)}
                    else:
                        payload = {"method": "subscribe", "channels": self.channels}
                    await ws.send(orjson.dumps(payload).decode())
                    log.info("Subscribe %s", payload)
                    ping_task = asyncio.create_task(self._heartbeat(ws))
                    try:
                        async for raw in ws:
                            if not self._running:
                                break
                            await self._handle(raw)
                    finally:
                        ping_task.cancel()
            except asyncio.CancelledError:
                break
            except Exception as exc:
                self.last_error = str(exc)
                log.warning("WSS kopuk: %s", exc)
            finally:
                self.connected = False
            if not self._running:
                break
            attempt += 1
            self.reconnects += 1
            if attempt > CFG.websocket.max_reconnect_attempts:
                log.error("WSS max reconnect asildi")
                break
            await asyncio.sleep(min(CFG.websocket.reconnect_delay_sec * attempt, 30.0))

    async def _heartbeat(self, ws) -> None:
        try:
            while self._running:
                await asyncio.sleep(CFG.websocket.ping_interval_sec)
                try:
                    await ws.send("ping")
                except Exception:
                    return
        except asyncio.CancelledError:
            return

    def _decode(self, raw: Union[str, bytes]) -> Optional[dict]:
        if raw in ("pong", b"pong", "ping", b"ping"):
            return None
        if isinstance(raw, bytes):
            if raw[:2] == b"\x1f\x8b":
                try:
                    raw = gzip.decompress(raw)
                except Exception:
                    return None
            try:
                msg = orjson.loads(raw)
            except Exception:
                return None
        else:
            try:
                msg = orjson.loads(raw)
            except Exception:
                return None
        return msg if isinstance(msg, dict) else None

    async def _handle(self, raw) -> None:
        msg = self._decode(raw)
        if not msg:
            return
        self.frames += 1
        self.last_message_at = time.time()
        if self.frames <= 8:
            log.info("WSS frame %s: %s", self.frames, orjson.dumps(msg).decode()[:400])
        for event in parse_message(msg):
            try:
                await self.on_event(event)
            except Exception as exc:
                log.debug("on_event: %s", exc)
