"""Schema Analyzer - JSON yapi analizi."""
import logging
from typing import Any, Dict, List, Tuple
from collections import Counter
import orjson
log = logging.getLogger("registry.schema")
class SchemaAnalyzer:
    def __init__(self, confidence=0.7): self._conf=confidence
    def analyze_samples(self, bodies):
        parsed=[]
        for b in bodies:
            try: parsed.append(orjson.loads(b))
            except Exception: continue
        if not parsed: return {"error":"json_yok","sample_count":len(bodies)}
        s=self._struct(parsed); s["sample_count"]=len(parsed); s["top_type"]=type(parsed[0]).__name__; return s
    def _struct(self, objs, depth=0):
        if depth>4: return {"type":"max_depth"}
        if not objs: return {"type":"empty"}
        f=objs[0]
        if isinstance(f,dict): return self._dict(objs,depth)
        elif isinstance(f,list): return self._list(objs,depth)
        return self._scalar(objs)
    def _dict(self, dicts, depth):
        keys: Counter=Counter(); kt: Dict[str,Counter]={}; ks: Dict[str,List]={}
        for d in dicts:
            if not isinstance(d,dict): continue
            for k,v in d.items(): keys[k]+=1; kt.setdefault(k,Counter())[type(v).__name__]+=1; ks.setdefault(k,[]).append(v)
        total=len(dicts); fields={}
        for k,cnt in keys.items():
            freq=cnt/total if total>0 else 0
            if freq<self._conf: continue
            dt=kt[k].most_common(1)[0][0]; fi={"type":dt,"frequency":round(freq,3),"occurrences":cnt}
            if dt=="dict" and depth<3:
                nested=[v for v in ks[k] if isinstance(v,dict)]
                if nested: fi["schema"]=self._dict(nested,depth+1)
            elif dt=="list" and depth<3:
                nl=[v for v in ks[k] if isinstance(v,list)]
                if nl: fi["schema"]=self._list(nl,depth+1)
            fields[k]=fi
        return {"type":"object","fields":fields,"total_keys":len(keys)}
    def _list(self, lists, depth):
        items=[]; lens=[]
        for l in lists:
            if isinstance(l,list): lens.append(len(l)); items.extend(l[:10])
        if not items: return {"type":"array","item_type":"empty"}
        it: Counter=Counter(type(x).__name__ for x in items); dom=it.most_common(1)[0][0]
        r={"type":"array","item_type":dom,"avg_len":round(sum(lens)/len(lens),1) if lens else 0}
        if dom=="dict" and depth<3:
            di=[x for x in items if isinstance(x,dict)]
            if di: r["item_schema"]=self._dict(di,depth+1)
        return r
    def _scalar(self, vals):
        t: Counter=Counter(type(v).__name__ for v in vals); dom=t.most_common(1)[0][0]; r={"type":dom}
        if dom in ("int","float"):
            nums=[v for v in vals if isinstance(v,(int,float))]
            if nums: r["min"]=min(nums); r["max"]=max(nums); r["sample"]=nums[0]
        elif dom=="str":
            strs=[v for v in vals if isinstance(v,str)]
            if strs: r["avg_len"]=round(sum(len(s) for s in strs)/len(strs),1); r["sample"]=strs[0][:200]
        return r
