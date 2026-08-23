# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

```
sembol gir → canlı fiyat · OI · liq · v4.3 skor · SL/TP strateji
```

Cihazda tarayıcı / mitm yok. Public WebSocket + REST.

## Ekran

- Sembol kutusu + chip’ler (`BTC ETH SOL ALLO EDGE SPELL`)
- Canlı fiyat, 24s değişim, hacim
- Yön / skor / confluence / coverage / risk / spoof
- LONG/SHORT/NEUTRAL strateji + SL/TP
- OI, funding, L/S, CVD, likidasyon L/S
- 1m / 5m / 15m
- Bileşen barları (OB TF OI FUND LIQ VOL MOM)
- Bağlantı: Binance `/public` · `/market` · CoinGlass `liq`

## Ağ

| Kaynak | Ne |
|---|---|
| `wss://fstream.binance.com/public/stream` | `@trade` + `@depth20@100ms` |
| `wss://fstream.binance.com/market/stream` | `@kline_1m` `@kline_5m` `@markPrice@1s` `@forceOrder` |
| `wss://wss.coinglass.com/ws` | gzip `liq` (API key yok) |
| Binance / Bybit / OKX REST | ~20 sn yedek OI / funding / kline |

Binance USD-M 2026 split: unrouted `/ws` sadece `/public` basar. `@trade` canlı testte `/public` altında akar (resmi excerpt listelemez).

## Proje

```
android/          Gradle uygulaması (com.coinglass.intel)
.github/workflows/apk.yml
```

minSdk 26 · targetSdk 35 · Compose · OkHttp WS · kotlinx.serialization

## APK (CI)

Her `push` / PR / `workflow_dispatch`:

1. JDK 17
2. Android SDK
3. `android/gradlew :app:assembleDebug`
4. Artifact: **`app-debug`**

İmza yok. Debug APK.

## Yerel

```bash
cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` commit edilmez.

## Not

Skor istihbarattır, emir göndermez, yatırım tavsiyesi değildir. Coverage düşükse pozisyonu küçült.
