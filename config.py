"""CoinGlass Intelligence - Konfigurasyon"""
import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import Dict, List, Optional
BASE_DIR = Path(__file__).parent.resolve()
DATA_DIR = BASE_DIR / "data"; DB_DIR = DATA_DIR / "db"; PROFILE_DIR = DATA_DIR / "browser_profile"
ENDPOINT_CACHE_DIR = DATA_DIR / "endpoint_cache"; LOG_DIR = DATA_DIR / "logs"
SESSION_DIR = DATA_DIR / "session"
for _d in (DATA_DIR, DB_DIR, PROFILE_DIR, ENDPOINT_CACHE_DIR, LOG_DIR, SESSION_DIR): _d.mkdir(parents=True, exist_ok=True)
COINGLASS_BASE = "https://www.coinglass.com"
COINGLASS_PAGES: Dict[str, str] = {"home":"/","futures_overview":"/futures","open_interest":"/futures/OpenInterest","funding_rate":"/futures/FundingRate","liquidation":"/liquidations","long_short":"/futures/LongShortRatio","orderbook":"/futures/OrderBook","bitcoin_oi":"/futures/BitcoinOpenInterest","ethereum_oi":"/futures/EthereumOpenInterest","whale":"/whale","exchange_flow":"/exchange-flow","options_oi":"/options/OpenInterest","global_chart":"/GlobalChart"}
def build_symbol_url(bp: str, sym: str) -> str:
    c = sym.upper().replace("USDT","").replace("-PERP","").replace("_PERP","")
    if not bp or bp == "/":
        return f"{COINGLASS_BASE}/"
    return f"{COINGLASS_BASE}{bp}/{c}"
@dataclass
class BrowserConfig:
    headless: bool = False; slow_mo: int = 80; viewport_width: int = 1920; viewport_height: int = 1080
    user_agent: str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    locale: str = "en-US"; timezone: str = "Europe/Istanbul"; persistent_profile_path: str = str(PROFILE_DIR)
    navigation_timeout_ms: int = 60_000; default_timeout_ms: int = 30_000
    block_resources: List[str] = field(default_factory=lambda: ["image","media","font","stylesheet"])
    extra_headers: Dict[str, str] = field(default_factory=lambda: {"Accept-Language":"en-US,en;q=0.9","DNT":"1"})
@dataclass
class CollectorConfig:
    capture_body: bool = True; max_body_bytes: int = 5*1024*1024; ws_capture: bool = True; ws_max_frames: int = 10_000
    dedup_window_sec: float = 2.0; request_timeout_sec: float = 30.0; retry_count: int = 3; retry_backoff_sec: float = 1.5
    domain_whitelist: List[str] = field(default_factory=lambda: ["coinglass.com","capi.coinglass.com","api.coinglass.com","open-api-v3.coinglass.com","open-api-v4.coinglass.com","cdn.coinglass.com","ws.coinglass.com","wss.coinglass.com","open-ws.coinglass.com","s3.coinglass.com"])
    path_blacklist_patterns: List[str] = field(default_factory=lambda: [".js",".css",".png",".jpg",".svg",".woff",".ico","/static/","/assets/","/_next/"])
@dataclass
class RegistryConfig:
    cache_file: str = str(ENDPOINT_CACHE_DIR / "endpoint_catalog.json"); min_samples_for_schema: int = 3
    schema_confidence_threshold: float = 0.7; auto_categorize: bool = True
    category_keywords: Dict[str, List[str]] = field(default_factory=lambda: {"open_interest":["openinterest","open-interest","oi","open_interest"],"funding":["funding","fund-rate","fundingrate"],"liquidation":["liquidation","liq","forced"],"long_short":["longshort","long-short","lsr"],"orderbook":["orderbook","order-book","depth","book"],"price":["price","ticker","kline","candle","ohlc"],"volume":["volume","vol","turnover"],"whale":["whale","large","big-order"],"exchange_flow":["flow","inflow","outflow","exchange"],"options":["option","greek","iv","pcr"],"global":["global","market-cap","dominance","btc-d"]})
