"""Public exchange REST feed. CoinGlass sifreli oldugu icin gercek fiyat/OI/funding buradan gelir."""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Dict, Optional

import aiohttp

from engine.util import safe_path, to_float

log = logging.getLogger("pipeline.exfeed")

BINANCE = "https://fapi.binance.com"
BYBIT = "https://api.bybit.com"
OKX = "https://www.okx.com"


class ExchangeFeed:
    def __init__(self, timeout: float = 10.0) -> None:
        self.timeout = aiohttp.ClientTimeout(total=timeout)

    def _pair(self, symbol: str) -> str:
        s = symbol.upper().replace("-PERP", "").replace("_PERP", "").replace("-", "")
        return s if s.endswith("USDT") else f"{s}USDT"

    def _coin(self, symbol: str) -> str:
        return self._pair(symbol).replace("USDT", "")

    async def _get(self, session: aiohttp.ClientSession, url: str, params: Optional[dict] = None) -> Any:
        try:
            async with session.get(url, params=params or {}) as resp:
                if resp.status != 200:
                    return {"_error": f"HTTP {resp.status}"}
                return await resp.json(content_type=None)
        except Exception as exc:
            return {"_error": str(exc)}

    async def fetch(self, symbol: str) -> Dict[str, Any]:
        pair = self._pair(symbol)
        coin = self._coin(symbol)
        async with aiohttp.ClientSession(timeout=self.timeout, headers={"User-Agent": "cg-intel/4.3"}) as s:
            bn, by, ok = await asyncio.gather(
                self._binance(s, pair),
                self._bybit(s, pair),
                self._okx(s, coin),
                return_exceptions=True,
            )
        if isinstance(bn, Exception):
            bn = {"_error": str(bn)}
        if isinstance(by, Exception):
            by = {"_error": str(by)}
        if isinstance(ok, Exception):
            ok = {"_error": str(ok)}
        return {
            "symbol": pair,
            "coin": coin,
            "ts": time.time(),
            "sources": {"binance_fut": bn, "bybit": by, "okx": ok},
        }

    async def _binance(self, s: aiohttp.ClientSession, pair: str) -> dict:
        ticker, depth, trades, funding, oi, oi_h, ls, taker, k5, k1h, k15 = await asyncio.gather(
            self._get(s, f"{BINANCE}/fapi/v1/ticker/24hr", {"symbol": pair}),
            self._get(s, f"{BINANCE}/fapi/v1/depth", {"symbol": pair, "limit": 100}),
            self._get(s, f"{BINANCE}/fapi/v1/trades", {"symbol": pair, "limit": 100}),
            self._get(s, f"{BINANCE}/fapi/v1/fundingRate", {"symbol": pair, "limit": 30}),
            self._get(s, f"{BINANCE}/fapi/v1/openInterest", {"symbol": pair}),
            self._get(s, f"{BINANCE}/futures/data/openInterestHist", {"symbol": pair, "period": "5m", "limit": 50}),
            self._get(s, f"{BINANCE}/futures/data/globalLongShortAccountRatio", {"symbol": pair, "period": "5m", "limit": 30}),
            self._get(s, f"{BINANCE}/futures/data/takerlongshortRatio", {"symbol": pair, "period": "5m", "limit": 30}),
            self._get(s, f"{BINANCE}/fapi/v1/klines", {"symbol": pair, "interval": "5m", "limit": 200}),
            self._get(s, f"{BINANCE}/fapi/v1/klines", {"symbol": pair, "interval": "1h", "limit": 100}),
            self._get(s, f"{BINANCE}/fapi/v1/klines", {"symbol": pair, "interval": "15m", "limit": 100}),
        )
        return {
            "ticker_24h": ticker, "orderbook": depth, "trades": trades, "funding": funding,
            "open_interest": oi, "oi_history": oi_h, "ls_account": ls, "taker_buysell": taker,
            "klines_5m": k5, "klines_1h": k1h, "klines_15m": k15,
        }

    async def _bybit(self, s: aiohttp.ClientSession, pair: str) -> dict:
        tick, ob, trades, oi, fund, ls = await asyncio.gather(
            self._get(s, f"{BYBIT}/v5/market/tickers", {"category": "linear", "symbol": pair}),
            self._get(s, f"{BYBIT}/v5/market/orderbook", {"category": "linear", "symbol": pair, "limit": 50}),
            self._get(s, f"{BYBIT}/v5/market/recent-trade", {"category": "linear", "symbol": pair, "limit": 100}),
            self._get(s, f"{BYBIT}/v5/market/open-interest", {"category": "linear", "symbol": pair, "intervalTime": "5min", "limit": 50}),
            self._get(s, f"{BYBIT}/v5/market/funding/history", {"category": "linear", "symbol": pair, "limit": 30}),
            self._get(s, f"{BYBIT}/v5/market/account-ratio", {"category": "linear", "symbol": pair, "period": "5min", "limit": 50}),
        )
        return {
            "ticker_linear": tick, "orderbook": ob, "trades": trades,
            "open_interest": oi, "funding_history": fund, "ls_ratio": ls,
        }

    async def _okx(self, s: aiohttp.ClientSession, coin: str) -> dict:
        inst = f"{coin}-USDT-SWAP"
        tick, ob, trades, fund, oi = await asyncio.gather(
            self._get(s, f"{OKX}/api/v5/market/ticker", {"instId": inst}),
            self._get(s, f"{OKX}/api/v5/market/books", {"instId": inst, "sz": "50"}),
            self._get(s, f"{OKX}/api/v5/market/trades", {"instId": inst, "limit": "100"}),
            self._get(s, f"{OKX}/api/v5/public/funding-rate-history", {"instId": inst, "limit": "30"}),
            self._get(s, f"{OKX}/api/v5/public/open-interest", {"instId": inst}),
        )
        return {
            "swap_ticker": tick, "orderbook": ob, "trades": trades,
            "funding_history": fund, "open_interest": oi,
        }


