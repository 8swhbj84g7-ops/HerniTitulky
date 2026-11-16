# PS5 bez obrazu a příjem překladů z telefonu

## Stav – 16. 11. 2026

Diagnostická větev `codex/ps5-session-diagnostics` vychází z masteru `bc5237d`.
Stejná TCL 65C805 nebyla dostupná přes ADB. Zaseknutí `ps5-konec` v
`setSurface(null)` není potvrzené a pořadí ukončování se zatím nemění.
Tento krok není potvrzenou opravou původního incidentu.

Dodané podklady zachycují různé situace:

- Hlášení Android TV „Připojeno, ale nechodí žádný obraz“ znamená, že aktuální
  relace ještě nemá snímky. Bez stacku nelze určit, zda je příčinou stará relace,
  síť nebo cesta dekódování.
- Dodaný textový log obsahuje přečtené anglické repliky a 898 snímků od startu
  relace. Nejde o nulu ani o naměřených 898 snímků za posledních 30 sekund.
- Fotografie telefonu potvrzuje český překlad na telefonu. Nedokazuje odeslání,
  příjem ani zobrazení na TV. Režim „S telefonem“ musí mít spuštěný přijímač;
  TV při něm nemá držet vlastní Remote Play relaci.
- Dřívější report popisuje obsazenou PS5 a pomoc po vynuceném ukončení, ale
  k tomuto kroku nebyl k dispozici záznam stacku visícího vlákna na stejné TCL.

## Co tato větev mění

- Každé ukončení má místní ID relace a začátek/konec kroků `setSurface(null)`,
  `stop` a `dispose(join/free)` včetně délky. Při překročení diagnostických
  5 sekund se jednou pro daný krok zachytí Java stack příslušného vlákna.
  Kontrola běží na samostatném vlákně, nezávisle na hlavním vlákně aplikace.
  Limit pouze zapisuje diagnostiku; nepřerušuje JNI, nevolá paralelně stop
  a neuvolňuje paměť relace násilím.
- Události připojení/konce označují zdrojovou i aktuální relaci. Hlídač zaznamená
  cestu YUV/GL, roli zařízení, počet snímků a stáří posledního snímku.
- První chyba `GlSnimac` se zapíše místo tichého spolknutí. Diagnostika obsahuje
  třídu chyby a stack metod, nikoli PSN klíče, tokeny, údaje účtu či argumenty JNI.
- Automatické GL se nově ukládá až po skutečném snímku. Po doloženém YUV snímku
  v této instalaci výpadek relace sám nevyvolá přepnutí na GL. Při nule snímků
  z neověřeného GL pokusu se návrat na YUV řídí stávajícím hlídačem.
- Stará uložená volba GL se bez důkazu nemaže: nejprve se použije jako dosud
  a její funkčnost se potvrdí snímkem. Předchozí verze nezaznamenávala historii
  úspěšného YUV, takže tuto historii nelze zpětně odhadnout. Pokud staré GL dává
  obraz, ale je pomalé, tato větev sama neprokazuje, že má místo něj použít YUV.
- Telefonní cesta, kodeky a stávající časy 10/15/30 sekund se nemění.
  Spárování, účty ani další nastavení se nemažou. Pro aktualizaci zachovat podpis APK.
- V režimu příjmu z telefonu se zaznamená start, první textový PCT1 paket,
  rozhodnutí o vstupu TV a oprávnění překryvu. Obsah textu se do nové diagnostiky
  nezapisuje. Výsledek je ve stejném `titulky.log` a v okně Záznam; nové značky
  jsou také v logcat pod tagem `PlayCaptionDiag`.

## Přejímka na stejné TCL – Adam

1. Použít APK sestavené z této větve se zachováním dat. Poznamenat verzi APK,
   model/firmware Android TV a přesný čas.
2. **Jen televize:** telefon zatím nepřipojovat. Zachytit normální příjem
   snímků a záznam `[obraz: potvrzen skutečný snímek YUV]` nebo GL.
3. Zastavit aplikaci běžným tlačítkem a zkusit dřívější chybný postup. Dokud
   chyba trvá, nepoužívat Vynutit ukončení. Po alespoň pěti sekundách uložit
   z TV část Záznam → „PS5 / obraz / příjem z telefonu“ a časově odpovídající
   záznam telefonu. Původní `ZALOHA\diag_vlakna.ps1` je podle reportu u Adama,
   není součástí tohoto repozitáře; případný debuggerd stack pořídit při závadě.
4. Rozhodující důkaz pro podezření na `setSurface(null)` je stejná relace
   `#ID`: začátek tohoto kroku, překročený limit a stack v
   `ChiakiNative.sessionSetSurface` / `Session.setSurface`, bez následného
   začátku `stop`. Pokud visí až `sessionJoin`, hypotéza o prvním kroku potvrzená
   není. Pokud všechny kroky skončí, hledat jinou příčinu obsazené PS5.
5. **S telefonem ověřit odděleně:** TV přepnout do „S telefonem“, na telefonu
   vybrat právě tuto TV jako výstup. Ověřit `[TV příjem] start S telefonem` a
   po českém titulku první neprázdný PCT1. Chybějící paket znamená problém
   odeslání/přenosu/příjmu; `vstupTV=false` nebo chybějící oprávnění ukazuje
   jinou větev než nulu snímků z PS5. Případná oprava se musí opřít o tyto logy.
6. Až po zachycení stavu lze jako dočasné zotavení použít Nastavení TV →
   Aplikace → PlayCaption → Vynutit ukončení a znovu spustit. Bez odinstalování
   a bez mazání dat.

Po potvrzení místa blokování teprve posoudit změnu pořadí stop/odpojení Surface
a zopakovat oba režimy na stejné TCL. Samotný timeout není oprávnění k souběžnému
`sessionFree`, dokud nativní vlákno paměť relace může používat.

## Automatická kontrola

`bash gradlew :app:testDebugUnitTest :app:assembleDebug` a `git diff --check`.
Na této větvi prošlo 201 JVM testů bez selhání nebo přeskočení a sestavení APK.
`VolbaObrazuTest` ověřuje historický úspěch YUV, zachování staré/ověřené volby GL,
neukládání pouhého pokusu, návrat neověřeného GL a opakované snímky bez dalších
zápisů nastavení. `PrubehUkonceniTest` simuluje čas a skutečně čekající Java
vlákno; ověřuje pořadí, délky, jednorázový stack a pokračování po výjimce.
Nejde o test nativního Chiaki na TCL ani o potvrzení příčiny její závady.
