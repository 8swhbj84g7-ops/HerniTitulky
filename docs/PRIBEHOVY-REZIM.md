# Příběhový režim: čtecí fronta

Větev `feature/story-mode`. Integrační MVP je napojené na Android překladač a LG přijímač 0.0.8. Standardní režim zůstává výchozí a zachovává dosavadní překladovou i zobrazovací cestu. Nový režim se zapíná v PlayCaption Test na obrazovce TelefonActivity.

`CteciFronta.kt` je čistý Kotlin bez Android závislostí, vláken, sítě a vlastních hodin. Volání serializuje jediný vlastník, výhledově Android. LG zde nemá vlastní příběhovou frontu.

## Vstupy a pravidla

| Volání | Odpovědnost |
| --- | --- |
| `zarad(puvodniCasMs)` | Jednou pro novou samostatnou repliku, ve zdrojovém pořadí **před spuštěním překladů**. Vrací token relace/pořadí/revize; při plné frontě `null`. |
| `oprav(token)` | Zneplatní čekající starou revizi, uvolní její stránky a zachová pořadí. Vrátí nový token; rozečtené nebo neplatné repliky odmítne `null`. |
| `priprav(token, stranky)` | Dodá finální české stránky. Výslovně rozlišuje přijetí, opakování, neplatný token, již připravený výsledek a překročený limit. |
| `zobraz(tedMs)` | Vrátí aktuální stránku s identitou, indexem a časem; nebo `null`, když není co číst / čtení stojí. Volat po změně vstupu i při dosažení `doMs`. |
| `pozastav(ano, tedMs)` | Schová výstup a uchová přesný zbývající čas. Příjem dalších replik/překladů pokračuje. Lze využít při volbách nebo odpojení výstupu. |
| `chyba(token)` | Explicitní konečné selhání dosud nepřipraveného překladu. Uvolní pořadí a zvýší počítadlo chyb; neodstraní už přijatý platný překlad. |
| `reset()` | Nová generace relace, prázdná fronta, ukončená pauza, nulová počítadla. Staré callbacky nelze znovu přijmout. |

Časy jsou milisekundy z jednoho monotónního zdroje, v běžícím Androidu například `SystemClock.elapsedRealtime()`. Reset nevrací hodiny zpět. `puvodniCasMs` se uchovává pro pozdější měření prodlevy; pořadí určuje registrace, nikoliv čas dokončení překladu.

Opakované OCR musí výrobce spojit se stejným tokenem; fronta sama nehádá identitu podle podobnosti textu. Dvě samostatně registrovaná „Ano.“ jsou dvě skutečné repliky. Režim `Text`, volby a OCR hypotézy nejsou automaticky další položky této fronty.

Překlady B/C mohou být připravené, zatímco se ještě překládá A. Zobrazí se až po A. Samostatná starší replika neztrácí platnost příchodem novější; odmítá se jen nesprávná relace/revize, odstraněný token nebo změna zmrazeného finálního výsledku.

Čekající revizi lze opravit i po jejím překladu, dokud se nezačala číst. Od první stránky je obsah celé repliky neměnný; pozdní offline/online náhled nesmí rozečtený text překlopit. Zde fronta přijímá pouze finální překlady, volbu finálního výsledku musí dodat P2-04.

## Stránky a čas

Android vlastní celou čtecí frontu a předává LG vždy jen jednu aktuální repliku. LG změří skutečné zalomení do dvouřádkového boxu a postupně vykreslí její stránky. Žádné další repliky nefrontuje. Android používá `zobraz(tedMs, externiDokonceni = true)` a čeká na potvrzení `potvrzeno(token)`; lokální odhad doby celé věty nemůže předčasně uvolnit další repliku. Potvrzení LG přichází až po čtecí době poslední stránky.

Každá stránka má dobu:

```text
max(1800 ms, ceil(počet Unicode code pointů po NFC × 1000 / 14))
```

Původní text se nemění. NFC při počítání sjednotí například `č` a `c` + kombinující háček. Doplňkové Unicode znaky, například běžné emoji, se nepočítají jako dvě UTF-16 jednotky. Nejde o obecné počítání všech složených emoji jako jednoho grafému.

První volná připravená replika začne při prvním `zobraz()` ihned. Nové výsledky, duplicitní doručení ani změny původních anglických titulků automaticky nekrátí českou čtecí dobu. Pokud je volání pozdě, začne následující dosud neviděná stránka až nyní a dostane svůj celý čas; fronta ji nepřeskočí kvůli starému plánu. Poslední stránka má stejnou ochranu.

