"""Known futures symbol map (scalper registry). Unknown symbols still work via convention."""
from __future__ import annotations

from typing import Dict

SYMBOLS_REGISTRY: Dict[str, Dict[str, str]] = {
    "EDGEUSDT": {
        "base": "EDGE", "quote": "USDT",
        "coingecko_id": "edgex", "coinpaprika_id": "edge-edgex",
        "binance": "EDGEUSDT", "bybit": "EDGEUSDT", "okx": "EDGE-USDT-SWAP",
    },
    "ALLOUSDT": {
        "base": "ALLO", "quote": "USDT",
        "coingecko_id": "allora", "coinpaprika_id": "allora",
        "binance": "ALLOUSDT", "bybit": "ALLOUSDT", "okx": "ALLO-USDT-SWAP",
    },
    "SPELLUSDT": {
        "base": "SPELL", "quote": "USDT",
        "coingecko_id": "spell-token", "coinpaprika_id": "spell-token",
        "binance": "SPELLUSDT", "bybit": "SPELLUSDT", "okx": "SPELL-USDT-SWAP",
    },
    "XAUTUSDT": {
        "base": "XAUT", "quote": "USDT",
        "coingecko_id": "tether-gold", "coinpaprika_id": "xaut-tether-gold",
        "binance": "XAUTUSDT", "bybit": "XAUTUSDT", "okx": "XAUT-USDT-SWAP",
    },
    "BLUAIUSDT": {
        "base": "BLUAI", "quote": "USDT",
        "coingecko_id": "blueai", "coinpaprika_id": "blueai",
        "binance": "BLUAIUSDT", "bybit": "BLUAIUSDT", "okx": "BLUAI-USDT-SWAP",
    },
}


def resolve_symbol(raw: str) -> Dict[str, str]:
    s = raw.upper().replace("-PERP", "").replace("_PERP", "").replace("-", "")
    if not s.endswith("USDT"):
        s = f"{s}USDT"
    if s in SYMBOLS_REGISTRY:
        return {"symbol": s, **SYMBOLS_REGISTRY[s]}
    base = s.replace("USDT", "")
    return {
        "symbol": s, "base": base, "quote": "USDT",
        "coingecko_id": base.lower(), "coinpaprika_id": base.lower(),
        "binance": s, "bybit": s, "okx": f"{base}-USDT-SWAP",
    }
