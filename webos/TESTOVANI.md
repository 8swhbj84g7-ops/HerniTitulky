# Kontrola LG přijímače před předáním

## Krátké dialogy ve verzi 0.0.7 – 15. 11. 2026

- Záznam z Redmi Pad Pro zachytil dvě chybné OCR varianty téže repliky v GTA V,
  které Android spojil jako pokračování. Ochrana proti tomuto případu je na větvi
  `bugfix/subtitle-cues`; 44 JVM testů prošlo a na tablet byla nainstalována
  `PlayCaption Test 0.20.1-cues-test01` se zachováním dat (`adb install -r`).
- LG přijímač nyní ukazuje dialog po nejvýše dvou řádcích. Nová replika zruší
  zbytek předchozí, heartbeat ani stejné znění s novým ID nevrací stránky na začátek.
  Platnost paketu dál omezuje celkovou dobu čtení; rychlá nová replika může
  přerušit nedočtené stránky. Text a nabídky odpovědí nejsou tímto limitem dotčené.
- C5 Web Inspector otestoval devět kombinací šířky (20/54/100 %) a velikosti
  písma (0.5/1.05/2.5). Obsah stránek dohromady odpovídal originálu, bez přetečení.
  Největší naměřená výška boxu byla 17.998 % obrazu.
- Skutečné UDP pakety v nainstalované aplikaci vystřídaly stránky a novou krátkou
  repliku; prázdný paket zobrazení ukončil. Nejvýše dva řádky, žádný návrat starého
  textu po nové replice. SHA256 všech 11 souborů UI a služby souhlasí se zdroji.
- Herní přejímka po opravě ještě zbývá. Špatně rozpoznaná slova z Remote Play
  tímto krokem obecně neopravujeme; nejde o důkaz kvality všech her a fontů.

## Ověření voleb a rámečků ve verzi 0.0.6

- Skutečné UDP pakety na C5 současně vykreslily titulky, volby a oba rámečky.
  Web Inspector naměřil modrou oblast `(0.1, 0.72, 0.8, 0.25)` a žlutou
  `(0.55, 0.45, 0.4, 0.35)` ve formátu x/y/šířka/výška, podle paketů.
- Český seznam byl vlevo vedle oblasti voleb, druhá odpověď byla zvýrazněná.
  Jména byla zelená a červená podle ARGB dat; zelený náhled měl přerušovaný obrys.
- Celý testovací odstavec v režimu Text odpovídal odeslanému obsahu. DOM potvrdil
  umístění uvnitř obrazovky a obsah bez oříznutí.
- Uživatel na tabletu potvrdil, že modrý i žlutý rámeček jsou na LG vidět
  a pohybují se podle tabletu. Jde o end-to-end test nastavování oblastí.
- SHA256 všech šesti souborů UI a pěti souborů služby na TV odpovídal aktuálním
  zdrojům; nejde pouze o kontrolu souboru APK/IPK v počítači.
- Skutečné herní odpovědi a úplnost jejich OCR/překladu zbývají k přejímce;
  syntetické UDP pakety je nenahrazují. Viz `SHODA-ANDROID.md`.

## Automatické kontroly

```sh
node webos/test.cjs
node webos/test-udp.cjs
node webos/test-input.cjs
node webos/test-receiver.cjs
python3 webos/build.py
git diff --check
```

`test.cjs` testuje parser, Unicode a escapování řádků, neplatné/velké pakety,
číselné limity, dobu zobrazení, heartbeat, výpadek a obnovení přenosu, restart UI,
prodlužování překryvu, opožděné odpovědi a trvalé chybové hlášení. Síť a systémový
most LG zde nahrazují testovací objekty; není to důkaz kompatibility konkrétní TV.

`test-udp.cjs` spouští produkční službu se skutečnými UDP sockety. Pouze porty/cíle
přesměruje na loopback a náhrada Luna busu zachytí odpověď odběrateli. Ověřuje
discovery, přenos víceřádkového českého paketu a ukončení socketu/časovače po odhlášení.
Nezávisí na televizi, připojeném tabletu ani přístupu k lokální síti.

`test-input.cjs` ověřuje seznam HDMI, výběr jiného než pevně daného portu,
pořadí spuštění HDMI → overlay, validaci vstupu, souběžný požadavek, odmítnutí
API, timeout a opožděnou aktivitu. Testuje i výběr v UI, uloženou volbu, neplatné
odpovědi a obnovení bez opětovného otevření menu. Systémová API jsou zde náhrady.

`test-receiver.cjs` testuje PCV1/PCO1, limity a neplatné souřadnice, časování
voleb/textu/rámečků, zvýraznění odpovědi, barevná jména, souběžné kanály se stejným
ID a společný výpadek spojení. Také dvouřádkové stránky, úplnost obsahu, dlouhé slovo,
Unicode, opakované znění, nahrazení novou replikou a ukončení stránkování.

