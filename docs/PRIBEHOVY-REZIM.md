# Příběhový režim: čtecí fronta

Větev `feature/story-mode`, první krok P2. **Hotové je samostatné testovatelné jádro, nikoliv zapnutelný režim v aplikaci.** OCR, překladač, nastavení, UDP, LG a Android vykreslování se tímto krokem nemění.

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

Zalomení stránek dodá výstupní vrstva. Testy používají předem připravené české stránky; fronta **neodhaduje šířku textu na LG**. P2-05 musí vyřešit, jak přijímač předá skutečnou geometrii/dokončení a kdy bylo zobrazení opravdu zahájené. Žádné nové zprávy protokolu zde nejsou zavedené.

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

Fronta sama nemá překladový timeout a neposuzuje, že A je už „moc staré“. Výrobce překladu musí vrátit výsledek, novou revizi nebo explicitní konečnou chybu. Jinak zůstane správné pořadí čekat na A. Omezený rozpočet překladové práce je úkol P2-04; není to důvod automaticky zahazovat hotové příběhové repliky.

## Ověření

```sh
bash gradlew :app:testDebugUnitTest --tests cz.titulky.tv.CteciFrontaTest
bash gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

Testy používají JUnit a virtuální milisekundy bez `sleep`. Pokrývají krátké/dlouhé/Unicode věty, A/B/C, obrácené dokončení překladu, revize, stejné samostatné repliky, deduplikaci, celé stránky, zpožděné tick, pauzy, obnovení, reset relace, explicitní chyby, limity a neměnnost přijatých dat.

15. 11. 2026: **33 nových testů fronty a všech 44 původních JVM testů prošlo** (77 celkem, žádná chyba/selhání/přeskočení). `:app:assembleDebug` také prošlo; běh používal Java 21 a Android SDK 34.

Žádná nová APK nebyla kvůli tomuto kroku instalovaná. Testy pauzy/obnovení nejsou test skutečného výpadku UDP; produkční UI, automatický převod OCR na tokeny a potvrzení z LG zatím frontu nevolají.
