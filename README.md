# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room).

Ürün + faz spec: [`SPEC.md`](SPEC.md) — Faz 0+1 kodda (6 tab).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1.

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır. Sol long, sağ short. DOM değil.

## Grafik

**1m / 3m / 5m / 15m**. Pinch + kaydırma. REST 600 seed.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
