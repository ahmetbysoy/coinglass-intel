# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). İlk açılışta öneri çipi yok.

## Karar katmanı

Ekranın en üstü artık sayı yığını değil:

- **Tek satır karar** + A/B/C/D notu (coverage + confluence + spoof + risk + netRR)
- **GİRME** kırmızı şerit: spoof≥50 veya coverage<%40 veya netRR<1
- Spoof 50+ ise kart açıkça der: SL duvarı yok, ATR+VAL

## Tema (artık yalan değil)

`MaterialTheme.colorScheme` + `Space`/`Radii` token. Ayarlar → Koyu tema kapanınca **tüm ekranlar** açık (pastel zemin) olur. Yeşil/kırmızı skor pastelleşmez.

## Grafik

Sadece **1m / 3m / 5m / 15m** chip.

REST **600 mum** seed. WS ezmez. Ekranda 60/90/150 pencere, yatay kaydırma.

VAL/VAH **bant**, POC çizgi. Spoof duvarı kesikli/sarı. CVD divergence nokta.

## Skor

Confluence ve momentum **1m + 3m + 5m + 15m**. 1h sadece ATR yedek.

`Mom` ayrı `momentum` sinyali. `netRR` fee + yakın funding dahil.

## Bugfix (audit)

- Sembol değişince eski WS event **drop** (B3)
- Watchlist tarama `chunked(5)+200ms`, 418/429 backoff (B4)
- FGS açıkken ScoreWorker taramaz (B8)
- `PEPE` → `1000PEPEUSDT` format denemesi (liste değil, 1000/1M kuralı)
- REST yaşı SourceStale'de
- İsabet: 5m / 15m / 1h + equity + bileşen wr

## Ekranlar

| Tab | |
|---|---|
| Canlı | KARAR → fiyat → strateji → grafik → bileşen → metrik |
| Tarayıcı | filtre chip + sparkline + öne çıkanlar |
| İsabet | çoklu ufuk + equity |
| Ayarlar | gerçek tema, eşikler |

## Ağ

`/public` trade+depth · `/market` kline 1/3/5/15 + mark + forceOrder · CG liq · REST 600 mum.

## CI

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
