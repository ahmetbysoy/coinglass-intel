"""Persistent Chromium oturumu. Istege bagli mitmproxy uzerinden cikar."""
import asyncio
import logging
from typing import Optional, List, Dict, Any

from playwright.async_api import async_playwright, Playwright, BrowserContext, Page, Route

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG, COINGLASS_BASE, build_symbol_url, COINGLASS_PAGES

log = logging.getLogger("collector.browser")

DECRYPT_HOOK = r"""
(() => {
  window.__CG_PLAIN = window.__CG_PLAIN || [];
  const orig = JSON.parse;
  JSON.parse = function(text, reviver) {
    const val = orig.call(this, text, reviver);
    try {
      if (val && typeof val === "object") {
        const s = JSON.stringify(val);
        if (s.length > 40 && s.length < 1500000 &&
            /openInterest|fundingRate|liquidation|volUsd|oiUsd|symbol|price|oi/i.test(s)) {
          window.__CG_PLAIN.push({t: Date.now(), payload: val});
          if (window.__CG_PLAIN.length > 120) window.__CG_PLAIN.shift();
        }
      }
    } catch (e) {}
    return val;
  };
})();
"""


class BrowserSession:
    def __init__(self, proxy_url: Optional[str] = None):
        self._pw: Optional[Playwright] = None
        self._context: Optional[BrowserContext] = None
        self._page: Optional[Page] = None
        self._alive = False
        self._cfg = CFG.browser
        self.proxy_url = proxy_url

    async def launch(self):
        if self._alive:
            return self
        self._pw = await async_playwright().start()
        kwargs: Dict[str, Any] = dict(
            user_data_dir=self._cfg.persistent_profile_path,
            headless=self._cfg.headless,
            slow_mo=self._cfg.slow_mo,
            viewport={"width": self._cfg.viewport_width, "height": self._cfg.viewport_height},
            user_agent=self._cfg.user_agent,
            locale=self._cfg.locale,
            timezone_id=self._cfg.timezone,
            ignore_https_errors=True,
            bypass_csp=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--disable-features=IsolateOrigins,site-per-process",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-infobars",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-software-rasterizer",
                "--ignore-certificate-errors",
                f"--window-size={self._cfg.viewport_width},{self._cfg.viewport_height}",
            ],
            extra_http_headers=self._cfg.extra_headers,
        )
        if self.proxy_url:
            kwargs["proxy"] = {"server": self.proxy_url}
            log.info("Browser proxy: %s", self.proxy_url)
        self._context = await self._pw.chromium.launch_persistent_context(**kwargs)
        await self._context.add_init_script(DECRYPT_HOOK)
        if self._cfg.block_resources:
            await self._context.route("**/*", self._blk)
        self._page = self._context.pages[0] if self._context.pages else await self._context.new_page()
        self._page.set_default_navigation_timeout(self._cfg.navigation_timeout_ms)
        self._page.set_default_timeout(self._cfg.default_timeout_ms)
        self._alive = True
        log.info("Browser acildi")
        return self

    async def _blk(self, route: Route):
        if route.request.resource_type in self._cfg.block_resources:
            await route.abort()
        else:
            await route.continue_()

    async def restore_cookies(self, cookies: List[Dict[str, Any]]) -> int:
        if not cookies or not self._context:
            return 0
        clean = []
        for c in cookies:
            item = {k: c[k] for k in ("name", "value", "domain", "path") if k in c and c[k] is not None}
            if "name" in item and "value" in item:
                clean.append(item)
        if not clean:
            return 0
        try:
            await self._context.add_cookies(clean)
            log.info("Vault'tan %d cookie yuklendi", len(clean))
            return len(clean)
        except Exception as exc:
            log.warning("Cookie restore: %s", exc)
            return 0

    async def dump_plain(self) -> List[Dict[str, Any]]:
        try:
            return await asyncio.wait_for(
                self.page.evaluate("() => (window.__CG_PLAIN || []).splice(0)"),
                timeout=5,
            )
        except Exception:
            return []

    async def shutdown(self):
        if not self._alive:
            return
        try:
            if self._page:
                await self._page.close()
            if self._context:
                await self._context.close()
            if self._pw:
                await self._pw.stop()
        except Exception as e:
            log.error("Kapanis: %s", e)
        finally:
            self._alive = False

    @property
    def page(self):
        if not self._page or not self._alive:
            raise RuntimeError("Browser acik degil.")
        return self._page

    @property
    def context(self):
        if not self._context:
            raise RuntimeError("Context yok.")
        return self._context

    @property
    def alive(self):
        return self._alive

    async def goto_base(self):
        await self.page.goto(COINGLASS_BASE, wait_until="domcontentloaded")
        await self._w()

    async def goto_page(self, pk, symbol=None):
        if pk not in COINGLASS_PAGES:
            raise ValueError(f"Bilinmeyen: {pk}")
        bp = COINGLASS_PAGES[pk]
        url = build_symbol_url(bp, symbol) if symbol else f"{COINGLASS_BASE}{bp}"
        log.info("Nav -> %s", url)
        await self.page.goto(url, wait_until="domcontentloaded")
        await self._w()

    async def goto_url(self, url):
        await self.page.goto(url, wait_until="domcontentloaded")
        await self._w()

    async def _w(self, t=15000):
        try:
            await self.page.wait_for_load_state("networkidle", timeout=t)
        except Exception:
            pass
        await asyncio.sleep(1.5)

    async def get_cookies(self, domain="coinglass.com"):
        return [c for c in await self.context.cookies() if domain in c.get("domain", "")]

    async def has_active_session(self):
        return any(
            c["name"] in ("session", "token", "auth", "connect.sid", "sid", "cg_session")
            for c in await self.get_cookies()
        )

    async def screenshot(self, path="debug.png"):
        await self.page.screenshot(path=path)
        return path

    async def new_tab(self, url=None):
        p = await self.context.new_page()
        if url:
            await p.goto(url, wait_until="domcontentloaded")
        return p

    async def __aenter__(self):
        return await self.launch()

    async def __aexit__(self, *a):
        await self.shutdown()
