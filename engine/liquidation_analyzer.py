"""Likidasyon Analiz. Kaskad, heatmap clustering, basinç."""
import logging, time
from typing import Dict, List, Optional
from dataclasses import dataclass
import numpy as np
from scipy.signal import find_peaks
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.liq")
@dataclass
class LiqCluster:
    price_level: float; total_usd: float; side: str; density: float; distance_pct: float
@dataclass
class LiquidationSignal:
    symbol: str; timestamp: float; current_price: float
    long_liq_usd_24h: float; short_liq_usd_24h: float; total_liq_usd_24h: float
    liq_count_24h: int; largest_single_liq: float; long_short_liq_ratio: float; liq_imbalance: float
    liq_momentum: float; liq_acceleration: float; cascade_probability: float; cascade_direction: str
    cascade_trigger_price: float; clusters: List[LiqCluster]; nearest_long_cluster: float; nearest_short_cluster: float
    directional_score: float; signal_strength: float; narrative: str
class LiquidationAnalyzer:
    def __init__(self): self._th=CFG.analysis
    def analyze(self,symbol,liq_data,price_data,heatmap=None):
        if len(liq_data)<3: return None
        ll=np.array([d.get("long_liq_usd",0) for d in liq_data],dtype=np.float64)
        sl=np.array([d.get("short_liq_usd",0) for d in liq_data],dtype=np.float64); tl=ll+sl
        pp=np.array([d["close"] for d in price_data],dtype=np.float64) if price_data else np.array([0.0])
        cp=float(pp[-1]) if len(pp)>0 else 0.0; n=len(ll)
        tlong=float(np.sum(ll)); tshort=float(np.sum(sl)); tall=tlong+tshort
        lsr=tlong/tshort if tshort>0 else float("inf"); imb=(tlong-tshort)/tall if tall>0 else 0.0
        if n>=6: rc=float(np.mean(tl[-3:])); oc=float(np.mean(tl[-6:-3])); lmom=(rc-oc)/(oc+1e-9)*100
        else: lmom=0.0
        lacc=0.0
        if n>=12: r1=float(np.mean(tl[-3:])); r2=float(np.mean(tl[-6:-3])); r3=float(np.mean(tl[-9:-6])); lacc=(r1-r2)-(r2-r3)
        ml=float(np.mean(tl)) if n>0 else 1.0; sdl=max(float(np.std(tl,ddof=1)) if n>1 else 1.0,1e-9)
        lz=(float(tl[-1])-ml)/sdl; ct=self._th.liq_cascade_multiplier
        if lz>ct and lmom>0: cprob=float(min(1.0/(1.0+np.exp(-2.0*(lz-ct))),0.98))
        else: cprob=float(max(0.0,lz/(ct*3.0)))
        rl=float(np.mean(ll[-3:])) if n>=3 else 0.0; rs=float(np.mean(sl[-3:])) if n>=3 else 0.0
        if rl>rs*1.5: cdir="long_cascade"
        elif rs>rl*1.5: cdir="short_cascade"
        else: cdir="none"
        clusters=[]; nlp=0.0; nsp=0.0
        if heatmap and len(heatmap)>=5:
            clusters=self._clusters(heatmap,cp)
            for c in clusters:
                if c.side=="long" and (nlp==0 or abs(c.price_level-cp)<abs(nlp-cp)): nlp=c.price_level
                elif c.side=="short" and (nsp==0 or abs(c.price_level-cp)<abs(nsp-cp)): nsp=c.price_level
        score=0.0
        if imb>0.3:
            if lz>ct: score+=0.15
            else: score-=0.20
        elif imb<-0.3:
            if lz>ct: score-=0.10
            else: score+=0.20
        if cprob>0.6:
            if cdir=="long_cascade": score-=0.15
            elif cdir=="short_cascade": score+=0.15
        score+=np.clip(lmom/200,-0.15,0.15)
        if nlp>0 and cp>0 and abs(cp-nlp)/cp<0.02: score-=0.10
        if nsp>0 and cp>0 and abs(cp-nsp)/cp<0.02: score+=0.10
        score=float(np.clip(score,-1,1)); st=min(abs(score)*1.5+cprob*0.3,1.0)
        narr=f"{symbol} Liq@${cp:,.0f} L:${tlong:,.0f} S:${tshort:,.0f} Imb:{imb:+.3f} Z:{lz:+.2f} Cascade:%{cprob*100:.0f}({cdir}) Mom:{lmom:+.1f}% SKOR:{score:+.3f}"
        return LiquidationSignal(symbol=symbol,timestamp=time.time(),current_price=cp,long_liq_usd_24h=tlong,short_liq_usd_24h=tshort,total_liq_usd_24h=tall,liq_count_24h=int(liq_data[-1].get("liq_count",0)),largest_single_liq=float(liq_data[-1].get("largest_liq",0)),long_short_liq_ratio=lsr,liq_imbalance=imb,liq_momentum=lmom,liq_acceleration=lacc,cascade_probability=cprob,cascade_direction=cdir,cascade_trigger_price=cp*(1-0.02) if cdir=="long_cascade" else cp*(1+0.02),clusters=clusters[:10],nearest_long_cluster=nlp,nearest_short_cluster=nsp,directional_score=score,signal_strength=st,narrative=narr)
    def _clusters(self,hm,cp):
        pa,ua=[],[]
        for e in hm:
            p=e.get("price",0); u=e.get("usd",0)
            if p>0 and u>0: pa.append(p); ua.append(u)
        if len(pa)<5: return []
        pn=np.array(pa); un=np.array(ua)
        try:
            nb=self._th.liq_heatmap_bins; pmin,pmax=float(pn.min()),float(pn.max())
            if pmax<=pmin: return []
            edges=np.linspace(pmin,pmax,nb+1); centers=(edges[:-1]+edges[1:])/2; bu=np.zeros(nb)
            for i in range(len(pn)):
                idx=max(0,min(int((pn[i]-pmin)/(pmax-pmin)*(nb-1)),nb-1)); bu[idx]+=un[i]
            peaks,_=find_peaks(bu,height=self._th.liq_cluster_min_usd,distance=2)
            tbu=float(np.sum(bu))+1e-9; out=[]
            for pi in peaks:
                clp=float(centers[pi]); clu=float(bu[pi]); dp=(clp-cp)/cp*100 if cp>0 else 0
                side="long" if clp<cp else "short"
                out.append(LiqCluster(price_level=clp,total_usd=clu,side=side,density=round(clu/tbu,4),distance_pct=round(dp,3)))
            out.sort(key=lambda c: c.total_usd,reverse=True); return out
        except Exception: return []
