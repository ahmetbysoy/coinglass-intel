"""Data Normalizer - farkli JSON formatlarini TEK standart yapiya cevirir."""
import logging, time
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass
import orjson
log = logging.getLogger("pipeline.normalizer")
@dataclass
class NormalizedOI:
    symbol: str; exchange: str; timestamp: float; open_interest_usd: float; open_interest_coin: float
    oi_change_1h_pct: float; oi_change_4h_pct: float; oi_change_24h_pct: float; source_endpoint: str
@dataclass
class NormalizedFunding:
    symbol: str; exchange: str; timestamp: float; funding_rate: float; predicted_funding_rate: float
    next_funding_time: float; funding_interval_hours: int; source_endpoint: str
@dataclass
class NormalizedLiquidation:
    symbol: str; exchange: str; timestamp: float; long_liq_usd: float; short_liq_usd: float; total_liq_usd: float
    liq_count_24h: int; largest_single_liq_usd: float; liquidation_heatmap: List[Dict[str,float]]; source_endpoint: str
@dataclass
class NormalizedLongShort:
    symbol: str; exchange: str; timestamp: float; long_ratio: float; short_ratio: float; long_short_ratio: float
    long_accounts: int; short_accounts: int; top_trader_long_ratio: float; top_trader_short_ratio: float; source_endpoint: str
@dataclass
class NormalizedOrderBook:
    symbol: str; exchange: str; timestamp: float; bids: List[Tuple[float,float]]; asks: List[Tuple[float,float]]
    bid_total_usd: float; ask_total_usd: float; spread: float; mid_price: float; source_endpoint: str
@dataclass
class NormalizedPrice:
    symbol: str; exchange: str; timestamp: float; open: float; high: float; low: float; close: float
    volume_usd: float; volume_coin: float; source_endpoint: str
@dataclass
class NormalizedWhale:
    symbol: str; exchange: str; timestamp: float; side: str; size_usd: float; price: float
    order_type: str; is_market_order: bool; source_endpoint: str
