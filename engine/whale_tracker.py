"""Balina Takip. Clustering, yonsel akis, anomali."""
import logging, time
from typing import Dict, List, Optional
from dataclasses import dataclass
import numpy as np
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.whale")
@dataclass
class WCluster:
    window_start: float; window_end: float; buy_count: int; sell_count: int
    buy_usd: float; sell_usd: float; net_usd: float; avg_size: float; max_single: float; dominant: str; coordinated: bool
@dataclass
class WhaleSignal:
    symbol: str; timestamp: float; total_orders: int; buy_orders: int; sell_orders: int
    buy_usd: float; sell_usd: float; net_flow: float; avg_size: float; max_size: float
    clusters: List[WCluster]; active_clusters: int; coordinated: bool
    size_z: float; freq_z: float; anomalous: bool; pressure: float; pressure_mom: float
    directional_score: float; signal_strength: float; narrative: str
class WhaleTracker:
    def __init__(self): self._th=CFG.analysis
    def analyze(self,symbol,whale_data):
        if len(whale_data)<3: return None
        filtered=[d for d in whale_data if abs(d.get("size_usd",0))>=self._th.whale_min_order_usd]
        if len(filtered)<2: filtered=whale_data
        n=len(filtered); ts=np.array([d["ts"] for d in filtered],dtype=np.float64)
        sz=np.array([abs(d.get("size_usd",0)) for d in filtered],dtype=np.float64)
        sides=[d.get("side","").lower() for d in filtered]
        bm=np.array([s in ("buy","long","bid","b") for s in sides]); sm=np.array([s in ("sell","short","ask","s") for s in sides])
        bu=float(np.sum(sz[bm])) if bm.any() else 0.0; su=float(np.sum(sz[sm])) if sm.any() else 0.0
        bc=int(bm.sum()); sc=int(sm.sum()); net=bu-su; avg=float(np.mean(sz)); mx=float(np.max(sz))
        clusters=self._cluster(filtered,ts,sz,sides); coord=any(c.coordinated for c in clusters)
        if n>=5: smn=float(np.mean(sz)); ssd=max(float(np.std(sz,ddof=1)),1e-9); szz=(mx-smn)/ssd
        else: szz=0.0
        if n>=10 and ts[-1]>ts[0]: freq=n/max(ts[-1]-ts[0],1.0); fzz=(freq-0.2)/0.1
        else: fzz=0.0
        anom=szz>3.0 or fzz>3.0; pres=self._pressure(ts,sz,sides)
        if n>=6: h=n//2; p1=self._pressure(ts[:h],sz[:h],sides[:h]); p2=self._pressure(ts[h:],sz[h:],sides[h:]); pmom=p2-p1
        else: pmom=0.0
        score=0.0; tu=bu+su
        if tu>0: score+=(net/tu)*0.30
        score+=pres*0.25
        if coord:
            for c in clusters:
                if c.coordinated:
                    if c.dominant=="buy": score+=0.15
                    elif c.dominant=="sell": score-=0.15
                    break
        if anom: score+=np.sign(pres)*0.10
        score+=np.clip(pmom*2,-0.15,0.15); score=float(np.clip(score,-1,1)); st=min(abs(score)*1.5+(0.2 if anom else 0.0),1.0)
        narr=f"{symbol} Whale:{n} B:{bc}(${bu:,.0f}) S:{sc}(${su:,.0f}) Net:${net:,.0f} Max:${mx:,.0f}(Z:{szz:.1f})"
        if coord: narr+=" KOORDINE!"
        narr+=f" Basinc:{pres:+.3f} SKOR:{score:+.3f}"
        return WhaleSignal(symbol=symbol,timestamp=time.time(),total_orders=n,buy_orders=bc,sell_orders=sc,buy_usd=bu,sell_usd=su,net_flow=net,avg_size=avg,max_size=mx,clusters=clusters[:15],active_clusters=len(clusters),coordinated=coord,size_z=szz,freq_z=fzz,anomalous=anom,pressure=pres,pressure_mom=pmom,directional_score=score,signal_strength=st,narrative=narr)
    def _cluster(self,data,ts,sz,sides):
        out=[]; w=self._th.whale_cluster_window_sec; i=0; n=len(data)
        while i<n:
            t0=ts[i]; t1=t0+w; j=i
            while j<n and ts[j]<=t1: j+=1
            if j-i>=2:
                ws=sides[i:j]; wsz=sz[i:j]
                bc=sum(1 for s in ws if s in ("buy","long","bid","b")); sc=sum(1 for s in ws if s in ("sell","short","ask","s"))
                bu=float(sum(wsz[k] for k in range(len(ws)) if ws[k] in ("buy","long","bid","b")))
                su=float(sum(wsz[k] for k in range(len(ws)) if ws[k] in ("sell","short","ask","s")))
                dom="buy" if bc>sc else ("sell" if sc>bc else "mixed")
                streak=1; mxx=1
                for k in range(1,len(ws)):
                    if ws[k]==ws[k-1]: streak+=1; mxx=max(mxx,streak)
                    else: streak=1
                out.append(WCluster(window_start=float(t0),window_end=float(ts[min(j-1,n-1)]),buy_count=bc,sell_count=sc,buy_usd=bu,sell_usd=su,net_usd=bu-su,avg_size=float(np.mean(wsz)),max_single=float(np.max(wsz)),dominant=dom,coordinated=(mxx>=self._th.whale_directional_threshold)))
            i=max(j,i+1)
        return out
    def _pressure(self,ts,sz,sides):
        n=len(sz)
        if n==0: return 0.0
        decay=0.9; w=np.array([decay**(n-1-i) for i in range(n)]); w/=np.sum(w)
        signed=np.zeros(n)
        for i,s in enumerate(sides):
            if s in ("buy","long","bid","b"): signed[i]=sz[i]
            elif s in ("sell","short","ask","s"): signed[i]=-sz[i]
        ws=float(np.sum(signed*w)); tw=float(np.sum(sz*w))
        return ws/tw if tw>0 else 0.0
