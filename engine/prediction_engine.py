"""Ensemble Tahmin. 6 analizor -> 1m/5m/15m yon tahmini."""
import logging, time
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass
import numpy as np
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("engine.prediction")
@dataclass
class TFPred:
    timeframe: str; direction: str; confidence: float; weighted_score: float
    contributing: Dict[str,float]; conflicts: List[str]; expected_move_pct: float; risk: str
@dataclass
class PredictionResult:
    symbol: str; timestamp: float; current_price: float; predictions: Dict[str,TFPred]
    component_scores: Dict[str,float]; component_weights: Dict[str,float]
    ensemble_score: float; ensemble_confidence: float; signal_agreement: float
    conflicting_pairs: List[Tuple[str,str]]; dominant_signal: str
    overall_risk: str; risk_score: float; action: str; action_confidence: float
    executive_summary: str; detailed_breakdown: str
class PredictionEngine:
    TF_MODS={"1m":{"oi_momentum":0.6,"funding_signal":0.3,"liq_pressure":0.8,"ob_imbalance":1.5,"volume_signal":1.2,"whale_flow":1.4},"5m":{"oi_momentum":1.0,"funding_signal":0.7,"liq_pressure":1.2,"ob_imbalance":1.0,"volume_signal":1.0,"whale_flow":1.0},"15m":{"oi_momentum":1.4,"funding_signal":1.3,"liq_pressure":1.0,"ob_imbalance":0.6,"volume_signal":0.9,"whale_flow":0.7}}
    def __init__(self):
        self._th=CFG.analysis; self._bw=dict(self._th.ensemble_weights); self._mc=self._th.prediction_confidence_min; self._tfs=list(self._th.prediction_timeframes)
    def predict(self,symbol,price,signals):
        scores={}; strengths={}
        for k in self._bw:
            sig=signals.get(k)
            if sig:
                try: scores[k]=float(sig.directional_score); strengths[k]=float(sig.signal_strength)
                except Exception: scores[k]=0.0; strengths[k]=0.0
            else: scores[k]=0.0; strengths[k]=0.0
        preds={tf:self._tf(tf,scores,strengths,price) for tf in self._tfs}
        ens=self._w(scores,strengths,"5m"); conf=self._conf(scores,strengths)
        agr,confs=self._confs(scores); dom=max(scores,key=lambda k: abs(scores[k])) if scores else "none"
        rs,rl=self._risk(scores,strengths,agr); act,ac=self._act(ens,conf,agr,rl)
        summ=self._summ(symbol,price,act,ac,ens,agr,rl,preds); brk=self._brk(scores,strengths,preds)
        return PredictionResult(symbol=symbol,timestamp=time.time(),current_price=price,predictions=preds,component_scores=scores,component_weights=dict(self._bw),ensemble_score=ens,ensemble_confidence=conf,signal_agreement=agr,conflicting_pairs=confs,dominant_signal=dom,overall_risk=rl,risk_score=rs,action=act,action_confidence=ac,executive_summary=summ,detailed_breakdown=brk)
    def _tf(self,tf,scores,strengths,price):
        mods=self.TF_MODS.get(tf,{}); ws=0.0; wt=0.0; contrib={}
        for k in self._bw:
            bw=self._bw[k]; mod=mods.get(k,1.0); st=max(strengths.get(k,0),0.1); ew=bw*mod*st; sc=scores.get(k,0)
            ws+=sc*ew; wt+=ew; contrib[k]=round(sc*ew,4)
        fs=ws/wt if wt>0 else 0.0
        if fs>0.08: d="UP"
        elif fs<-0.08: d="DOWN"
        else: d="FLAT"
        c=self._conf(scores,strengths); c=min(c*{"1m":0.75,"5m":0.90,"15m":1.0}.get(tf,0.9),0.95)
        em=abs(fs)*{"1m":0.05,"5m":0.15,"15m":0.35}.get(tf,0.1)*100
        active={k:v for k,v in scores.items() if abs(v)>0.1}; confs=[]
        if active:
            sgn=[np.sign(v) for v in active.values()]
            if len(set(sgn))>1:
                ks=list(active.keys())
                for i in range(len(ks)):
                    for j in range(i+1,len(ks)):
                        if np.sign(active[ks[i]])!=np.sign(active[ks[j]]): confs.append(f"{ks[i]}({active[ks[i]]:+.2f}) vs {ks[j]}({active[ks[j]]:+.2f})")
        if c>0.7 and len(confs)==0: risk="low"
        elif c>0.5: risk="medium"
        elif len(confs)>=2: risk="extreme"
        else: risk="high"
        return TFPred(timeframe=tf,direction=d,confidence=round(c,3),weighted_score=round(fs,4),contributing=contrib,conflicts=confs[:5],expected_move_pct=round(em,4),risk=risk)
    def _w(self,scores,strengths,tf):
        mods=self.TF_MODS.get(tf,{}); ws=0.0; wt=0.0
        for k in self._bw:
            bw=self._bw[k]; mod=mods.get(k,1.0); st=max(strengths.get(k,0),0.1); ew=bw*mod*st; ws+=scores.get(k,0)*ew; wt+=ew
        return float(ws/wt) if wt>0 else 0.0
    def _conf(self,scores,strengths):
        active={k:v for k,v in scores.items() if abs(v)>0.05}
        if not active: return 0.1
        sgn=[np.sign(v) for v in active.values()]
        if not sgn: return 0.1
        maj=max(set(sgn),key=sgn.count); agr=sgn.count(maj)/len(sgn)
        avg_s=float(np.mean([strengths.get(k,0) for k in active])); vals=list(active.values())
        cons=max(0,1-float(np.std(vals))) if len(vals)>1 else 0.5
        return float(np.clip(agr*0.4+avg_s*0.3+cons*0.3,0.05,0.95))
    def _confs(self,scores):
        active={k:v for k,v in scores.items() if abs(v)>0.1}; confs=[]; keys=list(active.keys())
        for i in range(len(keys)):
            for j in range(i+1,len(keys)):
                if np.sign(active[keys[i]])!=np.sign(active[keys[j]]): confs.append((keys[i],keys[j]))
        total=len(keys)*(len(keys)-1)/2 if len(keys)>1 else 1
        return round(1-len(confs)/total,3) if total>0 else 1.0,confs
    def _risk(self,scores,strengths,agr):
        r=(1-agr)*0.4; avg_s=float(np.mean(list(strengths.values()))) if strengths else 0; r+=(1-avg_s)*0.3
        vals=list(scores.values())
        if len(vals)>1: r+=min(float(np.std(vals)),1)*0.3
        r=float(np.clip(r,0,1))
        if r<0.25: l="low"
        elif r<0.50: l="medium"
        elif r<0.75: l="high"
        else: l="extreme"
        return r,l
    def _act(self,score,conf,agr,risk):
        if risk=="extreme" or conf<self._mc: return "NEUTRAL",conf*0.5
        if score>0.35 and conf>0.7: return "STRONG_LONG",conf
        elif score>0.12: return "LONG",conf*0.85
        elif score<-0.35 and conf>0.7: return "STRONG_SHORT",conf
        elif score<-0.12: return "SHORT",conf*0.85
        return "NEUTRAL",conf*0.6
    def _summ(self,sym,price,act,conf,score,agr,risk,preds):
        tfp=[]
        for tf in ("1m","5m","15m"):
            p=preds.get(tf)
            if p: tfp.append(f"  {tf}: {p.direction} (%{p.confidence*100:.0f} guven, ~%{p.expected_move_pct:.3f})")
        lines=[f"=== {sym} TAHMIN ===",f"Fiyat: ${price:,.2f}",f"KARAR: {act} (guven: %{conf*100:.0f})",f"Ensemble: {score:+.4f}",f"Anlasma: %{agr*100:.0f}",f"Risk: {risk.upper()}","--- TF ---"]
        lines.extend(tfp); return chr(10).join(lines)
    def _brk(self,scores,strengths,preds):
        lines=["--- Bilesen ---"]
        for k in sorted(scores):
            sc=scores[k]; st=strengths.get(k,0); w=self._bw.get(k,0); bl=int(abs(sc)*20); bc="+" if sc>0 else "-"
            lines.append(f"  {k:20s} | skor:{sc:+.3f} | guc:{st:.2f} | agirlik:{w:.2f} | [{bc*bl:>20s}]")
        lines.append("--- TF Katki ---")
        for tf,p in preds.items():
            lines.append(f"  [{tf}]")
            for comp,contrib in p.contributing.items():
                if abs(contrib)>0.001: lines.append(f"    {comp:20s}: {contrib:+.4f}")
            if p.conflicts: lines.append(f"    CELISKI: {', '.join(p.conflicts[:3])}")
        return chr(10).join(lines)
