# Dokončení LG a následná správa TV platforem

Stav auditu 15. 11. 2026. Hlavním produktem je jedna Android aplikace PlayCaption.
LG je doplňkový zobrazovač; Samsung řeší jiný vývojář. Tento plán nepřidává jeho
implementaci ani instalační předpoklady. Story se nyní nerozvíjí.

## Audit větví a rizika slučování

Po `git fetch origin` je `master` i `origin/master` na `95b3cc3`.

| Větev / commit při auditu | Obsah | Další postup |
|---|---|---|
| `feature/lg-webos-receiver` / `679687c` | LG přijímač, HDMI, volby, stránky, vzhled/discovery/návody | Dokončit P0 zde. Rozdíl `app/` a `native/` proti master je prázdný. |
| `feature/story-mode` / `ef29356` | OCR opravy, plán P0, čtecí fronta, LG merge `b6b97bf`, Story integrace/časování | Zachovat. Nové obecné LG úpravy zatím neobsahuje. |
| `bugfix/lg-subtitle-pages`, `feature/lg-choices-frames`, `feature/lg-hdmi-lifecycle` | Předchozí LG etapy | Již předci LG větve, znovu necherry-pickovat. |
| `bugfix/ocr-image-quality`, `bugfix/subtitle-cues`, `feature/ocr-text-blocks` | Android OCR | Nejsou základem nové platformní větve automaticky; samostatné schválení jejich začlenění. |
| `feature/tv-platform-management` | Zatím neexistuje | Založit bez upstreamu až z aktuálního schváleného základu po LG etapě. |

Rozpracovaných 21 řádků OCR diagnostiky v `RemotePlayService.kt` bylo před přepnutím
ze Story zachováno v samostatném stashi a patchi. Osobní dokumenty a `experiments/`
zůstávají nedotčené a nejsou součástí LG commitů.

Rizika: obě větve mění `webos/app/app.js`, službu/parser, metadata, testy a návody.
Story navíc přidává `PCQ1/PCS1/PCA1` a mění protokolové časování. Při budoucím merge
zachovat nové ovládání a pravidlo odesílatele z LG, pak vědomě integrovat Story ACK.
Root `CHANGELOG.md`, `ZMENY.md` a backlog budou vyžadovat obsahové sloučení. Nestačí
porovnat verze IPK; integrovaný a samostatný starší build mají oba 0.0.10.

LG dokončit a předat jako samostatnou změnu do master přes běžné review. Teprve
potom založit Android etapu; žádný hromadný merge, automatické zveřejnění ani přímý
push do master. Story později přizpůsobit novému společnému základu.

## Android: potvrzené zdroje a minimální budoucí model

- `Rezim.jeTelefon()` rozpoznává TV přes UiModeManager/FEATURE_LEANBACK. To je
  režim hostitelského zařízení, nikoliv značka vzdáleného přijímače.
- `MainActivity` samostatný TV provoz a `PrijemService` již rozlišuje. Mobilní
  průvodce nesmí být bezpodmínečně vložen před tento tok.
- `TelefonActivity` ukládá poslední `tvId` do společných prefs `gamelingo`, ale
  nemá databázi samostatných TV profilů. Automatická volba preferuje poslední ID.
- `Propojeni.Televize` obsahuje ID/jméno/IP a příznak konzolového párování;
  neobsahuje spolehlivé údaje o verzi a schopnostech přijímače.
- `Vysilac.nastavCil()` používá globální aktivní IP. Bezpečné ukončení starého cíle
  a směrování k novému proto musí mít samostatný regresní test.

Model: platforma vyjadřuje typ integrace; profil má vlastní stabilní ID, platformu,
jméno, poslední adresu, ověřenou verzi/schopnosti a pouze své nastavení/párování.
Aktivní výstup je samostatná volba profilu nebo lokálního provozu. Dvě LG znamenají
dva profily stejné platformy. Neznámou verzi nedoplňovat z verze přibaleného IPK.
Jazyk a skutečně společné OCR nastavení zůstanou společné. Bez důkazu nemigrovat
staré `tvId` na konkrétní platformu a nepřepsat fungující provoz.

## Distribuce balíčků

Výchozí samostatný LG IPK 0.0.10: **29 010 B (28,33 KiB)**, SHA256
`fe1489c76c393474240d2063cb299c5a5fe8c638dc78d605e08f55ed4a2e1779`.
Jde o skutečný IPK ze samostatné LG větve, nikoliv ZIP s návody. Finální P0 artefakt
se změří znovu a dostane manifest s ID, verzí, schopnostmi, velikostí a SHA256.

Takto malý LG balíček přibalit v P2 do Android assets. Lokální instalace z něj
nemusí stahovat balíček z internetu; již aktivní Developer Mode, Key Server a LAN
jsou předpoklady. Založení účtu/aktivace/prodloužení režimu jsou jiná operace a
nelze jim slibovat offline provoz. Samotné sestavení CLI může vyžadovat internet.

