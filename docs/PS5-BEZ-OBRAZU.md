# PS5 bez obrazu a příjem překladů z telefonu

## Stav – 16. 11. 2026

PR #15 zůstává draft. Adam předal přejímku na TCL 65C805, která potvrdila
zablokování `ps5-konec` v `setSurface(null)` ještě před zavoláním `stop()`.
Níže je úzká oprava. Její nové sestavení ještě nebylo ověřené na stejné TCL;
bez opakované přejímky PR neslučovat. Původní soubory `ZALOHA\tv_titulky.log`
a `ZALOHA\diag_vlakna\*` jsou u Adama, na tomto Macu nejsou k dispozici.

## Rozhodující důkaz předaný Adamem

- Relace `#a226453`, GL: připojena 20:33:29, přechod do „S telefonem“ 20:34:05.
- `20:34:05 [PS5 konec #a226453] setSurface(null) začátek`.
- `20:34:10 5000 ms překročeno: setSurface(null)`; stack
  `ChiakiNative.sessionSetSurface(Native Method) ← Session.setSurface(Chiaki.kt:393)
  ← ukonciVPozadi(RemotePlayService.kt:2140)`. `stop` nezačal.
- Podle reportu ani po více než 90 s nenastal konec tohoto kroku; `ps5-konec`,
  MediaCodec a Takion dál běžely. Telefon se připojil až po vynuceném ukončení TV.
- Oddělená kontrola „S telefonem“: start 20:40:10, neprázdný PCT1 s 18 znaky
  20:42:22, `vstupTV=true`, oprávnění překryvu=true a Adamem potvrzené zobrazení.
  To potvrzuje funkční příjem/překryv v tomto pokusu, nikoli opravu ukončení.
- `debuggerd -j` bez rootu nefungoval, ale diagnostický Java stack zachytil
  vstup do konkrétního JNI volání. Pro tuto změnu není nutné získat root.

## Úzká oprava po zachycení závady

Ukončení nyní volá `stop()` → `dispose()` (`sessionJoin` → `sessionFree`).
Samostatné `setSurface(null)` se v této cestě nevolá. Pouhé přehození na
`stop()` → `setSurface(null)` → `dispose()` by stále mohlo zablokovat úklid
ve stejném Surface volání.

Jde o stejný postup jako
[shutdown v Chiaki-TV](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/android/app/src/main/java/com/metallic/chiaki/session/StreamSession.kt#L41).
Lokální `Session.dispose()` čeká přes join a až potom volá free. Referenční
[JNI sessionFree](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/android/app/src/main/cpp/chiaki-jni.c#L368)
zahrnuje ukončení video dekodéru. Je to podklad k použití API, nikoli nezávislé
ověření shody každé dodané binární knihovny s tímto upstream commitem.

Diagnostika měří nové kroky `stop` a `dispose(join/free)`. Pokud se zastaví až
join nebo úklid dekodéru, záznam to rozliší. Žádné paralelní free, přerušení JNI,
násilné zabíjení procesu nebo nový ukončovací timeout se nepřidává.

Pozorování staré GL volby a rozdílu ~1000 ms proti dřívějším ~45 ms u YUV je
samostatný výkonový nález. Tato úprava GL preference nemění: přejímka musí
nejdřív zopakovat původní GL scénář. Historicky uložené GL už může být prvním
snímkem označené jako ověřené; jednorázový návrat k YUV vyžaduje samostatný
návrh a přejímku, nelze ho vydávat za součást opravy ukončení.

Dodané podklady zachycují různé situace:

- Hlášení Android TV „Připojeno, ale nechodí žádný obraz“ znamená, že aktuální
  relace ještě nemá snímky. Bez stacku nelze určit, zda je příčinou stará relace,
  síť nebo cesta dekódování.
- Dodaný textový log obsahuje přečtené anglické repliky a 898 snímků od startu
  relace. Nejde o nulu ani o naměřených 898 snímků za posledních 30 sekund.
- Fotografie telefonu potvrzuje český překlad na telefonu. Nedokazuje odeslání,
  příjem ani zobrazení na TV. Režim „S telefonem“ musí mít spuštěný přijímač;
  TV při něm nemá držet vlastní Remote Play relaci.

## Co tato větev mění

- Každé ukončení má místní ID relace a začátek/konec kroků `stop`
  a `dispose(join/free)` včetně délky. Při překročení diagnostických
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

## Opakovaná přejímka na stejné TCL – Adam

1. Použít nové APK z aktuálního headu PR #15, sestavené stejným podpisem jako
   dosavadní instalace. Aktualizovat bez mazání dat. Zapsat commit, model,
   firmware, cestu GL/YUV a čas. Před začátkem smí být nutné jednou vynuceně
   ukončit už visící starý proces; od začátku měřeného pokusu už nikoli.
2. „Jen televize“: připojit PS5 a potvrdit příchod snímků. Na stejné GL cestě
   přepnout do „S telefonem“. Pro stejné ID relace očekávat začátek/konec
   `stop`, jeho návratový kód a začátek/konec `dispose(join/free)`. Nový záznam
   nesmí ukazovat ukončovací volání `setSurface(null)`.
3. Telefon musí převzít PS5 bez vynuceného ukončení TV a bez opakovaného
   `Remote Play on Console is already in use`. Na TV ověřit neprázdný PCT1
   a skutečně zobrazené české titulky. Pouhé přijetí paketu není přejímka obrazu.
4. Opakovat nejméně třikrát: „Jen televize“ → „S telefonem“ → „Jen televize“.
   Ověřit i běžné ukončení aplikace. Tlačítko „Vypnout překlad“ jen pozastavuje
   čtení a není testem ukončení relace; jeho význam tento PR nemění.
5. Zopakovat „Jen televize“ s YUV, pokud je tato cesta na dané instalaci dostupná
   bez mazání dat. Nevyvozovat z pomalého GL závěr o rychlosti YUV bez porovnání
   stejného měření a scény.
6. Pokud některý krok zůstane viset, nejprve uložit stejnou relaci z TV logu,
   stack z diagnostického limitu a časově odpovídající log telefonu. Teprve
   potom lze jako zotavení použít Vynutit ukončení. PR zůstává draft, pokud
   původní scénář nelze zopakovat, chybí důkaz převzetí nebo oprava nefunguje.

Limit pouze zapisuje diagnostiku. Není oprávněním k souběžnému `sessionFree`,
dokud nativní vlákno paměť relace může používat. Bez releasu, tagu a změny verze.

## Automatická kontrola

`bash gradlew :app:testDebugUnitTest :app:assembleDebug` a `git diff --check`.
Na této větvi prošlo 204 JVM testů bez selhání nebo přeskočení a sestavení APK.
`VolbaObrazuTest` ověřuje historický úspěch YUV, zachování staré/ověřené volby GL,
neukládání pouhého pokusu, návrat neověřeného GL a opakované snímky bez dalších
zápisů nastavení. `PrubehUkonceniTest` simuluje čas a skutečně čekající Java
vlákno; ověřuje pořadí, délky, jednorázový stack a pokračování po výjimce.
Nové testy ověřují stop před dispose a diagnostiku při blokujícím dispose.
Původní blokování potvrdil Adamův report, novou nativní cestu na TCL automatické
JVM testy neověřují; rozhodne až opakovaná přejímka výše.
