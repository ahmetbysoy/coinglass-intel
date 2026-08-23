#!/usr/bin/env python3
"""CoinGlass Intelligence - Ana Orkestrator. Symbol ver, pipeline baslasin."""
import asyncio, argparse, logging, sys, os, time
from typing import Dict, List, Optional, Any
from pathlib import Path
import orjson

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich import box

from config import CFG, COINGLASS_PAGES, LOG_DIR
from collector.browser_session import BrowserSession
from collector.network_interceptor import NetworkInterceptor
from collector.endpoint_discovery import EndpointDiscovery
from collector.mitm_bridge import MitmBridge
from collector.session_vault import SessionVault
from collector.traffic_merger import TrafficMerger
from registry.endpoint_registry import EndpointRegistry
from registry.schema_analyzer import SchemaAnalyzer
from pipeline.data_normalizer import DataNormalizer
from pipeline.data_store import DataStore
from engine.oi_analyzer import OIAnalyzer
from engine.funding_analyzer import FundingAnalyzer
from engine.liquidation_analyzer import LiquidationAnalyzer
from engine.orderbook_analyzer import OrderBookAnalyzer
from engine.volume_profile import VolumeProfileAnalyzer
from engine.whale_tracker import WhaleTracker
from engine.prediction_engine import PredictionEngine
from engine.market_score import MarketScorer
from engine.history_store import HistoryStore
from pipeline.exchange_feed import ExchangeFeed

console = Console()
log = logging.getLogger("main")

def setup_logging(level: str = "INFO") -> None:
    fmt = "%(asctime)s | %(name)-28s | %(levelname)-7s | %(message)s"
    logging.basicConfig(level=getattr(logging, level.upper(), logging.INFO), format=fmt, datefmt="%H:%M:%S",
        handlers=[logging.StreamHandler(sys.stdout),
                  logging.FileHandler(str(LOG_DIR / f"session_{int(time.time())}.log"), encoding="utf-8")])
    for noisy in ("playwright","websockets","urllib3","asyncio"):
        logging.getLogger(noisy).setLevel(logging.WARNING)

PAGE_CATEGORY_MAP: Dict[str, List[str]] = {
    "futures_overview":["open_interest","funding","price","volume"],
    "open_interest":["open_interest"],
    "funding_rate":["funding"],
    "liquidation":["liquidation"],
    "long_short":["long_short"],
    "orderbook":["orderbook"],
    "bitcoin_oi":["open_interest","price"],
    "ethereum_oi":["open_interest","price"],
    "whale":["whale"],
    "exchange_flow":["volume","whale"],
    "global_chart":["price","volume"],
}