Instalátor dostane ověřený lokální soubor a manifest. Jeho síťový protokol nebude
řešit, odkud soubor pochází. Pro MVP implementovat jen assets; žádné vzdálené
stahování, CDN ani backend. SHA256 hlídá integritu, důvěryhodnost dnes plyne z
přibalení v distribuované Android aplikaci. Budoucí externí manifest musí mít
ověřený původ; zachovat lokální zálohu.

Samsung balíček není k dispozici a jeho velikost nebyla měřena. Distribuci,
podpisy a instalaci dodá jeho vývojář. Pouhá přítomnost assetů nesmí spouštět
integraci ani měnit Android TV režim. UI ukáže pouze zvolenou platformu.

## Backlog podle vlastníka a priority

### LG-P0-01 — verze, kompatibilita a balení

- **Priorita/větev:** P0 / `feature/lg-webos-receiver`. **Stav:** implementováno; 5 LG sad, kontrola artefaktu a dvě bitově shodná sestavení prošly.
- **Komponenty:** parser, `build.py`, metadata, testy, manifest a dokumentace.
- **Závislosti:** audit větví; původní PCT1/PCV1/PCO1.
- **Řešení:** nové jednoznačné vydání a manifest skutečného IPK; zachovat původní
  protokol. Story nyní nepodporovat ani nepotvrzovat. Nepřidávat nový handshake.
- **Akceptace/test:** staré pakety fungují; Story/účtové PCS1 se ignorují a
  nezískají aktivní příjem; následný standardní paket funguje. Build kontroluje
  obsah, manifest, hash a reprodukovatelnost; aktualizace drží stejné instalační ID.

### LG-P0-02 — ovladač a lokální test

- **Priorita/větev:** P0 / LG. **Stav:** implementováno; automatické testy a fyzické OK/šipky/Zpět na C5 potvrzené uživatelem.
- **Komponenty:** `app.js`, HTML/CSS, UI testy a návod.
- **Závislosti:** současná HDMI nabídka/renderer.
- **Řešení:** malá nabídka přes OK/Zpět: HDMI, stav, lokální test, skrýt/zobrazit,
  návrat ke hře a ukončení. Skrytí Standardní titulky nepozastavuje ani neprodlužuje.
- **Akceptace/test:** šipky/OK/Zpět na C5; neblokující příjem při otevření menu;
  lokální test nemění síťový stav ani netvrdí úspěch OCR. Žádné Story ACK při skrytí.

### LG-P0-03 — pravdivé stavy

- **Priorita/větev:** P0 / LG. **Stav:** implementováno; renderer/lokální stav ověřený testy a DOM C5.
- **Komponenty:** UI, keepalive, diagnostika a regresní test.
- **Závislosti:** LG-P0-02.
- **Řešení:** připraven / platná data / poslední titulek / výpadek / chyba překryvu;
  verze, identifikátor, stáří paketu a naposledy vybraný HDMI. Žádná historie obsahu.
- **Akceptace/test:** samotné rámečky neznamenají nový titulek; test neznamená
  připojení; přetrvávající keepalive chyba není skrytá příchozími daty.

### LG-P0-04 — jeden odesílatel a přejímka

- **Priorita/větev:** P0 / LG. **Stav:** implementováno; dva skutečné zdroje Mac/Android a převzetí po uvolnění prošly. Několikahodinové hraní, uspání a změna IP skutečné TV zbývají.
- **Komponenty:** UDP služba, UI, testy a hardware.
- **Závislosti:** LG-P0-01 až 03.
- **Řešení:** první platný zdroj vlastní příjem do 5 s bez svých dat; jiný zdroj
  mu živý příjem nepřebírá. Změna se provede až po uvolnění. Je to kompatibilní
  pravidlo adres, ne autentizace nebo rozlišení dvou procesů na stejné IP.
- **Akceptace/test:** dva odesílatelé, dlouhá replika při výpadku, obnova, zavření/
  nové spuštění, HDMI, dlouhý běh; změna IP a uspání pouze bezpečně, s rozlišením
  simulace a skutečného testu. Bez hardwarové přejímky nevydávat P0 za stabilní.

### AND-P1-01 — zachování režimů a profily TV

- **Priorita/větev:** P1 / budoucí `feature/tv-platform-management`. **Stav:** čeká.
- **Komponenty:** Rezim, MainActivity, TelefonActivity, Nastaveni, Propojeni.
- **Závislosti:** dokončená LG etapa začleněná do schváleného společného základu.
- **Řešení:** výše popsaný malý model platforma/profil/aktivní výstup a opatrná
  migrace `tvId`; žádné OCR nebo Story změny.
