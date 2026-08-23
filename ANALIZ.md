> Durum notu (kod, 2026-08-24): Bu analizdeki §2 SMC/paper/MTF boşluklarının
> bir kısmı spec fazlarında kapandı (Faz 2 SMC, Faz 3 paper, Faz 7 crosshair).
> **Hâlâ doğru ve acil:** §1 chart range bug (VAL/VAH + spoof duvarı mumları eziyor).
> Aşağıdaki metin kaynak analiz; silinmedi.

# COINGLASS INTEL — Sert Analiz + TODO Promptu
Repo: github.com/ahmetbysoy/coinglass-intel (Kotlin/Compose)
Tarih: 2026-08-24

---

## 1) CHART BUG — screenshot'taki asıl sorun

`CandleChart.kt` incelendi. Kırmızı alarm: grafik "küçük görünüyor" değil,
**range hesabı bozuk**. Kanıt kod içinde:

```kotlin
val extra = listOf(entry, sl, tp, support, resistance, bidWall, askWall, poc).filter { it > 0 }
val lo = (listOf(lows) + extra).min()
val hi = (listOf(highs) + extra).max()
```

`support/resistance` → `Structure.volumeArea()` **son 120 mumun** VAL/VAH'ı.
Chart ise `window` (60/90/150) kadarını gösteriyor. `bidWall/askWall` ise
`walls()` fonksiyonunda **en büyük hacimli tek emri** alıyor — spoof/anomali
emir olabilir, gerçek fiyattan onlarca % uzakta durabilir.

Sonuç: bu dört değerden biri ekrandaki 90 mumun min/max aralığının çok
dışına düşünce, `lo/hi` range'i dev genişliyor. Mumlar range'in üst
%20-25'ine sıkışıyor, geri kalan alan da şu satırdaki teal bant ile doluyor:

```kotlin
if (support > 0 && resistance > 0 && resistance > support) {
    val top = y(resistance); val bot = y(support)
    drawRect(Color(0x3300E5C3), Offset(0f, top), Size(size.width, (bot-top)...))
}
```

Screenshot'taki o büyük düz yeşilimsi/teal blok — bu rect. Ekrana "sığmayan
küçük mum" görüntüsü buradan geliyor, canvas boyutu değil.

İkinci sorun: `.height(188.dp)` sabit. Telefon ekranının büyük kısmı boşta
kalıyor, "tam ekrana sığdır" isteği haklı — chart kartı flex/weight almıyor.

**Düzeltilecek nokta (sadece bu, baştan yazma yok):**
1. `lo/hi` hesabına `support/resistance/bidWall/askWall`'ı doğrudan sokma.
   Bunun yerine: `range = highs-lows`; eğer extra değer `price ± range*1.5`
   dışındaysa çizgiyi çiz ama range'e katma (clamp/skip).
2. `support/resistance` hesaplamasını `shown` (görünen mum seti) üzerinden
   yap, sabit son-120 üzerinden değil — chart penceresiyle SR'ı senkronla.
3. Chart yüksekliğini `.height(188.dp)` yerine `Modifier.weight(1f)` + üst
   Column'da `fillMaxHeight` ver, min 260-320dp taban koy. Telefon dikeyde
   chart alanı ekranın en az %45-50'sini kaplamalı.
4. Y ekseni fiyat etiketleri yok — sağ kenara 4-5 tane fiyat çizgisi + label
   şart, yoksa "bu seviye ne kadar uzakta" göz kararı kalıyor.

---

## 2) Genel kod eleştirisi (ağır, filtresiz)

**MarketScorer.kt / Curves.kt / Verdict.kt** — matematik tarafı gerçekten
iyi kurulmuş: tanh bazlı yumuşatma, coverage-ağırlıklı skor, netRR fee
düşülmüş, ATR percentile risk. Bunu övüyorum, nadiren böyle temiz görürüm.

Ama sinyal→para dönüşümünde kritik boşluklar var:

- **Backtest yok.** Skor üretiliyor ama "bu skor tipi geçmişte kaç kez
  tuttu" hiçbir yerde ölçülmüyor. `OutcomeTracker.kt` var (112 satır) —
  bakılmalı, muhtemelen sadece kayıt tutuyor, geri besleme (weight
  kalibrasyonuna) bağlanmamış olabilir. `WeightCalibrator.kt` 44 satır —
  çok küçük, gerçek bir öğrenme değil sabit bonus/malus gibi duruyor.
- **Spoof tespiti tek anlık snapshot + kısa history.** Gerçek spoofing
  (iceberg, layering) saniyeler içinde defalarca gelip gidiyor; 16 örnekli
  pencere yetersiz. Bu "REST BAYAT" etiketi ekranda görünüyor zaten —
  demek ki veri tazeliği sorunu kullanıcıya da yansıyor, yamalı değil kabul
  edilmiş durumda.
