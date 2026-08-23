"""Emir Defteri Analiz. Imbalance, duvar, mikro yapi."""
import logging, time
from typing import Dict, List, Optional
from dataclasses import dataclass
import numpy as np
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.ob")
@dataclass
class OBWall:
    price: float; size_usd: float; side: str; dist_pct: float; significant: bool
@dataclass
class OrderBookSignal:
    symbol: str; timestamp: float; mid_price: float; spread: float; spread_pct: float
    bid_total_usd: float; ask_total_usd: float; imbalance_ratio: float; imbalance_zscore: float
    bid_depth_slope: float; ask_depth_slope: float; depth_asymmetry: float
    walls: List[OBWall]; largest_bid_wall: Optional[OBWall]; largest_ask_wall: Optional[OBWall]
    top_bid_size: float; top_ask_size: float; top_level_imbalance: float
    imbalance_history: List[float]; imbalance_momentum: float
    directional_score: float; signal_strength: float; narrative: str
class OrderBookAnalyzer:
    def __init__(self): self._th=CFG.analysis
    def analyze(self,symbol,snaps):
        if len(snaps)<2: return None
        l=snaps[-1]; mid=float(l.get("mid_price",0)); spr=float(l.get("spread",0))
        bt=float(l.get("bid_total",0)); at=float(l.get("ask_total",0))
        spp=(spr/mid*100) if mid>0 else 0; tot=bt+at; imb=bt/tot if tot>0 else 0.5
        ih=[]
        for s in snaps:
            b=float(s.get("bid_total",0)); a=float(s.get("ask_total",0)); t=b+a; ih.append(b/t if t>0 else 0.5)
        ia=np.array(ih); im=float(np.mean(ia)); istd=max(float(np.std(ia,ddof=1)) if len(ia)>1 else 1e-6,1e-9); iz=(imb-im)/istd
        bs,asl=self._slopes(snaps); da=bs-asl; walls=self._walls(snaps,mid)
        bw=[w for w in walls if w.side=="bid"]; aw=[w for w in walls if w.side=="ask"]
        lb=max(bw,key=lambda w: w.size_usd) if bw else None; la=max(aw,key=lambda w: w.size_usd) if aw else None
        imom=float(np.mean(ia[-3:]))-float(np.mean(ia[-6:-3])) if len(ia)>=6 else 0.0
        score=0.0; score+=(imb-0.5)*2.0*0.25; score+=np.clip(iz/2,-1,1)*0.15; score+=np.clip(da,-1,1)*0.10
        if lb and lb.significant: score+=0.10
        if la and la.significant: score-=0.10
        score+=np.clip(imom*10,-0.15,0.15)
        if imb>self._th.ob_imbalance_bullish: score+=0.10
        elif imb<self._th.ob_imbalance_bearish: score-=0.10
        score=float(np.clip(score,-1,1)); st=min(abs(score)*1.5,1.0)
        narr=f"{symbol} OB@${mid:,.2f} Sprd:{spp:.4f}% Imb:{imb:.3f}(Z:{iz:+.2f}) Walls:{len(walls)} Mom:{imom:+.4f} SKOR:{score:+.3f}"
        return OrderBookSignal(symbol=symbol,timestamp=time.time(),mid_price=mid,spread=spr,spread_pct=spp,bid_total_usd=bt,ask_total_usd=at,imbalance_ratio=imb,imbalance_zscore=iz,bid_depth_slope=bs,ask_depth_slope=asl,depth_asymmetry=da,walls=walls[:20],largest_bid_wall=lb,largest_ask_wall=la,top_bid_size=bt,top_ask_size=at,top_level_imbalance=imb,imbalance_history=ih[-50:],imbalance_momentum=imom,directional_score=score,signal_strength=st,narrative=narr)
    def _slopes(self,snaps):
        if len(snaps)<3: return 0.0,0.0
        bts=np.array([float(s.get("bid_total",0)) for s in snaps[-10:]]); ats=np.array([float(s.get("ask_total",0)) for s in snaps[-10:]])
        n=len(bts)
        if n<2: return 0.0,0.0
        x=np.arange(n,dtype=np.float64); bm=float(np.mean(bts))+1e-9; am=float(np.mean(ats))+1e-9
        return float(np.polyfit(x,bts,1)[0])/bm,float(np.polyfit(x,ats,1)[0])/am
    def _walls(self,snaps,mid):
        walls=[]
        if not snaps: return walls
        l=snaps[-1]; bt=float(l.get("bid_total",0)); at=float(l.get("ask_total",0))
        if bt>=self._th.ob_wall_min_usd: walls.append(OBWall(price=mid*0.998,size_usd=bt,side="bid",dist_pct=-0.5,significant=bt>=self._th.ob_wall_min_usd*2))
        if at>=self._th.ob_wall_min_usd: walls.append(OBWall(price=mid*1.002,size_usd=at,side="ask",dist_pct=0.5,significant=at>=self._th.ob_wall_min_usd*2))
        return walls
