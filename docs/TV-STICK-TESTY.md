# Herní titulky – audit a ověření

Stav k 21. listopadu 2026. Základ úpravy je PlayCaption `v0.22` (`633a847`). V době auditu mířila výchozí větev `master` na stejný commit. Novější práce byla v samostatných větvích, například `codex/android-ocr-lg-contrast` přidávala úpravu telefonního OCR a `samsung-podpora` měnila přijímač Samsung. Starší větev `codex/ps5-session-diagnostics` neměla proti vydání vlastní commit. [Vydání v0.22](https://github.com/AdamRuzick4/PlayCaption-app/releases/tag/v0.22)

## Co ukázal audit

- PlayCaption běžně vykresluje překlad nad HDMI obrazem konzole. Jeho Remote Play video končí v neveřejné ploše určené pro rozpoznávání textu. Chiaki má rozhraní pro dekódování na obrazovku, posílání stavu ovladače i přenos zvuku. Původní Android aplikace ovladač do Chiaki nepředává a originální ARM knihovna ve v0.22 dekódovaný zvuk úmyslně zahazuje, aby se nezdvojil s HDMI zvukem konzole.
- Stick používá vlastní proces a variantu knihovny pro obě ARM architektury. Využívá místní spojení s konzolí přes Chiaki a dekodér vybíraný systémem podle H.264. Z obrazu se přibližně každých 450 ms odebírá jen spodní třetina. Rozpoznávání a překlad mají omezenou kapacitu a běží nezávisle na vykreslování. Příběhový režim používá existující čtecí frontu.
- Nativní zvuk používá sdílený výstup Oboe. V herní variantě se při ukončení dekodéru čeká na jeho výstupní vlákno a uvolňuje se plocha pro obraz. Tím se řeší cesta, která při nedostupném vstupním bufferu mohla přeskočit čekání a ponechat referenci na plochu.
- V otevřeném hlášení #39 popisuje uživatel Sony Bravia 8A opakované odpojování ve v0.22 při chybějících snímcích. Další otevřená hlášení se týkala zejména přijímačů LG a nastavení TV. Režim Stick má omezený počet pokusů a ruční opakování. Chování nahlášené sestavy nebylo ověřeno. [Hlášení #39](https://github.com/AdamRuzick4/PlayCaption-app/issues/39) · [Otevřená hlášení](https://github.com/AdamRuzick4/PlayCaption-app/issues)

## Testy a naměřený výkon

- Android build a 295 testů JVM prošly bez selhání. V emulátoru Android TV 12 prošlo 10 instrumentačních scénářů. Tři byly přeskočeny, protože vyžadují OCR korpus, povolený systémový překryv nebo skutečný LG přijímač.
- Sestavily se herní knihovny pro ARM64 i ARMv7. Hostitelské testy ověřily šest scénářů zvukového dekodéru a dva scénáře herního výstupu PCM včetně odmítnutého otevření zařízení. Se stejným testovacím nástrojem prošly i stávající scénáře tichého zvuku. Testy na emulátoru založily šifrované párování s testovacími klíči, načetly ARM64 knihovnu a předaly jí stav tlačítka bez připojení ke konzoli.
- Dálkovým ovládáním v emulátoru prošla úvodní obrazovka, nastavení, párovací formulář a diagnostika. S nedostupnou testovací adresou prošly tři opakované pokusy, ruční obnovení, odchod ze hry a zobrazení uložené diagnostiky.
- Prošlo osm testů OCR metrik, šest sad pro webOS a služba Samsung. Test vykreslování Samsung v Chromiu se přeskočil kvůli chybějícímu Playwrightu.

| Profil | Vykreslené FPS | První překlad od prvního snímku | Paměť procesu na začátku a konci |
| --- | ---: | ---: | ---: |
| 720p / 30 | 29,7 | 330 ms | 110,0 → 117,8 MiB |
| 720p / 60 | 58,6 | 119 ms | 124,0 → 129,9 MiB |
| 1080p / 30 | 29,4 | 75 ms | 135,6 → 141,8 MiB |

Měření proběhlo na emulátoru ARM64 s Android TV 12, 2 GB RAM a softwarovým dekódováním přes MediaCodec. Každý profil dostal 30sekundové okno po zahřátí. Klip se opakoval a obsahoval jednu anglickou větu. Výstup nativního dekodéru Chiaki prošel výřezem i rozpoznáním až k titulku „Počkejte na mě!“. Krátká čísla u pozdějších zkoušek mohou odrážet zahřáté knihovny a spojení. Měření neobsahuje zpoždění sítě PS5 ani odezvu na ovladač. Paměť tvoří odhad sdílených a soukromých stránek procesu (PSS). Dlouhodobý růst paměti a teplota Sticku vyžadují delší zkoušku na zařízení.

První přímé zkoušky nedodaly obraz. Diagnostika ukázala, že dekodér na začátku odmítl úvodní konfiguraci a klíčový snímek, po nichž zpracovával další data bez potřebné konfigurace. Herní dekodér nyní déle čeká na první buffery, připojuje plochu před startem relace a testovací adaptér respektuje dočasné odmítnutí prvních vstupů.

APK má ověřený vývojový podpis Androidu, identifikátor `cz.siller.hernititulky` a obě ARM varianty. V archivu jsem porovnal SHA-256 obou herních knihoven s nativním sestavovacím manifestem.

## Zbývající ověření na cílovém zařízení

1. **Přihlášení a PS5.** Ověřit PSN přihlášení v prohlížeči Sticku, párovací kód, případný PIN profilu a spuštění skutečné relace. Obě zařízení musí být v domácí síti a konzole musí být zapnutá.
2. **DualSense.** Ověřit příjem všech základních tlačítek, obou páček, směrového kříže a spouští v Androidu 11 a následně ve hře. Klik touchpadu a tlačítko PS závisí na událostech předaných systémem. Dotyková gesta, pohybové senzory, haptika a adaptivní spouště nejsou v režimu Stick implementované. [Podpora Sony](https://www.playstation.com/en-us/support/hardware/pair-dualsense-controller-bluetooth/)
3. **Obraz, zvuk a zpoždění.** Hrát alespoň 30 minut při 720p/30 s viditelnou diagnostikou. Ověřit slyšitelný zvuk přes HDMI, stabilitu snímků a paměti a kvalitu OCR při skutečných scénách. Zkusit 720p/60 a 1080p/30 zvlášť. Pro herní odezvu je potřeba samostatně změřit dobu od stisku tlačítka po reakci na obrazovce. Aplikace měří čas od zachycení výřezu obrazu do překladu.
4. **Obnova.** Za běhu odpojit a znovu zapnout Wi-Fi, vypnout PS5 a vrátit se do aplikace po jejím opuštění. Ověřit, že se vrátí obraz, zvuk i vstup a nepřibývá paměť při opakovaných relacích. Po třech neúspěšných pokusech lze použít OK pro nové připojení.

Další omezení jsou pevná oblast OCR ve spodní třetině, možné chyby přepisu při šumu nebo netypickém písmu a dostupnost veřejných překladových služeb. HDMI zvuk používá aktuální systémový výstup. H.265 a HDR nejsou v herním nastavení této verze.

## Opakování testů

Po instalaci Javy 17, SDK 34 a připojení jediného testovacího zařízení spusť z kořene forku:

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest
bash gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.windowMs=30000
```

Testy `StickPlaybackTest` načítají syntetické klipy ze složky `app/src/androidTest/assets/stick`. Pošlou je přímo do dekodéru sestavené knihovny Chiaki, počkají na českou větu ve vrstvě titulků a změří počet skutečně vykreslených snímků. Adaptér a klipy jsou pouze v testovací APK. Přehledy Gradle najdeš v `app/build/reports/tests` a `app/build/reports/androidTests`.
