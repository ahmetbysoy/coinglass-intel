# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). Keşif = Binance 24s ticker, hacim top-N.

Ürün + faz spec: [`SPEC.md`](SPEC.md) — Faz 0+1+4+2+3 kodda (6 tab, Radar, SMC, paper).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1. Aynı yönde boş OB/FVG varsa **SMC +8** (cap A).

A/B + enterOk iken **KAĞIT AÇ**. SL/TP veya 15dk timeout. Emir gitmez. Ayar’da oto-kağıt varsayılan kapalı. Aynı sembol 120sn ikinci paper yok.

## Radar

İki liste: **KEŞİF** + **WATCHLIST**. Açık kağıt satırları üstte. Fırsat bildirimi varsayılan kapalı.

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