Po pauze zbývá přesně tolik času jako před ní. Pozastavení v okamžiku konce stránky nechá po návratu začít další stránku s celým časem. `dokoncenych` se zvýší až po doběhnutí poslední stránky; `pocet` zahrnuje čekající překlady, připravené i právě čtenou repliku. Počítadla se resetují s relací.

## Simulovaný rychlý rozhovor

Test `triRychleReplikySeZobraziJednouVPoradiADoctouVPauze`:

| Zdrojový příchod | Český dialog | Zobrazení |
| ---: | --- | --- |
| 0 ms | Trevor: Ahoj. | 0–1800 ms |
| 100 ms | Michael: Pojď. | 1800–3600 ms |
| 200 ms | Franklin: Jdu. | 3600–5400 ms |

Tři přijaté a dokončené repliky, žádné přeskočení nebo předčasné přerušení. V této simulaci čtecí prodleva je 0 / 1700 / 3400 ms a fronta se vyprázdní v 5400 ms. Není to naměřená latence při hraní.

Další test dokončí překlady v pořadí C → B → A. Fronta čeká na platné A a zachová původní pořadí. Jiný test pustí 1000 postupně čtených replik při kapacitě jediné položky; dokončené položky se odstraňují a jejich pozdní výsledky se odmítají.

## Limity bez tichého zahazování

Výchozí kapacita je 128 rozpracovaných replik a 8192 UTF-16 jednotek textu **na repliku v součtu všech jejích stránek**. Uchovávaný obsah je tedy omezený nejvýše 1 048 576 UTF-16 jednotkami plus režie seznamů a položek. Prázdné stránky jsou chybou volajícího. Limity nemají vliv na rychlost čtení již přijatého textu.

Limit textu je samostatný pro každou repliku. Společný rozpočet by mohl způsobit deadlock: B/C zaplní všechen textový prostor, opožděné A se nevejde a B/C se bez A nesmějí přečíst. Tento scénář má vlastní regresní test.

Při naplnění se nová položka nepřijme; při příliš dlouhém textu se vrátí `LIMIT_TEXTU`, nic se neořízne a dříve přijaté položky zůstanou zachované. **Napojení musí odmítnutí obsloužit viditelně nebo řízeným zpětným tlakem.** Nesmí takto odmítnutý obsah potichu zapomenout ani ho neomezeně přesunout do jiné fronty.

Fronta sama nemá překladový timeout a neposuzuje, že A je už „moc staré“. `PrekladPribehu` nyní poskytuje výrobu jednoho finálního výsledku: nejvýše jeden souběžný online požadavek bez síťové fronty a retry, po 2,5 s offline záloha, konečný limit 8 s. Obsazené online zpracování použije pro další repliku offline rovnou. Chyba offline modelu nezahodí dosud běžící online překlad. Pozdější úspěch nesmí podruhé změnit finální výsledek. Chyba je viditelná ve stavu Příběhového režimu a uvolní následující repliku; nepoužívá se anglický text vydávaný za český překlad. Standardní Google/Edge/MyMemory/offline fallback cesta zůstala zachovaná. Pro Story MVP se používá jeden Google pokus a lokální ML Kit.

## Ověření

