# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1.

İlk açılış 3 adımlı tur. Ayarlar’dan tekrar.

## Widget

Ana ekran widget: Room watchlist’ten `|skor|` top 3. Watchlist boşsa “pair yaz” — BTC/ETH yok.

## Grafik

**1m / 3m / 5m / 15m**. Pinch zoom + kaydırma. REST 600 seed, WS ezmez.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
