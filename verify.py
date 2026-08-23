#!/usr/bin/env python3
"""Kurulum dogrulama."""
import sys, importlib
mods = ["playwright","websockets","aiohttp","numpy","scipy","pandas","rich","cryptography","aiosqlite","orjson"]
print("=== BAGIMLILIK DOGRULAMA ===")
ok = True
for m in mods:
    try:
        importlib.import_module(m)
        print(f"  OK {m}")
    except ImportError:
        print(f"  EKS {m}")
        ok = False
if ok: print("\\nTum bagimliliklar mevcut.")
else: print("\\nEksik var. pip install -r requirements.txt"); sys.exit(1)
print("\\n=== PROJE MODULLERI ===")
pm = ["config","collector.browser_session","collector.network_interceptor","collector.endpoint_discovery",
      "registry.endpoint_registry","registry.schema_analyzer","pipeline.data_normalizer",
      "pipeline.data_store","pipeline.websocket_listener","engine.oi_analyzer","engine.funding_analyzer",
      "engine.liquidation_analyzer","engine.orderbook_analyzer","engine.volume_profile",
      "engine.whale_tracker","engine.prediction_engine",
      "realtime.models","realtime.cache","realtime.wss_client","realtime.token_hunter",
      "realtime.signal_loop","realtime.telegram_push","realtime.rest_pump",
      "collector.mitm_bridge","collector.session_vault","collector.traffic_merger"]
for m in pm:
    try:
        importlib.import_module(m)
        print(f"  OK {m}")
    except Exception as e:
        print(f"  HATA {m} - {e}")
print("\\nDogrulama tamamlandi.")