`build.py` znovu spustí všechny čtyři testy a kontrolu syntaxe, sestaví IPK oficiálním CLI
3.2.6 a porovná veškerý obsah IPK se zdroji. Odmítne soubory navíc či jinou verzi.
Vytvoří ZIP s návodem, licencí, odpovídajícími zdroji a SHA256SUMS; ověří CRC ZIPu.
Vyžaduje Python 3.9+, Node.js 20+ a internet pro stažení CLI přes npx.

## Dosavadní důkazy ze skutečné TV – 14. 11. 2026

| Oblast | Důkaz / omezení |
|---|---|
| Sestava | LG OLED65C5ELB, SDK 11.2.0, firmware 43.21.96; Redmi Pad Pro, PS5. |
| Překrytí HDMI | Uživatel potvrdil testovací text přes HDMI a následně skutečné titulky z tabletu. |
| Discovery a UDP | Na počítači přijato ohlášení LG; testovací paket prošel přes TV službu až do viditelného titulku v DOM aplikace. |
| Systémový limit | TV log zaznamenal skrytí overlay přesně 120 s po spuštění verze 0.0.2. |
| Oprava ve verzi 0.0.3 | Volání restartUserActivityTimer vrátilo returnValue=true. Web Inspector kontroloval běh v 0/30/60/90/125/150 s; ve 150 s byl viditelný přijatý titulek. |
| Kontrola verze 0.0.4 | Stejný IPK jako v distribučním ZIPu nainstalovaný na C5. Kontrola přes Web Inspector v 0/30/60/90/125/150 s: aplikace zůstala viditelná, bez hlášení o odmítnutém časovači. |
| Tři řádky ve verzi 0.0.4 | Syntetický český UDP paket se třemi řádky prošel přes službu do UI. DOM text přesně odpovídal paketu; výška boxu pokryla tři řádky a box zůstal v ploše obrazovky. Nejde o test OCR ani překladu ze hry. |
| Zavření a opětovné spuštění 0.0.4 | Po zavření aplikace přes CLI přestalo přicházet UDP discovery; po dalším spuštění opět přicházelo z C5. |
| Úplnost překladu | Uživatel dodal chybu víceřádkového textu; zatím nebyla lokalizovaná ani opravovaná, viz PC-001 v BACKLOG.md. |

Tato tabulka nedokazuje několikahodinový běh, všechny HDR/VRR režimy, jiné modely,
Xbox nebo distribuci přes LG Store. Výsledky starší verze se automaticky nepřenášejí
na novou verzi; u vydávaného IPK znovu zaznamenej provedené kroky níže.

## Ruční přejímka aktuálního IPK na TV

- [ ] Zapsat verzi LG přijímače, verzi Android APK, model/firmware LG a model Android zařízení.
- [ ] Nainstalovat IPK ze sestaveného ZIPu, otevřít na TV a vybrat HDMI přímo v aplikaci.
- [ ] Najít LG přes přepínač 📺 Televize na Android obrazovce s překladem.
- [ ] Porovnat stejný český text na tabletu a LG; vyzkoušet české znaky i více řádků v jednom paketu.
- [ ] Změnit polohu, šířku a velikost zeleného boxu; pozor na překrytí nápovědy.
- [ ] Nechat běžet alespoň 3 minuty bez LG ovladače a potvrdit, že přijímač stále běží.
- [ ] Přerušit odesílání z tabletu: starý text zmizí do přibližně 5 s. Obnovit přenos.
- [ ] Zpět na LG ovladači zavře aplikaci; po dalším spuštění funguje discovery i příjem.
- [ ] Ověřit obraz, zvuk a běžně používaný HDR/VRR režim; zaznamenat, který byl zkoušen.
- [ ] Před širším vydáním otestovat delší hraní a alespoň další kombinaci model/firmware.

Bez skutečné TV lze potvrdit balení a logiku, nikoli chování nedokumentovaného LG overlay.

## Ověření výběru HDMI ve verzi 0.0.5

- C5 vrátila seznam čtyř HDMI včetně názvů zařízení přes `getAllInputStatus`.
- UI zobrazilo načtené vstupy; výběr PS5 spustil službu a překryv byl obnoven
  s parametrem `resumeOverlay: true`. Web Inspector potvrdil zavřený výběr HDMI.
- Uživatel ovladačem vyzkoušel domeček → PlayCaption LG → HDMI s PS5 a potvrdil
  současný obraz PS5 i překryv. To ověřuje konkrétní postup, nikoli automatické
  obnovení po libovolné systémové změně vstupu.
- Stejné C5 odmítla webové aplikaci metodu `getForegroundAppInfo`.
- Delší provoz nové verze, jiná TV/firmware a všechny HDR/VRR kombinace zůstávají
  k ruční přejímce. Doklady běhu do 150 s výše se vztahují ke starším verzím.
