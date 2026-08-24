# CoinGlass Intel — Ürün + Mühendislik Spec

Kaynak: mevcut repo + `coinglass-intel-analiz-ve-roadmap.md`.  
Hedef: **onaylayan hesap makinesi değil, 3 saniyede karar + kendi başına fırsat bulan motor.**

Kurallar (değişmez):

- Sabit coin listesi yok. Watchlist = kullanıcının yazdığı pair. Keşif listesi = borsadan canlı ticker, hardcode değil.
- Placeholder / sahte skor / “örnek BTC” chip yok.
- Emir gönderilmez. Skor istihbarattır.
- Mimariyi sıfırdan yazma: fonksiyon yama + ekran böl. Room / DataStore / WS / `MarketScorer` korunur.
- Python `python/engine` sadece eğri + outcome lockstep; masaüstü stack geri gelmez.
- Her faz kendi başına derlenir, test edilir, CI yeşil olmadan sonraki faza geçilmez.

---

## 1. Ürün cümlesi

**Şimdi:** Kullanıcı sembol yazar → 7 kaynak okunur → tek satır KARAR + spoof-aware SL + fee-net RR.

**Sonra:** Uygulama ayrıca piyasayı tarar, A/B + spoof düşük + netRR yüksek fırsatları Radar’da gösterir. Kullanıcı 3 saniyede Tab Karar’da gir/girme der; nedeni Tab Grafik’te ölçer.

Saklanacak farklılaşma: spoof≥50 duvarı SL yapmaz, `netRR` fee+funding düşer, GİRME bağırır.

---

## 2. Mevcut durum (spec’in dayandığı gerçek)

| Var | Yok / kırık |
|---|---|
| Compose 6 tab, Room watchlist/snap/outcome/dedup/discovery/paper | Crosshair / tap-fiyat |
| Dual Binance WS + CG liq + Bybit/OKX BBO + SMC overlay + crosshair | — |
| Verdict A–D, GİRME, netRR, SMC +8, pozisyon boyutu, alarm CRUD | — |
| Liq heatmap (24 bin), DOM, VAL/VAH, haftalık/aylık open, izole liq sim | — |
| Radar = KEŞİF (ticker) + WATCHLIST | — |
| Python/Kotlin eğriler ayrı test | İki motoru birbirine karşı CI diff yok |
| Karar tab ince (5 kart) | — |

---

## 3. Bilgi mimarisi — 6 tab

State kaybı yok: `AppViewModel.tab` + her tab `rememberSaveable` scroll.

| Tab | Dosya | Tek ekranda ne var | Scroll |
|---|---|---|---|
| **Karar** | `ui/DecisionScreen.kt` (IntelScreen üstü taşınır) | Arama, chip, GİRME/Funding banner, Verdict, Price, Strategy (SL/TP/netRR), DailyRisk | Asgari. 5 kart sığmalı. |
| **Grafik** | `ui/ChartScreen.kt` | CandleChart ≥320dp, crosshair, DOM, LiqHeat, Metrics, TF, Components, Conn | Evet |
| **Radar** | `ui/ScannerScreen.kt` evrilir | İki liste: **Keşif** (borsa hacim top-N, watchlist dışı) + **Watchlist**. Filtre/sparkline/grid kalır. Keşif → yıldızla watchlist | Evet |
| **İsabet & Risk** | `ui/PerformanceScreen.kt` | 5m/15m/1h WR, equity, attribution, DailyRisk büyütülmüş, PositionSizer + **MarginSimulator**, paper pozisyon listesi | Evet |
| **Nabız** | `ui/PulseScreen.kt` yeni | Cross-ex funding tablo + η, seans (Asya/Londra/NY), haftalık/aylık open, liq nabız özeti | Evet |
| **Ayarlar** | `ui/SettingsScreen.kt` | Tema, eşikler, FGS, bakiye/risk%, tur, **alarm CRUD**, keşif/fırsat bildirimi aç-kapa | Evet |

Karar tab’ında grafik yok (veya 48dp sparkline). Grafik tab’ında Verdict yok — AppViewModel `live` paylaşılır.

Bottom nav: mevcut 4 custom ikona 2 yeni (`ic_nav_radar` zaten scan, `ic_nav_pulse`). 6 öğe sıkışırsa etiket kısalt: Karar / Grafik / Radar / İsabet / Nabız / Ayar.

---

