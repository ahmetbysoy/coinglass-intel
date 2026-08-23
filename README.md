# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1.

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır.

- Sol kırmızı = long liq
- Sağ yeşil = short liq
- Grafiğin sağ şeridinde overlay

DOM kitap değil — gerçek tasfiye akışı.

## Grafik

**1m / 3m / 5m / 15m**. Pinch + kaydırma. REST 600 seed.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
