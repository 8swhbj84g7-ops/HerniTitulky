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

Stav: náměty, bez implementace v první verzi.

- Dialogové volby a delší herní texty přenášené zprávou `PCV1`.
- Modrý OCR rámeček `PCO1`, rámeček pro nastavení a barvy jmen postav.
- Další LG modely/firmware a několika hodinové hraní, včetně uspání a návratu TV.

## PC-003 – Měřením podložená optimalizace přenosu a čtení

Stav: návrh pro pozdější fázi, ne důvod k přepisu aplikace nyní.

- Zpracovávat nejnovější použitelný snímek místo rostoucí fronty starých snímků.
- OCR spouštět podle změny textové oblasti, překladem nezatěžovat opakovaný text.
- Sledovat identitu textového bloku a úplnost řádků, ne pouze poslední přečtený řádek.
- U budoucí verze protokolu vyhodnotit session ID a pořadí zpráv, aby se daly
  odlišit opožděné UDP pakety od restartu aplikace na stejném zařízení.
- Návrhy posoudit až po PC-001; mohou již částečně existovat v Android kódu.