def analyze_order_book(ob: Any, contract_size: float = 1.0) -> Optional[dict]:
    if not ob or isinstance(ob, dict) and ob.get("_error"):
        return None
    if isinstance(ob, dict) and ob.get("retCode") == 0:
        bids = [[to_float(x[0]), to_float(x[1])] for x in (ob.get("result") or {}).get("b", [])]
        asks = [[to_float(x[0]), to_float(x[1])] for x in (ob.get("result") or {}).get("a", [])]
    elif isinstance(ob, dict) and "bids" in ob and "asks" in ob:
        bids = [[to_float(x[0]), to_float(x[1])] for x in ob["bids"]]
        asks = [[to_float(x[0]), to_float(x[1])] for x in ob["asks"]]
    elif isinstance(ob, dict) and ob.get("data"):
        row = ob["data"][0] if isinstance(ob["data"], list) else ob["data"]
        bids = [[to_float(x[0]), to_float(x[1])] for x in row.get("bids", [])]
        asks = [[to_float(x[0]), to_float(x[1])] for x in row.get("asks", [])]
    else:
        return None
    if not bids or not asks:
        return None
    mid = (bids[0][0] + asks[0][0]) / 2
    bv = sum(b[1] for b in bids) * contract_size
    av = sum(a[1] for a in asks) * contract_size
    tot = bv + av
    return {
        "bids": bids, "asks": asks, "mid": mid, "bid_vol": bv, "ask_vol": av,
        "bid_pct": bv / tot * 100 if tot else 50, "ask_pct": av / tot * 100 if tot else 50,
        "imbalance": (bv - av) / tot * 100 if tot else 0,
        "spread_pct": (asks[0][0] - bids[0][0]) / bids[0][0] * 100 if bids[0][0] else 0,
        "top_bid_walls": sorted(bids, key=lambda x: -x[1])[:5],
        "top_ask_walls": sorted(asks, key=lambda x: -x[1])[:5],
    }


def analyze_trades_binance(trades: Any) -> Optional[dict]:
    if not isinstance(trades, list) or not trades:
        return None
    buy = sum(to_float(t.get("qty")) for t in trades if not t.get("isBuyerMaker"))
    sell = sum(to_float(t.get("qty")) for t in trades if t.get("isBuyerMaker"))
    tot = buy + sell
    return {"buy_vol": buy, "sell_vol": sell, "buy_pct": buy / tot * 100 if tot else 50,
            "cvd": buy - sell, "cvd_pct": (buy - sell) / tot * 100 if tot else 0}


def analyze_funding(data: Any) -> Optional[dict]:
    if not data:
        return None
    rates = []
    if isinstance(data, list):
        rates = [to_float(x.get("fundingRate", 0)) for x in data]
    elif isinstance(data, dict):
        if data.get("retCode") == 0:
            rates = [to_float(x.get("fundingRate", 0)) for x in (data.get("result") or {}).get("list", [])]
        elif data.get("data"):
            rates = [to_float(x.get("fundingRate", x.get("fundingRate", 0))) for x in data["data"]]
    if not rates:
        return None
    import statistics
    avg = statistics.mean(rates)
    return {"current": rates[0], "avg": avg, "max": max(rates), "min": min(rates),
            "trend": "up" if rates[0] > avg else "down" if rates[0] < avg else "flat"}


def analyze_ls(data: Any, source: str = "binance") -> Optional[dict]:
    if not data:
        return None
    try:
        if source == "binance" and isinstance(data, list) and data:
            return {"current": to_float(data[0].get("longShortRatio")),
                    "long_pct": to_float(data[0].get("longAccount", 0)) * 100,
                    "short_pct": to_float(data[0].get("shortAccount", 0)) * 100}
        if source == "bybit" and data.get("retCode") == 0:
            lst = (data.get("result") or {}).get("list") or []
            if lst:
                b, s_ = to_float(lst[0].get("buyRatio")), to_float(lst[0].get("sellRatio"))
                return {"current": b / s_ if s_ else 1, "long_pct": b * 100, "short_pct": s_ * 100}
        if source == "okx" and data.get("data"):
            r = to_float(data["data"][0][1] if isinstance(data["data"][0], list) else data["data"][0].get("ratio", 1))
            return {"current": r, "long_pct": r / (1 + r) * 100, "short_pct": 100 - r / (1 + r) * 100}
    except Exception:
        return None
    return None


def analyze_oi_change(oi_hist: Any) -> Optional[dict]:
    if not isinstance(oi_hist, list) or len(oi_hist) < 2:
        return None
    vals = [to_float(x.get("sumOpenInterest", 0)) for x in oi_hist]
    if not vals or vals[-1] == 0:
        return None
    newest, oldest = vals[0], vals[-1]
    return {"newest": newest, "oldest": oldest, "change_pct": (newest - oldest) / oldest * 100}
