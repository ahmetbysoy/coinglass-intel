# Ziya Slim Pain Pro v6 → CoinGlass Intel

Kaynak: `ziya_slim_pain_pro_v6` HTML. Grafik motoru **TradingView Lightweight Charts 4.1** (`handleScroll` + `handleScale`). Jestler kütüphanede; bizde `ChartViewport` + `ChartLayout` + `ChartGesture`.

## 1.16 — neden yeniden yazıldı

1.15 `detectTransformGestures` + tap + long-press **üç** `pointerInput` birbirini yiyordu. Pan her bar adımında fling başlatıyordu (parmak hâlâ ekrandayken). Pinch `pointerInput(visible)` key’i yüzünden zoom’da iptal oluyordu. Fiyat etiketleri mumların üstüne biniyordu, ızgara ham hi-lo / N idi.

## Taşınan (1.16)

| HTML (LW Charts) | Bizde |
|---|---|
| 1 parmak yatay sürükle = pan | tek `awaitEachGesture`; sub-bar `pixelsToBars` |
| 2 parmak pinch zoom (odaklı) | `zoomAccum` — 1.01× adımlar kaybolmaz |
| `rightOffset: 5` canlı boşluk | `RIGHT_PAD` + `ChartLayout` plot |
| Kinetic / atalet | **sadece bırakınca** fling 0.90 |
| Sağ fiyat ekseninden dikey scale | fiyat gutter’da dikey sürükle |
| `liqmap_chartrange` persist | `chartVisibleBars` DataStore |
| Normal crosshair + OHLC | tap / uzun bas; tooltip üstte |
| Time label on axis | crosshair HH:mm time-axis |
| Double-click reset | double-tap reset + priceZoom=1 |
| `scrollToRealTime` | **CANLI** (offset=0) |
| Volume histogram overlay | VOL chip, plot altında |
| Son fiyat çizgisi / rozet | gutter’da `fmtAxis` |
| Heat overlay | sabit heat gutter, mumun üstünde değil |
| Resize fill | `weight(1f)` ChartScreen |

## Bilinçli taşınmayan

- Pain Trend / HSI ayrı chart + time-scale sync
- Whale / sweep HTML canvas overlay (bizde SMC chip + LiqHeat var)
- CVD line sol scale (CVD Metrics/Nabız’da)
- 1h / 4h TF (spec: sadece 1m 3m 5m 15m)
- `createPriceLine` liq kademeleri (kaldıraç çizgileri)
- AI / localStorage / Web Audio / Firebase
- Magnet crosshair mode (ileride)

## Test

`ChartViewportTest` (pan/zoom/remainder/swipe), `ChartGestureTest` (sınıflama/fling), `ChartLayoutTest` (gutter/hit=draw), `ChartRangeTest` (nice ticks / fmtAxis).
