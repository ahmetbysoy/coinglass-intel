"""Start/stop mitmdump and tail its JSONL sink."""
from __future__ import annotations

import asyncio
import json
import logging
import os
import signal
from pathlib import Path
from typing import Any, Dict, List, Optional

from config import BASE_DIR, CFG

log = logging.getLogger("collector.mitm")


class MitmBridge:
    def __init__(self) -> None:
        self.cfg = CFG.mitm
        self.sink = Path(self.cfg.sink_file)
        self.confdir = Path(self.cfg.confdir)
        self.confdir.mkdir(parents=True, exist_ok=True)
        self.sink.parent.mkdir(parents=True, exist_ok=True)
        self._proc: Optional[asyncio.subprocess.Process] = None
        self._offset = 0

    @property
    def proxy_url(self) -> str:
        return f"http://{self.cfg.host}:{self.cfg.port}"

    async def start(self) -> None:
        if not self.cfg.enabled:
            log.info("mitmproxy kapali")
            return
        if self.sink.exists():
            self.sink.write_text("", encoding="utf-8")
        addon = str(BASE_DIR / "collector" / "mitm_addon.py")
        env = os.environ.copy()
        env["CG_MITM_SINK"] = str(self.sink)
        cmd = [
            env.get("VIRTUAL_ENV", "") and f"{env['VIRTUAL_ENV']}/bin/mitmdump" or "mitmdump",
            "-s",
            addon,
            "--listen-host",
            self.cfg.host,
            "--listen-port",
            str(self.cfg.port),
            "--set",
            f"confdir={self.confdir}",
            "--ssl-insecure",
            "--quiet",
        ]
        # prefer venv mitmdump
        venv_bin = BASE_DIR / ".venv" / "bin" / "mitmdump"
        if venv_bin.exists():
            cmd[0] = str(venv_bin)
        log.info("mitmdump basliyor %s:%s", self.cfg.host, self.cfg.port)
        self._proc = await asyncio.create_subprocess_exec(
            *cmd,
            env=env,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )
        for _ in range(40):
            if await self._port_up():
                log.info("mitmproxy hazir")
                return
            if self._proc.returncode is not None:
                err = b""
                if self._proc.stderr:
                    err = await self._proc.stderr.read()
                raise RuntimeError(f"mitmdump cikti: {err[-400:]!r}")
            await asyncio.sleep(0.15)
        raise RuntimeError("mitmproxy dinlemeye baslamadi")

    async def _port_up(self) -> bool:
        try:
            reader, writer = await asyncio.open_connection(self.cfg.host, self.cfg.port)
            writer.close()
            await writer.wait_closed()
            return True
        except Exception:
            return False

    async def stop(self) -> None:
        if not self._proc:
            return
        try:
            self._proc.send_signal(signal.SIGTERM)
            try:
                await asyncio.wait_for(self._proc.wait(), timeout=5)
            except asyncio.TimeoutError:
                self._proc.kill()
                await self._proc.wait()
        except Exception as exc:
            log.debug("mitm stop: %s", exc)
        self._proc = None
        log.info("mitmproxy durdu")

    def drain(self) -> List[Dict[str, Any]]:
        if not self.sink.exists():
            return []
        recs: List[Dict[str, Any]] = []
        try:
            with self.sink.open("r", encoding="utf-8") as fh:
                fh.seek(self._offset)
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        recs.append(json.loads(line))
                    except json.JSONDecodeError:
                        continue
                self._offset = fh.tell()
        except Exception as exc:
            log.debug("drain: %s", exc)
        return recs

    def all_records(self) -> List[Dict[str, Any]]:
        if not self.sink.exists():
            return []
        out = []
        for line in self.sink.read_text(encoding="utf-8").splitlines():
            try:
                out.append(json.loads(line))
            except json.JSONDecodeError:
                continue
        return out
