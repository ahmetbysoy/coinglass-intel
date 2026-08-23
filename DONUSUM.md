# Native Kotlin dönüşüm planı

Tarih: 2026-08-23. Hedef: telefon istihbaratı. Playwright / mitm **cihazda yok**.

## Ürün

```
sembol gir → canlı fiyat / OI / liq / v4 skor / strateji (SL-TP)
```

Tek Compose ekran, koyu tema, chip’ler (BTC ETH SOL ALLO EDGE SPELL).

## Ne taşındı (android/)

| Python | Kotlin | Not |
|---|---|---|
| `realtime/binance_ws.py` | `data/ws/BinanceDualWs.kt` | 2026 `/public` + `/market` dual WS |
| `realtime/wss_client.py` (site) | `data/ws/CoinGlassLiqWs.kt` | gzip `liq` subscribe |
| `pipeline/exchange_feed.py` | `data/rest/ExchangeRest.kt` | BN / Bybit / OKX REST yedek |
| `engine/scalper.py` | `domain/Scalper.kt` | Wilder RSI, CVD, 25bps, weights |
| `engine/indicators.py` | `domain/Indicators.kt` | ATR MACD StochRSI VWAP |
| `engine/market_score.py` | `domain/MarketScorer.kt` | v4.3 skor + spoof + SL/TP |
| `engine/symbols.py` | `domain/Symbols.kt` | registry + convention |
| `engine/prediction_engine.py` | `MarketScorer.ensembleTf` | 1m/5m/15m |
| `realtime/models.py` | `domain/model/Models.kt` | StreamEvent, V4Report |

## Taşınmadı (bilinçli)

Playwright, mitmproxy, Fernet vault, TokenHunter, SQLite crawl, Telegram daemon, official CoinGlass ücretli WSS. Telefonda gerekmez.

## Veri yolu (cihaz)

1. `wss://fstream.binance.com/public/stream` → `@trade` + `@depth20@100ms`
2. `wss://fstream.binance.com/market/stream` → `@kline_1m/5m` `@markPrice@1s` `@forceOrder`
3. `wss://wss.coinglass.com/ws` → `liq` gzip
4. REST her ~20 sn (Binance geo-block olursa Bybit/OKX)

Skor 2 sn’de bir; REST + canlı WS birleşir.

## CI

`.github/workflows/apk.yml` — her push / PR / manuel: JDK 17 + Android SDK + `assembleDebug` → artifact `app-debug`. İmza yok.

## Temizlik

Python **silinmedi**. İlk yeşil debug APK + CI’dan sonra konuşulur.

## Yerel derleme (SDK varsa)

```bash
cd android
# local.properties: sdk.dir=/path/to/Android/sdk
./gradlew :app:assembleDebug
```

Bu sandbox’ta Android SDK / JDK 17 yok; APK GitHub Actions’ta üretilir.