class DataNormalizer:
    def __init__(self):
        self._p={"open_interest":self._oi,"funding":self._fund,"liquidation":self._liq,"long_short":self._ls,"orderbook":self._ob,"price":self._price,"whale":self._whale}
        self.stats: Dict[str,int]={}
    def normalize(self,cat,raw,ep,target=""):
        p=self._p.get(cat)
        if not p: return None
        try: obj=orjson.loads(raw); obj=self._uw(obj); r=p(obj,ep,target); self.stats[cat]=self.stats.get(cat,0)+1; return r
        except Exception as e: log.debug("Norm [%s]: %s",cat,e); return None
    def _uw(self,o):
        if isinstance(o,dict):
            if "data" in o and isinstance(o["data"],(dict,list)): return o["data"]
            if "result" in o and isinstance(o["result"],(dict,list)): return o["result"]
        return o
    @staticmethod
    def _gf(d,*ks,default=0.0):
        for k in ks:
            if k in d:
                try: return float(d[k])
                except (ValueError,TypeError): continue
        return default
    @staticmethod
    def _gi(d,*ks,default=0):
        for k in ks:
            if k in d:
                try: return int(d[k])
                except (ValueError,TypeError): continue
        return default
    @staticmethod
    def _gs(d,*ks,default=""):
        for k in ks:
            if k in d and d[k]: return str(d[k])
        return default
    @staticmethod
    def _sym(d):
        for k in ("symbol","pair","instrument","contract","coin","token"):
            if k in d and d[k]: return str(d[k]).upper()
        return ""
    @staticmethod
    def _exch(d):
        for k in ("exchange","exch","platform","venue","source"):
            if k in d and d[k]: return str(d[k])
        return "unknown"
    @staticmethod
    def _ts(d):
        for k in ("time","timestamp","ts","t","createTime","updateTime","date"):
            if k in d:
                try:
                    v=float(d[k])
                    if v>1e12: return v/1000.0
                    if v>1e9: return v
                    return v
                except (ValueError,TypeError): continue
        return time.time()
    def _oi(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            out.append(NormalizedOI(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),open_interest_usd=self._gf(it,"openInterestUsd","open_interest_usd","oiUsd","oi_usd","value","openInterest","oi"),open_interest_coin=self._gf(it,"openInterestCoin","open_interest_coin","oiCoin","amount","quantity"),oi_change_1h_pct=self._gf(it,"change1h","change_1h","oiChange1h","h1"),oi_change_4h_pct=self._gf(it,"change4h","change_4h","oiChange4h","h4"),oi_change_24h_pct=self._gf(it,"change24h","change_24h","oiChange24h","h24"),source_endpoint=ep))
        return out
    def _fund(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            out.append(NormalizedFunding(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),funding_rate=self._gf(it,"fundingRate","funding_rate","rate","currentRate","funding"),predicted_funding_rate=self._gf(it,"predictedRate","predicted_funding_rate","nextRate","estimatedRate"),next_funding_time=self._gf(it,"nextFundingTime","next_funding_time","nextTime","fundingTime"),funding_interval_hours=self._gi(it,"intervalHours","interval","fundingInterval",default=8),source_endpoint=ep))
        return out
    def _liq(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            ll=self._gf(it,"longLiqUsd","long_liq_usd","buyVolUsd","longVol","longLiquidation"); sl=self._gf(it,"shortLiqUsd","short_liq_usd","sellVolUsd","shortVol","shortLiquidation")
            hm=[]
            for hk in ("heatmap","liqHeatmap","liquidation_map","clusters"):
                if hk in it and isinstance(it[hk],list):
                    for e in it[hk]:
                        if isinstance(e,dict): hm.append({"price":self._gf(e,"price","p","level"),"usd":self._gf(e,"usd","amount","vol","size"),"side":self._gs(e,"side","direction","type")})
                    break
            out.append(NormalizedLiquidation(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),long_liq_usd=ll,short_liq_usd=sl,total_liq_usd=ll+sl,liq_count_24h=self._gi(it,"count","liqCount","total"),largest_single_liq_usd=self._gf(it,"largestLiq","maxLiq","largest"),liquidation_heatmap=hm,source_endpoint=ep))
        return out
    def _ls(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            lr=self._gf(it,"longRatio","long_ratio","longRate","long"); sr=self._gf(it,"shortRatio","short_ratio","shortRate","short")
            ratio=self._gf(it,"longShortRatio","lsr","ratio","longShort")
            if ratio==0.0 and sr>0: ratio=lr/sr
            out.append(NormalizedLongShort(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),long_ratio=lr,short_ratio=sr,long_short_ratio=ratio,long_accounts=self._gi(it,"longAccounts","longAcct"),short_accounts=self._gi(it,"shortAccounts","shortAcct"),top_trader_long_ratio=self._gf(it,"topLongRatio","topTraderLong"),top_trader_short_ratio=self._gf(it,"topShortRatio","topTraderShort"),source_endpoint=ep))
        return out
    def _ob(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            rb=it.get("bids",it.get("buy",it.get("bid",[]))); ra=it.get("asks",it.get("sell",it.get("ask",[])))
            bids=self._depth(rb); asks=self._depth(ra); bt=sum(p*q for p,q in bids); at=sum(p*q for p,q in asks)
            bb=bids[0][0] if bids else 0.0; ba=asks[0][0] if asks else 0.0; mid=(bb+ba)/2.0 if bb and ba else 0.0; spr=ba-bb if bb and ba else 0.0
            out.append(NormalizedOrderBook(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),bids=bids,asks=asks,bid_total_usd=bt,ask_total_usd=at,spread=spr,mid_price=mid,source_endpoint=ep))
        return out
    @staticmethod
    def _depth(raw):
        r=[]
        if isinstance(raw,list):
            for e in raw:
                if isinstance(e,(list,tuple)) and len(e)>=2:
                    try: r.append((float(e[0]),float(e[1])))
                    except (ValueError,TypeError): continue
                elif isinstance(e,dict):
                    try:
                        p=float(e.get("price",e.get("p",0))); q=float(e.get("qty",e.get("q",e.get("size",e.get("amount",0)))))
                        if p>0: r.append((p,q))
                    except (ValueError,TypeError): continue
        return r
    def _price(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            out.append(NormalizedPrice(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),open=self._gf(it,"open","o","openPrice"),high=self._gf(it,"high","h","highPrice"),low=self._gf(it,"low","l","lowPrice"),close=self._gf(it,"close","c","last","price","lastPrice"),volume_usd=self._gf(it,"volumeUsd","volUsd","quoteVolume","turnover"),volume_coin=self._gf(it,"volume","vol","baseVolume","amount"),source_endpoint=ep))
        return out
    def _whale(self,data,ep,target):
        items=data if isinstance(data,list) else [data]; out=[]
        for it in items:
            if not isinstance(it,dict): continue
            out.append(NormalizedWhale(symbol=self._sym(it) or target,exchange=self._exch(it),timestamp=self._ts(it),side=self._gs(it,"side","direction","type",default="unknown"),size_usd=self._gf(it,"sizeUsd","usdValue","amount","value","notional"),price=self._gf(it,"price","avgPrice","executionPrice"),order_type=self._gs(it,"orderType","type","kind"),is_market_order=self._gs(it,"orderType","type").lower() in ("market","m","taker"),source_endpoint=ep))
        return out
