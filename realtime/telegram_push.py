"""Telegram alert sink. Silent no-op if token/chat missing."""
from __future__ import annotations

import logging
import time
from typing import Dict

import aiohttp

from .models import Anomaly

log = logging.getLogger("realtime.telegram")


class TelegramPush:
    def __init__(self, token: str = "", chat_id: str = "", min_interval: float = 45.0) -> None:
        self.token = (token or "").strip()
        self.chat_id = (chat_id or "").strip()
        self.min_interval = min_interval
        self._last: Dict[str, float] = {}
        self.enabled = bool(self.token and self.chat_id)
        if not self.enabled:
            log.info("Telegram kapali — TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID yok")

    def _dedup_key(self, a: Anomaly) -> str:
        return f"{a.kind}:{a.symbol}:{a.severity}"

    async def send(self, anomaly: Anomaly) -> bool:
        key = self._dedup_key(anomaly)
        now = time.time()
        if now - self._last.get(key, 0) < self.min_interval:
            return False
        text = (
            f"*{anomaly.title}*\n"
            f"`{anomaly.symbol}`  {anomaly.severity.upper()}  score={anomaly.score:.2f}\n"
            f"{anomaly.body}"
        )
        log.info("ALERT %s | %s | %s", anomaly.kind, anomaly.symbol, anomaly.title)
        if not self.enabled:
            return True
        url = f"https://api.telegram.org/bot{self.token}/sendMessage"
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "Markdown",
            "disable_web_page_preview": True,
        }
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(url, json=payload, timeout=aiohttp.ClientTimeout(total=12)) as resp:
                    if resp.status >= 300:
                        body = await resp.text()
                        log.warning("Telegram HTTP %s: %s", resp.status, body[:200])
                        return False
            self._last[key] = now
            return True
        except Exception as exc:
            log.warning("Telegram hata: %s", exc)
            return False
