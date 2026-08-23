# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

Sembol **hardcode değil**. Watchlist = senin yazdığın pair (Room). İlk açılışta öneri çipi yok.

## Grafik

Sadece **1m / 3m / 5m / 15m**. Chip'e dokun, seç.

Sembol değişince REST **600 mum** çeker. WS ilk mumu gelince o 600'ü **ezmez** — aynı timestamp'te canlı bar kazanır, gerisi REST seed kalır.

Ekranda son **90 mum** + hacim şeridi çizilir (600'ü 160dp'ye sıkıştırmak leke oluyordu). VAL/VAH ve duvar çizgileri üstte.

## Skor

Confluence ve momentum artık **1m + 3m + 5m + 15m**. 1h sadece ATR yedek (grafikte yok).

Ensemble satırı da 1m/3m/5m/15m. `Mom` ağırlığı `volume_signal`'e karışmıyor — ayrı `momentum` sinyali.

## Para kaybettiren bug (düzeltildi)

Spoof skoru **50+** ise `bidWall`/`askWall` SL/TP'ye **girmez** — sadece ATR + volume-area (VAL/VAH).

`netRR = (tp − fee − yakın funding) / (sl + fee)`  fee ≈ 0.08% round-trip.

## Kalibrasyon

- n < 8: boost yok
- 8–29: yarı güç
- ≥ 30: tam `tanh` boost

Aynı ağırlık haritası hem ana skor hem `ensembleTf` için kullanılır.

## Sinyal

- Confluence: `tanh(ret/atr)` büyüklük
- Destek/direnç: volume-weighted value area (POC bandı), fractal değil
- Risk: ATR geçmişinin yüzdeliği (statik >4 yerine)
- Alt sinyal BTC 24s ile çelişirse uyarı
- CVD: hacim ağırlıklı eğim

## Ekranlar

| Tab | |
|---|---|
| Canlı | Fiyat → strateji + neden + netRR → skor → 1m/3m/5m/15m grafik |
| Tarayıcı | `\|skor\|` + R/S rozeti + VS karşılaştırma |
| İsabet | WR + expectancy + R-multiple |
| Ayarlar | bildirim, FGS, tema, eşikler |

Watchlist `<` `>` ile klavyesiz geçiş. Funding kalan süre `FUND η`. RSI seçili TF'ye göre.

## Ağ

`/public` trade+depth · `/market` kline 1/3/5/15 + mark + forceOrder · CG liq · REST 600 mum.

## CI

`python -m unittest` + `:app:testDebugUnitTest` + `assembleDebug` → `app-debug`

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

Skor istihbarattır, emir göndermez.
