# PlayCaption – backlog

## PC-001 – Úplný překlad víceřádkových a postupně se objevujících textů

Stav: zaznamenáno, zatím bez opravy. Priorita: po dokončení LG přijímače.

### Pozorované chování

Uživatel 14. 11. 2026 na PS5 + Redmi Pad Pro + LG C5 hlásí, že první dva řádky
víceřádkového textu krátce probliknou a zůstane přeložený pouze poslední řádek.
Na dodané fotografii je anglický text:

> The Shoebox can store your items. Alan can access stored items from any Shoebox in the Dark Place.

Výsledný český text na LG je pouze „Botník na temném místě.“. Fotografie ukazuje
výsledek, ne průběh čtení; zatím neprokazuje, ve které části zpracování se text ztrácí.
Osobní fotografii místnosti nevkládáme do veřejného repozitáře.

Samostatný test LG přijímače 0.0.4 na stejné C5 zobrazil celý syntetický český paket
se třemi řádky bez oříznutí (porovnání paketu s DOM a rozměry boxu). To ověřuje
pouze zobrazení již kompletního textu; nereprodukuje ani neopravuje chybu ze hry.

### Kontext a hypotézy

- Autor popisuje zpoždění obrazu Remote Play a rozdíl PS5 30 FPS / Xbox 60 FPS.
  To je jeho vysvětlení, nikoli změřená příčina této chyby.
- V aktuálním kódu `RemotePlayService.kt` je pro PS5 vytvořen `ConnectVideoProfile`
  s hodnotou `30` FPS. Vyšší podporovaná frekvence se má posoudit samostatně.
- Prověřit ořez oblasti OCR, seskupení řádků, stabilizaci postupně psaného textu,
  nahrazování částečných výsledků a pořadí asynchronních překladů.
- Oddělit skutečně chybějící text od oříznutí při zobrazení na tabletu nebo LG.
  LG v této verzi pouze přijímá hotový text; samo OCR ani překlad neprovádí.

### Postup budoucího řešení

1. Na stejné scéně porovnat snímek z Remote Play, kompletní výsledek OCR,
   vstup/výstup překladu, odeslaný paket a zobrazení na tabletu i LG.
2. Změřit stáří snímku, interval OCR a čas překladu; identifikovat, kde vzniká
   zpoždění nebo ztráta řádků. Záznamy nesmějí obsahovat PSN klíče/tokeny.
3. Připravit malé opakovatelné scénáře: statický text o 3+ řádcích, postupně psaný
   text, změna celé scény během překladu a opožděná odpověď staršího překladu.
4. Opravit zjištěnou příčinu a přidat regresní test. Nepřepínat FPS naslepo.
5. Teprve potom porovnat 30/60 FPS na stejných datech: latence, úplnost textu,
   zatížení tabletu, teplota a síťový provoz.

Hotovo znamená: zůstává kompletní aktuální text, starší odpověď ho nepřepíše,
neproblikávají zbytečně částečné řádky a po změně scény nezůstává starý překlad.

## PC-002 – Dorovnání funkcí LG přijímače

Stav: v LG 0.0.6 doplněny níže uvedené vizuální funkce; herní přejímka pokračuje.

- Dialogové volby a delší herní texty přenášené zprávou `PCV1`.
- Modrý OCR rámeček `PCO1`, rámeček pro nastavení a barvy jmen postav.
- Matice implementace a testů: `webos/SHODA-ANDROID.md` a `webos/TESTOVANI.md`.
- Zbývají další LG modely/firmware a několika hodinové hraní, včetně uspání a návratu TV.

## PC-003 – Měřením podložená optimalizace přenosu a čtení

Stav: návrh pro pozdější fázi, ne důvod k přepisu aplikace nyní.

- Zpracovávat nejnovější použitelný snímek místo rostoucí fronty starých snímků.
- OCR spouštět podle změny textové oblasti, překladem nezatěžovat opakovaný text.
- Sledovat identitu textového bloku a úplnost řádků, ne pouze poslední přečtený řádek.
- U budoucí verze protokolu vyhodnotit session ID a pořadí zpráv, aby se daly
  odlišit opožděné UDP pakety od restartu aplikace na stejném zařízení.
- Návrhy posoudit až po PC-001; mohou již částečně existovat v Android kódu.

## PC-003 – Instalace LG přijímače přímo z Android PlayCaption

Stav: technicky prověřený návrh, vlastní instalátor zatím neimplementovaný.
Developer Mode a Key Server uživatel aktivuje na TV; root není potřeba. Existuje
samostatný mobilní správce s instalací místního IPK. Aktuální uživatelský návod
obsahuje tuto možnost vedle počítačové instalace. Vlastní průvodce v PlayCaption
vyžaduje SSH/SFTP, bezpečné párování a skutečné potvrzení dokončení instalace.
Rozsah a přejímku popisuje [návrh instalátoru](webos/INSTALACE-Z-ANDROIDU.md).

## PC-004 – Profily přijímačů v Android odesílači

Implementováno: platforma oddělená od konkrétní TV a od aktivního výstupu.
Každá TV má stabilní ID, poslední adresu a vlastní polohu/velikost titulků,
zámky a oblasti čtení. Staré společné nastavení přebírá pouze původní TV;
jazyk a účty konzole se nekopírují. Neznámou platformu potvrzuje uživatel,
verze a schopnosti starého přijímače se neodhadují. Nalezená adresa má přednost
před uloženou; vypnutá poslední TV se automaticky nenahradí jinou.

Kontroly: `bash gradlew :app:testDebugUnitTest :app:assembleDebug` – pět testů
profilů a test skutečného UDP přenosu na loopbacku. Ruční přejímka: dvě fyzické
TV s odlišným nastavením, návrat na telefon a zpět, restart aplikace, původní
Android TV režim. Uživatel na Redmi Pad Pro potvrdil nalezení a výběr C5.
Karta výstupu byla po opravě výšky/odsazení vizuálně zkontrolovaná na tabletu:
rozlišuje vybranou TV od aktivního odesílání. Nepředstírá potvrzení příjmu UDP.
Původní Android TV hardware dosud touto etapou nepřetestován.
Vlastní LG instalátor a přibalený IPK zůstávají navazující etapou.

## PC-005 – Průvodce přípravou LG

Implementováno: pět kroků v Android aplikaci, odkazy na oficiální LG Developer,
veřejná vydání a komunitní instalační správce. Přístup přes zvolenou platformu
LG nebo nabídku Jak připojit televizi. Návrat k rozpracovanému kroku, přeskočení
přípravy u již nainstalovaného přijímače a skutečné vyhledání na konci.
Průvodce neukládá hesla, neprodlužuje Developer Mode a nepovažuje proklikání
instrukcí za dokončenou instalaci či spojení. Vlastní instalátor IPK navazuje.

Kontroly: čtyři testy navigace průvodce a šest dosavadních testů profilů/přenosu;
`bash gradlew :app:testDebugUnitTest :app:assembleDebug`. Postup LG ověřen podle
oficiální dokumentace Developer Mode 15. 11. 2026. Ruční přejímka první instalace
novým uživatelem a mobilní externí správce zůstávají otevřené.

Přejímka na Redmi Pad Pro (test03): automatizované UI prošlo všech pět obrazovek,
systémové Zpět, zavření/obnovení uloženého kroku, přeskočení přípravy, návrat
z externího odkazu a navazující discovery se skutečným seznamem LG přijímačů.
Screenshot posledního kroku potvrdil viditelnost celého textu i navigace.
Tento test nemění Developer Mode na TV a nenahrazuje první instalaci na čisté LG.
