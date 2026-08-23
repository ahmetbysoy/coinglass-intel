"""Playwright event sistemiyle TUM network trafigini yakalar."""
import asyncio, hashlib, logging, time, re
from dataclasses import dataclass
from typing import Optional, Callable, List, Dict, Any, Set
from collections import deque
from urllib.parse import urlparse, parse_qs
from playwright.async_api import Page, Request, Response, WebSocket as PwWebSocket
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("collector.interceptor")
@dataclass
class CapturedRequest:
    url: str; method: str; headers: Dict[str,str]; post_data: Optional[str]; resource_type: str; timestamp: float; request_id: str; page_url: str
    @property
    def domain(self): return urlparse(self.url).netloc
    @property
    def path(self): return urlparse(self.url).path
    @property
    def query_params(self): return parse_qs(urlparse(self.url).query)
    @property
    def uid(self): return hashlib.md5(f"{self.method}|{self.url}|{self.timestamp:.3f}".encode()).hexdigest()[:16]
@dataclass
class CapturedResponse:
    request: CapturedRequest; status: int; status_text: str; headers: Dict[str,str]; body: Optional[bytes]; timestamp: float
    @property
    def content_type(self): return self.headers.get("content-type","")
    @property
    def is_json(self): ct=self.content_type.lower(); return "json" in ct or "javascript" in ct
    @property
    def body_size(self): return len(self.body) if self.body else 0
    def body_text(self):
        if self.body:
            try: return self.body.decode("utf-8", errors="replace")
            except Exception: return None
        return None
@dataclass
class CapturedWsFrame:
    ws_url: str; direction: str; payload: str; timestamp: float; frame_index: int
    @property
    def is_text(self): return isinstance(self.payload, str)
class NetworkInterceptor:
    def __init__(self, page: Page):
        self._page=page; self._cfg=CFG.collector; self._active=False
        self.requests: deque=deque(maxlen=10_000); self.responses: deque=deque(maxlen=10_000)
        self.ws_frames: deque=deque(maxlen=20_000); self.ws_connections: Dict[str,List[CapturedWsFrame]]={}
        self._seen: Set[str]=set(); self._on_req: List[Callable]=[]; self._on_resp: List[Callable]=[]; self._on_ws: List[Callable]=[]
        self.stats={"requests_total":0,"responses_total":0,"responses_json":0,"ws_connections":0,"ws_frames_total":0,"filtered_out":0}; self._wsc=0
    def _rel(self,url): return any(w in urlparse(url).netloc.lower() for w in self._cfg.domain_whitelist)
    def _bl(self,url): p=urlparse(url).path.lower(); return any(x in p for x in self._cfg.path_blacklist_patterns)
    def _cap(self,url,rt=""):
        if not self._rel(url): return False
        if self._bl(url): return False
        if rt in ("image","stylesheet","font","media","manifest"): return False
        return True
    async def _hr(self,req: Request):
        if not self._cap(req.url,req.resource_type): self.stats["filtered_out"]+=1; return
        cap=CapturedRequest(url=req.url,method=req.method,headers=dict(req.headers),post_data=req.post_data,resource_type=req.resource_type,timestamp=time.time(),request_id=f"{id(req)}_{time.time_ns()}",page_url=self._page.url)
        if cap.uid in self._seen: return
        self._seen.add(cap.uid)
        if len(self._seen)>50_000: self._seen=set(list(self._seen)[-10_000:])
        self.requests.append(cap); self.stats["requests_total"]+=1
        if CFG.verbose_network: log.debug("REQ %s %s",cap.method,cap.url[:120])
        for cb in self._on_req:
            try:
                r=cb(cap)
                if asyncio.iscoroutine(r): await r
            except Exception as e: log.error("Req cb: %s",e)
    async def _hresp(self,resp: Response):
        rq=resp.request
        if not self._cap(rq.url,rq.resource_type): return
        cr=None
        for r in reversed(self.requests):
            if r.url==rq.url and r.method==rq.method and abs(r.timestamp-time.time())<10: cr=r; break
        if not cr: cr=CapturedRequest(url=rq.url,method=rq.method,headers=dict(rq.headers),post_data=rq.post_data,resource_type=rq.resource_type,timestamp=time.time(),request_id=f"r_{id(resp)}",page_url=self._page.url)
        body=None
        if self._cfg.capture_body:
            try:
                body=await resp.body()
                if body and len(body)>self._cfg.max_body_bytes: body=body[:self._cfg.max_body_bytes]
            except Exception: pass
        cap=CapturedResponse(request=cr,status=resp.status,status_text=resp.status_text,headers=dict(resp.headers),body=body,timestamp=time.time())
        self.responses.append(cap); self.stats["responses_total"]+=1
        if cap.is_json: self.stats["responses_json"]+=1
        if CFG.verbose_network: log.debug("RESP %d %s (%dB)",cap.status,cap.request.url[:80],cap.body_size)
        for cb in self._on_resp:
            try:
                r=cb(cap)
                if asyncio.iscoroutine(r): await r
            except Exception as e: log.error("Resp cb: %s",e)
    def _hws(self,ws: PwWebSocket):
        if not self._rel(ws.url): return
        u=ws.url; self.stats["ws_connections"]+=1; self.ws_connections.setdefault(u,[]); log.info("WS -> %s",u)
        ws.on("framereceived",lambda d: self._wf(u,"received",d)); ws.on("framesent",lambda d: self._wf(u,"sent",d)); ws.on("close",lambda: log.info("WS kapandi -> %s",u))
    def _wf(self,u,d,data):
        if not self._cfg.ws_capture or len(self.ws_frames)>=self._cfg.ws_max_frames: return
        p=data if isinstance(data,str) else str(data); self._wsc+=1
        f=CapturedWsFrame(ws_url=u,direction=d,payload=p,timestamp=time.time(),frame_index=self._wsc)
        self.ws_frames.append(f); self.ws_connections[u].append(f); self.stats["ws_frames_total"]+=1
        for cb in self._on_ws:
            try: cb(f)
            except Exception: pass
    def attach(self):
        if self._active: return
        self._page.on("request",lambda r: asyncio.ensure_future(self._hr(r))); self._page.on("response",lambda r: asyncio.ensure_future(self._hresp(r))); self._page.on("websocket",self._hws); self._active=True; log.info("Interceptor baglandi.")
    def detach(self):
        if not self._active: return
        try: self._page.remove_listener("request",self._hr); self._page.remove_listener("response",self._hresp); self._page.remove_listener("websocket",self._hws)
        except Exception: pass
        self._active=False
    def on_request(self,cb): self._on_req.append(cb)
    def on_response(self,cb): self._on_resp.append(cb)
    def on_ws_frame(self,cb): self._on_ws.append(cb)
    def get_json_responses(self,since=0.0): return [r for r in self.responses if r.is_json and r.timestamp>=since and r.status==200]
    def get_responses_by_path(self,pattern): rx=re.compile(pattern,re.IGNORECASE); return [r for r in self.responses if rx.search(r.request.path)]
    def clear(self):
        self.requests.clear(); self.responses.clear(); self.ws_frames.clear(); self.ws_connections.clear(); self._seen.clear(); self._wsc=0
        for k in self.stats: self.stats[k]=0
    def stats_report(self):
        lines=["=== NETWORK STATS ==="]
        for k,v in self.stats.items(): lines.append(f"  {k:25s} : {v:>8,d}")
        lines.append(f"  ws_endpoints            : {len(self.ws_connections):>8,d}"); return chr(10).join(lines)