class IntelligencePipeline:
    def __init__(self, symbol: str, pages: Optional[List[str]] = None):
        self.symbol = symbol.upper().replace("-PERP","").replace("_PERP","")
        self.clean_symbol = self.symbol.replace("USDT","")
        self.pages = pages or list(COINGLASS_PAGES.keys())
        self.mitm = MitmBridge()
        self.vault = SessionVault()
        self.browser = BrowserSession(proxy_url=self.mitm.proxy_url if CFG.mitm.enabled else None)
        self.interceptor: Optional[NetworkInterceptor] = None
        self.discovery = EndpointDiscovery()
        self.registry = EndpointRegistry()
        self.merger = TrafficMerger(self.discovery, self.registry)
        self.mitm_hits = 0
        self.schema_analyzer = SchemaAnalyzer()
        self.normalizer = DataNormalizer()
        self.store = DataStore()
        self.oi_analyzer = OIAnalyzer()
        self.funding_analyzer = FundingAnalyzer()
        self.liq_analyzer = LiquidationAnalyzer()
        self.ob_analyzer = OrderBookAnalyzer()
        self.vol_analyzer = VolumeProfileAnalyzer()
        self.whale_tracker = WhaleTracker()
        self.prediction_engine = PredictionEngine()
        self.scorer = MarketScorer()
        self.exfeed = ExchangeFeed()
        self.history = HistoryStore()
        self.v4 = None
        self.signals: Dict[str, Any] = {}
        self.prediction = None
        self._running = False

    async def run(self) -> None:
        self._running = True
        console.print(Panel(
            f"[bold red]COINGLASS INTELLIGENCE[/bold red]\\n"
            f"Hedef: [bold yellow]{self.symbol}[/bold yellow]\\n"
            f"Sayfalar: {', '.join(self.pages)}\\n"
            f"Motor: 6 analizor + ensemble tahmin",
            title="PIPELINE BASLATILIYOR", border_style="red"))
        try:
            await self._init_infra()
            await self._crawl()
            self._save_discovery()
            await self._analyze()
            await self._score_exchanges()
            self._predict()
            self._print_results()
        except KeyboardInterrupt:
            console.print("\\n[bold yellow]Pipeline durduruldu.[/bold yellow]")
        except Exception as exc:
            console.print(f"\\n[bold red]HATA: {exc}[/bold red]")
            log.exception("Pipeline hatasi")
        finally:
            await self._cleanup()

    async def _init_infra(self) -> None:
        if CFG.mitm.enabled:
            console.print("[dim]-> mitmproxy (ikinci goz) aciliyor...[/dim]")
            try:
                await self.mitm.start()
                console.print(f"[green]mitmproxy {self.mitm.proxy_url}[/green]")
            except Exception as exc:
                console.print(f"[yellow]mitmproxy baslamadi, sadece Playwright: {exc}[/yellow]")
                log.warning("mitm start: %s", exc)
                self.browser.proxy_url = None
                CFG.mitm.enabled = False
        console.print("[dim]-> Browser aciliyor...[/dim]")
        await self.browser.launch()
        saved = self.vault.load()
        if saved["cookies"]:
            n = await self.browser.restore_cookies(saved["cookies"])
            console.print(f"[green]Vault'tan {n} cookie yuklendi (sifreli kasa)[/green]")
        console.print("[dim]-> Network interceptor baglaniyor...[/dim]")
        self.interceptor = NetworkInterceptor(self.browser.page)
        self.interceptor.attach()
        self.discovery.bind(self.interceptor)
        console.print("[dim]-> DataStore baglaniyor...[/dim]")
        await self.store.connect()
        known = self.registry.get_known()
        if known:
            console.print(f"[green]Registry'de {len(known)} bilinen endpoint var[/green]")
        else:
            console.print("[yellow]Registry bos - tam kesif modu[/yellow]")

    async def _crawl(self) -> None:
        console.print("\\n[bold]=== SAYFA TARAMASI ===[/bold]")
        console.print("  [cyan]-> Ana sayfa...[/cyan]")
        await self.browser.goto_base()
        await asyncio.sleep(2)
        has_session = await self.browser.has_active_session()
        if has_session:
            console.print("  [green]Aktif oturum var[/green]")
        else:
            console.print("  [yellow]Oturum yok - public veri[/yellow]")
        for i, pk in enumerate(self.pages, 1):
            if not self._running: break
            if pk not in COINGLASS_PAGES:
                console.print(f"  [red]Bilinmeyen sayfa: {pk}[/red]")
                continue
            console.print(f"  [cyan]-> [{i}/{len(self.pages)}] {pk} ({self.clean_symbol})...[/cyan]")
            pre = self.interceptor.stats["responses_json"]
            try:
                await self.browser.goto_page(pk, symbol=self.symbol)
            except Exception as exc:
                console.print(f"  [red]Navigasyon hatasi: {exc}[/red]")
                continue
            await asyncio.sleep(4)
            try:
                await self.browser.page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                await asyncio.sleep(1.5)
                await self.browser.page.evaluate("window.scrollTo(0, 0)")
                await asyncio.sleep(1)
            except Exception: pass
            new_json = self.interceptor.stats["responses_json"] - pre
            mitm_recs = self.mitm.drain() if CFG.mitm.enabled else []
            if mitm_recs:
                self.mitm_hits += self.merger.ingest_mitm(mitm_recs)
            plains = await self.browser.dump_plain()
            for item in plains:
                await self._ingest_plain(pk, item.get("payload"))
            console.print(f"  [green]{pk}: {new_json} JSON / mitm +{len(mitm_recs)} / hook {len(plains)}[/green]")
            await self._process_page(pk)
        console.print(f"\\n{self.interceptor.stats_report()}")

    async def _ingest_plain(self, pk: str, payload: Any) -> None:
        if payload is None:
            return
        categories = PAGE_CATEGORY_MAP.get(pk, ["open_interest", "funding", "liquidation", "price", "whale"])
        raw = orjson.dumps(payload)
        for cat in categories:
            norm = self.normalizer.normalize(cat, raw, f"hook:{pk}", self.symbol)
            if norm:
                await self._store_norm(cat, norm)

    async def _process_page(self, pk: str) -> None:
        categories = PAGE_CATEGORY_MAP.get(pk, [])
        for resp in self.interceptor.get_json_responses():
            for cat in categories:
                norm = self.normalizer.normalize(cat, resp.body, resp.request.path, self.symbol)
                if norm:
                    await self._store_norm(cat, norm)

    async def _store_norm(self, cat: str, data: Any) -> None:
        if not isinstance(data, list): data = [data]
        if not data: return
        m = {"open_interest":self.store.insert_oi,"funding":self.store.insert_funding,
             "liquidation":self.store.insert_liquidation,"long_short":self.store.insert_long_short,
             "orderbook":self.store.insert_orderbook,"price":self.store.insert_price,"whale":self.store.insert_whale}
        ins = m.get(cat)
        if ins:
            try: await ins(data)
            except Exception as e: log.debug("Store insert [%s]: %s", cat, e)

    def _save_discovery(self) -> None:
        console.print("\\n[bold]=== ENDPOINT KESFI ===[/bold]")
        catalog = self.discovery.export_catalog()
        new = self.registry.update_from_discovery(catalog)
        console.print(self.discovery.print_catalog())
        leftover = self.mitm.all_records() if CFG.mitm.enabled else []
        if leftover:
            extra = self.merger.ingest_mitm(leftover)
            self.mitm_hits += extra
        console.print(f"[green]{new} yeni endpoint registry'ye eklendi (mitm goz: {self.mitm_hits})[/green]")
        self.registry.save()

    async def _analyze(self) -> None:
        console.print("\\n[bold]=== ANALIZ MOTORLARI ===[/bold]")
        await self.store.flush()
        sym = self.symbol
        price_data = await self.store.query_price(sym, limit=500)

        console.print("  [cyan]-> Open Interest...[/cyan]")
        oi_data = await self.store.query_oi(sym, limit=200)
        if oi_data:
            self.signals["oi_momentum"] = self.oi_analyzer.analyze(sym, oi_data, price_data)
            if self.signals["oi_momentum"]:
                console.print(f"  [green]{self.signals['oi_momentum'].narrative}[/green]")
        else:
            console.print("  [yellow]OI verisi yok[/yellow]")

        console.print("  [cyan]-> Funding Rate...[/cyan]")
        fd = await self.store.query_funding(sym, limit=200)
        if fd:
            self.signals["funding_signal"] = self.funding_analyzer.analyze(sym, fd, price_data)
            if self.signals["funding_signal"]:
                console.print(f"  [green]{self.signals['funding_signal'].narrative}[/green]")
        else:
            console.print("  [yellow]Funding verisi yok[/yellow]")

        console.print("  [cyan]-> Likidasyon...[/cyan]")
        ld = await self.store.query_liquidation(sym, limit=200)
        if ld:
            self.signals["liq_pressure"] = self.liq_analyzer.analyze(sym, ld, price_data)
            if self.signals["liq_pressure"]:
                console.print(f"  [green]{self.signals['liq_pressure'].narrative}[/green]")
        else:
            console.print("  [yellow]Likidasyon verisi yok[/yellow]")

        console.print("  [cyan]-> Emir Defteri...[/cyan]")
        obd = await self.store.query_orderbook(sym, limit=200)
        if obd:
            self.signals["ob_imbalance"] = self.ob_analyzer.analyze(sym, obd)
            if self.signals["ob_imbalance"]:
                console.print(f"  [green]{self.signals['ob_imbalance'].narrative}[/green]")
        else:
            console.print("  [yellow]Emir defteri verisi yok[/yellow]")

        console.print("  [cyan]-> Hacim Profili...[/cyan]")
        if price_data and len(price_data) >= 10:
            self.signals["volume_signal"] = self.vol_analyzer.analyze(sym, price_data)
            if self.signals["volume_signal"]:
                console.print(f"  [green]{self.signals['volume_signal'].narrative}[/green]")
        else:
            console.print("  [yellow]Yeterli fiyat verisi yok[/yellow]")

        console.print("  [cyan]-> Balina Takibi...[/cyan]")
        wd = await self.store.query_whale(sym, limit=200)
        if wd:
            self.signals["whale_flow"] = self.whale_tracker.analyze(sym, wd)
            if self.signals["whale_flow"]:
                console.print(f"  [green]{self.signals['whale_flow'].narrative}[/green]")
        else:
            console.print("  [yellow]Balina verisi yok[/yellow]")

        active = sum(1 for v in self.signals.values() if v is not None)
        console.print(f"\\n[bold green]{active}/6 analiz motoru aktif[/bold green]")

    async def _score_exchanges(self) -> None:
        console.print("\\n[bold]=== EXCHANGE FEED + v4.3 SKOR ===[/bold]")
        try:
            feed = await self.exfeed.fetch(self.symbol)
            self.v4 = self.scorer.score(feed, self.symbol)
        except Exception as exc:
            console.print(f"  [red]exchange feed: {exc}[/red]")
            log.exception("v4 score")
            return
        if self.v4.price > 0:
            for key, sig in self.v4.signals.items():
                existing = self.signals.get(key)
                if existing is None or getattr(existing, "current_price", 0) in (0, None):
                    self.signals[key] = sig
        self.history.save(self.v4.symbol, self.v4.price, self.v4.total_score, self.v4.direction, self.v4.component)
        console.print(f"  [green]{self.v4.text}[/green]")
        console.print(f"  [dim]{self.history.validate(self.v4.symbol)}[/dim]")

    def _predict(self) -> None:
        console.print("\\n[bold]=== ENSEMBLE TAHMIN ===[/bold]")
        cp = float(self.v4.price) if self.v4 and self.v4.price else 0.0
        for key in ("volume_signal","liq_pressure","ob_imbalance"):
            sig = self.signals.get(key)
            if sig and hasattr(sig, "current_price") and sig.current_price > 0:
                cp = sig.current_price; break
        if cp == 0:
            sig = self.signals.get("oi_momentum")
            if sig and sig.price_series: cp = sig.price_series[-1]
        self.prediction = self.prediction_engine.predict(self.symbol, cp, self.signals)

    def _print_results(self) -> None:
        if not self.prediction:
            console.print("[red]Tahmin uretilemedi - yeterli veri yok[/red]")
            return
        p = self.prediction
        ac = {"STRONG_LONG":"bold green","LONG":"green","NEUTRAL":"yellow","SHORT":"red","STRONG_SHORT":"bold red"}
        ast = ac.get(p.action, "white")
        console.print(Panel(
            f"[bold]Symbol:[/bold] {p.symbol}\\n"
            f"[bold]Fiyat:[/bold] ${p.current_price:,.2f}\\n"
            f"[bold]KARAR:[/bold] [{ast}]{p.action}[/{ast}]\\n"
            f"[bold]Guven:[/bold] %{p.action_confidence*100:.0f}\\n"
            f"[bold]Ensemble Skor:[/bold] {p.ensemble_score:+.4f}\\n"
            f"[bold]Sinyal Anlasma:[/bold] %{p.signal_agreement*100:.0f}\\n"
            f"[bold]Risk:[/bold] {p.overall_risk.upper()}\\n"
            f"[bold]Dominant:[/bold] {p.dominant_signal}",
            title="TAHMIN SONUCU", border_style="cyan"))

        tf_table = Table(title="Timeframe Tahminleri", box=box.HEAVY_EDGE, border_style="cyan")
        tf_table.add_column("TF", style="bold")
        tf_table.add_column("Yon", justify="center")
        tf_table.add_column("Guven", justify="right")
        tf_table.add_column("Skor", justify="right")
        tf_table.add_column("Beklenen", justify="right")
        tf_table.add_column("Risk", justify="center")
        tf_table.add_column("Celiski", justify="center")
        for tfn in ("1m","5m","15m"):
            tp = p.predictions.get(tfn)
            if not tp: continue
            ds = "green" if tp.direction == "UP" else ("red" if tp.direction == "DOWN" else "yellow")
            dsym = {"UP":"^","DOWN":"v","FLAT":"-"}.get(tp.direction, "?")
            rs = {"low":"green","medium":"yellow","high":"red","extreme":"bold red"}.get(tp.risk,"white")
            tf_table.add_row(tfn, f"[{ds}]{dsym} {tp.direction}[/{ds}]", f"%{tp.confidence*100:.0f}",
                f"{tp.weighted_score:+.4f}", f"~%{tp.expected_move_pct:.3f}",
                f"[{rs}]{tp.risk}[/{rs}]", str(len(tp.conflicts)))
        console.print(tf_table)

        ct = Table(title="Bilesen Sinyalleri", box=box.SIMPLE_HEAVY, border_style="magenta")
        ct.add_column("Analizor", style="bold")
        ct.add_column("Skor", justify="right")
        ct.add_column("Guc", justify="right")
        ct.add_column("Agirlik", justify="right")
        ct.add_column("Bar", justify="left")
        for key in sorted(p.component_scores.keys()):
            sc = p.component_scores[key]; w = p.component_weights.get(key, 0)
            sig = self.signals.get(key)
            st = sig.signal_strength if sig and hasattr(sig, "signal_strength") else 0.0
            bl = int(abs(sc)*25)
            if sc > 0: bar = f"[green]{'#' * bl}[/green]"
            elif sc < 0: bar = f"[red]{'#' * bl}[/red]"
            else: bar = "[dim]-[/dim]"
            ss = "green" if sc > 0 else ("red" if sc < 0 else "dim")
            ct.add_row(key, f"[{ss}]{sc:+.3f}[/{ss}]", f"{st:.2f}", f"{w:.2f}", bar)
        console.print(ct)

        if p.conflicting_pairs:
            console.print("\\n[bold yellow]Celisen sinyaller:[/bold yellow]")
            for a, b in p.conflicting_pairs:
                console.print(f"  [yellow]{a} <-> {b}[/yellow]")

        if self.v4:
            console.print(Panel(
                f"[bold]{self.v4.direction}[/bold]  skor {self.v4.total_score:+.1f}  confluence {self.v4.confluence:+.1f}\\n"
                f"{self.v4.strategy}\\n"
                + "\\n".join(f"! {w}" for w in self.v4.strategy_warnings),
                title="v4.3 STRATEJI", border_style="yellow"))
        console.print("\\n[bold]=== DETAY ===[/bold]")
        console.print(p.detailed_breakdown)

    async def _cleanup(self) -> None:
        self._running = False
        console.print("\\n[dim]-> Temizlik...[/dim]")
        if self.interceptor: self.interceptor.detach()
        try:
            if self.browser.alive:
                cookies = await self.browser.get_cookies()
                self.vault.save(cookies, extra={"symbol": self.symbol})
        except Exception as exc:
            log.debug("vault save: %s", exc)
        await self.store.close()
        await self.browser.shutdown()
        await self.mitm.stop()
        console.print("[green]Pipeline tamamlandi.[/green]")