- **MTF confluence yok.** 1m/3m/5m/15m ayrı ayrı skorlanıyor ama "3 TF aynı
  yönde" gibi bir confluence bonus'u MarketScorer'da görünmüyor (LiqMap Pro
  v5'te bu var — `findMTFConfluence`). Bu, kazanç fırsatını kaçıran en
  büyük eksik: tek TF sinyali gürültüye çok açık.
- **CVD divergence var ama tek noktada kullanılıyor** (sadece log satırı ve
  chart'ta nokta işareti). Skor'a girmiyor, sadece "gösteriliyor". Gösterip
  skorlamayan özellik = kullanıcı için süs, para için işe yaramaz.
- **Order Block / FVG (SMC) hiç yok.** Bu segmentte (liq + SMC) rakip
  ürünler bunu temel alıyor; olmaması ciddi eksik.
- **Sweep/liquidation-hunt tespiti yok.** LiqHeat var (68 satır, ısı
  haritası) ama "bu likidasyon zonu süpürüldü mü, kapanış geri döndü mü"
  mantığı MarketScorer'da yok. Isı haritası sadece görsel, aksiyon
  tetiklemiyor.
- **Paper trading / forward-test yok.** Skor üretiliyor, kullanıcı manuel
  giriyor, sonuç hiçbir yere yazılmıyor (OutcomeTracker'ın neyi track
  ettiğine bakmak lazım — muhtemelen sadece geçmiş skor snapshot'ı,
  gerçekleşen PnL değil).

---

## 3) UI/UX — Android kullanıcısı neden bu app'i kullansın?

Şu an cevap net değil. "Karar / spoof / netRR / REST bayat" chip'leri
teknik doğru ama bir scalper'ın 2 saniyede karar vermesi için okunabilir
değil. Şu an ekranda:
- Chip satırı: durum bayrakları (fiyat ok / OI ok / fund ok / OB ok / REST
  bayat) — bunlar debug bilgisi, kullanıcı bunları "trade edilebilir mi"
  diye okumaz, geliştirici okur.
- Ana karar (LONG/SHORT/BEKLE + grade) chart'ın **altında değil**, ayrı bir
  sekmede muhtemelen (Karar sekmesi ayrı, Grafik sekmesi ayrı). Bu bölünme
  yanlış: fiyata bakarken karara bakamıyorsun, tab değiştirmen gerekiyor.
- Renk dili tutarsız görünüyor: OB ok yeşil, REST BAYAT turuncu — ama grade
  (A/B/C/D) nerede gösteriliyor ekranda görünmüyor, tab'a gitmek gerekiyor.

**Bir scalper neyi 1 bakışta görmek ister (öncelik sırası):**
1. Yön + grade + enterOk (GİR / GİRME) — en büyük punto, en üstte, renkli.
2. Entry/SL/TP + netRR — sayısal, tek satır, chart üstünde sabit.
3. Chart (tam ekran, fiyat ekseni ile).
4. Spoof/risk/coverage — küçük ikincil chip, ama "GİRME" tetikleyicisiyse
   kırmızı flash olmalı, chip'te gömülü kalmamalı.
5. Diğer her şey (OI, funding, L/S, CVD) — chart altında kaydırılan strip,
   LiqMap Pro v5'teki `infoStrip` deseni gibi (o kısım gerçekten iyi
   çözülmüş, oradan ilham alınabilir).

---

## 4) TODO PROMPTU — kronolojik, uygulanacak sırayla

Bu bölümü doğrudan bir sonraki oturumda "şunu yap" diye kullan.

### FAZ 0 — Acil bugfix (bugün)
1. `CandleChart.kt`: range hesabından support/resistance/bidWall/askWall'ı
   çıkar, sadece "aralık içindeyse çiz" mantığına çevir (yukarıdaki madde
   1'deki 4 adım). Sadece bu fonksiyonu düzelt, dosyayı yeniden yazma.
2. Chart yüksekliğini `weight(1f)` yap, min yükseklik 280dp.
3. Sağ kenara fiyat ekseni etiketleri ekle (4-5 çizgi, `y()` fonksiyonunu
   kullanarak metin çiz — Canvas'ta zaten `drawLine` var, `drawContext`
   üzerinden `nativeCanvas.drawText` ile fiyat yaz).

### FAZ 1 — Karar ekranı birleşimi (bu hafta)
4. Karar kartını (yön/grade/enterOk/entry-sl-tp) `IntelScreen`'de
   `CandleChart`'ın **hemen üstüne** sabitle — ayrı sekmeye gitmeden
   görünsün.
5. Chip satırını ikiye ayır: "tetikleyici" (GİRME sebebi varsa kırmızı,
   tam genişlik, en üstte) vs "bilgi" (fiyat ok / OI ok gibi — küçük, gri,
   alta).
6. `netRR` ve `spoof` değerlerini karar kartında büyük punto göster; şu an
   muhtemelen küçük text içinde kayboluyor.

### FAZ 2 — Sinyal kalitesi (gelecek hafta)
7. **MTF confluence** ekle: `MarketScorer`'a 1m/3m/5m/15m yön uyumu
   bonus/malus'u sok (LiqMap Pro v5'teki `findMTFConfluence` mantığından
   ilham al — fiyat kümeleme + TF sayısı ağırlığı).
