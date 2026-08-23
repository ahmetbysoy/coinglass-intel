"""WebSocket dinleyici."""
import asyncio, logging, time
from typing import Any, Callable, Dict, List, Optional
import websockets, orjson
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("pipeline.ws")
class WebSocketListener:
    def __init__(self):
        self._cfg=CFG.websocket; self._conns: Dict[str,Any]={}; self._tasks: List[asyncio.Task]=[]
        self._running=False; self._msg_cbs: List[Callable]=[]; self._conn_cbs: List[Callable]=[]; self._disc_cbs: List[Callable]=[]
        self._q: asyncio.Queue=asyncio.Queue(maxsize=self._cfg.message_queue_size); self._fc: Dict[str,int]={}; self._rc: Dict[str,int]={}
    def on_message(self,cb): self._msg_cbs.append(cb)
    def on_connect(self,cb): self._conn_cbs.append(cb)
    def on_disconnect(self,cb): self._disc_cbs.append(cb)
    async def connect(self,url,subs=None):
        if url in self._conns: return
        t=asyncio.create_task(self._loop(url,subs)); self._tasks.append(t); self._fc[url]=0; self._rc[url]=0
    async def _loop(self,url,subs):
        attempt=0
        while self._running or attempt==0:
            try:
                async with websockets.connect(url,ping_interval=self._cfg.ping_interval_sec,ping_timeout=self._cfg.ping_timeout_sec,max_size=10*1024*1024,extra_headers={"User-Agent":CFG.browser.user_agent,"Origin":"https://www.coinglass.com"}) as ws:
                    self._conns[url]=ws; attempt=0
                    for cb in self._conn_cbs:
                        try: cb(url)
                        except Exception: pass
                    if subs:
                        for m in subs: await ws.send(orjson.dumps(m).decode())
                    async for raw in ws:
                        if not self._running: break
                        self._fc[url]=self._fc.get(url,0)+1; p=self._parse(raw)
                        if p is not None:
                            for cb in self._msg_cbs:
                                try:
                                    r=cb(url,p)
                                    if asyncio.iscoroutine(r): await r
                                except Exception: pass
                            try: self._q.put_nowait((url,p))
                            except asyncio.QueueFull: pass
            except Exception as e: log.debug("WS [%s]: %s",url,e)
            finally:
                self._conns.pop(url,None)
                for cb in self._disc_cbs:
                    try: cb(url)
                    except Exception: pass
            if not self._running: break
            attempt+=1; self._rc[url]=attempt
            if attempt>self._cfg.max_reconnect_attempts: break
            await asyncio.sleep(min(self._cfg.reconnect_delay_sec*attempt,30.0))
    def _parse(self,raw):
        if isinstance(raw,bytes):
            try: return orjson.loads(raw)
            except Exception: return None
        elif isinstance(raw,str):
            try: return orjson.loads(raw)
            except Exception: return {"_raw":raw,"_ts":time.time()}
        return None
    async def send(self,url,msg):
        ws=self._conns.get(url)
        if ws:
            try: await ws.send(orjson.dumps(msg).decode()); return True
            except Exception: pass
        return False
    async def get_message(self,timeout=1.0):
        try: return await asyncio.wait_for(self._q.get(),timeout=timeout)
        except asyncio.TimeoutError: return None
    def start(self): self._running=True
    async def stop(self):
        self._running=False
        for ws in self._conns.values():
            try: await ws.close()
            except Exception: pass
        for t in self._tasks: t.cancel()
        await asyncio.gather(*self._tasks,return_exceptions=True); self._tasks.clear(); self._conns.clear()
    def stats(self): return {"active":len(self._conns),"frames":dict(self._fc),"reconnects":dict(self._rc),"queue":self._q.qsize()}
