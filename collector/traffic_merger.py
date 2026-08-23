"""Merge Playwright interceptor + mitmproxy + in-page decrypt hook."""
from __future__ import annotations

import hashlib
import logging
from typing import Any, Dict, Iterable, List
from urllib.parse import urlparse

from registry.endpoint_registry import EndpointRegistry
from collector.endpoint_discovery import EndpointDiscovery

log = logging.getLogger("collector.merge")


class TrafficMerger:
    def __init__(self, discovery: EndpointDiscovery, registry: EndpointRegistry) -> None:
        self.discovery = discovery
        self.registry = registry

    def ingest_mitm(self, records: Iterable[Dict[str, Any]]) -> int:
        added = 0
        catalog = {"rest_endpoints": [], "ws_endpoints": []}
        seen = set()
        for rec in records:
            kind = rec.get("kind")
            if kind == "response":
                path = rec.get("path") or urlparse(rec.get("url") or "").path
                method = rec.get("method") or "GET"
                uid = hashlib.sha256(f"{method}|{path}".encode()).hexdigest()[:16]
                if uid in seen:
                    continue
                seen.add(uid)
                catalog["rest_endpoints"].append(
                    {
                        "uid": uid,
                        "path": path,
                        "method": method,
                        "domain": rec.get("host") or "",
                        "category": self.discovery._cat(path),
                        "hit_count": 1,
                        "first_seen": rec.get("ts"),
                        "last_seen": rec.get("ts"),
                        "params": {},
                        "status_codes": [rec.get("status") or 0],
                        "content_types": [rec.get("content_type") or ""],
                        "source_page": "mitm",
                        "sample_body_count": 1 if rec.get("body") else 0,
                    }
                )
                added += 1
            elif kind == "websocket":
                url = rec.get("url") or ""
                if not url:
                    continue
                uid = hashlib.sha256(url.encode()).hexdigest()[:16]
                catalog["ws_endpoints"].append(
                    {
                        "uid": uid,
                        "url": url,
                        "domain": urlparse(url).netloc,
                        "frame_count": 1,
                        "channels": [],
                    }
                )
                added += 1
        if catalog["rest_endpoints"] or catalog["ws_endpoints"]:
            self.registry.update_from_discovery(catalog)
        return added

    def stats(self, records: List[Dict[str, Any]]) -> Dict[str, int]:
        out = {"request": 0, "response": 0, "websocket": 0}
        for rec in records:
            k = rec.get("kind")
            if k in out:
                out[k] += 1
        return out