- **Akceptace/test:** původní Android TV cesta nezměněná, dvě LG uchovají odlišné
  profily, přepnutí vyčistí starý výstup, návrat obnoví nastavení bez smazání druhé TV.

### AND-P1-02 — průvodce a změna zařízení

- **Priorita/větev:** P1 / Android. **Stav:** čeká.
- **Komponenty:** mobilní nastavení/UI a návody. **Závislosti:** AND-P1-01.
- **Řešení:** volba platformy oddělená od výběru konkrétní TV; nové nastavení → TV
  → změnit zařízení. Fungující konfiguraci nenutit znovu nastavovat.
- **Akceptace/test:** jen relevantní instrukce, podporované/neznámé schopnosti
  rozlišené; Android TV nevidí LG Developer Mode; nedostupná integrace není nabízena
  jako hotová. Hardware Android TV přejímka vedle mobilních testů.

### AND-P2-01 — izolovaný LG instalátor a pak UI

- **Priorita/větev:** P2 / Android. **Stav:** návrh, nic implementováno.
- **Komponenty:** assets+manifest, malý zdroj balíčku, SSH/SFTP, Android Keystore,
  průvodce. **Závislosti:** P0, AND-P1-01/02.
- **Řešení:** nejprve prototyp první instalace/aktualizace pouze vlastního IPK;
  ověření výsledku, bezpečné klíče, neznámá platnost Developer Mode bez odpočtu.
- **Akceptace/test:** lokální instalace bez stahování, chybný klíč/IP, výpadek,
  update bez uninstallu, kontrola verze/hash a žádné citlivé logy/zálohy; potom UI
  s externím záložním postupem. Vzdálené aktualizace nejsou MVP.

### EXT-P3-01 — samostatný vstup dalšího TV vývojáře

- **Priorita/větev:** P3 / externí Samsung práce + později Android adaptér.
- **Stav:** odloženo na jeho vývojáře. **Komponenty:** profil/adaptér/metadatový kontrakt.
- **Závislosti:** jeho skutečný balíček, schopnosti, distribuce a měření velikosti.
- **Řešení:** zde žádná implementace. **Akceptace/test:** po dodání neovlivní LG
  ani Android TV; žádné převzetí webOS instalačních předpokladů.

### STORY-P4-01 — vědomá reintegrace

- **Priorita/větev:** P4 / `feature/story-mode`. **Stav:** odloženo, zachováno.
- **Komponenty:** Android fronta/překlad, LG renderer/ACK a testy.
- **Závislosti:** společný základ po LG a Android etapách.
- **Řešení:** audit skutečných konfliktů, jedna čtecí autorita na Androidu,
  integrace LG ovládání a odesílacího pravidla bez ztráty existující fronty.
- **Akceptace/test:** starý standardní odesílač, kompatibilní Story a nepodporovaný
  přijímač; skrytí/pauza neznamenají dočtení, žádné tiché ztráty nebo duplicity.

## Provedení P0

- `1e5499e`: audit a rozdělení P0–P4.
- `894ad43`: kompatibilní pravidlo jednoho zdroje; platný druhý zdroj uvolnění nevynucuje.
- `9f33e3a`: LG menu, lokální test, diagnostika, maskování a reproducibilní artefakt/manifest.
- `19ef4eb`: místní vysvětlení nepodporovaného Story režimu bez ready/ACK; ověřeno na C5 i simulacemi.
- Uživatel na C5 potvrdil fyzické OK/šipky/Zpět a následně explicitní ukončení přes menu. První monitorovací běh byl tímto uživatelským ukončením přerušen; nejde o prokázané selhání lifecycle.
- Balíček 0.1.0 je kandidát P0. GitHub Release, tag ani merge do master nebyly provedeny. Android integrace, instalátor i Story zůstávají odložené.

Finální kandidát P0 0.1.0: **31060 B (30.33 KiB)**, SHA256 `c3bdef2dddb1abd1ab238e6d920660c35420eb9abb543e02df43daf5fa27a4af`. Takto malý IPK podporuje volbu assets jako výchozí distribuce. Manifest vzniká z konkrétních sestavených bajtů.

Kontrola 0–180 s na C5 po novém spuštění prošla bez uzavření/keepalive chyby. Původní Android 0.20 posílal při uživatelském testu pouze prázdné PCT1/PCV1 (6+6 během 6 s); síť funguje, herní překlad tím potvrzený není. Uživatel následně potvrdil fungování existující opravené Test APK ve Standardním režimu; v neúspěšném pokusu Test APK byla zapnutá Story. Doplněno místní vysvětlení nepodporovaného Story dotazu bez síťového ACK nebo změny čtecí logiky. Žádný výsledek OCR se z lokálního testu neodvozuje.
