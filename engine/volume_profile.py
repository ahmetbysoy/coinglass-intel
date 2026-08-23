"""Hacim Profili + VWAP. POC, VAH, VAL, spike, OBV."""
import logging, time
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
import numpy as np
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.vol")
@dataclass
class VPLevel:
    price_low: float; price_high: float; price_mid: float; volume_usd: float; volume_pct: float; is_poc: bool; is_va: bool
@dataclass
class VolumeSignal:
    symbol: str; timestamp: float; current_price: float
    vwap: float; vwap_dev_pct: float; vwap_upper: float; vwap_lower: float; vwap_pos: str
    poc: float; vah: float; val: float; va_pct: float; levels: List[VPLevel]
    spike: bool; spike_mult: float; spike_dir: str
    vol_trend: float; vol_price_corr: float; obv_slope: float
    directional_score: float; signal_strength: float; narrative: str
class VolumeProfileAnalyzer:
    def __init__(self): self._th=CFG.analysis
    def analyze(self,symbol,price_data):
        if len(price_data)<10: return None
        cl=np.array([d["close"] for d in price_data],dtype=np.float64)
        hi=np.array([d.get("high",d["close"]) for d in price_data],dtype=np.float64)
        lo=np.array([d.get("low",d["close"]) for d in price_data],dtype=np.float64)
        vo=np.array([d.get("vol_usd",0) for d in price_data],dtype=np.float64)
        n=len(cl); cp=float(cl[-1])
        tp=(hi+lo+cl)/3.0; cv=np.cumsum(vo); cv=np.where(cv==0,1e-9,cv)
        vs=np.cumsum(tp*vo)/cv; vwap=float(vs[-1])
        sq=((tp-vs)**2)*vo; vstd=np.sqrt(np.maximum(np.cumsum(sq)/cv,0))
        vup=vwap+2*float(vstd[-1]); vlo=vwap-2*float(vstd[-1])
        vd=((cp-vwap)/vwap*100) if vwap>0 else 0.0
        if cp>vup: vpos="above"
        elif cp<vlo: vpos="below"
        else: vpos="inside"
        lvls,poc,vah,val,vap=self._profile(cl,hi,lo,vo)
        spk,sm,sd=self._spike(cl,vo)
        vw=min(20,n)
        if n>=vw: vr=float(np.mean(vo[-vw//2:])); volo=float(np.mean(vo[-vw:-vw//2])); vt=(vr-volo)/(volo+1e-9)*100
        else: vt=0.0
        obv=self._obv(cl,vo)
        if len(obv)>=10: osl=float(np.polyfit(range(10),obv[-10:],1)[0]); om=float(np.mean(np.abs(obv[-10:])))+1e-9; osl=osl/om
        else: osl=0.0
        cw=min(30,n)
        if cw>=10 and np.std(cl[-cw:])>0 and np.std(vo[-cw:])>0: vpc=float(np.corrcoef(cl[-cw:],vo[-cw:])[0,1])
        else: vpc=0.0
        score=0.0
        if vpos=="above": score+=0.15
        elif vpos=="below": score-=0.15
        if abs(vd)>self._th.vwap_deviation_pct*3: score-=np.sign(vd)*0.10
        if poc>0:
            pd=(cp-poc)/poc
            if pd>0.01: score+=0.05
            elif pd<-0.01: score-=0.05
        if cp>vah and vah>0: score-=0.05
        elif cp<val and val>0: score+=0.05
        if spk:
            if sd=="buy": score+=0.15
            elif sd=="sell": score-=0.15
        score+=np.clip(osl*5,-0.15,0.15)
        if vpc>0.3: score+=0.05
        elif vpc<-0.3: score-=0.05
        score=float(np.clip(score,-1,1)); st=min(abs(score)*1.5,1.0)
        narr=f"{symbol} Vol@${cp:,.2f} VWAP:${vwap:,.2f}({vd:+.3f}%) {vpos} POC:${poc:,.2f} VA:${val:,.2f}-${vah:,.2f}"
        if spk: narr+=f" SPIKE!{sm:.1f}x({sd})"
        narr+=f" OBV:{osl:+.4f} Corr:{vpc:+.3f} SKOR:{score:+.3f}"
        return VolumeSignal(symbol=symbol,timestamp=time.time(),current_price=cp,vwap=vwap,vwap_dev_pct=vd,vwap_upper=vup,vwap_lower=vlo,vwap_pos=vpos,poc=poc,vah=vah,val=val,va_pct=vap,levels=lvls,spike=spk,spike_mult=sm,spike_dir=sd,vol_trend=vt,vol_price_corr=vpc,obv_slope=osl,directional_score=score,signal_strength=st,narrative=narr)
    def _profile(self,cl,hi,lo,vo):
        nb=self._th.volume_profile_bins; pmin=float(lo.min()); pmax=float(hi.max())
        if pmax<=pmin: return [],0.0,0.0,0.0,0.0
        edges=np.linspace(pmin,pmax,nb+1); centers=(edges[:-1]+edges[1:])/2; bv=np.zeros(nb)
        for i in range(len(cl)):
            bl=float(lo[i]); bh=float(hi[i]); bvol=float(vo[i])
            if bh<=bl or bvol<=0: continue
            for b in range(nb):
                el=float(edges[b]); eh=float(edges[b+1]); ol=max(bl,el); oh=min(bh,eh)
                if oh>ol: bv[b]+=bvol*(oh-ol)/(bh-bl)
        tv=float(np.sum(bv))
        if tv<=0: return [],0.0,0.0,0.0,0.0
        pi=int(np.argmax(bv)); poc=float(centers[pi]); vat=tv*0.70; vv=float(bv[pi]); vl=pi; vh=pi
        while vv<vat and (vl>0 or vh<nb-1):
            el=float(bv[vl-1]) if vl>0 else -1.0; eh=float(bv[vh+1]) if vh<nb-1 else -1.0
            if el>=eh and vl>0: vl-=1; vv+=el
            elif vh<nb-1: vh+=1; vv+=eh
            else: break
        vah=float(centers[vh]); val=float(centers[vl]); vap=vv/tv*100
        lvls=[VPLevel(price_low=float(edges[b]),price_high=float(edges[b+1]),price_mid=float(centers[b]),volume_usd=float(bv[b]),volume_pct=float(bv[b]/tv*100),is_poc=(b==pi),is_va=(vl<=b<=vh)) for b in range(nb)]
        return lvls,poc,vah,val,vap
    def _spike(self,cl,vo):
        n=len(vo)
        if n<10: return False,0.0,"none"
        lb=min(50,n-1); vm=float(np.mean(vo[-lb-1:-1])); latest=float(vo[-1]); mult=latest/(vm+1e-9)
        if mult>=self._th.volume_spike_multiplier:
            d="buy" if (n>=2 and cl[-1]>=cl[-2]) else "sell"; return True,mult,d
        return False,mult,"none"
    @staticmethod
    def _obv(cl,vo):
        obv=np.zeros(len(cl))
        for i in range(1,len(cl)):
            if cl[i]>cl[i-1]: obv[i]=obv[i-1]+vo[i]
            elif cl[i]<cl[i-1]: obv[i]=obv[i-1]-vo[i]
            else: obv[i]=obv[i-1]
        return obv
