"""Playwright one-shot: harvest WSS handshake, cookies, decrypted snapshots."""
from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

from config import CFG, SESSION_DIR
from .models import SessionBundle

log = logging.getLogger("realtime.hunter")

HOOK_JS = r"""
(() => {
  window.__CG_EVENTS = [];
  window.__CG_WS = [];
  const origParse = JSON.parse;
  JSON.parse = function(text, reviver) {
    const val = origParse.call(this, text, reviver);
    try {
      if (val && typeof val === "object") {
        const s = JSON.stringify(val);
        if (s.length > 40 && s.length < 1500000 &&
            /openInterest|fundingRate|liquidation|volUsd|oiUsd|symbol|price/i.test(s)) {
          window.__CG_EVENTS.push({t: Date.now(), payload: val});
          if (window.__CG_EVENTS.length > 80) window.__CG_EVENTS.shift();
        }
      }
    } catch (e) {}
    return val;
  };
  const WS = window.WebSocket;
  window.WebSocket = function(url, protocols) {
    try { window.__CG_WS.push({t: Date.now(), url: String(url)}); } catch (e) {}
    return protocols === undefined ? new WS(url) : new WS(url, protocols);
  };
  window.WebSocket.prototype = WS.prototype;
  window.WebSocket.OPEN = WS.OPEN;
  window.WebSocket.CLOSED = WS.CLOSED;
  window.WebSocket.CONNECTING = WS.CONNECTING;
  window.WebSocket.CLOSING = WS.CLOSING;
})();
"""

HUNT_PAGES = [
    "https://www.coinglass.com/",
    "https://www.coinglass.com/liquidations",
    "https://www.coinglass.com/pro/futures/LiquidationHeatMap",
]


class TokenHunter:
    def __init__(self, session_path: Optional[str] = None) -> None:
        self.path = Path(session_path or CFG.daemon.session_file)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def load(self) -> Optional[SessionBundle]:
        if not self.path.exists():
            return None
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            return SessionBundle.from_dict(data)
        except Exception as exc:
            log.warning("Session okunamadi: %s", exc)
            return None

    def save(self, bundle: SessionBundle) -> None:
        self.path.write_text(json.dumps(bundle.to_dict(), indent=2, default=str), encoding="utf-8")
        log.info("Session yazildi: %s", self.path)

    async def hunt(self, headless: bool = True) -> SessionBundle:
        from playwright.async_api import async_playwright

        notes: List[str] = []
        ws_seen: List[Dict[str, Any]] = []
        cookies: List[Dict[str, Any]] = []
        snapshot_n = 0
        api_key = CFG.daemon.api_key

        cfg = CFG.browser
        async with async_playwright() as pw:
            context = await pw.chromium.launch_persistent_context(
                user_data_dir=cfg.persistent_profile_path,
                headless=headless,
                viewport={"width": cfg.viewport_width, "height": cfg.viewport_height},
                user_agent=cfg.user_agent,
                locale=cfg.locale,
                timezone_id=cfg.timezone,
                ignore_https_errors=True,
                args=[
                    "--disable-blink-features=AutomationControlled",
                    "--no-first-run",
                    "--no-sandbox",
                    "--disable-setuid-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                ],
                extra_http_headers=cfg.extra_headers,
            )
            page = context.pages[0] if context.pages else await context.new_page()
            await page.add_init_script(HOOK_JS)

            def on_ws(ws) -> None:
                rec = {"url": ws.url, "ts": time.time()}
                ws_seen.append(rec)
                log.info("WSS yakalandi: %s", ws.url)

            page.on("websocket", on_ws)

            for url in HUNT_PAGES:
                try:
                    log.info("Hunt -> %s", url)
                    await page.goto(url, wait_until="domcontentloaded", timeout=cfg.navigation_timeout_ms)
                    await page.wait_for_timeout(4500)
                except Exception as exc:
                    notes.append(f"nav fail {url}: {exc}")
                    log.warning("Hunt nav: %s", exc)

            try:
                dumped = await page.evaluate(
                    """() => ({
                        events: (window.__CG_EVENTS || []).length,
                        ws: window.__CG_WS || [],
                        href: location.href,
                        title: document.title
                    })"""
                )
                snapshot_n = int(dumped.get("events") or 0)
                for item in dumped.get("ws") or []:
                    if item not in ws_seen:
                        ws_seen.append(item)
                notes.append(f"title={dumped.get('title')} events={snapshot_n}")
            except Exception as exc:
                notes.append(f"evaluate: {exc}")

            cookies = await context.cookies()
            await context.close()

        wss_url = ""
        for rec in ws_seen:
            url = rec.get("url") or ""
            if url.startswith("ws"):
                wss_url = url
                break

        if api_key and not wss_url:
            wss_url = f"{CFG.websocket.official_url}?cg-api-key={api_key}"
            notes.append("official WSS url built from COINGLASS_API_KEY")

        if not wss_url:
            notes.append("sitede public WSS yok — official API key gerekli")

        bundle = SessionBundle(
            acquired_at=time.time(),
            api_key=api_key,
            wss_url=wss_url,
            wss_headers={
                "Origin": "https://www.coinglass.com",
                "User-Agent": CFG.browser.user_agent,
            },
            cookies=[{k: c.get(k) for k in ("name", "value", "domain", "path")} for c in cookies],
            subscribe=[],
            notes=notes,
            snapshot_events=snapshot_n,
        )
        self.save(bundle)
        snap_path = SESSION_DIR / "last_hunt.json"
        snap_path.write_text(json.dumps({"ws": ws_seen, "notes": notes}, indent=2, default=str), encoding="utf-8")
        return bundle