## 4. Veri modeli (ek alanlar)

Mevcut Room v4 + destructive migration. Yeni entity’ler ayrı tablolar, version bump + fallbackToDestructive (debug) kabul.

```
AlertDedupEntity
  symbol PK, lastScore, lastTs

DiscoverySnapEntity          // keşif; watchlist değil
  symbol PK, price, score, grade, spoof, netRr, vol24, coverage, updatedAt

PaperTradeEntity
  id, symbol, side, entry, sl, tp, openedAt, closedAt?, exitPx?, win?, source (auto|manual)

AlarmEntity
  id, symbol, kind (price|score|funding), op (gte|lte), threshold, enabled, label

SessionLevels (hesaplanır, persist şart değil)
  weeklyOpen, monthlyOpen, session (asia|london|ny)
```

`ScanCoordinator.last` **silinir**. Dedup sadece `AlertDedupEntity`.

---

## 5. Fazlar — kabul kriterleri

Her faz: Kotlin unit test + `python -m unittest` + CI `assembleDebug` yeşil. README güncellenir.

### FAZ 0 — Bug (özellik yok)

| ID | İş | Kabul |
|---|---|---|
| 0.1 | Dedup → Room `AlertDedup` | **yapıldı** Worker 15dk sonra aynı skor+Δ<8 tekrar bildirmez. |
| 0.2 | `snapSpoof/2` | **yapıldı** `Curves.SPOOF_SNAP_WEIGHT = 0.5`. |
| 0.3 | Risk mod etiketi | **yapıldı** `riskMode` + UI `pctl`/`stat`. |
| 0.4 | Lockstep CI | **yapıldı** `test_lockstep.py` + `LockstepTest` aynı fixture. |

### FAZ 1 — 6 tab

| ID | İş | Kabul |
|---|---|---|
| 1.1 | Nav 6 öğe | **yapıldı** Karar/Grafik/Radar/İsabet/Nabız/Ayar. |
| 1.2 | IntelScreen böl | **yapıldı** Karar = IntelScreen (ince), Grafik = ChartScreen. |
| 1.3 | Karar sığar | **yapıldı** DOM/liq/chart Karar’dan çıktı. |
| 1.4 | Grafik büyük | **yapıldı** `chartHeight = 320.dp`. |

### FAZ 2 — SMC

**yapıldı** (v1.9) `domain/Smc.kt` — JS kopya yok.

- **Order block:** son 80 mumda impulse (gövde > 1.6× medyan) sonrası son karşıt mumun high-low kutusu. Max 6 OB.
- **FVG:** 3 mum gap: `low[i] > high[i-2]` (bull) / `high[i] < low[i-2]` (bear). Doldurulmamış = hâlâ gap.
- **Sweep:** son 20 mum equal-high/low (±0.08%) sonrası fitil kırıp kapanış içeride.

`Verdict`: yön ile aynı tarafta untouched OB veya FVG varsa `smcBoost` +8 (cap A). Overlay default **kapalı**, chip ile açılır.

Test: `SmcTest` — sentetik 3-mum FVG, sweep wick, OB kutusu, boost cap A.

### FAZ 3 — Paper + kalibrasyon

**yapıldı** (v1.10) `PaperEngine` + `paper_trade` Room v7.

- Verdict `enterOk` ve grade A/B iken kullanıcı “kağıt aç” veya Ayar `autoPaper=true`.
- Kapanış: fiyat SL/TP’ye değer veya 15m timeout (`OutcomeTracker` ufku).
- `WeightCalibrator.boost` kaynağı: settled paper + mevcut outcome. `n<8` kuralı aynı.
- Radar’da açık kağıt satırları.

Test: `PaperTradeTest` — long SL → `win=false`. Dedup 120s aynı sembol ikinci paper yok.

### FAZ 4 — Radar keşif (ürünün neden açık kaldığı)

**Sabit liste yok.** **yapıldı** (v1.8)

1. `GET /fapi/v1/ticker/24hr` → `quoteVolume` top **N=40** (USDT-M, `symbol.endsWith("USDT")`).
2. Ön-filtre: `quoteVolume ≥ 20M` AND `|priceChangePercent| ≥ 1.2`. Max **K=12** tam `MarketScorer`.
3. Throttle: `chunked(4) + delay(250)` + 418/429 backoff (mevcut ExchangeRest).
4. Sonuç `DiscoverySnapEntity`. Watchlist'e otomatik **yazılmaz**.
5. UI: KEŞİF / WATCHLIST segment. Keşif satırında `+` → `toggleWatch`.
6. Fırsat bildirimi (ayar default **kapalı**): keşif + grade A/B + spoof<40 + netRR≥1.5 + coverage≥50. Dedup tablosu aynı.

