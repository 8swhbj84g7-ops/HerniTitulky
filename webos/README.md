# PlayCaption LG – podklady pro vývoj a předání

Experimentální přijímač pro LG webOS, oddělený od Android aplikace.
Pro uživatele je určen [návod bez sestavování kódu](NAVOD-LG.md).
[Důkazy a přejímací testy](TESTOVANI.md) rozlišují automatické testy a skutečný běh na TV.
[Backlog](../BACKLOG.md) obsahuje odloženou chybu víceřádkového překladu.

## Jak to funguje

Konzole posílá obraz hry přes HDMI do TV. Původní Android PlayCaption získává další
kopii přes Remote Play, čte a překládá text. Přijímač na LG pouze zobrazuje výsledek.
Android kód ani APK se kvůli tomuto přijímači nemění.

- `app/`: průhledné HTML/CSS/JS UI, životní cyklus, heartbeat a doba zobrazení.
- `service/`: Node.js služba používající systémový `webos-service`, UDP a parser.
- `test.cjs`, `test-udp.cjs`: regresní testy a skutečný loopback UDP přenos.
- `build.py`: testy, syntaxe, sestavení IPK, kontrola obsahu a distribuční ZIP.

Kompatibilní protokol je v Android `Propojeni.kt`: `PLAYCAPTION_TV` na UDP 41988
každé 2 s a příjem `PCT1` na UDP 41989. Posíláme příznak nespárované konzole (`0`),
aby přihlášení zůstávalo na Android zařízení. `PCS1` se ignoruje; žádné tokeny konzole
se nepředávají. Síťové texty se vykreslují přes `textContent`, nikoli HTML.

Aktuální rozsah: titulky, nápověda, poloha a velikost. `PCV1`, `PCO1` a barvy jmen
se zatím ignorují. Opakované ID neobnovuje dobu zobrazení; po 5 s bez přenosu se text
skryje. Po zavření posledního odběratele skončí UDP i discovery.

## Sestavení

Potřebuješ Node.js 20+, Python 3.9+ a internet pro připnuté oficiální webOS CLI.
Z rootu projektu spusť jediný příkaz:

```sh
python3 webos/build.py
```

Na Windows lze použít `py -3 webos/build.py`. Aplikace nemá přidané npm knihovny;
CLI 3.2.6 se spouští přes npx pouze pro sestavení. Výstup ve `webos/dist/`:

- `cz.playcaption.overlaytest_0.0.4_all.ipk` pro instalaci do LG;
- `PlayCaption-LG-0.0.4.zip` pro předání uživatelům: IPK, návod, licence,
  přesně odpovídající zdroje přijímače a SHA256SUMS.

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

Přidej TV jako `c5` s vlastní IP, portem 9922 a uživatelem `prisoner`. Na TV zapni
Key Server. Z rootu projektu pokračuj:

```sh
ares-novacom --device c5 --getkey
ares-install --device c5 webos/dist/cz.playcaption.overlaytest_0.0.4_all.ipk
```

Nech TV na HDMI a aplikaci spusť z počítače:

```sh
ares-launch --device c5 cz.playcaption.overlaytest
```

Passphrase ani získaný klíč nepatří do projektu. Identifikátor `c5` je pouze příklad.

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

## Omezení platformy

`transparent` a typ okna `overlay` vycházejí z existujícího
[LG Camera Overlay](https://github.com/mbsintegrations/LG-Camera-Overlay/blob/main/source/appinfo.json).
Nedokumentovaný `restartUserActivityTimer` každých 45 s brání systémovému zavření
po 120 s; nesmí se zaměňovat s prodlužováním Developer Mode. Odmítnutí volání zůstává
viditelné i při příjmu titulků. Nejde o garantované API pro všechny LG ani o schválení v LG Store.

UDP původní aplikace nemá autentizaci, proto pouze důvěryhodná LAN bez přesměrování
portů. Licence celého přijímače je AGPLv3, viz `../LICENSE`.

Reference: [LG JS služby](https://webostv.developer.lge.com/develop/guides/js-service-usage),
[CLI](https://webostv.developer.lge.com/develop/tools/cli-dev-guide),
[Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app).
