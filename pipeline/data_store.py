"""SQLite zaman serisi deposu."""
import asyncio, logging
from typing import Any, Dict, List, Optional, Tuple
import aiosqlite
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import CFG
log = logging.getLogger("pipeline.store")
SCHEMA="""CREATE TABLE IF NOT EXISTS open_interest (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, oi_usd REAL NOT NULL, oi_coin REAL NOT NULL, change_1h REAL DEFAULT 0, change_4h REAL DEFAULT 0, change_24h REAL DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_oi ON open_interest(symbol, ts DESC);CREATE TABLE IF NOT EXISTS funding_rate (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, rate REAL NOT NULL, predicted_rate REAL DEFAULT 0, next_time REAL DEFAULT 0, interval_hours INTEGER DEFAULT 8, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_fund ON funding_rate(symbol, ts DESC);CREATE TABLE IF NOT EXISTS liquidation (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, long_liq_usd REAL DEFAULT 0, short_liq_usd REAL DEFAULT 0, total_liq_usd REAL DEFAULT 0, liq_count INTEGER DEFAULT 0, largest_liq REAL DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_liq ON liquidation(symbol, ts DESC);CREATE TABLE IF NOT EXISTS long_short (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, long_ratio REAL DEFAULT 0, short_ratio REAL DEFAULT 0, lsr REAL DEFAULT 0, long_accounts INTEGER DEFAULT 0, short_accounts INTEGER DEFAULT 0, top_long_ratio REAL DEFAULT 0, top_short_ratio REAL DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_ls ON long_short(symbol, ts DESC);CREATE TABLE IF NOT EXISTS orderbook_snapshot (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, mid_price REAL DEFAULT 0, spread REAL DEFAULT 0, bid_total REAL DEFAULT 0, ask_total REAL DEFAULT 0, imbalance REAL DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_ob ON orderbook_snapshot(symbol, ts DESC);CREATE TABLE IF NOT EXISTS price_ohlc (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, open REAL DEFAULT 0, high REAL DEFAULT 0, low REAL DEFAULT 0, close REAL DEFAULT 0, vol_usd REAL DEFAULT 0, vol_coin REAL DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_price ON price_ohlc(symbol, ts DESC);CREATE TABLE IF NOT EXISTS whale_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, exchange TEXT NOT NULL, ts REAL NOT NULL, side TEXT DEFAULT '', size_usd REAL DEFAULT 0, price REAL DEFAULT 0, order_type TEXT DEFAULT '', is_market INTEGER DEFAULT 0, endpoint TEXT DEFAULT '');CREATE INDEX IF NOT EXISTS idx_whale ON whale_orders(symbol, ts DESC);"""
class DataStore:
    def __init__(self): self._db_path=CFG.datastore.db_path; self._db: Optional[aiosqlite.Connection]=None; self._batch: List[Tuple[str,tuple]]=[]; self._bs=CFG.datastore.batch_insert_size; self._lock=asyncio.Lock()
    async def connect(self):
        self._db=await aiosqlite.connect(self._db_path)
        if CFG.datastore.wal_mode: await self._db.execute("PRAGMA journal_mode=WAL")
        await self._db.execute("PRAGMA synchronous=NORMAL"); await self._db.executescript(SCHEMA); await self._db.commit(); log.info("DataStore -> %s",self._db_path)
    async def close(self):
        if self._batch: await self.flush()
        if self._db: await self._db.close(); self._db=None
    async def insert_oi(self,recs):
        for r in recs: self._batch.append(("INSERT INTO open_interest (symbol,exchange,ts,oi_usd,oi_coin,change_1h,change_4h,change_24h,endpoint) VALUES (?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.open_interest_usd,r.open_interest_coin,r.oi_change_1h_pct,r.oi_change_4h_pct,r.oi_change_24h_pct,r.source_endpoint)))
        await self._mf()
    async def insert_funding(self,recs):
        for r in recs: self._batch.append(("INSERT INTO funding_rate (symbol,exchange,ts,rate,predicted_rate,next_time,interval_hours,endpoint) VALUES (?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.funding_rate,r.predicted_funding_rate,r.next_funding_time,r.funding_interval_hours,r.source_endpoint)))
        await self._mf()
    async def insert_liquidation(self,recs):
        for r in recs: self._batch.append(("INSERT INTO liquidation (symbol,exchange,ts,long_liq_usd,short_liq_usd,total_liq_usd,liq_count,largest_liq,endpoint) VALUES (?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.long_liq_usd,r.short_liq_usd,r.total_liq_usd,r.liq_count_24h,r.largest_single_liq_usd,r.source_endpoint)))
        await self._mf()
    async def insert_long_short(self,recs):
        for r in recs: self._batch.append(("INSERT INTO long_short (symbol,exchange,ts,long_ratio,short_ratio,lsr,long_accounts,short_accounts,top_long_ratio,top_short_ratio,endpoint) VALUES (?,?,?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.long_ratio,r.short_ratio,r.long_short_ratio,r.long_accounts,r.short_accounts,r.top_trader_long_ratio,r.top_trader_short_ratio,r.source_endpoint)))
        await self._mf()
    async def insert_orderbook(self,recs):
        for r in recs:
            imb=r.bid_total_usd/(r.bid_total_usd+r.ask_total_usd) if (r.bid_total_usd+r.ask_total_usd)>0 else 0.5
            self._batch.append(("INSERT INTO orderbook_snapshot (symbol,exchange,ts,mid_price,spread,bid_total,ask_total,imbalance,endpoint) VALUES (?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.mid_price,r.spread,r.bid_total_usd,r.ask_total_usd,imb,r.source_endpoint)))
        await self._mf()
    async def insert_price(self,recs):
        for r in recs: self._batch.append(("INSERT INTO price_ohlc (symbol,exchange,ts,open,high,low,close,vol_usd,vol_coin,endpoint) VALUES (?,?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.open,r.high,r.low,r.close,r.volume_usd,r.volume_coin,r.source_endpoint)))
        await self._mf()
    async def insert_whale(self,recs):
        for r in recs: self._batch.append(("INSERT INTO whale_orders (symbol,exchange,ts,side,size_usd,price,order_type,is_market,endpoint) VALUES (?,?,?,?,?,?,?,?,?)",(r.symbol,r.exchange,r.timestamp,r.side,r.size_usd,r.price,r.order_type,int(r.is_market_order),r.source_endpoint)))
        await self._mf()
    async def _mf(self):
        if len(self._batch)>=self._bs: await self.flush()
    async def flush(self):
        if not self._batch or not self._db: return
        async with self._lock:
            for sql,params in self._batch: await self._db.execute(sql,params)
            await self._db.commit(); self._batch.clear()
    async def _q(self,sql,params,cols):
        if not self._db: return []
        cur=await self._db.execute(sql,params); rows=await cur.fetchall()
        return [dict(zip(cols,row)) for row in reversed(rows)]
    async def query_oi(self,sym,limit=200): return await self._q("SELECT ts,oi_usd,oi_coin,change_1h,change_4h,change_24h,exchange FROM open_interest WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","oi_usd","oi_coin","change_1h","change_4h","change_24h","exchange"])
    async def query_funding(self,sym,limit=200): return await self._q("SELECT ts,rate,predicted_rate,next_time,interval_hours,exchange FROM funding_rate WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","rate","predicted_rate","next_time","interval_hours","exchange"])
    async def query_liquidation(self,sym,limit=200): return await self._q("SELECT ts,long_liq_usd,short_liq_usd,total_liq_usd,liq_count,largest_liq,exchange FROM liquidation WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","long_liq_usd","short_liq_usd","total_liq_usd","liq_count","largest_liq","exchange"])
    async def query_long_short(self,sym,limit=200): return await self._q("SELECT ts,long_ratio,short_ratio,lsr,long_accounts,short_accounts,top_long_ratio,top_short_ratio,exchange FROM long_short WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","long_ratio","short_ratio","lsr","long_accounts","short_accounts","top_long_ratio","top_short_ratio","exchange"])
    async def query_orderbook(self,sym,limit=200): return await self._q("SELECT ts,mid_price,spread,bid_total,ask_total,imbalance,exchange FROM orderbook_snapshot WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","mid_price","spread","bid_total","ask_total","imbalance","exchange"])
    async def query_price(self,sym,limit=500): return await self._q("SELECT ts,open,high,low,close,vol_usd,vol_coin,exchange FROM price_ohlc WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","open","high","low","close","vol_usd","vol_coin","exchange"])
    async def query_whale(self,sym,limit=200): return await self._q("SELECT ts,side,size_usd,price,order_type,is_market,exchange FROM whale_orders WHERE symbol=? ORDER BY ts DESC LIMIT ?",(sym,limit),["ts","side","size_usd","price","order_type","is_market","exchange"])
    async def count_all(self):
        if not self._db: return {}
        tables=["open_interest","funding_rate","liquidation","long_short","orderbook_snapshot","price_ohlc","whale_orders"]; counts={}
        for t in tables: cur=await self._db.execute(f"SELECT COUNT(*) FROM {t}"); row=await cur.fetchone(); counts[t]=row[0] if row else 0
        return counts
