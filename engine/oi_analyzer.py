"""OI Analiz. Z-score, momentum, delta, rejim."""
import logging, time
from typing import Dict, List, Optional
from dataclasses import dataclass
import numpy as np
from scipy import stats as sp_stats
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.oi")
@dataclass
class OISignal:
    symbol: str; timestamp: float; current_oi_usd: float; oi_series: List[float]; price_series: List[float]
    zscore_current: float; zscore_mean: float; zscore_std: float; zscore_percentile: float
    momentum_1h: float; momentum_4h: float; momentum_24h: float; momentum_acceleration: float
    delta_absolute: float; delta_pct: float; delta_significant: bool
    oi_price_correlation: float; oi_price_divergence: float; divergence_direction: str
    regime: str; regime_confidence: float; directional_score: float; signal_strength: float; narrative: str
class OIAnalyzer:
    def __init__(self): self._th=CFG.analysis; self._lb=self._th.oi_lookback_periods
    def analyze(self, symbol, oi_data, price_data):
        if len(oi_data)<5: return None
        oi=np.array([d["oi_usd"] for d in oi_data],dtype=np.float64)
        ts=np.array([d["ts"] for d in oi_data],dtype=np.float64)
        pm={d["ts"]:d["close"] for d in price_data}
        pv=np.array([pm.get(t,np.nan) for t in ts],dtype=np.float64)
        valid=~np.isnan(pv)
        if valid.sum()<5: pv=np.full_like(oi,oi[-1])
        else:
            for i in range(len(pv)):
                if np.isnan(pv[i]): pv[i]=pv[i-1] if i>0 else pv[np.where(~np.isnan(pv))[0][0]]
        cur=float(oi[-1]); n=len(oi); w=min(self._lb,n); ow=oi[-w:]
        zm=float(np.mean(ow)); zs=max(float(np.std(ow,ddof=1)) if w>1 else 1.0,1e-9)
        z=(cur-zm)/zs; zp=float(sp_stats.percentileofscore(ow,cur))
        def roc(a,p):
            if len(a)<=p: return 0.0
            past=a[-p-1]; return float((a[-1]-past)/past*100.0) if past!=0 else 0.0
        m1=roc(oi,min(12,n-1)); m4=roc(oi,min(48,n-1)); m24=roc(oi,min(288,n-1))
        acc=0.0
        if n>24:
            rm=roc(oi[-12:],min(6,len(oi[-12:])-1)); om=roc(oi[-24:-12],min(6,len(oi[-24:-12])-1)); acc=rm-om
        da=float(oi[-1]-oi[-2]) if n>=2 else 0.0
        dp=(da/oi[-2]*100.0) if n>=2 and oi[-2]!=0 else 0.0
        ds=abs(dp)>=self._th.oi_delta_significant_pct
        cw=min(48,n)
        corr=float(np.corrcoef(oi[-cw:],pv[-cw:])[0,1]) if cw>=10 and np.std(oi[-cw:])>0 and np.std(pv[-cw:])>0 else 0.0
        ot=np.polyfit(range(min(20,n)),oi[-min(20,n):],1)[0]; pt=np.polyfit(range(min(20,n)),pv[-min(20,n):],1)[0]
        dv=float(ot*(-pt))
        if ot>0 and pt<0: dd="bullish_div"
        elif ot<0 and pt>0: dd="bearish_div"
        else: dd="none"
        regime,rc=self._regime(oi,pv,z,m4,corr)
        score=self._score(z,m1,m4,acc,dp,corr,dd,regime); st=min(abs(score)*1.5,1.0)
        narr=f"{symbol} OI:${cur:,.0f} Z:{z:+.2f}(%{zp:.0f}) M1h:{m1:+.2f}% M4h:{m4:+.2f}% D:{dp:+.2f}% Corr:{corr:+.3f} Div:{dd} Rejim:{regime} SKOR:{score:+.3f}"
        return OISignal(symbol=symbol,timestamp=time.time(),current_oi_usd=cur,oi_series=oi.tolist(),price_series=pv.tolist(),zscore_current=z,zscore_mean=zm,zscore_std=zs,zscore_percentile=zp,momentum_1h=m1,momentum_4h=m4,momentum_24h=m24,momentum_acceleration=acc,delta_absolute=da,delta_pct=dp,delta_significant=ds,oi_price_correlation=corr,oi_price_divergence=dv,divergence_direction=dd,regime=regime,regime_confidence=rc,directional_score=score,signal_strength=st,narrative=narr)
    def _regime(self,oi,pv,z,m4,corr):
        n=min(20,len(oi)); osl=np.polyfit(range(n),oi[-n:],1)[0]; psl=np.polyfit(range(n),pv[-n:],1)[0]
        on=osl/(np.mean(oi[-n:])+1e-9); pn=psl/(np.mean(pv[-n:])+1e-9)
        ou=on>0.001; od=on<-0.001; pu=pn>0.001; pd=pn<-0.001
        if ou and not pu and not pd: return "accumulation",min(abs(z)/3,1)*0.7+min(abs(m4)/5,1)*0.3
        elif od and (pu or (not pu and not pd)): return "distribution",min(abs(z)/3,1)*0.6+min(abs(m4)/5,1)*0.4
        elif ou and pu: return "expansion",min(abs(corr),1)*0.5+min(abs(m4)/5,1)*0.5
        elif od and pd: return "contraction",min(abs(corr),1)*0.5+min(abs(m4)/5,1)*0.5
        return "contraction",0.3
    def _score(self,z,m1,m4,acc,dp,corr,dd,regime):
        s=0.0
        if regime in ("accumulation","expansion"): s+=np.clip(z/3,-1,1)*0.25
        else: s-=np.clip(z/3,-1,1)*0.20
        s+=np.clip(m1/3,-1,1)*0.20; s+=np.clip(acc/2,-1,1)*0.10
        if dd=="bullish_div": s+=0.15
        elif dd=="bearish_div": s-=0.15
        s+={"accumulation":0.15,"expansion":0.10,"distribution":-0.15,"contraction":-0.10}.get(regime,0)
        if abs(dp)>self._th.oi_delta_significant_pct: s+=np.sign(dp)*0.10
        return float(np.clip(s,-1,1))
