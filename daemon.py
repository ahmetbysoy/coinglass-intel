#!/usr/bin/env python3
"""CoinGlass Intelligence — WebSocket-first realtime daemon.

Playwright sadece token/WSS avı için bir kez açılır, sonra kapanır.
Asıl süreç hafif Python WSS + 5 sn sinyal taraması + Telegram.
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import os
import signal
import sys
import time
from typing import List

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rich.console import Console
from rich.live import Live
from rich.panel import Panel
from rich.table import Table
from rich import box

from config import CFG, LOG_DIR
from realtime.cache import LiveCache
from realtime.models import SessionBundle, StreamEvent, normalize_symbol
from realtime.rest_pump import RestPump
from realtime.signal_loop import SignalLoop
from realtime.telegram_push import TelegramPush
from realtime.token_hunter import TokenHunter
from realtime.wss_client import CoinGlassWSS, build_channels

console = Console()
log = logging.getLogger("daemon")


def setup_logging(level: str) -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    fmt = "%(asctime)s | %(name)-24s | %(levelname)-7s | %(message)s"
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format=fmt,
        datefmt="%H:%M:%S",
        handlers=[
            logging.StreamHandler(sys.stdout),
            logging.FileHandler(str(LOG_DIR / f"daemon_{int(time.time())}.log"), encoding="utf-8"),
        ],
    )
    for noisy in ("websockets", "aiohttp", "playwright", "asyncio"):
        logging.getLogger(noisy).setLevel(logging.WARNING)


def parse_args():
    p = argparse.ArgumentParser(description="CoinGlass realtime daemon (WSS-first)")
    p.add_argument("--symbols", nargs="+", default=None, help="BTC ETH SOL ...")
    p.add_argument("--hunt", action="store_true", help="Playwright ile token/WSS avi zorla")
    p.add_argument("--no-hunt", action="store_true", help="Browser acma")
    p.add_argument("--headless", action="store_true", default=True)
    p.add_argument("--headed", action="store_true", help="Hunt sirasinda browseri goster")
    p.add_argument("--log-level", default="INFO")
    return p.parse_args()


class Daemon:
    def __init__(self, symbols: List[str], hunt: bool, headless: bool) -> None:
        self.symbols = [normalize_symbol(s) for s in symbols]
        self.do_hunt = hunt
        self.headless = headless
        self.cache = LiveCache(redis_url=CFG.daemon.redis_url)
        self.telegram = TelegramPush(
            CFG.daemon.telegram_bot_token,
            CFG.daemon.telegram_chat_id,
            CFG.daemon.telegram_min_interval_sec,
        )
        self.signals = SignalLoop(self.cache, self.telegram, self.symbols)
        self.hunter = TokenHunter()
        self.session: SessionBundle = SessionBundle(acquired_at=0)
        self.wss: CoinGlassWSS | None = None
        self.rest: RestPump | None = None
        self._running = True
        self.started = time.time()

    async def on_event(self, event: StreamEvent) -> None:
        await self.cache.push(event)

    def _render(self) -> Panel:
        stats = self.cache.stats()
        wss_ok = bool(self.wss and self.wss.connected)
        table = Table(box=box.SIMPLE_HEAVY, expand=True)
        table.add_column("alan")
        table.add_column("deger")
        table.add_row("semboller", ", ".join(self.symbols))
        table.add_row("WSS", "BAGLI" if wss_ok else "KOPUK")
        table.add_row("WSS url", (self.session.wss_url or "-")[:72])
        table.add_row("frame", str(self.wss.frames if self.wss else 0))
        table.add_row("reconnect", str(self.wss.reconnects if self.wss else 0))
        table.add_row("son hata", (self.wss.last_error if self.wss else "")[:80] or "-")
        table.add_row("ticker", str(stats["tickers"]))
        table.add_row("olay", str(stats["counts"]))
        table.add_row("sinyal", str(self.signals.emitted))
        table.add_row("telegram", "acik" if self.telegram.enabled else "kapali")
        table.add_row("uptime", f"{time.time() - self.started:.0f}s")
        tick_table = Table(title="son ticker", box=box.MINIMAL, expand=True)
        tick_table.add_column("sym")
        tick_table.add_column("px", justify="right")
        tick_table.add_column("ex")
        tick_table.add_column("oi", justify="right")
        for sym in self.symbols:
            t = self.cache.ticker(sym)
            if not t:
                tick_table.add_row(sym, "-", "-", "-")
                continue
            tick_table.add_row(
                sym,
                f"${t.price:,.2f}" if t.price else "-",
                t.exchange or "-",
                f"${t.extra.get('oi', 0):,.0f}" if t.extra.get("oi") else "-",
            )
        from rich.console import Group
        return Panel(Group(table, tick_table), title="COINGLASS REALTIME v4", border_style="red")

    async def start(self) -> None:
        await self.cache.connect_redis()
        existing = self.hunter.load()
        if existing and existing.api_key and not CFG.daemon.api_key:
            CFG.daemon.api_key = existing.api_key
        if CFG.daemon.api_key:
            url = f"{CFG.websocket.official_url}?cg-api-key={CFG.daemon.api_key}"
            self.session = SessionBundle(
                acquired_at=time.time(),
                api_key=CFG.daemon.api_key,
                wss_url=url,
                wss_headers={"Origin": "https://www.coinglass.com", "User-Agent": CFG.browser.user_agent},
                subscribe=build_channels(self.symbols),
                notes=["official api key"],
            )
            console.print("[green]Official API key bulundu — browser gerekmez[/green]")
        elif self.do_hunt:
            console.print("[yellow]Playwright token avi basliyor (tek sefer)...[/yellow]")
            self.session = await self.hunter.hunt(headless=self.headless)
            if CFG.daemon.api_key and not self.session.wss_url:
                self.session.api_key = CFG.daemon.api_key
                self.session.wss_url = f"{CFG.websocket.official_url}?cg-api-key={CFG.daemon.api_key}"
            self.session.subscribe = build_channels(self.symbols)
            self.hunter.save(self.session)
        elif existing:
            self.session = existing
            if not self.session.subscribe:
                self.session.subscribe = build_channels(self.symbols)
            console.print("[green]Kayitli session yuklendi[/green]")
        else:
            self.session = SessionBundle(
                acquired_at=time.time(),
                notes=["session yok, WSS bekleniyor"],
                subscribe=build_channels(self.symbols),
            )

        self.wss = CoinGlassWSS(self.session, self.symbols, self.on_event)
        self.wss.start()
        self.rest = RestPump(self.cache, self.symbols, self.session.api_key or CFG.daemon.api_key)
        self.rest.start(interval=20.0)
        self.signals.start()

    async def stop(self) -> None:
        self._running = False
        if self.wss:
            await self.wss.stop()
        if self.rest:
            await self.rest.stop()
        await self.signals.stop()
        await self.cache.close()

    async def run(self) -> None:
        await self.start()
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, lambda: asyncio.create_task(self.stop()))
            except NotImplementedError:
                pass
        with Live(self._render(), console=console, refresh_per_second=2) as live:
            while self._running:
                live.update(self._render())
                await asyncio.sleep(0.5)
        console.print("[green]Daemon durdu.[/green]")


async def async_main(args) -> None:
    symbols = args.symbols or CFG.daemon.symbols
    hunt = True
    if args.no_hunt:
        hunt = False
    elif args.hunt:
        hunt = True
    elif CFG.daemon.api_key:
        hunt = False
    headless = not args.headed
    daemon = Daemon(symbols=symbols, hunt=hunt, headless=headless)
    try:
        await daemon.run()
    finally:
        await daemon.stop()


def main() -> None:
    args = parse_args()
    setup_logging(args.log_level)
    if args.headed:
        CFG.browser.headless = False
    console.print(
        Panel(
            "[bold red]COINGLASS INTELLIGENCE[/bold red]\n"
            "Paradigma 1 — WebSocket-first + lightweight daemon\n"
            "Playwright = token avi  |  Python WSS = 7/24 stream  |  5sn sinyal + Telegram",
            border_style="red",
        )
    )
    try:
        asyncio.run(async_main(args))
    except KeyboardInterrupt:
        console.print("\n[yellow]Cikis.[/yellow]")


if __name__ == "__main__":
    main()
