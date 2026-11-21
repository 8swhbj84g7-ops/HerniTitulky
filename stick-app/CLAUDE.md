# Produkční TV Stick

Modul `:stick-app` používá vlastní identifikátor balíčku a výslovný seznam sdíleného kódu z původní aplikace. Vydávaný balíček podporuje pouze ruční místní párování PS5, hraní a překlad titulků přes model na zařízení. Původní aplikaci a její nástroje sestavuje modul `:app`.

Testy spustíš pomocí `bash gradlew :stick-app:testDebugUnitTest :stick-app:connectedDebugAndroidTest` na emulátoru. Podepisování a kontrolu hotové release APK popisuje `docs/TV-STICK-PRODUKCE.md` v kořeni forku.
