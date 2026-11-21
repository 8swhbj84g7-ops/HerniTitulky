# Herní titulky

Samostatný Android fork z PlayCaption v0.22. Vývoj režimu začal na větvi `feature/tv-stick-remote-play`. Produkční varianta vznikla na `codex/tv-stick-production`. Vzdálený repozitář `origin` patří forku, `upstream` původnímu projektu.

- Kód Sticku je v `app/src/main/java/cz/titulky/tv/stick`. Jeho obrazovky běží ve vlastním procesu `:stick`. Před prvním použitím Chiaki nastaví výběr herní knihovny. ML Kit se pro hru inicializuje i v tomto procesu.
- Herní zvuk používá `libchiaki-hra.so` pro 32bitové i 64bitové ARM. Sestavení popisuje `native/README.md`. Herní knihovna používá OpenSSL 3.5.9. Starší varianty Chiaki patří jen do původního modulu.
- Java 17 a Android SDK 34. Sestavení a místní testy spustí `bash gradlew :app:assembleDebug :app:testDebugUnitTest`. Pro testy na Androidu použij `bash gradlew :app:connectedDebugAndroidTest` nad samostatným emulátorem.
- Výsledky emulátoru, testovacího videa a fyzické PS5 zaznamenávej odděleně. Návod a zpráva jsou v `docs/TV-STICK.md` a `docs/TV-STICK-TESTY.md`.

- Produkční APK sestavuje jen `:stick-app`. Jeho zdrojový seznam udržuj bez starých přijímačů a přihlášení. Používej stejný podpisový klíč mimo repozitář a audituj hotový balíček nástrojem `tools/audit_stick_release.py`.
