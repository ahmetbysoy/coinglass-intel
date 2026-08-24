# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). Keşif = Binance 24s ticker, hacim top-N.

Ürün + faz spec: [`SPEC.md`](SPEC.md). Chart range analizi: [`ANALIZ.md`](ANALIZ.md).

## Karar

Tek satır + A/B/C/D. **GİRME** kırmızı: spoof≥50 / coverage<%40 / netRR<1. Aynı yönde boş OB/FVG varsa **SMC +8** (cap A).

A/B + enterOk iken **KAĞIT AÇ**. SL/TP veya 15dk timeout. Emir gitmez.

## Radar

İki liste: **KEŞİF** + **WATCHLIST**. Açık kağıt satırları üstte. v1.24: arama, FilterChip, yönlü sıra, keşif risk=null (filtre yalan söylemez), Locale.US, adaptif grid, VS kazanan.

## Nabız

Seans (Asia/London/NY UTC) + haftalık/aylık open. Funding BN/BY/OKX yan yana, 30dk kala kırmızı. v1.27: canlı nokta, seans chip, eta “sa/dk” + progress, funding spread, L/S çubuğu. İsabet’te izole liq lev10 (emir yok). v1.25 DOM.

## Alarm

Ayarlar’da CRUD. Sembol sen yazarsın (hardcode yok). Tip: fiyat / |skor| / |funding|. ≥ veya ≤. Dedup 10dk / alarm. AlertService + canlı rapor. Widget’a dokunulmaz. v1.26: izin sonucu beklenir, slider bitince yazılır, log-para, silme onayı.

## Grafik

v1.27: Nabız — canlı nokta, seans chip, funding progress/spread. Ayarlar/DOM/Radar.

HTML envanter: [`CHART-HTML.md`](CHART-HTML.md).

## Likidasyon haritası

Canlı `forceOrder` + CoinGlass liq, fiyat kademesine (24 bin) yığılır. Sol long, sağ short. DOM değil. v1.23: dikey fiyat ekseni, tap/sürükle kademe, ısı gradyanı, mıknatıs top-3, mark aralık-dışı oku.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
