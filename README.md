# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). Keşif = Binance 24s ticker, hacim top-N.

Ürün + faz spec: [`SPEC.md`](SPEC.md). Chart range analizi: [`ANALIZ.md`](ANALIZ.md).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1. Aynı yönde boş OB/FVG varsa **SMC +8** (cap A).

A/B + enterOk iken **KAĞIT AÇ**. SL/TP veya 15dk timeout. Emir gitmez.

## Radar

İki liste: **KEŞİF** + **WATCHLIST**. Açık kağıt satırları üstte.

## Nabız

Seans (Asia/London/NY UTC) + haftalık/aylık open. Funding BN/BY/OKX yan yana, 30dk kala kırmızı. İsabet’te izole liq lev10 (emir yok).

## Alarm

Ayarlar’da CRUD. Sembol sen yazarsın (hardcode yok). Tip: fiyat / |skor| / |funding|. ≥ veya ≤. Dedup 10dk / alarm. AlertService + canlı rapor. Widget’a dokunulmaz.

## Grafik

v1.20: overlay chip DataStore persist. Magnet crosshair (OHLC’ye yapışır). Header’da KARAR satırı. Jestler: tek parmak pan, pinch, sağ gutter y-scale, çift dokun sıfırla.

HTML envanter: [`CHART-HTML.md`](CHART-HTML.md).

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır. Sol long, sağ short. DOM değil.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