8. **Sweep/likidasyon-hunt tetikleyici** ekle: `LiqHeat` zonlarından biri
   fitille delinip kapanışta geri dönüyorsa (wick-through + close-back)
   bunu ayrı bir "SWEEP" olayı olarak logla ve skor bonusu ver. `tuk.md`
   içindeki `checkSweep()` mantığı (Kotlin'e uyarlanabilir referans).
9. **Order Block + FVG** modülü ekle (`Structure.kt`'ye yeni fonksiyonlar):
   bearish mum + sonrası impulsive yükseliş = bullish OB; 3 mum kuralıyla
   FVG. Bunlar liq zonlarıyla çakışırsa (`confluence`) skor bonusu.
10. `WeightCalibrator`'ı gerçek geri besleme ile çalıştır: `OutcomeTracker`
    kapanan sinyallerin gerçek sonucunu (WIN/LOSS, gerçek PnL%) tutmalı,
    her N sinyalde bir ağırlıkları buna göre kaydır (basit online learning,
    büyük ML değil — hareketli ortalama win-rate yeter).

### FAZ 3 — Forward-test / paper trading (2 hafta içi)
11. Sinyal üretilince otomatik "paper trade" aç, TP/SL'e göre otomatik
    kapat, WIN/LOSS + PnL%'i local DB'ye yaz (Room zaten var — `AppDb.kt`,
    `Daos.kt`).
12. `PerformanceScreen.kt`'ye win-rate, ortalama PnL, Sharpe, max drawdown
    ekle (formüller `tuk.md`'deki `renderPaperTrades()` içinde hazır,
    doğrudan referans al).
13. Kaldıraç bazlı win-rate kırılımı (hangi x'te daha başarılı) ekle.

### FAZ 4 — Görsel devrim (3-4 hafta)
14. Design token dosyası (`Tokens.kt` zaten var, kontrol et) — tek bir yerden
    renk/spacing/radius yönetimi; chip renklerini buradan tekilleştir.
15. Alt navigasyon sekmelerini kategorik grupla: **Canlı** (Karar+Grafik
    birleşik), **Radar** (Scanner), **İsabet** (Performance/backtest),
    **Nabız** (Funding/OI/CVD/Session — LiqMap Pro v5'teki infoStrip
    tarzı kayan şerit), **Ayarlar**. Şu an 5 sekme varsa bunu koru ama
    "Grafik" ile "Karar"ı birleştir (madde 4).
16. Kayan bilgi şeridi (infoStrip) ekle: near S/R, funding, OI, CVD, 24h
    yüksek/düşük — chart'ın hemen altına, `tuk.md`'deki `#infoStrip`
    animasyon deseninden ilham al (yatay kayan, sonsuz döngü).
17. Sweep/balina anlık uyarısı: ekran flaş + kısa toast + (opsiyonel) titreşim.
    `tuk.md`'deki `flashScreen()` + `navigator.vibrate` deseni Android
    tarafında `Vibrator` API + `Modifier.background` animasyonuyla karşılığı var.

### FAZ 5 — Dayanıklılık (arka planda sürekli)
18. "REST BAYAT" durumu için otomatik fallback zinciri: WS kopunca N saniye
    içinde REST polling'e geç, geri gelince WS'e dön (tuk.md'deki
    `connectWS`/fallback polling deseni referans).
19. Her yeni modülde `trading-signal-qa` SKILL.md'deki QA sürecini uygula
    — skor/strateji dokunan her değişiklik önce bu kontrolden geçsin.

---

## 5) Kapanış notu

En büyük tek kazanım FAZ 0 + FAZ 1'den gelir: chart okunaklı olur, karar
tek bakışta görünür. FAZ 2 (MTF + sweep + SMC) olmadan sinyal kalitesi
rakiplerin gerisinde kalır — coverage/risk/netRR matematiği iyi ama "neden
bu an, neden bu seviye" hikâyesi (confluence) eksik. FAZ 3 olmadan da "bu
gerçekten kazandırıyor mu" sorusuna cevap yok — şu an sistem kör uçuyor,
kimse skorun gerçek başarı oranını ölçmüyor.
