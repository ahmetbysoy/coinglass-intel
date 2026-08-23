"""Shared realtime types."""
from __future__ import annotations

import time
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional


def now() -> float:
    return time.time()


@dataclass
class StreamEvent:
    channel: str
    kind: str
    symbol: str
    exchange: str
    timestamp: float
    price: float = 0.0
    size_usd: float = 0.0
    side: str = ""
    extra: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class Anomaly:
    kind: str
    symbol: str
    severity: str
    score: float
    title: str
    body: str
    timestamp: float = field(default_factory=now)
    payload: Dict[str, Any] = field(default_factory=dict)


@dataclass
class SessionBundle:
    acquired_at: float
    api_key: str = ""
    wss_url: str = ""
    wss_headers: Dict[str, str] = field(default_factory=dict)
    cookies: List[Dict[str, Any]] = field(default_factory=list)
    subscribe: List[str] = field(default_factory=list)
    notes: List[str] = field(default_factory=list)
    snapshot_events: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "SessionBundle":
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        return cls(**{k: v for k, v in data.items() if k in known})


def normalize_symbol(raw: str) -> str:
    s = (raw or "").upper().replace("-PERP", "").replace("_PERP", "").replace("-", "")
    if s.endswith("USDT"):
        return s
    if s:
        return s if s.endswith("USD") else f"{s}USDT"
    return s


def base_asset(raw: str) -> str:
    s = normalize_symbol(raw)
    for suf in ("USDT", "USD", "USDC"):
        if s.endswith(suf):
            return s[: -len(suf)]
    return s