@dataclass
class AnalysisThresholds:
    oi_zscore_bullish: float = 1.5; oi_zscore_bearish: float = -1.5; oi_delta_significant_pct: float = 2.0; oi_lookback_periods: int = 48
    funding_extreme_positive: float = 0.05; funding_extreme_negative: float = -0.05; funding_ema_fast: int = 8; funding_ema_slow: int = 24; funding_divergence_threshold: float = 0.01
    liq_cascade_multiplier: float = 3.0; liq_heatmap_bins: int = 50; liq_cluster_min_usd: float = 10_000_000
    ob_imbalance_bullish: float = 0.65; ob_imbalance_bearish: float = 0.35; ob_depth_levels: int = 20; ob_wall_min_usd: float = 5_000_000
    vwap_deviation_pct: float = 0.5; volume_spike_multiplier: float = 2.5; volume_profile_bins: int = 30
    whale_min_order_usd: float = 1_000_000; whale_cluster_window_sec: int = 60; whale_directional_threshold: int = 3
    prediction_confidence_min: float = 0.55; prediction_timeframes: List[str] = field(default_factory=lambda: ["1m","5m","15m"])
    ensemble_weights: Dict[str, float] = field(default_factory=lambda: {"oi_momentum":0.20,"funding_signal":0.15,"liq_pressure":0.20,"ob_imbalance":0.15,"volume_signal":0.15,"whale_flow":0.15})
@dataclass
class MitmConfig:
    enabled: bool = True
    host: str = "127.0.0.1"
    port: int = 18080
    confdir: str = str(DATA_DIR / "mitm")
    sink_file: str = str(DATA_DIR / "mitm" / "traffic.jsonl")
    vault_file: str = str(SESSION_DIR / "session.vault")
    vault_key_file: str = str(SESSION_DIR / ".vault.key")
@dataclass
class DataStoreConfig:
    db_path: str = str(DB_DIR / "market_intelligence.db"); wal_mode: bool = True; batch_insert_size: int = 100; retention_hours: int = 72; vacuum_interval_hours: int = 6
@dataclass
class WebSocketConfig:
    reconnect_delay_sec: float = 2.0; max_reconnect_attempts: int = 50; ping_interval_sec: float = 20.0; ping_timeout_sec: float = 10.0; message_queue_size: int = 50_000; parse_workers: int = 4
    official_url: str = "wss://open-ws.coinglass.com/ws-api"
    rest_base: str = "https://open-api-v4.coinglass.com"
    min_trade_usd: int = 100_000
    ticker_exchanges: List[str] = field(default_factory=lambda: ["Binance", "OKX", "Bybit"])
@dataclass
class DaemonConfig:
    symbols: List[str] = field(default_factory=lambda: ["BTC", "ETH", "SOL"])
    signal_interval_sec: float = 5.0
    hunt_on_start: bool = True
    rehunt_interval_sec: float = 0.0
    session_file: str = str(SESSION_DIR / "credentials.json")
    redis_url: str = os.environ.get("REDIS_URL", "")
    api_key: str = os.environ.get("COINGLASS_API_KEY", "")
    telegram_bot_token: str = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    telegram_chat_id: str = os.environ.get("TELEGRAM_CHAT_ID", "")
    telegram_min_interval_sec: float = 45.0
    liq_alert_usd: float = 250_000.0
    whale_alert_usd: float = 1_000_000.0
    oi_jump_pct: float = 1.5
    funding_extreme: float = 0.0005
    price_move_pct: float = 0.25
@dataclass
class SystemConfig:
    browser: BrowserConfig = field(default_factory=BrowserConfig); collector: CollectorConfig = field(default_factory=CollectorConfig)
    registry: RegistryConfig = field(default_factory=RegistryConfig); analysis: AnalysisThresholds = field(default_factory=AnalysisThresholds)
    datastore: DataStoreConfig = field(default_factory=DataStoreConfig); websocket: WebSocketConfig = field(default_factory=WebSocketConfig)
    mitm: MitmConfig = field(default_factory=MitmConfig)
    daemon: DaemonConfig = field(default_factory=DaemonConfig)
    target_symbol: str = "BTCUSDT"; exchanges: List[str] = field(default_factory=lambda: ["Binance","OKX","Bybit","Bitget","dYdX","Hyperliquid"])
    log_level: str = "INFO"; verbose_network: bool = True
CFG = SystemConfig()
