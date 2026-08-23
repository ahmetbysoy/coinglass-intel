# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1.

İlk açılışta 3 adımlı tur (sabit coin yok). Ayarlar’dan tekrar açılır.

## Para

Pozisyon boyutu = bakiye × risk% / SL. Günlük risk şeridi: 8+ kayıt veya 3 kayıp üst üste → DUR.

## DOM + borsalar

Canlı kitap heatmap. Binance WS + Bybit/OKX BBO. REST 600 mum, WS ezmez.

## Grafik

Sadece **1m / 3m / 5m / 15m**. VAL/VAH bant, spoof kesikli.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