def parse_args():
    parser = argparse.ArgumentParser(description="CoinGlass Intelligence")
    parser.add_argument("symbol", type=str, help="Hedef sembol (BTCUSDT, ETH, SOL)")
    parser.add_argument("--pages", nargs="+", default=None, help="Taranacak sayfalar")
    parser.add_argument("--discover-only", action="store_true", help="Sadece endpoint kesfi")
    parser.add_argument("--headless", action="store_true", help="Headless browser")
    parser.add_argument("--no-mitm", action="store_true", help="mitmproxy ikinci gozu kapat")
    parser.add_argument("--exchange-only", action="store_true", help="Sadece borsa feed + v4 skor")
    parser.add_argument("--log-level", default="INFO", choices=["DEBUG","INFO","WARNING","ERROR"])
    return parser.parse_args()

async def async_main(args):
    setup_logging(args.log_level)
    if args.headless: CFG.browser.headless = True
    if args.no_mitm: CFG.mitm.enabled = False
    pipeline = IntelligencePipeline(symbol=args.symbol, pages=args.pages)
    if args.exchange_only:
        await pipeline._score_exchanges()
        pipeline._predict()
        pipeline._print_results()
        return
    if args.discover_only:
        pipeline.pages = args.pages or list(COINGLASS_PAGES.keys())
        await pipeline._init_infra()
        await pipeline._crawl()
        pipeline._save_discovery()
        await pipeline._cleanup()
    else:
        await pipeline.run()

def main():
    args = parse_args()
    console.print(Panel(
        "[bold red]COINGLASS INTELLIGENCE AGENT\n"
        "Playwright + mitmproxy collector v4.1\n"
        "Ensemble Engine[/bold red]", border_style="red"))
    try:
        asyncio.run(async_main(args))
    except KeyboardInterrupt:
        console.print("\\n[bold yellow]Cikis.[/bold yellow]")

if __name__ == "__main__":
    main()
