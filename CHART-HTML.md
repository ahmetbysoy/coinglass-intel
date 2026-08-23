# Ziya Slim Pain Pro v6 → CoinGlass Intel

Kaynak: `ziya_slim_pain_pro_v6` HTML. Grafik motoru **TradingView Lightweight Charts 4.1** (`handleScroll` + `handleScale`). Jestler kütüphanede; bizde `ChartViewport` ile karşılığı yazılıyor.

## Taşındı / taşınıyor (1.15)

| HTML (LW Charts) | Bizde |
|---|---|
| 1 parmak yatay sürükle = pan | `detectTransformGestures` pan |
| 2 parmak pinch zoom (odaklı) | `state.zoom(factor, focus01)` |
| `rightOffset: 5` canlı boşluk | `ChartViewport.RIGHT_PAD` canlıyken |
| Kinetic / atalet | fling decay 0.88 |
| Sağ fiyat ekseninden dikey scale | sağ %12 dikey sürükle → `priceZoom` |
| `liqmap_chartrange` persist | `chartVisibleBars` DataStore |
| Normal crosshair + OHLC | tap / uzun bas; tooltip parmağın yanında |
| Double-click reset | double-tap reset + priceZoom=1 |
| `scrollToRealTime` | **CANLI** (offset=0) |
| Volume histogram overlay | VOL chip |
| Son fiyat çizgisi / rozet | last-price badge |
| Resize fill | `weight(1f)` ChartScreen |

## Bilinçli taşınmayan

- Pain Trend / HSI ayrı chart + time-scale sync
- Whale / sweep HTML canvas overlay (bizde SMC chip + LiqHeat var)
- CVD line sol scale (CVD Metrics/Nabız’da)
- 1h / 4h TF (spec: sadece 1m 3m 5m 15m)
- `createPriceLine` liq kademeleri (kaldıraç çizgileri)
- AI / localStorage / Web Audio / Firebase
- Magnet crosshair mode (ileride)

## Sonraki (opsiyonel)

1. Heat’i mum alanından ayırıp sabit gutter
2. Crosshair’de zaman etiketi time-axis üzerinde
3. `drawWithCache` — mum katmanı vs crosshair katmanı