Worker: 15dk `ScoreWorker` içinde, FGS açıksa AlertService 30sn döngüsünde **keşif her 5. turda**.

Test: `DiscoveryPickTest` + `ticker24h.json` → N/K seçimi; watchlist boşken keşif dolabilir; keşif BTC hardcode içermez (sadece volume sıralı).

### FAZ 5 — Alarm

**yapıldı** (v1.18) `AlarmEngine` + Room `alarm` / `alarm_fire` v8.

- CRUD Ayarlar’da. Sembol normalize. Tip: fiyat / \|skor\| / funding abs.
- `AlarmEngine.check(snaps + live)` AlertService döngüsünde + canlı rapor (`AlarmBook.evaluate(quotes=snaps, live)`). Dedup 10dk/alarmId.
- Widget’a dokunulmaz.

Test: `AlarmEngineTest` — abs skor/funding, 10dk dedup, normalize, hardcode yok.

### FAZ 6 — Nabız

**yapıldı** (v1.19) `SessionClock` + `MarginSimulator` + Pulse funding tablosu.

- Funding: BN/Bybit/OKX REST last rate yan yana + `nextFundingMs` geri sayım. 30dk kala kırmızı (banner Karar’da kalır).
- Session: UTC haftalık open (1w son mum open = Pazartesi 00:00), aylık open (1M, yoksa ayın 1’i 1d). Asia 00–08, London 08–16, NY 13–21 UTC.
- `MarginSimulator.liqPrice(entry, lev, side, mmr=0.004)` — isolated approx. İsabet’te PositionSizer yanında. Emir yok.

Test: `MarginSimTest` long 100 lev 10 → liq &lt; entry; `SessionClockTest` pencereler + weekly/monthly.

### FAZ 7 — Grafik dokunuş

**yapıldı** (v1.11) `ChartHit` + CandleChart overlay.

- `pointerInput` tap/drag: `idx = x/slot`, tooltip `OHLC + time + liqBin`.
- Grafik tab 320dp; Karar’da chart yok.
- Chip: OB / FVG / Sweep / Heat. Default Heat açık, SMC kapalı.

Test: `ChartHitTest` — slot index, OHLC satır, liq bin.

---

## 6. Bilinçli olarak taşınmayacaklar

- Firebase / `fbPush` / `localStorage` — Room + DataStore var.
- Web Audio `playTone` kopyası — ileride `SoundPool`, şimdi sistem notif sesi.
- tuk.md 10 sayfa birebir — 6 tab’a sıkıştırıldı.
- Home widget yeniden yazılmaz; keşif widget’a **girmez** (sadece watchlist).

---

## 7. Öncelik (tartışmasız sıra)

```
0 bug/dedup/lockstep
1 tab böl (Karar 3sn)
4 Radar keşif          ← ürün açığı
2 SMC overlay
3 paper → calibrator
7 crosshair + 320dp
5 alarm
6 nabız (funding/session/liq sim)
```

Faz 4, Faz 1 olmadan da çalışır ama UI “Tarayıcı” kafasını karıştırır → **1 sonra 4**.

---

## 8. CI / test sözleşmesi

Mevcut: `python-engine` + `:app:testDebugUnitTest` + `assembleDebug`.

Ekle:

- `python/tests/test_lockstep.py` fixture `python/engine/fixtures/lockstep.json`
- Kotlin aynı fixture
- Yeni: `SmcTest`, `PaperTradeTest`, `DiscoveryPickTest` (ticker → K, no hardcoded symbols), `AlarmEngineTest`, `MarginSimTest`, `DedupStoreTest`

JDK 17 CI. Local JDK 11 ile assemble yok — beklenen.

---

## 9. Başarı ölçütü

Kullanıcı uygulamayı **sembol yazmadan** açar → Radar’da hacimden gelmiş, A/B, spoof&lt;40 fırsat görür.  
Fikir varsa Karar tab’ında kaydırmadan GİRME/LONG görür.  
Grafikte parmağıyla fiyat okur.

Bu üçü yoksa spec tamamlanmış sayılmaz.
