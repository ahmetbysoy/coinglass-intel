# CoinGlass Intel (Android)

Native Kotlin / Jetpack Compose. Python motorunun telefon portu.

- applicationId: `com.coinglass.intel`
- minSdk 26 / targetSdk 35
- Debug APK: GitHub Actions artifact `app-debug`

## Ekran

Sembol → canlı fiyat, OI, funding, L/S, CG/BN likidasyon, v4.3 skor, SL/TP, 1m/5m/15m.

## Ağ

- Binance USD-M 2026: `/public` (trade+depth20) + `/market` (kline, markPrice, forceOrder)
- CoinGlass site WSS: `wss://wss.coinglass.com/ws` gzip `liq`
- REST yedek: Binance / Bybit / OKX (Binance REST bazı bölgelerde 451)

## Yerel derleme

Android Studio Ladybug+ veya:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
# apk: app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` commit edilmez. CI SDK’yı kendi kurar.
