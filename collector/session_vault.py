"""Encrypted cookie/session vault. Never write tokens as plaintext."""
from __future__ import annotations

import json
import logging
import os
import stat
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

from cryptography.fernet import Fernet, InvalidToken

from config import CFG

log = logging.getLogger("collector.vault")

SENSITIVE = ("cookie", "token", "auth", "session", "authorization", "set-cookie")


class SessionVault:
    def __init__(self, vault_file: Optional[str] = None, key_file: Optional[str] = None) -> None:
        self.vault_path = Path(vault_file or CFG.mitm.vault_file)
        self.key_path = Path(key_file or CFG.mitm.vault_key_file)
        self.vault_path.parent.mkdir(parents=True, exist_ok=True)
        self._fernet = Fernet(self._load_or_create_key())

    def _load_or_create_key(self) -> bytes:
        env = os.environ.get("CG_VAULT_KEY", "").strip()
        if env:
            return env.encode() if env.startswith("gAAAA") is False and len(env) >= 40 else env.encode()
        if self.key_path.exists():
            return self.key_path.read_bytes().strip()
        key = Fernet.generate_key()
        self.key_path.write_bytes(key)
        try:
            os.chmod(self.key_path, stat.S_IRUSR | stat.S_IWUSR)
        except OSError:
            pass
        log.info("Yeni vault anahtari olusturuldu: %s", self.key_path.name)
        return key

    def save(self, cookies: List[Dict[str, Any]], extra: Optional[Dict[str, Any]] = None) -> None:
        payload = {
            "saved_at": time.time(),
            "cookies": cookies or [],
            "extra": extra or {},
        }
        blob = self._fernet.encrypt(json.dumps(payload).encode("utf-8"))
        self.vault_path.write_bytes(blob)
        try:
            os.chmod(self.vault_path, stat.S_IRUSR | stat.S_IWUSR)
        except OSError:
            pass
        log.info("Oturum kasaya yazildi (%d cookie, plaintext yok)", len(cookies or []))

    def load(self) -> Dict[str, Any]:
        if not self.vault_path.exists():
            return {"cookies": [], "extra": {}, "saved_at": 0}
        try:
            raw = self._fernet.decrypt(self.vault_path.read_bytes())
            data = json.loads(raw.decode("utf-8"))
            return {
                "cookies": data.get("cookies") or [],
                "extra": data.get("extra") or {},
                "saved_at": data.get("saved_at") or 0,
            }
        except InvalidToken:
            log.error("Vault anahtari uyusmuyor — oturum okunamadi")
            return {"cookies": [], "extra": {}, "saved_at": 0}
        except Exception as exc:
            log.warning("Vault okunamadi: %s", exc)
            return {"cookies": [], "extra": {}, "saved_at": 0}

    @staticmethod
    def redact(headers: Dict[str, str]) -> Dict[str, str]:
        out = {}
        for k, v in (headers or {}).items():
            lk = k.lower()
            if any(s in lk for s in SENSITIVE):
                out[k] = "<redacted>"
            else:
                out[k] = v
        return out
