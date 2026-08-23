# CoinGlass Intelligence

Futures piyasa istihbaratı. CoinGlass sitesinden (Playwright + mitmproxy + site WSS) ve borsalardan (Binance / Bybit / OKX REST + Binance USD-M dual WebSocket) veri toplar; 6 motorlu ensemble + **v4.3 Production / Scalper** skor üretir; 1m / 5m / 15m yön + SL/TP stratejisi basar.

## Android (native Kotlin) — aktif dönüşüm

Telefon uygulaması yazıldı: `android/` (Jetpack Compose). Cihazda browser / mitm **yok**.

```
sembol gir → canlı fiyat · OI · liq · v4.3 skor · SL/TP strateji
```

| Katman | Dosya |
|---|---|
| Binance 2026 dual WS | `android/app/.../data/ws/BinanceDualWs.kt` |
| CoinGlass site liq WS | `android/app/.../data/ws/CoinGlassLiqWs.kt` |
| BN / Bybit / OKX REST | `android/app/.../data/rest/ExchangeRest.kt` |
| v4.3 + scalper | `android/app/.../domain/MarketScorer.kt` + `Scalper.kt` |
| UI | `android/app/.../ui/IntelScreen.kt` |

**APK:** her push’ta GitHub Actions debug artifact üretir (imza yok).

```
.github/workflows/apk.yml  →  artifact adı: app-debug
```

Detaylı dönüşüm haritası: [`DONUSUM.md`](DONUSUM.md). Python **silinmedi** — ilk yeşil APK + CI sonrası temizlik.

İki çalışma biçimi var:

| Mod | Giriş | Ne yapar |
|---|---|---|
| Crawl + analiz | `python main.py BTCUSDT` | Browser + mitm + hook → SQLite → 6 motor + v4.3 |
| Exchange-only | `python main.py ALLOUSDT --exchange-only` | Browser yok. Binance/Bybit/OKX REST → v4.3 + scalper |
| Realtime daemon | `python daemon.py --symbols BTC ETH` | Playwright bir kez token avı, sonra saf Python WSS + 5 sn anomali + Telegram |
| Binance WS smoke | `python -m realtime.binance_ws BTCUSDT` | 2026 `/public` + `/market` dual bağlantı, 15 sn sayaç |

Python masaüstü/sunucu tarafı **çalışıyor**. Native Kotlin Android (“phone intel”) APK + GitHub Actions henüz **başlamadı**. Python silinmez; ilk yeşil debug APK + CI’dan sonra temizlik.

---

## İçindekiler

