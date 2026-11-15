# PlayCaption LG – podklady pro vývoj a předání

Experimentální přijímač pro LG webOS, oddělený od Android aplikace.
Pro uživatele je určen [návod bez sestavování kódu](NAVOD-LG.md).
[Důkazy a přejímací testy](TESTOVANI.md) rozlišují automatické testy a skutečný běh na TV.
[Backlog](../BACKLOG.md) obsahuje odloženou chybu víceřádkového překladu.

## Jak to funguje

Konzole posílá obraz hry přes HDMI do TV. Původní Android PlayCaption získává další
kopii přes Remote Play, čte a překládá text. Přijímač na LG pouze zobrazuje výsledek.
Používá dosavadní Android protokol bez nutnosti změny APK. Tato větev neobsahuje
Příběhový režim ani změny Android OCR; ty se vyvíjejí na `feature/story-mode`.

Od verze 0.0.5 lze přijímač otevřít z nabídky aplikací na TV a vybrat HDMI přímo
v PlayCaption. `com.webos.service.eim/getAllInputStatus` poskytne seznam vstupů;
`switchInput` ověří vybrané ID a služba krátce podrží Activity Manager aktivitu.
Nejprve spustí HDMI a následně vlastní overlay s `resumeOverlay: true`, takže
uzavření původního UI při přepnutí nezastaví dokončení operace. Aktivita se uvolní
po úspěchu, chybě nebo timeoutu 12 s. Služba neběží trvale po zavření přijímače.

Nejde o automatické obnovení po libovolném přepnutí vstupu přes systémové menu.
`getForegroundAppInfo` testované C5 webové aplikaci odmítla; takovou funkci
nepovažujeme za dostupnou jen na základě existence metody.

- `app/`: průhledné HTML/CSS/JS UI, životní cyklus, heartbeat a doba zobrazení.
- `service/`: Node.js služba používající systémový `webos-service`, UDP a parser.
- `test.cjs`, `test-udp.cjs`, `test-input.cjs`, `test-receiver.cjs`, `test-controls.cjs`: regrese UI/protokolu, UDP, přepínání HDMI a shoda vykreslení.
- `build.py`: testy, syntaxe, sestavení IPK, kontrola obsahu a distribuční ZIP.

Kompatibilní protokol je v Android `Propojeni.kt`: `PLAYCAPTION_TV` na UDP 41988
každé 2 s a příjem `PCT1`, `PCV1` a `PCO1` na UDP 41989. Posíláme příznak nespárované konzole (`0`),
aby přihlášení zůstávalo na Android zařízení. Přihlašovací `PCS1` z původní Android aplikace se ignoruje; žádné tokeny konzole
se nepředávají. Story `PCQ1`/`PCS1` tato samostatná LG větev nepodporuje.
Síťové texty se vykreslují přes `textContent`, nikoli HTML.

Aktuální rozsah zahrnuje i volby odpovědí, dlouhé texty, modrý/žlutý/zelený rámeček
a barvy jmen. [Matice shody s Android přijímačem](SHODA-ANDROID.md) uvádí detaily
a hranice ověření. Opakované ID neobnovuje dobu zobrazení; kanály titulků a voleb
mají oddělené čítače. Po 5 s bez přenosu se skryjí texty i rámečky.
Po zavření posledního odběratele skončí UDP i discovery. První platná IPv4 adresa
drží příjem do 5 s bez vlastních dat; jiné zdroje mezitím obsah nepřepisují.
Ochrana nemění protokol a není autentizací ani identitou procesu.

OK/Zpět nad hrou otevře malé menu; Zpět v něm vrací hru, explicitní Ukončit
zavře přijímač. Lokální test používá vlastní prvek a nefalšuje přijetí dat.
Skrytí maskuje prezentaci, ale nepozastavuje Standardní časování.

## Sestavení

Potřebuješ Node.js 20+, Python 3.9+ a internet pro připnuté oficiální webOS CLI.
Z rootu projektu spusť jediný příkaz:

```sh
python3 webos/build.py
```

Na Windows lze použít `py -3 webos/build.py`. Aplikace nemá přidané npm knihovny;
CLI 3.2.6 se spouští přes npx pouze pro sestavení. Výstup ve `webos/dist/`:

- `cz.playcaption.overlaytest_0.1.0_all.ipk` pro instalaci do LG;
- `receiver-manifest.json`: schema, platforma, ID, verze, protokoly/schopnosti,
  skutečná velikost IPK a SHA256; výslovně `story: false`;
- `PlayCaption-LG-0.1.0.zip` pro předání uživatelům: IPK, návod, licence,
  přesně odpovídající zdroje přijímače a SHA256SUMS.

Build normalizuje časy a UID/GID z CLI archivů; dvě stejná sestavení ve stejném
prostředí musí mít stejné bajty IPK. ZIP má také pevné časy. Kontrola porovnává
rozbalený obsah IPK se zdroji. SHA256 neprokazuje důvěryhodnost cizího downloadu;
pro plánované Android assets ji zajistí distribuovaná Android aplikace. Verze
přibaleného IPK není důkaz verze nainstalovaného přijímače.

