"""Endpoint Registry - kalici katalog."""
import json, logging, time
from pathlib import Path
from typing import Dict, List, Any, Optional
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("registry")
class EndpointRegistry:
    def __init__(self):
        self._path=Path(CFG.registry.cache_file); self._cat: Dict[str,Any]={"version":3,"updated_at":0.0,"rest_endpoints":{},"ws_endpoints":{}}; self._load()
    def _load(self):
        if self._path.exists():
            try:
                d=json.loads(self._path.read_text(encoding="utf-8"))
                if d.get("version")==3: self._cat=d; log.info("Registry: %d REST + %d WS",len(self._cat.get("rest_endpoints",{})),len(self._cat.get("ws_endpoints",{})))
            except Exception as e: log.error("Registry: %s",e)
    def save(self):
        self._cat["updated_at"]=time.time(); self._path.parent.mkdir(parents=True,exist_ok=True)
        self._path.write_text(json.dumps(self._cat,indent=2,ensure_ascii=False),encoding="utf-8")
    def update_from_discovery(self, export):
        updated=0
        for ep in export.get("rest_endpoints",[]):
            uid=ep["uid"]; ex=self._cat["rest_endpoints"].get(uid)
            if ex:
                ex["hit_count"]=max(ex.get("hit_count",0),ep.get("hit_count",0)); ex["last_seen"]=ep.get("last_seen",time.time())
                ex["params"]={**ex.get("params",{}),**ep.get("params",{})}
                if ep.get("category") and ep["category"]!="unknown": ex["category"]=ep["category"]
            else: self._cat["rest_endpoints"][uid]=ep; updated+=1
        for ws in export.get("ws_endpoints",[]):
            uid=ws["uid"]; ex=self._cat["ws_endpoints"].get(uid)
            if ex: ex["frame_count"]=max(ex.get("frame_count",0),ws.get("frame_count",0)); ex["channels"]=list(set(ex.get("channels",[]))|set(ws.get("channels",[])))
            else: self._cat["ws_endpoints"][uid]=ws; updated+=1
        if updated: self.save()
        return updated
    def get_known(self,category=None):
        eps=list(self._cat["rest_endpoints"].values())
        if category: eps=[e for e in eps if e.get("category")==category]
        return sorted(eps,key=lambda e: e.get("hit_count",0),reverse=True)
    def get_ws(self): return list(self._cat["ws_endpoints"].values())
    def has_category(self,cat): return any(e.get("category")==cat for e in self._cat["rest_endpoints"].values())
    def clear(self): self._cat={"version":3,"updated_at":0.0,"rest_endpoints":{},"ws_endpoints":{}}; self.save()