```sh
bash gradlew :app:testDebugUnitTest --tests cz.titulky.tv.CteciFrontaTest
bash gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

Testy používají JUnit a virtuální milisekundy bez `sleep`. Pokrývají krátké/dlouhé/Unicode věty, A/B/C, obrácené dokončení překladu, revize, stejné samostatné repliky, deduplikaci, celé stránky, zpožděné tick, pauzy, obnovení, reset relace, explicitní chyby, limity a neměnnost přijatých dat.

15. 11. 2026: **33 nových testů fronty a všech 44 původních JVM testů prošlo** (77 celkem, žádná chyba/selhání/přeskočení). `:app:assembleDebug` také prošlo; běh používal Java 21 a Android SDK 34.

Testy používají skutečný produkční adaptér `PribehovaRelace` a překladovou strategii s virtuálními hodinami. Testy UDP zahrnují skutečné loopback sockety a potvrzení od LG služby. Testy pauzy/obnovení samotného rendereru nenahrazují několikahodinové hraní.


## Zapnutí a návrat

Na Android tabletu otevři **PlayCaption Test**, připoj PS5/Xbox a vyber LG. Na obrazovce překladu je nový přepínač „Standardní režim · přepnout na Příběhový“. Po zapnutí se ukazuje velikost fronty nebo důvod čekání/chyby. Nastavení se ukládá, výchozí je Standardní.

Na LG spusť přijímač 0.0.8 a HDMI vyber přímo v něm. Starší LG přijímač zůstává kompatibilní se Standardním režimem; pro Story chybí potvrzení a Android to zobrazí jako čekání na novější přijímač. Při potížích přepni tlačítkem zpět na Standardní.

Přepnutí režimu, jazyka, zdrojové oblasti, cílové TV nebo relace konzole resetuje Story frontu a neplatné výsledky se již nezobrazí. Zmizení samotného anglického titulku frontu nemaže. Při aktivních dialogových volbách se pozastaví čtení aktuálního příběhového titulku. Syntetické rámečky a Text používají nadále svoje původní kanály.

V režimu jen telefonu Android časuje celé repliky; stávající TextView je přizpůsobí ploše. Toto MVP nepřidává telefonní dvouřádkový stránkovač ani přepínač do přímého Android TV režimu bez telefonu. Primární herní přejímka je Android tablet + LG C5.

## Přenos a obnova

- PCT1/PCV1/PCO1 a Standardní režim zůstávají stejné.
- PCQ1 obsahuje ID relace a ověří podporu Story přijímače.
- PCS1 obsahuje ID relace/repliky/revize, pauzu, celý text jediné aktuální repliky, layout a barvy jmen. Wire limit je 8192 bajtů; příliš velký paket je hlášená chyba výstupu, nikoliv úspěšné dočtení.
- PCA1 vrací pouze `ready`, `reading` nebo `done` se stejným ID. Socket je připojený na zvolenou TV a odpověď pro jiné aktuální ID ignoruje. Potvrzení dočtení starého tokenu neukončí novou repliku.
- LG služba vrací odpověď jen na adresu/port odesílatele právě přijatého Story paketu. Webové UI nemůže určit libovolný cíl odpovědi.
- Každý heartbeat opakuje aktuální stav; LG uchovává aktuální repliku, stránku a výsledek dočtení. Ztracené potvrzení se zopakuje bez návratu na první stránku.
- Změna generace obsahuje prázdný zrušovací paket. Opožděné pakety starší generace se ignorují. Při výpadku spojení se běžící LG čtení skryje a pozastaví; po návratu se obnoví jeho zbývající čas.
- Restart samotného UI může vyžadovat znovupřečtení aktuální repliky; nesmí ji chybně potvrdit jako dočtenou. Nejde o distribuovanou garanci přesně jednou přes úplné restarty zařízení.

## Aktuální důkazy na C5

15. 11. 2026, LG přijímač 0.0.8: skutečný UDP → JS služba → UI → ACK prošel. Testovací replika se rozdělila na **3 stránky**, všechny části dohromady odpovídaly celému vstupu. DOM neměřil žádné přetečení. Čtecí minimum podle stránek bylo **10 644 ms**, skutečné `done` dorazilo za **11 149 ms**.

Po zrušení Story se vykreslil Standardní PCT1 titulek. Následný starý Story paket jej nepřepsal. SHA-256 všech 11 nainstalovaných souborů na TV odpovídalo zdrojům. Lokální důkaz: `/private/tmp/playcaption-story-c5-proof.json` (není součást Git repozitáře).

Android PlayCaption Test byl aktualizovaný přes `adb install -r` pod původním testovacím ID `cz.titulky.tv.ocrfixes`; původní aplikace `cz.titulky.tv` se nepřepisovala. Podrobná herní přejímka úplnosti rychlých replik, návratu do Standardního režimu, dialogových voleb, dlouhé cutscény a delšího výpadku Wi-Fi ještě zbývá.


## Android runtime oprava story-test02

První herní pokus odhalil `NetworkOnMainThreadException`: konstrukce/otevření UDP socketu byla mimo worker. `PribehSpojeni` nyní vytváří, připojuje, obsluhuje i ruší socket na vlastním pracovním vlákně. Hlavní vlákno pouze mění opakovaný stav a požadavek ukončení.

94 JVM testů a všech pět LG testovacích sad prošlo. Na Redmi prošly také oba `PribehSitTest` instrumentační testy: otevření/odeslání/zrušení při `StrictMode.detectNetwork().penaltyDeathOnNetwork()` na hlavním vlákně a skutečný dotaz z tabletu na C5 s odpovědí `ready`. Opravená instalovaná verze je `0.20.1-story-test02`.

Uživatel následně při opětovném připojení PS5 a zapnutí Příběhového režimu potvrdil: chyba zmizela a titulky běží. Tím je potvrzený základní herní provoz opravené verze, nikoliv ještě úplnost všech replik, dlouhodobá stabilita nebo návrat do Standardního režimu při hraní.

Testy lze spustit v odděleném diagnostickém APK podle existujícího postupu; pro druhý test dodat argument `lgHost` s adresou vlastní TV. Pokud je cílem právě používaná PlayCaption Test, zachovat její ID/podpis a data, použít pouze `adb install -r`; samotný instrumentační běh neodinstalovává aplikaci. Bez `lgHost` se pouze volitelný test skutečné TV přeskočí.
