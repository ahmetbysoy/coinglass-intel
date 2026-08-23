# CoinGlass Intel (Android)

`com.coinglass.intel` · min 26 / target 35

Sekmeler: Canlı · Tarayıcı · İsabet · Ayarlar.

Sembol hardcode yok. Outcome Room’da settle olur. SL/TP ATR + swing + OB duvar.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
