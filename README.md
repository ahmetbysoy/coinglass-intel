# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). Keşif = Binance 24s ticker, hacim top-N.

Ürün + faz spec: [`SPEC.md`](SPEC.md) — Faz 0+1+4+2 kodda (6 tab, Radar keşif, SMC).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1. Aynı yönde boş OB/FVG varsa **SMC +8** (cap A).

## Radar

İki liste: **KEŞİF** (USDT-M 24s `quoteVolume` top 40 → vol≥20M ve |chg|≥1.2 → K=12 tam skor) ve **WATCHLIST**. Keşif satırında `+` watchlist'e alır. Watchlist'e otomatik yazılmaz. Fırsat bildirimi varsayılan kapalı.

## Grafik

**1m / 3m / 5m / 15m**. Pinch + kaydırma. REST 600 seed. OB / FVG / Sweep chip **varsayılan kapalı**. Heat açık.

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır. Sol long, sağ short. DOM değil.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
