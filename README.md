# CoinGlass Intel

Native Kotlin / Jetpack Compose telefon istihbaratı.

```
sembol yaz → fiyat + STRATEJİ (ilk bakış) → skor → yapıya duyarlı SL/TP
```

Cihazda tarayıcı / mitm yok. Public WebSocket + REST.

Sembol listesi **hardcode değil**. Watchlist = senin yazdığın pair’ler (Room). Boş açılışta 3 adımlık “nasıl çalışır”, önerilen coin chip’i yok.

## Ekranlar

| Tab | Ne |
|---|---|
| Canlı | Strateji kartı fiyatın hemen altında. Kaynak-bazlı BAYAT (fiyat/OI/fund/OB). 1h/4h mum + swing/OB duvar overlay. |
| Tarayıcı | Watchlist `\|skor\|` sıra. VS ile 2 sembol karşılaştır. |
| İsabet | Room outcome settle: t+5m/15m/1h, win/loss, 15m win-rate. |
| Ayarlar | Bildirim, FGS, koyu/açık tema, eşikler, bayat sn. Tema toggle bağlı. |

## Sinyal / kanıt

- Her skor 2 dk dedup ile `outcomes` tablosuna yazılır.
- 5m / 15m / 1h sonra gerçek fiyatla `win` işaretlenir.
- Strateji kartı: `bu tahmin gecmiste %X isabetli (n=N, 15m)`.
- n≥8 settle olunca bileşen ağırlıkları aligned-return ile `tanh` boost alır (sabit kalmaz).

## SL/TP (structure)

ATR yüzdesi taban. Yakındaki swing support/resistance ve OB duvarı ATR bandının içindeyse SL/TP onları kullanır. Kaynak satırı: `atr+swing-sup+ob-bid`.

## Spoof

Snapshot 10× medyan tek başına yetmez. Book geçmişinde 1.5–12 sn içinde kaybolan duvar `spoofFromHistory`.

## CVD divergence

Hacim ağırlıklı eğim + son barlar medyan hacmin altındaysa sinyal yok.

## Ağ

| Kaynak | Ne |
|---|---|
| `wss://fstream.binance.com/public/stream` | `@trade` + `@depth20@100ms` |
| `wss://fstream.binance.com/market/stream` | kline / markPrice / forceOrder |
| `wss://wss.coinglass.com/ws` | gzip `liq` |
| Binance / Bybit / OKX REST | ~20 sn yedek |

REST hataları artık yutulmuyor: log + UI `REST host HTTP 451`. Coverage neden düştüğü görünür.

Likidasyon: hiç frame yoksa **N/A**, frame var ve 0 ise **$0**. Sessiz sıfır yok.

Binance 2026: unrouted `/ws` sadece `/public`. `@trade` canlı testte `/public`.

## Bildirim tek otorite

- Foreground service açıkken yalnızca o bildirir (30 sn).
- Kapalıysa WorkManager 15 dk Room doldurur **ve** o zaman bildirir.
- İkisi birden aynı anda push etmez.

## Proje

```
android/                 Compose uygulama
python/engine/           curves + history_store (CI unit)
.github/workflows/apk.yml
```

minSdk 26 · targetSdk 35 · Room v2 (`outcomes`) · WorkManager · DataStore · OkHttp

## APK (CI)

push / PR / workflow_dispatch:

1. `python -m unittest` (`python/tests`)
2. `./gradlew :app:testDebugUnitTest`
3. `assembleDebug` → artifact **`app-debug`**

## Yerel

```bash
cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
cd ../python && python -m unittest discover -s tests -v
```

## Temizlik

Kullanılmayan `IntelViewModel` (BTCUSDT hardcode) silindi. `__pycache__` yok.

## Not

Skor istihbarattır, emir göndermez. n=0 isabette kör güvenme. Coverage veya REST hataları varsa küçült.
