# Ziya Slim Pain Pro v6 → CoinGlass Intel

Kaynak: `ziya_slim_pain_pro_v6` HTML. Grafik motoru **TradingView Lightweight Charts 4.1**. Jestler `ChartViewport` + `ChartLayout` + `ChartGesture` + `CandleChartState`.

## 1.17 — iskelet

1.16 jestleri düzeltti ama 21 parametreli God-composable + `delay(16)` fling + index crosshair + her tick EMA kaldı. 1.17 patch 1→2→4→3→5:

| Patch | Ne |
|---|---|
| 1 | `ChartData` / `ChartLevels` / `ChartSignals` / `Divergence` / `SPOOF_THRESHOLD` |
| 2 | `CandleChartState` — `Set<Overlay>`, `total` SideEffect, `crosshairTime` |
| 4 | `EmaCache`, `AxisLabelCache`, solid volume, window `toList()`, `snapshotFlow`+150ms |
| 3 | `ChartGesture.afterMove/afterTimeout/tapKind`, `Animatable`+`splineBasedDecay`, haptic |
| 5 | FilterChip, string resource, Loading/Error, dinamik tooltip, ⟲ Oto |
| 1.20 | Overlay DataStore persist, magnet OHLC, KARAR satırı header |

## Jestler

Tek parmak yatay = pan. İki parmak = pinch. Sağ fiyat gutter dikey = y-scale. Bırakınca spline fling (Hz bağımsız). Çift dokun = sıfırla. Uzun bas = crosshair + haptic. Crosshair mum `openTime` tutar; pencere kayınca aynı mum.

## Bilinçli taşınmayan

- Pain Trend / HSI, whale canvas, CVD sol scale
- 1h / 4h TF
- Magnet crosshair
- `kotlinx.collections.immutable` (snapshot `toList()`)

## Test

`ChartModelsTest`, `ChartGestureTest` (resolver), `ChartHitTest.indexOfTime`, `EmaTest` cache, mevcut viewport/layout/range.
