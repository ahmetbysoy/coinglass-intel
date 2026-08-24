# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). Keşif = Binance 24s ticker, hacim top-N.

Ürün + faz spec: [`SPEC.md`](SPEC.md). Chart range analizi: [`ANALIZ.md`](ANALIZ.md).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1. Aynı yönde boş OB/FVG varsa **SMC +8** (cap A).

A/B + enterOk iken **KAĞIT AÇ**. SL/TP veya 15dk timeout. Emir gitmez.

## Radar

İki liste: **KEŞİF** + **WATCHLIST**. Açık kağıt satırları üstte.

## Grafik

v1.17: `ChartData` / `ChartLevels` / `ChartSignals` imzası. `CandleChartState` (bitmask yok, `Set<Overlay>`). Crosshair **openTime**. Fling `Animatable` + spline decay. Incremental `EmaCache`. Axis label cache. `snapshotFlow` debounce. Loading/Error. FilterChip + ⟲ Oto. Jestler: tek parmak pan, pinch, sağ gutter y-scale, çift dokun sıfırla.

HTML envanter: [`CHART-HTML.md`](CHART-HTML.md).

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır. Sol long, sağ short. DOM değil.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