1. [Durum (2026-08-23)](#1-durum-2026-08-23)
2. [Mimari](#2-mimari)
3. [Kurulum](#3-kurulum)
4. [Kullanım](#4-kullanım)
5. [Paradigma 1 — WebSocket-first daemon](#5-paradigma-1--websocket-first-daemon)
6. [Playwright + mitmproxy collector](#6-playwright--mitmproxy-collector)
7. [CoinGlass şifreleme ve WSS](#7-coinglass-şifreleme-ve-wss)
8. [Binance USD-M WebSocket 2026 `/public` vs `/market`](#8-binance-usd-m-websocket-2026-public-vs-market)
9. [Exchange REST feed](#9-exchange-rest-feed)
10. [Analiz motorları](#10-analiz-motorları)
11. [v4.3 + Scalper skor](#11-v43--scalper-skor)
12. [Sembol kaydı](#12-sembol-kaydı)
13. [Veri katmanı](#13-veri-katmanı)
14. [Güvenlik / kasa](#14-güvenlik--kasa)
15. [Konfigürasyon ve ortam değişkenleri](#15-konfigürasyon-ve-ortam-değişkenleri)
16. [Proje ağacı](#16-proje-ağacı)
17. [Canlı test notları](#17-canlı-test-notları)
18. [Bilinen kısıtlar](#18-bilinen-kısıtlar)
19. [Telefon uygulaması (Kotlin) yol haritası](#19-telefon-uygulaması-kotlin-yol-haritası)
20. [CI planı](#20-ci-planı)
21. [Temizlik politikası](#21-temizlik-politikası)
22. [Yasal / etik](#22-yasal--etik)

---

## 1. Durum (2026-08-23)

### Çalışıyor

- Playwright persistent Chromium crawl (`collector/browser_session.py`). `--no-sandbox` / `--disable-dev-shm-usage` sandbox ve sunucu için açık.
- `JSON.parse` hook: CoinGlass REST body’si `encryption: true` olsa bile tarayıcı içinde çözülmüş nesne yakalanır (`window.__CG_PLAIN` / `__CG_EVENTS`).
- mitmproxy ikinci göz: Playwright’ın kaçırdığı HTTP/WS `data/mitm/traffic.jsonl`’e yazılır.
- Fernet session vault: cookie/token plaintext yazılmaz.
- Endpoint keşif + `data/endpoint_cache/endpoint_catalog.json`.
- SQLite WAL, 7 tablo, batch insert, 72 saat retention.
- 6 klasik motor + `PredictionEngine` (1m/5m/15m ensemble).
- v4.3 `MarketScorer` + scalper (Wilder RSI, CVD divergence, ±25 bps depth, EV bant, quality weights).
- `ExchangeFeed`: Binance / Bybit / OKX public REST paralel.
- CoinGlass site WSS: `wss://wss.coinglass.com/ws`, gzip JSON, `{"method":"subscribe","params":[{"channel":"liq","type":"-1"}]}`.
- Official CoinGlass WSS: `wss://open-ws.coinglass.com/ws-api?cg-api-key=` (ücretli key).
- Binance USD-M **dual-lane** WS (`realtime/binance_ws.py`) — resmi 2026 split + canlı test router.
- `--exchange-only` skor: ALLOUSDT son run **$0.284970**, **HAFIF BULLISH +14.2**, OB ±25bps **+18.2% bid**, coverage **38%**, LONG SL $0.282120 (−1%) TP $0.290669 (+2%). History n=1.

### Bilinçli olarak henüz yok / kırık

- CoinGlass `encryption:true` body’sini **offline** AES çözmek güvenilir değil. Anahtarlar JS’ten çıkarıldı (v55/v66/v77) ama ECB+PKCS7+zlib yolu düz metin üretmiyor. Çözüm: hook + site WSS + borsa feed.
- Bu sandbox’tan Binance **REST** sık geo-block (`Service unavailable from a restricted location`). Bybit/OKX REST çalışıyor. Binance **WS** aynı sandbox’tan çalışıyor (2026-08-23 ~00:11 UTC).
- Official CoinGlass WSS ücretli key ister; key yoksa site public WSS veya hunt.
- `forceOrder` 15 sn smoke’ta 0 frame — likidasyon seyrek, beklenen.
- Kotlin Compose “phone intel” APK **yazılmadı**.
- GitHub repo + `.github/workflows/apk.yml` **yazılmadı**.
- `BinanceFuturesWS` henüz `daemon.py` içine bağlanmadı (opsiyonel sonraki adım).
- Public proxy farm / proxyscrape / hardcoded IP listesi **yok ve eklenmeyecek**.

---

## 2. Mimari

```
                         ┌─────────────────────────────────────┐
                         │           CLI / Orkestratör          │
                         │   main.py          daemon.py         │
                         └──────────────┬──────────┬────────────┘
                                        │          │
              ┌─────────────────────────┘          └──────────────────┐
              ▼                                                       ▼
   Playwright + mitm + hook                                 TokenHunter (tek sefer)
   13 CoinGlass sayfası                                     browser kapanır
              │                                                       │
              ▼                                                       ▼
   NetworkInterceptor + MitmBridge                          CoinGlassWSS  (liq/ticker)
   EndpointDiscovery                                        BinanceFuturesWS  (/public+/market)
   TrafficMerger                                            RestPump (official REST, key varsa)
              │                                                       │
              ▼                                                       ▼
   DataNormalizer ──► SQLite WAL                            LiveCache (+ opsiyonel Redis)
              │                                                       │
              ├─ OI / Funding / Liq / OB / VP / Whale                 │
              │                                                       ▼
              ▼                                              SignalLoop (5 sn)
   PredictionEngine (ensemble 1m/5m/15m)                    TelegramPush
              │
              ├─ ExchangeFeed (BN / Bybit / OKX REST)
              ▼
   MarketScorer v4.3 + scalper ──► HistoryStore (score_history.csv)
              │
              ▼
        KARAR + STRATEJİ + SL/TP
```

İki veri yolu birbirini tamamlar:

- CoinGlass crawl / WSS → likidasyon haritası, site-özel OI/funding, whale akışı. Şifreli REST body’si hook ile yakalanır.
- Borsa REST + Binance WS → gerçek fiyat, depth, trade, markPrice, kline, funding. CoinGlass şifresi kırılmasa bile skor üretilir (`--exchange-only`).

Telefon uygulaması (plan) aynı ikinci yolu kullanır: cihazda browser/mitm yok, sadece public WS + REST + skor.

---

## 3. Kurulum

Python **3.11+** (geliştirme ortamı 3.13).

```bash
cd coinglass_intelligence
bash setup.sh
```

`setup.sh` şunları yapar:

1. `python3` kontrolü
2. `.venv` oluşturur
3. `pip install -r requirements.txt`
4. `playwright install chromium` (+ mümkünse `install-deps`)
5. `data/db`, `data/browser_profile`, `data/endpoint_cache`, `data/logs` klasörleri

Manuel:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -U pip
pip install -r requirements.txt
python -m playwright install chromium
python verify.py
```

Not: `.venv` bazı snapshot/CI ortamlarında persist edilmez. Yoksa yukarıyı tekrar çalıştır. Playwright Chromium cache genelde `~/.cache/ms-playwright` altında kalır.

### Bağımlılıklar

| Paket | Neden |
|---|---|
| `playwright>=1.49.0` | Persistent Chromium, hook, sayfa gezinme |
| `mitmproxy>=11.0.0` | İkinci göz (HTTP/WS tap) |
| `websockets>=13.0` | CoinGlass + Binance WSS |
| `aiohttp>=3.10.0` | Exchange REST, Telegram, CoinGlass official REST |
| `numpy>=1.26.0` / `scipy>=1.14.0` | Z-score, KDE, peak, ensemble |
| `pandas>=2.2.0` | Tablo/seri |
| `rich>=13.9.0` | Terminal UI |
| `cryptography>=43.0.0` | Fernet vault |
| `aiosqlite>=0.20.0` | Async SQLite WAL |
| `orjson>=3.10.0` | Hızlı JSON |
| `msgpack>=1.1.0` | Serileştirme |
| `redis>=5.0.0` | Opsiyonel live cache (`REDIS_URL`) |

---

## 4. Kullanım

```bash
source .venv/bin/activate

# Tam pipeline — 13 sayfa, mitm, 6 motor, v4.3
python main.py BTCUSDT

# Headless + belirli sayfalar
python main.py ETH --pages open_interest funding_rate liquidation --headless

# Sadece endpoint keşfi
python main.py SOL --discover-only --headless

# mitm kapalı (sadece Playwright + hook)
python main.py BTC --no-mitm --headless

# Browser yok — borsa REST + v4.3 + scalper (en hızlı skor)
python main.py ALLOUSDT --exchange-only

# Debug log
python main.py BTCUSDT --log-level DEBUG

# Realtime daemon
python daemon.py --symbols BTC ETH SOL
python daemon.py --no-hunt                 # kayıtlı session / API key
python daemon.py --hunt --headed           # login için görünen browser

# Binance dual-lane smoke (15 sn, stdout JSON)
python -m realtime.binance_ws BTCUSDT
python -m realtime.binance_ws ALLOUSDT
```

İlk headed crawl’da CoinGlass’a **manuel login** olun. Profil `data/browser_profile/` altında kalır; sonraki koşularda login gerekmez. Cookie ayrıca Fernet kasaya yazılır.

---

## 5. Paradigma 1 — WebSocket-first daemon

Asıl 7/24 süreç browser taşımaz.

```
[Token Acquisition]  Playwright (tek sefer)  →  WSS URL / key / cookie  →  browser kapat
                              │
                              ▼
[Real-Time Daemon]   websockets + asyncio
                     ├─ CoinGlassWSS   (site veya official)
                     └─ (sonraki) BinanceFuturesWS
                              │
                              ▼
[LiveCache]          bellek + opsiyonel Redis
                              │
                              ▼
[SignalLoop]         her 5 sn anomali tara
                              │
                              ▼
[TelegramPush]       dedup 45 sn
```

### Official CoinGlass kanalları

`wss://open-ws.coinglass.com/ws-api?cg-api-key=...`

| Kanal | İçerik |
|---|---|
| `liquidation_orders` | Anlık likidasyonlar |
| `futures_ticker@{ex}_{symbol}` | Fiyat / OI / funding snapshot |
| `futures_trades@{ex}_{symbol}@{minUsd}` | Büyük işlemler (varsayılan min $100k) |

`build_channels()` ticker borsaları: Binance, OKX, Bybit (`config.WebSocketConfig.ticker_exchanges`).

### Site public WSS

`wss://wss.coinglass.com/ws`

Subscribe (frontend’den yakalandı):

```json
{"method":"subscribe","params":[{"channel":"liq","type":"-1"}]}
```

Sembol için `type` base asset (`BTC`, `ETH`, `ALLO` …). Frame’ler **gzip** JSON (`\x1f\x8b`). Client `gzip.decompress` + `orjson` yapar. Heartbeat: her 20 sn `"ping"` string.

Hunt sayfaları (`realtime/token_hunter.py`):

- `https://www.coinglass.com/`
- `https://www.coinglass.com/liquidations`
- `https://www.coinglass.com/pro/futures/LiquidationHeatMap`

Hunt sonucu `data/session/credentials.json` + `data/session/last_hunt.json`.

### Anomali eşikleri (`DaemonConfig`)

| Sinyal | Varsayılan | Ne zaman |
|---|---|---|
| Likidasyon | `$250_000` | Tek emir ≥ eşik |
| Liq küme | `3× eşik` ve ≥4 emir | 15–5s pencerede |
| Balina trade | `$1_000_000` | Son 3 büyük |
| Fiyat hareketi | `%0.25` | ticker serisi |
| Funding ekstrem | `0.0005` | long/short crowded |
| OI sıçrama | `%1.5` | OI serisi |
| Telegram min aralık | `45s` | `kind:symbol:severity` dedup |
| Sinyal tarama | `5s` | `signal_interval_sec` |

Telegram yoksa (`TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` boş) sink no-op; alert yine loglanır.

---

## 6. Playwright + mitmproxy collector

Fikir resmi API değil: **kullanıcı gibi gez, trafiği iki gözle yakala, endpoint keşfet, analize sok.**

### Göz 1 — Playwright

`BrowserSession`:

- Persistent profil, locale `en-US`, timezone `Europe/Istanbul`
- Image/media/font/stylesheet blok
- `JSON.parse` hook → `window.__CG_PLAIN` (max 120 event)
- Sayfa sonrası scroll simülasyonu + 4 sn bekleme
- Cookie restore (vault’tan)

`NetworkInterceptor`: domain whitelist, body capture (max 5 MB), WS frame (max 10k), 2 sn dedup.

Whitelist: `coinglass.com`, `capi.coinglass.com`, `api.coinglass.com`, `open-api-v3/v4.coinglass.com`, `cdn.coinglass.com`, `ws/wss/open-ws.coinglass.com`, `s3.coinglass.com`.

### Göz 2 — mitmproxy

`MitmBridge` `mitmdump`’ı `127.0.0.1:18080` üzerinde açar, Playwright’ı bu proxy’ye verir.

`collector/mitm_addon.py`:

- Cookie / Authorization header **redacted**
- Response header’dan `v`, `ev`, `encryption` tutulur (AES versiyon izi)
- Body max 200 KB, sadece JSON benzeri
- WS preview; gzip frame `<gzip>` olarak işaretlenir
- Sink: `data/mitm/traffic.jsonl`

mitm kalkmazsa pipeline Playwright-only’ye düşer (`CFG.mitm.enabled = False`).

`TrafficMerger` iki gözü birleştirip registry’ye yazar.

### Taranan sayfalar

| Anahtar | Path |
|---|---|
| `home` | `/` |
| `futures_overview` | `/futures` |
| `open_interest` | `/futures/OpenInterest` |
| `funding_rate` | `/futures/FundingRate` |
| `liquidation` | `/liquidations` |
| `long_short` | `/futures/LongShortRatio` |
| `orderbook` | `/futures/OrderBook` |
| `bitcoin_oi` | `/futures/BitcoinOpenInterest` |
| `ethereum_oi` | `/futures/EthereumOpenInterest` |
| `whale` | `/whale` |
| `exchange_flow` | `/exchange-flow` |
| `options_oi` | `/options/OpenInterest` |
| `global_chart` | `/GlobalChart` |

Sembol URL: `https://www.coinglass.com{path}/{BASE}` (`BTCUSDT` → `BTC`).

---

## 7. CoinGlass şifreleme ve WSS

Site REST cevapları çoğu zaman:

```json
{ "encryption": true, "data": "<base64 ciphertext>" }
```

Response header `v` ∈ {`55`, `66`, `77`}. JS bundle’dan çıkan AES-128-ECB anahtarları (PKCS7):

| `v` | key (utf-8, 16 byte) |
|---|---|
| 55 | `170b070da9654622` |
| 66 | `d6537d845a964081` |
| 77 | `863f08689c97435b` |

Offline deneme (ECB + PKCS7 ± zlib) **düz metin üretmedi**. Muhtemel ek adımlar (IV, gzip-before-encrypt, özel padding, per-session key) henüz kilitlenmedi. Bu yüzden:

1. Tarayıcı içinde `JSON.parse` hook — frontend zaten çözer.
2. Site WSS gzip JSON — şifresiz stream.
3. Borsa REST/WS — fiyat/OI/funding/depth buradan.

Official REST: `https://open-api-v4.coinglass.com` + header `CG-API-KEY`. `RestPump` 20 sn’de bir OI exchange-list + funding exchange-list çeker.

---

## 8. Binance USD-M WebSocket 2026 `/public` vs `/market`

Kaynaklar:

- [Important WebSocket Change Notice](https://developers.binance.com/docs/derivatives/usds-margined-futures/websocket-market-streams/Important-WebSocket-Change-Notice)
- [Derivatives change log](https://developers.binance.com/docs/derivatives/change-log) — 2026-03-05 duyuru, 2026-04-02 notu, **decommission 2026-04-23**

### Ne değişti

2026-03-05’te üç ayrı base eklendi. 2026-04-23’te legacy:

- `wss://fstream.binance.com/ws`
- `wss://fstream.binance.com/stream`

**kapatıldı.** Unrouted / eski URL’ye bağlanan istemci **sadece `/public`** stream’lerini görür. `@markPrice` eski URL’de **susar**.

| Base | Rol |
|---|---|
| `wss://fstream.binance.com/public` | Yüksek frekans |
| `wss://fstream.binance.com/market` | Normal market data |
| `wss://fstream.binance.com/private` | User data (`listenKey`) — bu projede yok |

Combined stream (tercih edilen):

```
wss://fstream.binance.com/public/stream?streams=btcusdt@trade/btcusdt@depth20@100ms
wss://fstream.binance.com/market/stream?streams=btcusdt@kline_1m/btcusdt@markPrice@1s
```

Kurallar (resmi):

- Sembol **küçük harf** (`btcusdt@trade`)
- Max **1024** stream / bağlantı
- Sunucu ~3 dk’da bir ping; pong **10 dk** içinde
- Inbound ≤ **10 mesaj/sn**
- Bağlantı ömrü ~**24 saat** — reconnect şart

### Resmi stream haritası (excerpt)

**`/public`**

| Stream | Not |
|---|---|
| `<symbol>@bookTicker` | Best bid/ask |
| `!bookTicker` | Tüm semboller |
| `<symbol>@depth` / `@depth@500ms` / `@depth@100ms` | Diff book |
| `<symbol>@depth5/10/20` + `@100ms` / `@500ms` | Partial book |

**`/market`**

| Stream | Not |
|---|---|
| `<symbol>@aggTrade` | Agg trade |
| `<symbol>@markPrice` / `@markPrice@1s` | Mark + funding |
| `!markPrice@arr` / `@1s` | Tüm mark |
| `<symbol>@kline_<interval>` | Mum |
| continuous kline | Sözleşme tipi |
| `<symbol>@miniTicker` / `@ticker` | 24h ticker |
| `<symbol>@forceOrder` / `!forceOrder@arr` | Likidasyon |
| `<symbol>@compositeIndex` | Endeks |
| `!contractInfo` | Sözleşme bilgisi |
| `<symbol>@assetIndex` | Asset index |

Resmi excerpt **`@trade`’i listelemez**. USD-M dokümanı resmi olarak `@aggTrade`’i `/market`’e koyar.

### Canlı test (kullanıcı + sandbox, 2026-08-23)

Kullanıcı notu (aynen uygulanır):

> Canlı testte `@trade`, `/market` altında veri basmadı; `/public` altında sorunsuz veri bastı. O yüzden `@trade` + `depth20` public bağlantısına alındı. `kline` / `markPrice` / `forceOrder` market bağlantısında kaldı.

Sandbox smoke (`python -m realtime.binance_ws BTCUSDT`, 15 sn, ~00:11 UTC):

| Lane | Frame | Kırılım | Fiyat |
|---|---|---|---|
| `/public` | 198 | trade 62, depth 136 | px ≈ 77103.75 |
| `/market` | 75 | kline 60, markPrice 15 | mark ≈ 77101.19 |
| forceOrder | 0 | — | seyrek, beklenen |

Sonuç: router **canlı gözlemi** resmi excerpt’ten üstün tutar.

### Bu projedeki router

Dosya: `realtime/binance_ws.py`

```
route_lane(stream):
    trade | depth | bookTicker  →  public
    diğer her şey               →  market
```

Varsayılan plan (`default_streams`):

```
public:  {sym}@trade
         {sym}@depth20@100ms

market:  {sym}@kline_1m
         {sym}@kline_5m
         {sym}@markPrice@1s
         {sym}@forceOrder
```

`BinanceFuturesWS` iki `_Lane` açar, her biri auto-reconnect (2s, 4s, … max 20s), ping_interval 20s. Combined URL `urllib.parse.quote(..., safe='@/_')`.

Parse:

| kind | fiyat | ekstra |
|---|---|---|
| trade / aggTrade | `p` | `m=true` → sell (taker sell), trade_id |
| depth | mid(bid0, ask0) | bids/asks top 20 |
| kline | `k.c` | o/h/l/c/v/i, `closed` |
| markPrice | `p` | funding `r`, index `i` |
| forceOrder | `o.ap` / `o.p` | side `S`, status `X` |
| ticker / bookTicker | `c` / `b` / `a` | — |

Event tipi `StreamEvent` (`realtime/models.py`): `channel, kind, symbol, exchange="Binance", timestamp, price, size_usd, side, extra`.

`extra["lane"]` = `public` | `market`.

### Private lane

`PRIVATE_BASE = wss://fstream.binance.com/private` tanımlı, **kullanılmıyor**. User-data / listenKey / emir gönderme yok. Bu bir istihbarat okuyucusudur.

---

## 9. Exchange REST feed

`pipeline/exchange_feed.py` — CoinGlass şifreli olduğu için **gerçek fiyat / OI / funding / depth** buradan gelir.

Üç borsa paralel (`asyncio.gather`):

### Binance USD-M (`https://fapi.binance.com`)

| Endpoint | Alan |
|---|---|
| `/fapi/v1/ticker/24hr` | last, 24h %, quoteVolume |
| `/fapi/v1/depth?limit=100` | orderbook |
| `/fapi/v1/trades?limit=100` | taker buy/sell → CVD |
| `/fapi/v1/fundingRate?limit=30` | funding serisi |
| `/fapi/v1/openInterest` | anlık OI |
| `/futures/data/openInterestHist` | 5m OI geçmiş |
| `/futures/data/globalLongShortAccountRatio` | L/S |
| `/futures/data/takerlongshortRatio` | taker buy/sell hist |
| `/fapi/v1/klines` 5m/15m/1h | mum (200/100/100) |

### Bybit (`https://api.bybit.com`)

`/v5/market/tickers|orderbook|recent-trade|open-interest|funding/history|account-ratio` — `category=linear`.

### OKX (`https://www.okx.com`)

`instId = {COIN}-USDT-SWAP` — ticker, books, trades, funding-rate-history, open-interest.

Geo-block: sandbox’tan Binance REST sık `restricted location`. Bybit/OKX gelir. Skor o zaman Bybit/OKX fiyat + kısmi coverage ile devam eder; `warnings` listesine düşer. **WS tarafı Binance için çalışır** — telefon/daemon için asıl canlı kaynak o.

Yardımcılar (aynı dosya):

- `analyze_order_book` — Binance / Bybit / OKX format birleştir, mid, imb, wall
- `analyze_trades_binance` — `isBuyerMaker` → CVD %
- `analyze_funding` — current / avg / trend
- `analyze_ls` — borsa bazlı L/S
- `analyze_oi_change` — newest vs oldest %

---

## 10. Analiz motorları

Klasik 6’lı (`engine/*_analyzer.py`) crawl verisi üzerinde çalışır. Ensemble ağırlıkları `config.AnalysisThresholds.ensemble_weights`.

| Motor | Ağırlık | Ne bakar |
|---|---|---|
| `OIAnalyzer` | %20 | Z-score, polyfit, Pearson, 4 rejim: accumulation / distribution / expansion / contraction |
| `FundingAnalyzer` | %15 | EMA(8/24) cross, OU mean-reversion, ekstrem, contrarian |
| `LiquidationAnalyzer` | %20 | `find_peaks` + KDE, kaskad lojistik, L/S imb |
| `OrderBookAnalyzer` | %15 | bid/ask imb, derinlik eğimi, duvar ≥ $5M |
| `VolumeProfile` | %15 | VWAP ±2σ, POC/VAH/VAL %70, OBV, spike Z>2.5 |
| `WhaleTracker` | %15 | Üstel zaman ağırlığı, ≥$1M cluster, Z>3σ |

### PredictionEngine

6 skoru timeframe modifikatörüyle birleştirir:

| TF | öne çıkan | geri çekilen |
|---|---|---|
| 1m | OB 1.5, whale 1.4, volume 1.2, liq 0.8 | funding 0.3, OI 0.6 |
| 5m | dengeli (~1.0), liq 1.2 | funding 0.7 |
| 15m | OI 1.4, funding 1.3 | OB 0.6, whale 0.7 |

Yön: skor > +0.08 `UP`, < −0.08 `DOWN`, aksi `FLAT`.

Aksiyon:

- `STRONG_LONG` skor>+0.35 ve güven>0.7
- `LONG` >+0.12
- `STRONG_SHORT` <−0.35 ve güven>0.7
- `SHORT` <−0.12
- `NEUTRAL` risk=extreme veya güven < 0.55

Çelişen çiftler, dominant sinyal, risk `low/medium/high/extreme` rapora yazılır.

v4 skor, crawl sinyali boşsa veya fiyatı 0 ise aynı anahtarlara (`oi_momentum` …) `SimpleSignal` basarak ensemble’ı doldurur. Böylece `--exchange-only` da 1m/5m/15m üretir.

---

## 11. v4.3 + Scalper skor

`engine/market_score.py` + `engine/scalper.py` + `engine/indicators.py`.

Kaynak: EDGEUSDT Intelligence Engine v4.3 Production + v4 Scalper Edition. **Kör kopya değil** — OB ±25bps, Wilder RSI, CVD divergence, EV bant, quality weights, SYMBOLS_REGISTRY entegre edildi. Proxy IP listesi **alınmadı**.

### Bileşenler

| Kod | Girdi | Skor nasıl |
|---|---|---|
| OB | 3 borsa depth, ±25 bps pencere | `(bid−ask)/(bid+ask)*100` |
| TF | Binance son 100 trade | CVD % |
| OI | OI değişim × 24h fiyat yönü | +OI+fiyat=+60, +OI−fiyat=−40, −OI+fiyat=+30, −OI−fiyat=−60 |
| Funding | güncel rate | `clip(-rate*10000, ±100)` contrarian |
| Liq | L/S oranı | >2 → −40, <0.5 → +40 |
| Vol | 5m vol / medyan | yön × min(vr*15, 100) |
| Mom | 5m/15m/1h Wilder RSI + ret_3 + confluence | ortalama + 0.5×confluence |

Toplam: mevcut bileşenlerin **kalite-ağırlıklı** ortalaması, clip ±100.

Yön eşikleri:

| skor | yön |
|---|---|
| > +30 | BULLISH |
| > +10 | HAFIF BULLISH |
| < −30 | BEARISH |
| < −10 | HAFIF BEARISH |
| aksi | NEUTRAL |

Coverage = kullanılan ağırlıkların toplamı (yüzde). ALLOUSDT exchange-only’de **38%** — Binance REST geo-block + kısmi Bybit/OKX.

### Scalper ekstraları

- **Wilder RSI(14)** — klasik Wilder smoothing (`engine/scalper.py:wilder_rsi`)
- **StochRSI**, MACD(12,26,9), BB %B, ATR%, VWAP, S/R test sayısı (`candle_metrics`)
- **CVD serisi** `takerlongshortRatio` hist’ten, fiyat ile ölçekli
- **CVD divergence** son 20 bar linear slope: fiyat↑ CVD↓ = bearish, tersi bullish
- **depth_25bps** mid ± 0.25% içindeki bid/ask
- **EV bant** TF bazlı: `price * (1 ± 1.5 * ATR%)`, RSI<30 / >70 bias kaydırır
- **quality_weights** baz `{OB:20, TF:20, OI:15, Funding:10, Liq:15, Vol:10, Mom:10}` × (0.5 + 0.5×quality). Vol24 < $10M ise OB/TF +3; ATR% > 4 ise Vol/Mom +3
- **Spoof** skoru 0–100: mid’den >0.5% uzakta 10× medyan duvar, |imb|>30, L/S>2.5
- **Risk** 0–100: ATR, funding ekstrem, L/S uç, düşük hacim
- **Strateji** ATR% × 1.5 SL, 2R TP. BULL → LONG, BEAR → SHORT, NEUTRAL → range  ±1%

### History

`engine/history_store.py` → `data/score_history.csv`

Kolonlar: `ts, symbol, price, score, direction, ob, tf, oi, funding, liq, vol, mom, confluence`

`validate(symbol)` ardışık run’larda skor vs sonraki fiyat % değişim Pearson korelasyonu. n<2 ise “history yetersiz”.

Kayıtlı örnek (ALLOUSDT):

```
2026-08-22 23:53  $0.28559  skor +1.47   NEUTRAL
2026-08-22 23:58  $0.28497  skor +14.24  HAFIF BULLISH   OB +18.18
```

---

## 12. Sembol kaydı

`engine/symbols.py` — `SYMBOLS_REGISTRY`. Bilinmeyen sembol convention ile çalışır: `FOO` → `FOOUSDT`, OKX `FOO-USDT-SWAP`.

Şu an kayıtlı:

| Sembol | CoinGecko id |
|---|---|
| EDGEUSDT | edgex |
| ALLOUSDT | allora |
| SPELLUSDT | spell-token |
| XAUTUSDT | tether-gold |
| BLUAIUSDT | blueai |

`resolve_symbol("allo")` → `ALLOUSDT` + borsa ticker’ları.

---

## 13. Veri katmanı

### SQLite (`data/db/market_intelligence.db`)

WAL, `synchronous=NORMAL`, batch 100, retention 72 saat, vacuum 6 saatte bir.

| Tablo | İçerik |
|---|---|
| `open_interest` | oi_usd/coin, 1h/4h/24h % |
| `funding_rate` | rate, predicted, next, interval |
| `liquidation` | long/short/total usd, count, largest |
| `long_short` | lsr, hesap sayıları, top trader |
| `orderbook_snapshot` | mid, spread, bid/ask total, imb |
| `price_ohlc` | OHLCV |
| `whale_orders` | side, size, type, is_market |

Index: `(symbol, ts DESC)` her tabloda.

### Normalizer

`pipeline/data_normalizer.py` — 7 CoinGlass JSON şeklini tek dataclass’a çevirir. Hook payload’ı da aynı yoldan geçer.

### Live cache

`realtime/cache.py` — sembol+kind ring buffer. Opsiyonel Redis (`REDIS_URL`). Daemon ticker tablosu buradan.

---

## 14. Güvenlik / kasa

- Cookie/session: `data/session/session.vault` **Fernet**. Anahtar `data/session/.vault.key` (mode 0600) veya env `CG_VAULT_KEY`.
- Plaintext cookie dump **yok**.
- mitm addon Cookie/Authorization’ı `<redacted>` yazar.
- Log’da official WSS URL `cg-api-key=***` olarak kesilir.
- Public proxy listesi, proxyscrape, hardcoded IP **yok, eklenmeyecek**.
- `data/session/credentials.json` hunt çıktısıdır — git’e koyma.
- `data/browser_profile/` Chromium profili — git’e koyma.
- Bu repo emir göndermez, private listenKey kullanmaz, exploit/PoC yazmaz.

Önerilen `.gitignore` (APK CI gelene kadar elle uygula):

```
.venv/
__pycache__/
data/browser_profile/
data/session/
data/mitm/
data/logs/
data/db/
*.vault
.vault.key
```

---

## 15. Konfigürasyon ve ortam değişkenleri

Tek kaynak: `config.py` → `CFG = SystemConfig()`.

### Ortam

| Değişken | Ne |
|---|---|
| `COINGLASS_API_KEY` | Official WSS + RestPump. Yoksa site WSS / hunt |
| `TELEGRAM_BOT_TOKEN` | Alert bot |
| `TELEGRAM_CHAT_ID` | Hedef sohbet |
| `REDIS_URL` | `redis://127.0.0.1:6379` — yoksa bellek |
| `CG_VAULT_KEY` | Fernet key override |
| `CG_MITM_SINK` | mitm JSONL yolu (addon) |

### Önemli eşikler (`AnalysisThresholds` / `DaemonConfig`)

- OI Z ±1.5, delta %2, lookback 48
- Funding ekstrem ±0.05 (analiz), daemon 0.0005
- Liq cluster min $10M, cascade ×3
- OB bull 0.65 / bear 0.35, 20 seviye, duvar $5M
- VWAP sapma %0.5, volume spike ×2.5
- Whale min $1M, cluster 60s, 3 yönlü
- Tahmin min güven 0.55

Timezone varsayılan: `Europe/Istanbul`.

---

## 16. Proje ağacı

```
coinglass_intelligence/
├── README.md                      ← bu dosya
├── config.py                      tüm ayarlar
├── main.py                        crawl + analiz + --exchange-only
├── daemon.py                      WSS-first realtime
├── verify.py                      import sağlık kontrolü
├── setup.sh                       venv + pip + chromium
├── requirements.txt
│
├── collector/
│   ├── browser_session.py         persistent Chromium + JSON.parse hook
│   ├── network_interceptor.py     request/response/WS yakalama
│   ├── endpoint_discovery.py      otomatik API keşfi
│   ├── mitm_bridge.py             mitmdump start/stop + JSONL tail
│   ├── mitm_addon.py              mitmproxy addon (ikinci göz)
│   ├── traffic_merger.py          iki göz birleştir
│   └── session_vault.py           Fernet cookie kasa
│
├── registry/
│   ├── endpoint_registry.py       endpoint_catalog.json
│   └── schema_analyzer.py         JSON şema + kategori
│
├── pipeline/
│   ├── data_normalizer.py         7 format → tek tip
│   ├── data_store.py              SQLite WAL, 7 tablo
│   ├── exchange_feed.py           Binance/Bybit/OKX REST + analiz helper
│   └── websocket_listener.py      eski generic WSS (daemon artık wss_client kullanır)
│
├── engine/
│   ├── oi_analyzer.py
│   ├── funding_analyzer.py
│   ├── liquidation_analyzer.py
│   ├── orderbook_analyzer.py
│   ├── volume_profile.py
│   ├── whale_tracker.py
│   ├── prediction_engine.py       ensemble 1m/5m/15m
│   ├── market_score.py            v4.3 skor + strateji + spoof
│   ├── scalper.py                 Wilder RSI, CVD div, 25bps, EV, weights
│   ├── indicators.py              mum metrikleri
│   ├── history_store.py           score_history.csv
│   ├── symbols.py                 SYMBOLS_REGISTRY
│   └── util.py                    to_float, fmt_price, safe_path
│
├── realtime/
│   ├── models.py                  StreamEvent, Anomaly, SessionBundle
│   ├── cache.py                   live ring + Redis
│   ├── token_hunter.py            tek sefer Playwright avı
│   ├── wss_client.py              CoinGlass site + official WSS
│   ├── binance_ws.py              2026 /public + /market dual client
│   ├── rest_pump.py               official CoinGlass REST snapshot
│   ├── signal_loop.py             5 sn anomali
│   └── telegram_push.py           Markdown alert + dedup
│
└── data/
    ├── db/market_intelligence.db
    ├── browser_profile/           Chromium profil (gitignore)
    ├── endpoint_cache/endpoint_catalog.json
    ├── session/                   vault + hunt (gitignore)
    ├── mitm/traffic.jsonl
    ├── logs/
    └── score_history.csv
```

---

## 17. Canlı test notları

Tarih: **2026-08-23**, ortam: Arena sandbox, TZ Europe/Istanbul.

### Binance WS

```
python -m realtime.binance_ws BTCUSDT
```

15 sn: public 198 frame (trade 62 + depth 136) px~77103.75; market 75 frame (kline 60 + markPrice 15) mark~77101.19; forceOrder 0. **“kanka binance sorunsuz çalışıyor.”**

### Exchange-only skor (ALLOUSDT)

```
python main.py ALLOUSDT --exchange-only
```

- Fiyat $0.284970
- Yön HAFIF BULLISH, skor +14.2, confluence ~0 (tek TF)
- OB ±25bps +18.2% bid
- Coverage 38% (Binance REST geo-block; Bybit/OKX + kısmi alan)
- Strateji: LONG SL $0.282120 (−1%) TP $0.290669 (+2%)
- Ensemble 1m UP, 5m/15m FLAT (coverage düşük, Mom/Vol boş)

### CoinGlass crawl

Aynı sembolde site liq: short **$1,795,919** vs long **$32,769**. Ensemble NEUTRAL / $0 fiyat — REST body şifreli, hook o koşuda fiyat basmamış. Exchange-only bu boşluğu kapatır.

---

## 18. Bilinen kısıtlar

1. **Offline AES** çözümü yok. Hook / WSS / borsa kullan.
2. **Binance REST geo-block** bazı IP’lerden. WS çalışır. Telefon/daemon için WS tercih et.
3. **Official CoinGlass WSS** ücretli. Key yoksa site `wss.coinglass.com`.
4. **forceOrder** seyrek; 15 sn test sıfır gelebilir, bu hata değil.
5. **`@trade` resmi excerpt’te yok.** Router canlı teste güvenir → `/public`. Binance mapping değiştirirse `route_lane` + `default_streams` güncellenir.
6. **verify.py** henüz `realtime.binance_ws` / `engine.market_score` satırını listemez; import yine çalışır.
7. **daemon.py** henüz `BinanceFuturesWS` başlatmaz. İstihbarat için `python -m realtime.binance_ws` veya sonraki entegrasyon.
8. Kotlin / CI yok — aşağıdaki plan.

---

## 19. Telefon uygulaması (Kotlin) — uygulandı

Kullanıcı kararı: **phone intel**. Cihazda Playwright / mitm / Chromium **yok**.

Kaynak: `android/` (applicationId `com.coinglass.intel`, minSdk 26, target 35).

```
[Sembol gir]  →  canlı fiyat
              →  OI / funding / L/S
              →  likidasyon (BN forceOrder + CG liq)
              →  v4 skor / yön / coverage
              →  strateji (SL/TP) + 1m 5m 15m
```

Tek Compose ekran, koyu tema, chip’ler (`BTC ETH SOL ALLO EDGE SPELL`).

### Cihaz veri kaynakları (public only)

| Kaynak | Ne |
|---|---|
| `wss://fstream.binance.com/public/stream?...` | `@trade` + `@depth20@100ms` |
| `wss://fstream.binance.com/market/stream?...` | `@kline_1m` `@kline_5m` `@markPrice@1s` `@forceOrder` |
| `wss://wss.coinglass.com/ws` | `liq` gzip JSON (API key gerekmez) |
| Bybit / OKX / Binance REST | 20 sn’de bir yedek OI/funding/kline |

Taşınmayan: Playwright, mitmproxy, Fernet vault, SQLite crawl, TokenHunter, Telegram.

Python **silinmedi** — ilk yeşil APK + CI’ya kadar referans.

---

## 20. CI — debug APK

Dosya: `.github/workflows/apk.yml`

Her `push` / `pull_request` / `workflow_dispatch`:

1. JDK 17 (Temurin)
2. Android SDK (`android-actions/setup-android`)
3. `android/gradlew :app:assembleDebug`
4. Artifact: **`app-debug`** (`android/app/build/outputs/apk/debug/*.apk`)

İmza yok. Play Store yok. İlk yeşil koşu = “APK CI yeşil”.

---

## 21. Temizlik politikası

Kullanıcı kararı: **after_apk**.

Silinmeyecek (şimdilik):

- Tüm Python ağacı
- `data/` (profil, vault, db, history)
- crawl / mitm kodu

İlk Kotlin debug APK artifact’i inip workflow yeşil olduktan sonra konuşulacak temizlik:

- kullanılmayan crawl artıklarını ayıklama
- `verify.py` yeni modülleri ekleme
- `.gitignore` sıkılaştırma
- gerekirse Python’u `python/` altına taşıma

O günden önce `rm -rf` yok.

---

## 22. Yasal / etik

- Kendi hesabın / kendi oturumun. Sistem kullanıcı gibi gezer; 4 sn bekleme + scroll var.
- CoinGlass ve borsa ToS’una uy. Official API key varsa onu kullan.
- Bu yazılım **sinyal / istihbarat** üretir, emir göndermez.
- Skor yatırım tavsiyesi değildir. Coverage düşükse (ör. %38) kararı küçült.
- Üçüncü parti motorlardan yalnızca uyan parçalar alındı; proxy farm / IP listesi yok.

---

## Hızlı komut özeti

```bash
# kurulum
bash setup.sh && source .venv/bin/activate && python verify.py

# en hızlı skor (browser yok)
python main.py ALLOUSDT --exchange-only

# tam CoinGlass crawl
python main.py BTCUSDT --headless

# 7/24 daemon
export COINGLASS_API_KEY="cg_..."          # opsiyonel
export TELEGRAM_BOT_TOKEN="123:abc"        # opsiyonel
export TELEGRAM_CHAT_ID="123456"
python daemon.py --symbols BTC ETH SOL --no-hunt

# Binance 2026 dual WS
python -m realtime.binance_ws BTCUSDT
```

Sonraki adım (sen “yap” dersen): Kotlin Compose phone intel + GitHub repo + `assembleDebug` artifact. Python durur.
