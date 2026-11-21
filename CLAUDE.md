# Herní titulky

Samostatný Android fork z PlayCaption v0.22. Nová větev je `feature/tv-stick-remote-play`. Vzdálený repozitář `origin` patří forku, `upstream` původnímu projektu.

- Kód Sticku je v `app/src/main/java/cz/titulky/tv/stick`. Jeho obrazovky běží ve vlastním procesu `:stick`. Před prvním použitím Chiaki nastaví výběr herní knihovny. ML Kit se pro hru inicializuje i v tomto procesu.
- Herní zvuk používá `libchiaki-hra.so` pro 32bitové i 64bitové ARM. Sestavení popisuje `native/README.md`. Původní režimy používají své vlastní varianty Chiaki.
- Java 17 a Android SDK 34. Sestavení a místní testy spustí `bash gradlew :app:assembleDebug :app:testDebugUnitTest`. Pro testy na Androidu použij `bash gradlew :app:connectedDebugAndroidTest` nad samostatným emulátorem.
- Výsledky emulátoru, testovacího videa a fyzické PS5 zaznamenávej odděleně. Návod a zpráva jsou v `docs/TV-STICK.md` a `docs/TV-STICK-TESTY.md`.
