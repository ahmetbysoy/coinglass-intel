"""Canli trafikten otomatik API endpoint kesfi."""
import hashlib, logging, time, orjson
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any, Set
from urllib.parse import urlparse
from collections import defaultdict
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
from collector.network_interceptor import CapturedResponse, CapturedWsFrame
log = logging.getLogger("collector.discovery")
@dataclass
class DiscoveredEndpoint:
    path: str; method: str; full_url: str; domain: str; category: str="unknown"; hit_count: int=0
    first_seen: float=field(default_factory=time.time); last_seen: float=field(default_factory=time.time)
    sample_params: Dict[str,List[str]]=field(default_factory=dict); sample_headers: Dict[str,str]=field(default_factory=dict)
    response_content_types: Set[str]=field(default_factory=set); response_status_codes: Set[int]=field(default_factory=set)
    sample_bodies: List[bytes]=field(default_factory=list); source_page: str=""
    @property
    def uid(self): return hashlib.sha256(f"{self.method}|{self.path}".encode()).hexdigest()[:16]
    def add_sample(self, resp, page_url):
        self.hit_count+=1; self.last_seen=time.time(); self.sample_params.update(resp.request.query_params)
        self.response_content_types.add(resp.content_type); self.response_status_codes.add(resp.status); self.source_page=page_url
        if len(self.sample_headers)<10:
            for k,v in resp.request.headers.items():
                if k.lower() in ("authorization","cookie","x-token","x-api-key","content-type","accept","referer","origin"): self.sample_headers[k]=v
        if resp.body and len(self.sample_bodies)<5: self.sample_bodies.append(resp.body)
@dataclass
class DiscoveredWs:
    url: str; domain: str; frame_count: int=0; first_seen: float=field(default_factory=time.time)
    sample_frames: List[str]=field(default_factory=list); channel_patterns: Set[str]=field(default_factory=set)
    @property
    def uid(self): return hashlib.sha256(self.url.encode()).hexdigest()[:16]
    def add_frame(self, f):
        self.frame_count+=1
        if len(self.sample_frames)<20: self.sample_frames.append(f.payload[:2000])
        try:
            obj=orjson.loads(f.payload)
            for key in ("channel","ch","topic","sub","action","event"):
                if key in obj: self.channel_patterns.add(str(obj[key])); break
        except Exception: pass
class EndpointDiscovery:
    def __init__(self): self._cfg=CFG.registry; self.endpoints: Dict[str,DiscoveredEndpoint]={}; self.ws_endpoints: Dict[str,DiscoveredWs]={}; self._log: List[Dict]=[]
    def bind(self, interceptor): interceptor.on_response(self._on_resp); interceptor.on_ws_frame(self._on_ws)
    def _on_resp(self, resp):
        if not resp.is_json or resp.status not in (200,201,202,204): return
        parsed=urlparse(resp.request.url); path=parsed.path; method=resp.request.method
        if any(x in path.lower() for x in ("/static/","/assets/","/_next/",".js",".css","/favicon")): return
        uid=hashlib.sha256(f"{method}|{path}".encode()).hexdigest()[:16]
        if uid in self.endpoints: self.endpoints[uid].add_sample(resp, resp.request.page_url)
        else:
            ep=DiscoveredEndpoint(path=path,method=method,full_url=resp.request.url,domain=parsed.netloc,source_page=resp.request.page_url)
            ep.add_sample(resp, resp.request.page_url); ep.category=self._cat(path); self.endpoints[uid]=ep
            self._log.append({"time":time.time(),"type":"new_endpoint","path":path,"method":method,"category":ep.category}); log.info(">>> YENI: %s %s [%s]",method,path,ep.category)
    def _on_ws(self, frame):
        parsed=urlparse(frame.ws_url); uid=hashlib.sha256(frame.ws_url.encode()).hexdigest()[:16]
        if uid in self.ws_endpoints: self.ws_endpoints[uid].add_frame(frame)
        else: ws=DiscoveredWs(url=frame.ws_url,domain=parsed.netloc); ws.add_frame(frame); self.ws_endpoints[uid]=ws; log.info(">>> WS: %s",frame.ws_url)
    def _cat(self, path):
        if not self._cfg.auto_categorize: return "unknown"
        pl=path.lower().replace("-","").replace("_","")
        for cat,kws in self._cfg.category_keywords.items():
            for kw in kws:
                if kw.replace("-","").replace("_","") in pl: return cat
        return "unknown"
    def summary(self):
        cats: Dict[str,int]=defaultdict(int)
        for ep in self.endpoints.values(): cats[ep.category]+=1
        return {"total_rest":len(self.endpoints),"total_ws":len(self.ws_endpoints),"categories":dict(cats)}
    def get_by_category(self,cat): return [ep for ep in self.endpoints.values() if ep.category==cat]
    def export_catalog(self):
        eps=[{"uid":e.uid,"path":e.path,"method":e.method,"domain":e.domain,"category":e.category,"hit_count":e.hit_count,"first_seen":e.first_seen,"last_seen":e.last_seen,"params":{k:v for k,v in e.sample_params.items()},"status_codes":list(e.response_status_codes),"content_types":list(e.response_content_types),"source_page":e.source_page,"sample_body_count":len(e.sample_bodies)} for e in self.endpoints.values()]
        wss=[{"uid":w.uid,"url":w.url,"domain":w.domain,"frame_count":w.frame_count,"channels":list(w.channel_patterns)} for w in self.ws_endpoints.values()]
        return {"exported_at":time.time(),"summary":self.summary(),"rest_endpoints":eps,"ws_endpoints":wss}
    def print_catalog(self):
        lines=["+"+"="*58+"+","| COINGLASS ENDPOINT KATALOGU".ljust(58)+" |","+"+"="*58+"+"]
        cats: Dict[str,List]=defaultdict(list)
        for ep in self.endpoints.values(): cats[ep.category].append(ep)
        for cat in sorted(cats):
            eps=cats[cat]; lines.append(f"|  [{cat.upper()}] - {len(eps)} endpoint")
            for ep in sorted(eps,key=lambda e: e.hit_count,reverse=True): lines.append(f"|    {ep.method:6s} {ep.path[:50]:50s} x{ep.hit_count}")
        lines.append("+"+"-"*58+"+")
        for ws in self.ws_endpoints.values(): lines.append(f"|  WS: {ws.url[:55]}"); lines.append(f"|      frames={ws.frame_count} ch={ws.channel_patterns}")
        lines.append("+"+"="*58+"+"); return chr(10).join(lines)
