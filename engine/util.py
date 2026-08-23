"""Safe accessors and formatters (from v4.3 engine)."""
from __future__ import annotations

from typing import Any, List, Optional


def safe_path(d: Any, *keys, default=None, warnings: Optional[List[str]] = None):
    cur = d
    context = str(keys[0]) if keys else "?"
    for k in keys:
        if cur is None:
            if warnings is not None and len(warnings) < 50:
                warnings.append(f"{context}: eksik alan '{k}'")
            return default
        if isinstance(cur, list):
            if not isinstance(k, int) or k >= len(cur) or k < -len(cur):
                if warnings is not None and len(warnings) < 50:
                    warnings.append(f"{context}: liste index '{k}' gecersiz")
                return default
            cur = cur[k]
        elif isinstance(cur, dict):
            cur = cur.get(k)
        else:
            if warnings is not None and len(warnings) < 50:
                warnings.append(f"{context}: beklenmeyen tur '{type(cur).__name__}'")
            return default
    return cur if cur is not None else default


def to_float(v, default: float = 0.0) -> float:
    if v is None:
        return default
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def fmt_price(p) -> str:
    if p is None:
        return "$0"
    p = to_float(p)
    if p == 0:
        return "$0"
    if p >= 1000:
        return f"${p:,.2f}"
    if p >= 1:
        return f"${p:.4f}"
    if p >= 0.01:
        return f"${p:.6f}"
    if p >= 0.0001:
        return f"${p:.8f}"
    return f"${p:.10f}"
