# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). İlk açılışta öneri çipi yok.

## Karar katmanı

- **Tek satır karar** + A/B/C/D
- **GİRME** kırmızı şerit: spoof≥50 / coverage<%40 / netRR<1
- Pozisyon boyutu: bakiye × risk% / SL mesafesi (emir yok)

## DOM + cross-exchange

Canlı order book heatmap (spoof duvar sarı). Bybit `orderbook.1` + OKX `bbo-tbt` hafif WS — REST yaşı SourceStale'de BY/OKX.

## Tema

`colorScheme` + `Space`/`Radii`. Açık tema pastel zemin, skor yeşil/kırmızı pastelleşmez.

## Grafik

**1m / 3m / 5m / 15m**. REST 600 seed, WS ezmez. VAL/VAH bant, POC, spoof kesikli.

## Tarayıcı

Filtre chip, sparkline, liste/grid, öne çıkanlar. ScanCoordinator: FGS ve Worker tek yol, spam = 10dk + Δskor<8 sessiz.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
