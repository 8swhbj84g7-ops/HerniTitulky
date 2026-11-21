# Produkční TV Stick 1.0

## Opravy po auditu

- `:stick-app` je samostatný modul s výčtem sdílených zdrojů. Produkční balíček má identifikátor `cz.siller.hernititulky.stick`. Kód přihlášení Sony, starého QR serveru, UDP přenosu párovacích klíčů a přijímačů webOS/Tizen se do balíčku nekompiluje. Součástí APK je pouze herní varianta Chiaki. Vývojový modul `:app` zachovává ostatní režimy pro regresní testy.
- Párování přijímá osmibajtové ID v Base64, privátní IPv4 a osm číslic. Chiaki použije přímé místní párování. Přihlašovací údaje Sony, autorizační kódy OAuth a tokeny v produkčním kódu nemají cestu zpracování. Překlad rozpoznaného textu používá místní model ML Kit. SDK může komunikovat s Googlem kvůli jazykovým modelům, konfiguraci a diagnostice SDK.
- Registrační kód a Account ID se neukládají. Po zpracování párování zůstává v soukromých předvolbách zašifrovaná IP a dvojice klíčů Remote Play. Android Keystore chrání klíč AES-GCM. Zrušení párování odstraní šifrovací klíč před smazáním záznamu. Použití staršího záznamu, včetně případné zbylé kopie souboru, starý klíč neobnoví. Výsledek rozběhnutého párování po zrušení se odmítne. Nativní síťové logy zůstávají vypnuté.
- Manifest release má výslovně vypnuté ladění, zálohy i nešifrované HTTP pro síťové vrstvy Androidu. Aplikace při startu výslovně vypne ladění WebView. Přístupná zvenčí je pouze aktivita pro spuštění aplikace. APK neobsahuje testovací nativní adaptér.
- Herní knihovna je sestavená s OpenSSL **3.5.9 LTS** pro ARM64 i ARMv7. Zdrojový archiv ověřuje SHA-256 a výsledné knihovny kontroluje manifest. Původní knihovny starého vývojového modulu se v produkčním APK nenacházejí. [Verze OpenSSL](https://openssl-library.org/source/)

## OAuth pro případný samostatný návrat starých režimů

Původní implementace používá vestavěné WebView nebo HTTP předání z telefonu. Požadavky z existujícího kódu neobsahují `state` ani `code_challenge`, výměna kódu neposílá `code_verifier`. Z dostupného zdrojového kódu neplyne, které bezpečné varianty by Sony pro klienta tohoto forku povolilo. Produkční modul proto přihlašování neobsahuje.

Pokud bude samostatný režim někdy potřebovat přihlášení, použij externí systémový prohlížeč, schváleného klienta Sony a registrované HTTPS přesměrování spojené s aplikací. Pro každý pokus vygeneruj nový náhodný `state` a PKCE verifier, odešli výzvu S256, ověř přesnou návratovou adresu i `state` a použij kód jen jednou. Podporu je potřeba ověřit u Sony pro daného klienta. Jestli Sony bezpečný postup nepovolí, zůstane ruční Account ID. Přihlašovací URL ani token nesmí přecházet přes místní HTTP či QR formulář. [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252.html)

## Zbývající rizika a hranice ověření

Ruční párování nevyžaduje heslo ani dvoufázový kód PSN. Uděluje Sticku přístup Remote Play k vybranému profilu na konzoli. Při zahájení relace protokol Chiaki přenáší registrační klíč v nešifrované hlavičce přímo PS5. To je vlastnost podporovaného protokolu. Zapnutí zákazu HTTP v Androidu se na vlastní síťové sockety Chiaki nevztahuje. Šifrování uložených klíčů tento přenos nenahradí. Používej důvěryhodnou domácí síť bez přesměrování portů konzole. [Chiaki – zahájení relace](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/lib/src/session.c#L663-L719) · [Síťová pravidla Androidu](https://developer.android.com/guide/topics/manifest/application-element#usesCleartextTraffic)

Zrušení párování maže klíče na Sticku. Neodvolá případnou kopii klíče, kterou někdo získal dříve. Chování aktuálního firmwaru PS5, obraz, HDMI zvuk a DualSense na fyzické sestavě vyžadují přejímku uživatelem. Nativní testy ověřují funkce kryptografie, přehrávací testy používají syntetické H.264 a automatické kontroly běží bez skutečného PSN účtu a bez konzole.

## Sestavení, podpis a kontrola

Nejprve se sestaví `:stick-app:assembleRelease`. Nepodepsaná APK vznikne v `stick-app/build/outputs/apk/release/`. Pro podpis lze použít příkazy `zipalign` a `apksigner` z Android Build Tools 34.0.0. Použij stávající produkční klíč v soukromém úložišti macOS mimo Git. Heslo předávej z chráněného souboru parametrem `--ks-pass file:…`, nikoli přímo v příkazu. Zachovej klíč pro případnou aktualizaci. Výsledný soubor projde kontrolou `python3 tools/audit_stick_release.py CESTA_K_APK --sdk CESTA_K_ANDROID_SDK` a `zipalign -c -p 4`.

Původní a produkční testy spusť s jediným připojeným emulátorem. Produkční instrumentační testy běží na variantě `.stick.testbed`, takže nepřepisují podepsaný balíček. Závěrečná zkouška samostatně instaluje podepsanou release APK do emulátoru.

## Výsledky ověření

| Kontrola | Výsledek |
| --- | --- |
| Původní Android aplikace | 295 testů JVM prošlo. V emulátoru prošlo 10 scénářů, další 3 se přeskočily kvůli chybějícímu OCR korpusu, povolenému systémovému překryvu nebo skutečnému LG přijímači. |
| Produkční modul | 2 testy validace vstupů a 6 instrumentačních testů prošlo. Ověřily se tři profily videa s místním českým překladem, rozhraní ovladače, šifrování, zrušení párování, odmítnutí starého klíče a očekávané komponenty APK. |
| Chiaki | Sestavení pro ARMv7 i ARM64. Prošlo 94 původních nativních testů s OpenSSL 3.5.9 na ARM64, včetně ověřovacích hodnot kryptografie PS5. Prošlo také šest zkoušek ukončování zvuku a dvě zkoušky herního výstupu PCM. |
| Ostatní původní testy | Prošlo 8 testů měření OCR, šest sad webOS a síťový test služby Samsung. Vykreslování Samsung se přeskočilo, protože v prostředí nebyl Playwright. |
| Podepsaná release APK | Ověřen podpis APK v2 a v3, zarovnání, manifest, výpis tříd a otisky obou nativních knihoven. Podepsaná aplikace se nainstalovala a spustila na emulátoru. Formulář má tři místní vstupy. Android odmítl běh příkazu pod identitou aplikace pomocí `run-as` a proces nevytvořil ladicí socket WebView. |

Během zkoušek se projevil pád při souběžném dokončení OCR a zavření čtečky. Ukončení teď synchronizuje přístup k detekci jazyka. Obě Android sady potom doběhly bez pádu.

Při krátké syntetické zkoušce produkčního režimu na emulátoru bylo vykresleno **29,5 FPS při 720p/30**, **27,3 FPS při 720p/60** a **26,8 FPS při 1080p/30**. Ve všech režimech se zobrazil český překlad. Vyšší profily dosáhly na emulátoru méně FPS než v předchozím režimu s online překladem. Příčina rozdílu se samostatně neověřovala. Výsledky nejsou měřením výkonu Xiaomi Sticku ani živé PS5.

SHA-256 podepsaného APK je `c428249ec6c39b6431ed54c499c769f2195edf279818b7324916520f1baee679`. SHA-256 veřejného podpisového certifikátu je `b80afdeb108b0d7c43da0b7f5e3fdced6785a5a6ae00b373e99434ac65477f1a`.
