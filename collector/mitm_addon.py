"""mitmdump addon — ikinci goz. Playwright'in kacirdigi HTTP/WS'i JSONL'e yazar.

    mitmdump -s collector/mitm_addon.py --listen-host 127.0.0.1 --listen-port 18080
"""
from __future__ import annotations

import json
import os
import time
from pathlib import Path
from urllib.parse import urlparse

from mitmproxy import http

SINK = Path(os.environ.get("CG_MITM_SINK", "data/mitm/traffic.jsonl"))
WHITELIST = (
    "coinglass.com",
    "capi.coinglass.com",
    "open-api-v4.coinglass.com",
    "open-ws.coinglass.com",
    "wss.coinglass.com",
)
SKIP_EXT = (".js", ".css", ".png", ".jpg", ".svg", ".woff", ".woff2", ".ico", ".map")
MAX_BODY = 200_000


def _rel(url: str) -> bool:
    host = (urlparse(url).netloc or "").lower()
    return any(w in host for w in WHITELIST)


def _write(rec: dict) -> None:
    try:
        SINK.parent.mkdir(parents=True, exist_ok=True)
        with SINK.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(rec, ensure_ascii=False, default=str) + "\n")
    except Exception:
        pass


def _clip(raw: bytes | None) -> str:
    if not raw:
        return ""
    return raw[:MAX_BODY].decode("utf-8", "replace")


class CoinGlassTap:
    def request(self, flow: http.HTTPFlow) -> None:
        url = flow.request.pretty_url
        if not _rel(url):
            return
        path = urlparse(url).path.lower()
        if any(path.endswith(ext) for ext in SKIP_EXT):
            return
        _write(
            {
                "ts": time.time(),
                "eye": "mitm",
                "kind": "request",
                "method": flow.request.method,
                "url": url,
                "path": urlparse(url).path,
                "host": urlparse(url).netloc,
                "headers": {k: ("<redacted>" if k.lower() in {"cookie", "authorization"} else v) for k, v in flow.request.headers.items()},
                "has_cookie": "cookie" in {k.lower() for k in flow.request.headers.keys()},
            }
        )

    def response(self, flow: http.HTTPFlow) -> None:
        url = flow.request.pretty_url
        if not _rel(url):
            return
        path = urlparse(url).path.lower()
        if any(path.endswith(ext) for ext in SKIP_EXT):
            return
        ct = flow.response.headers.get("content-type", "") if flow.response else ""
        body = _clip(flow.response.content if flow.response else None)
        _write(
            {
                "ts": time.time(),
                "eye": "mitm",
                "kind": "response",
                "method": flow.request.method,
                "url": url,
                "path": urlparse(url).path,
                "host": urlparse(url).netloc,
                "status": flow.response.status_code if flow.response else 0,
                "content_type": ct,
                "resp_headers": {
                    k: v
                    for k, v in (flow.response.headers.items() if flow.response else [])
                    if k.lower() in {"content-type", "v", "ev", "encryption", "cache-control", "user"}
                },
                "body": body if ("json" in ct.lower() or body[:1] in "{[") else "",
                "body_len": len(flow.response.content or b"") if flow.response else 0,
            }
        )

    def websocket_message(self, flow: http.HTTPFlow) -> None:
        url = flow.request.pretty_url
        if not _rel(url):
            return
        msg = flow.websocket.messages[-1] if flow.websocket and flow.websocket.messages else None
        if msg is None:
            return
        content = msg.content
        preview = ""
        if isinstance(content, bytes):
            if content[:2] == b"\x1f\x8b":
                preview = "<gzip>"
            else:
                preview = content[:400].decode("utf-8", "replace")
        else:
            preview = str(content)[:400]
        _write(
            {
                "ts": time.time(),
                "eye": "mitm",
                "kind": "websocket",
                "url": url,
                "from_client": bool(getattr(msg, "from_client", False)),
                "preview": preview,
            }
        )


addons = [CoinGlassTap()]