Adresář `dist/` se necommituje. Build nečte žádné místní přihlašovací údaje,
nepřipojuje se k TV a nic nezveřejňuje. ID zůstává stejné jako u původního testu,
aby bylo možné aktualizovat existující instalace. Při změně verze uprav `appinfo.json`,
`service/package.json` a názvy balíčků v návodu/README; Android verzi nezvyšuj.

## Instalace přes CLI pro vývojáře

Uživatelský návod používá webOS Dev Manager. Alternativa s již nastaveným CLI:

```sh
ares-config --profile tv
ares-setup-device
```

Přidej TV jako `lg-tv` s vlastní IP, portem 9922 a uživatelem `prisoner`. Na TV zapni
Key Server. Z rootu projektu pokračuj:

```sh
ares-novacom --device lg-tv --getkey
ares-install --device lg-tv webos/dist/cz.playcaption.overlaytest_0.1.0_all.ipk
```

Volitelné spuštění z počítače otevře stejný výběr HDMI jako spuštění z TV:

```sh
ares-launch --device lg-tv cz.playcaption.overlaytest
```

Passphrase ani získaný klíč nepatří do projektu. Identifikátor `lg-tv` je pouze příklad.

## Předání správci projektu

1. Přidat složku `webos/`, `BACKLOG.md` a změny hlavního README / ZMENY do stejné změny.
2. Spustit `python3 webos/build.py` a `git diff --check`.
3. Dokončit dostupnou ruční přejímku na TV podle `TESTOVANI.md`; uvést skutečné limity ověření.
4. Po schválení změny přidat vytvořený ZIP k odpovídajícímu GitHub Release jako
   experimentální LG přijímač. ZIP obsahuje i zdroje této konkrétní verze (AGPLv3).
5. Do popisu vydání přidat odkaz na `webos/NAVOD-LG.md` a zdůraznit požadavek
   Android telefonu/tabletu a Developer Mode. Nepopisovat to jako samostatný překlad v LG.

Tento postup automaticky nezakládá release, nezvyšuje verzi Android aplikace a
neprovádí commit/push. Obecná optimalizace OCR a FPS patří do samostatné změny.

## Přenositelnost a vzhled

- Název v launcheru a nabídce vstupů: **PlayCaption LG**. Discovery zachovává
  **LG PlayCaption · identifikátor TV**. Stejný název je na LG při čekání; více
  TV lze rozlišit bez změny Android APK. ID zůstává stejné po změně IP.
- `icon.png` (80×80) a `largeIcon.png` (130×130) jsou zmenšeniny Android launcher
  ikony `app/src/main/res/drawable-nodpi/gamelingo_ikona.png`; grafika se nemění.
  Před zmenšením se ořízne nadbytečný černý okraj (středový výřez 256×256
  z původních 512×512), aby symbol na LG nebyl příliš malý.
- Runtime nemá IP, SSID, model TV, pevný HDMI port ani osobní klíč. Discovery
  počítá broadcast z aktuálních IPv4 adres/masek a identitu TV z hostitele/MAC.
  HDMI seznam i poslední výběr patří konkrétní instalaci TV.
- `cz.playcaption.overlaytest` je stabilní instalační ID, nikoli vazba na testovací
  TV. Zůstává kvůli aktualizaci starších instalací bez druhé aplikace.
- PC není předpokladem samotného protokolu, ale podporovaný odesílač nyní dodává
  Android. Obecný text „zařízení“ neslibuje hotovou PC aplikaci.
- [Instalace z Androidu](INSTALACE-Z-ANDROIDU.md): existující správce a návrh
  samostatného instalátoru přímo v PlayCaption. Nevyžaduje root.

## Omezení platformy

`transparent` a typ okna `overlay` vycházejí z existujícího
[LG Camera Overlay](https://github.com/mbsintegrations/LG-Camera-Overlay/blob/main/source/appinfo.json).
Nedokumentovaný `restartUserActivityTimer` každých 45 s brání systémovému zavření
po 120 s; nesmí se zaměňovat s prodlužováním Developer Mode. Odmítnutí volání zůstává
viditelné i při příjmu titulků. Nejde o garantované API pro všechny LG ani o schválení v LG Store.

UDP původní aplikace nemá autentizaci, proto pouze důvěryhodná LAN bez přesměrování
portů. Licence celého přijímače je AGPLv3, viz `../LICENSE`.

Reference: [LG JS služby](https://webostv.developer.lge.com/develop/guides/js-service-usage),
[životní cyklus služby a Activity Manager](https://webostv.developer.lge.com/develop/guides/js-service-faq),
[spouštění aplikací](https://webostv.developer.lge.com/develop/references/application-manager),
[CLI](https://webostv.developer.lge.com/develop/tools/cli-dev-guide),
[Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app).

Plán P0–P4 a hranice větví: [TV-PLATFORMS-PLAN.md](../docs/TV-PLATFORMS-PLAN.md).
