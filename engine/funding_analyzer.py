"""Funding Analiz. EMA crossover, diverjans, ekstrem, mean-reversion."""
import logging, time
from typing import Dict, List, Optional
from dataclasses import dataclass
import numpy as np
from scipy import stats as sp_stats
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.funding")
@dataclass
class FundingSignal:
    symbol: str; timestamp: float; current_rate: float; predicted_rate: float; rate_series: List[float]
    ema_fast: float; ema_slow: float; ema_crossover: str; ema_spread: float
    is_extreme: bool; extreme_direction: str; zscore: float; percentile: float
    mean_reversion_prob: float; expected_reversion_direction: str
    funding_price_divergence: float; divergence_signal: str
    directional_score: float; signal_strength: float; narrative: str
class FundingAnalyzer:
    def __init__(self): self._th=CFG.analysis
    def analyze(self,symbol,funding_data,price_data=None):
        if len(funding_data)<5: return None
        rates=np.array([d["rate"] for d in funding_data],dtype=np.float64); n=len(rates)
        cr=float(rates[-1]); pr=float(funding_data[-1].get("predicted_rate",cr))
        ef=self._ema(rates,self._th.funding_ema_fast); es=self._ema(rates,self._th.funding_ema_slow)
        efv,esv=float(ef[-1]),float(es[-1]); sp=efv-esv; cross="none"
        if n>=3:
            ps=float(ef[-2]-es[-2])
            if ps<=0 and sp>0: cross="bullish_cross"
            elif ps>=0 and sp<0: cross="bearish_cross"
        w=min(48,n); rw=rates[-w:]; rm=float(np.mean(rw)); rs=max(float(np.std(rw,ddof=1)) if w>1 else 1e-6,1e-9)
        z=(cr-rm)/rs; pct=float(sp_stats.percentileofscore(rw,cr))
        ie=cr>=self._th.funding_extreme_positive or cr<=self._th.funding_extreme_negative
        if cr>=self._th.funding_extreme_positive: ed="overheated_long"
        elif cr<=self._th.funding_extreme_negative: ed="overheated_short"
        else: ed="neutral"
        dev=abs(z); mrp=float(1.0/(1.0+np.exp(-1.2*(dev-1.5)))) if rs>1e-9 else 0.5; mrd="down" if z>0 else "up"
        div=0.0; ds="none"
        if price_data and len(price_data)>=10:
            pp=np.array([d["close"] for d in price_data],dtype=np.float64)
            psl=np.polyfit(range(min(20,len(pp))),pp[-min(20,len(pp)):],1)[0]; fsl=np.polyfit(range(min(20,n)),rates[-min(20,n):],1)[0]
            pn=psl/(np.mean(pp[-20:])+1e-9); fn=fsl/(np.mean(rates[-20:])+1e-9); div=float(fn-pn)
            if fn>0.01 and pn<-0.005: ds="funding_up_price_down"
            elif fn<-0.01 and pn>0.005: ds="funding_down_price_up"
        score=0.0; score-=np.clip(cr/0.05,-1,1)*0.30; score+=np.clip(sp/0.02,-1,1)*0.15
        if cross=="bullish_cross": score+=0.15
        elif cross=="bearish_cross": score-=0.15
        if ed=="overheated_long": score-=0.20
        elif ed=="overheated_short": score+=0.20
        if ds=="funding_up_price_down": score-=0.10
        elif ds=="funding_down_price_up": score+=0.10
        score=float(np.clip(score,-1,1)); st=min(abs(score)*1.5,1.0)
        narr=f"{symbol} Fund:{cr*100:.4f}% Pred:{pr*100:.4f}% Z:{z:+.2f}(%{pct:.0f}) EMA:{efv*100:.4f}/{esv*100:.4f}% Cross:{cross} Ext:{ed} MR:%{mrp*100:.0f} Div:{ds} SKOR:{score:+.3f}"
        return FundingSignal(symbol=symbol,timestamp=time.time(),current_rate=cr,predicted_rate=pr,rate_series=rates.tolist(),ema_fast=efv,ema_slow=esv,ema_crossover=cross,ema_spread=sp,is_extreme=ie,extreme_direction=ed,zscore=z,percentile=pct,mean_reversion_prob=mrp,expected_reversion_direction=mrd,funding_price_divergence=div,divergence_signal=ds,directional_score=score,signal_strength=st,narrative=narr)
    @staticmethod
    def _ema(data,period):
        a=2.0/(period+1.0); e=np.zeros_like(data); e[0]=data[0]
        for i in range(1,len(data)): e[i]=a*data[i]+(1-a)*e[i-1]
        return e
