# PlayCaption – úplnost textu a aktuálnost překladu

Audit výchozího `master` na commitu `95b3cc3`, 14. 11. 2026.
Větev `feature/ocr-text-blocks` odděluje Android změny od LG přijímače v PR #1.
Rozsah: příjem snímku, výběr OCR textu, časová stabilizace a zobrazení překladu.
ML Kit, překladové služby, 30 FPS i současné UDP formáty zůstávají zachované.

## Skutečná cesta zpracování

1. `RemotePlayService.kt` přijímá PS5 snímky přes Chiaki a Xbox přes WebRTC.
   Android zařízení má pro PS5 profil 1280 × 720 při 30 FPS.
2. Zvolí se automatická spodní oblast, případně modrá ruční oblast.
   Otisk obrazu, čekání na ustálení a obsazení čtečky omezují četnost OCR.
3. `Ctecka.prectiPosledniTitulek()` spustí ML Kit. Následuje geometrické seskupení,
   filtr vodorovné polohy, menu a nápovědy, výběr poslední repliky a kontrola jazyka.
4. Služba porovnává text s minulým, filtruje dlouho zobrazené a nejisté výsledky
   a podle potřeby spojuje pokračování věty. Překlad může mít offline náhled.
5. `Prekladac.kt` používá cache a jednu síťovou frontu. Zkouší Google, Edge,
   MyMemory a offline ML Kit; měří se i částečně odlišná rychlá cesta.
6. Výsledek jde do Android overlay nebo `TelefonTitulky`, odkud jej vysílač
   posílá přes `PCT1`. LG pouze vykresluje hotový text; HDMI obraz nečte.

Žlutá oblast má vlastní OCR `prectiVolby()` a vlastní cestu. Režim Text spojí
řádky do odstavce; seznam odpovědí zachová samostatné položky. Obě varianty
se posílají přes `PCV1`. Nejde o automatickou klasifikaci instrukcí v celém obraze.

## Potvrzené problémy a jejich opravy

| Problém ve výchozím kódu | Změna a důkaz |
| --- | --- |
| Po rozložení bloků na řádky se jejich původní příslušnost ztratila. Mezera nad 0,9 výšky řádku ukončila skládání i uvnitř jednoho bloku. | `OcrKus` zachovává ID bloku uvnitř snímku. Stejný blok dovolí mezeru do dvou výšek řádku při vodorovném překryvu. Regresní test před opravou vrátil jen `to our home.`, po opravě všechny tři řádky. |
| Pojistka `posledniUkazany` nezrušila čekající překlad při zmizení textu ani při rozpoznání nového textu před dokončením jeho překladu. | Token platnosti výsledku se mění při novém překladu a ruší při změně oblasti, potvrzeném zmizení či ukončení spojení. Fronta dál zpracovává jednotlivé repliky; opožděný výsledek se nevykreslí nad novou scénou. |
| Jediný kratší OCR výsledek mohl nahradit kompletní text. | Podmnožina slov nebo prázdný výsledek vyžaduje dvě shodná čtení. Návrat kompletního textu čekající zkrácení zahodí. |
| Podobné doplnění se opravovalo jen krátce po zobrazení a při změně závěrečné interpunkce. | Doplnění předchozího obsahu se může přeložit i bez tečky. Rozepsané pokračování bez závěrečné interpunkce se potvrdí druhým shodným čtením; nová odlišná replika toto čekání nemá. |
| Přepnutí/vypnutí žluté oblasti neznamenalo zrušení všech rozpracovaných callbacků. | OCR i překlad kontrolují revizi nastavení a relaci spojení. Výstup režimu Text je podmíněn jeho aktuálním zapnutím; chráněn je i sousední režim voleb. |

ID OCR bloku samo o sobě nestačí ke spojení replik. Zachovány jsou hranice jmen
mluvčích a další původní filtry. Geometrie je nyní v `VyberTitulku.kt`, bez
Android závislostí; `Ctecka` ji přímo používá. Další změny filtrů nejsou součástí
této opravy.

## Instrukce a existující nastavení

- `nast.dialogy` povoluje překlad dialogů.
- `nast.napovedy` má implementovanou oddělenou cestu v `zpracujNapovedu()` a
  vykreslení žlutého textu. `MainActivity` jej však při sestavení nastavení
  bezpodmínečně nastaví na `false`; uživatelský přepínač se tam nevytváří.
- `PREKLADAT_NAPOVEDY = false` je nepoužitá konstanta. Není skutečnou podmínkou
  ve zpracování a její samotné přepnutí nic nevyřeší.
- Pro tento krok uživatel zvolil existující žlutý rámeček, zapnutí `Volby.zapnuto`
  a režim „Text (dopis, SMS)“. Nový přepínač ani automatické hledání nepřidáváme.
- Režim Text nemá patnáctisekundový filtr dialogů. `TextVRamecku` testuje celý
  odstavec, změnu zalomení, dopisování, jednorázově chybějící řádky a zmizení.
- Vypnutí režimu brání jeho vlastním překladům a pozdnímu vykreslení. Nemůže
  obecně zakázat, aby automatická titulková cesta považovala podobný text za
  dialog; k takovému rozlišení samotný vzhled nebo délka zobrazení nestačí.

Android přijímač obsahuje vykreslení `PCV1`; samotný tablet v režimu vysílače
nenabízí tímto krokem nové okno pro tyto výsledky. Experimentální LG přijímač
z PR #1 `PCV1` ignoruje. **Zpracování odstavce na Androidu tedy neznamená, že se
jeho překlad už zobrazí na LG.** Doplnění LG `PCV1` je samostatný návazný krok.

Pravidlo `MAX_DELKA_TITULKU_MS = 15_000L` v automatické dialogové cestě zatím
neměníme: bez klasifikace nelze spolehlivě rozhodnout, zda jde o čekající dialog,
nebo statický HUD. Ručně vybraná instrukce tímto pravidlem neprochází.

## Případ Alan Wake 2 a hypotézy

Instrukce „The Shoebox can store your items. Alan can access stored items from
any Shoebox in the Dark Place.“ je regresní vstup režimu Text. Její slova se
nemají překládat jako nezávislé vizuální řádky.

Fotografie s výsledkem „Botník na temném místě.“ nedokládá vstupní snímek,
surový výsledek OCR ani odeslaný paket. Geometrický test potvrzuje existenci
chyby se stejným typem ztráty obsahu, **ne potvrzení příčiny tohoto incidentu**.
Ze statické instrukce nevyvozujeme poruchu všech víceřádkových dialogů.

K prověření na skutečné scéně zbývá kvalita Remote Play obrazu, hranice ořezu,
výstup ML Kit před filtry, klasifikace nápovědy a pořadí překladů/paketů.
Přepnutí 30 → 60 FPS není provedené ani doložené jako oprava.

## Testy a jejich hranice

Deterministické testy v `app/src/test/java/cz/titulky/tv/`:

- `VyberTitulkuTest`: jeden až tři řádky, větší řádkování, jména, dvě repliky,
  různé bloky/sloupce, vzdálený starší text a HUD vedle dialogu.
- `StabilitaTextuTest`: kratší/prázdný snímek, návrat úplné verze, dopisování,
  platnost výsledků, dvě různé repliky a počet vyvolaných změn.
- `TextVRameckuTest`: konkrétní odstavec instrukce, vypnutí režimu, jiné zalomení,
  chybějící řádek, dlouhé zobrazení, opětovné zapnutí a slovo rozdělené spojovníkem.
- `PrehraniOcrTest`: text, souřadnice, ID bloku a časové posloupnosti. Spojuje
  produkční geometrii se stabilizací a tokenem výsledku; kontroluje požadavky
  i počet a pořadí viditelných změn se simulovanými odpověďmi překladače.

Původní geometrie: 10 z 11 prvních testů prošlo, test většího řádkování selhal.
Po opravách: 41 JVM testů bez selhání. Jde o testy deterministických komponent,
nikoli celý `RemotePlayService`, detekci jazyka, všechny filtry nebo síťové služby.
Počet zobrazení v přehrávači nezahrnuje offline/online náhled ani skutečné UI.

`app/src/androidTest/java/cz/titulky/tv/OcrObrazuTest.kt` je odlišná vrstva:
vykreslí syntetický třířádkový obraz, spustí skutečný ML Kit a zaznamená bloky,
řádky a souřadnice do `ocr-synteticka-instrukce.json` v datech testované aplikace.
Syntetický výsledek vypisuje také do testovacího logu s prefixem
`PLAYCAPTION_OCR_FIXTURE=`. Druhý test pouští jednořádkový, dvouřádkový
a třířádkový dialog přes skutečnou `Ctecka`, včetně jazykového filtru.

Na Redmi Pad Pro (model 2405CRPFDG, Android 16, API 36) dne 14. 11. 2026 prošly
oba Android testy, bez selhání. Použité oddělené application ID bylo
`cz.titulky.tv.ocrtest`; původní `cz.titulky.tv` zůstalo na stejné cestě APK.
Gradle testovací instalaci po dokončení odstranil.

ML Kit v syntetické instrukci rozpoznal **tři samostatné bloky**, každý s jedním
řádkem. Souřadnice byly `(41,40)-(744,76)`, `(42,106)-(644,139)` a
`(42,170)-(505,200)`. `prectiVolby()` a režim Text přesto spojily všechny řádky
do původního odstavce. To přímo ukazuje, proč nelze ztotožnit jeden OCR blok
s jednou replikou. Testy neměří latenci Remote Play ani kvalitu překladu a
nejsou reprodukcí původního herního incidentu.

### Příkazy

Java 21, Android SDK pro `compileSdk 34`, SDK Build Tools a připojení pro stažení
Gradle/závislostí. Umístění SDK nastav přes `ANDROID_HOME` nebo lokální
`local.properties`; osobní cesty necommituj.

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
git diff --check
```

Toto sestavení a JVM testy prošly. Lint vykázal 54 varování, žádnou chybu;
varování celého původního projektu nejsou touto opravou plošně odstraňována.

Na vyhrazeném Android zařízení nebo emulátoru lze navíc spustit:

```sh
bash gradlew :app:connectedDebugAndroidTest
```

Pozor: tento příkaz instaluje testovanou aplikaci. Pro tablet s používaným
PlayCaption je vhodné oddělené application ID, aby se nepřepsala jeho instalace
nebo nenarazilo na jiný podpis. Neodinstalovávat používanou aplikaci kvůli testu.
Pro zopakování izolovaného běhu lze vedle checkoutu vytvořit Gradle init soubor:

```groovy
gradle.beforeProject { project ->
    project.afterEvaluate {
        if (project.plugins.hasPlugin('com.android.application')) {
            project.android.defaultConfig.applicationId = 'cz.titulky.tv.ocrtest'
        }
    }
}
```

Potom spusť `bash gradlew --init-script /cesta/k/isolated-test.gradle :app:connectedDebugAndroidTest`.
Před instalací lze sestavit `:app:assembleDebug :app:assembleDebugAndroidTest`
a ověřit `applicationId` v obou souborech `app/build/outputs/apk/**/output-metadata.json`.

## Zbývající rizika a další kroky

1. Po úspěšném syntetickém OCR testu na Androidu zaznamenat problémovou herní
   scénu včetně původních bloků a jejich souřadnic. Syntetické testy nejsou její náhradou.
2. Samostatně doplnit `PCV1` na LG a testy přijímače, jinak režim Text nemá na LG výstup.
3. Změnu plánovače překladů řešit odděleně: jedna fronta a opakované pokusy mohou
   zadržovat aktuální práci. Ochrana výstupu sama neodstraňuje čekání ve frontě.
4. Dva shodně neúplné snímky již zkrácení potvrdí. Bez obrazového důkazu nelze
   trvale ztracený řádek od skutečně kratší nové repliky vždy rozlišit.
5. Větší mezera v jednom chybně spojeném OCR bloku může přidat nesouvisející text;
   omezujeme ji překryvem a velikostí mezery, ale potřebujeme různé herní scény.
6. Pokud první překlad dorazí až po rozpoznání druhé repliky, první se již
   nezobrazí. Požadavek se nezahazuje z fronty, ale vykreslení staré repliky
   by bylo v rozporu s aktuálním obrazem. Případná historie/čtecí fronta je
   samostatné produktové rozhodnutí.
7. Provést ruční přejímku dialogů, voleb, oblastí a ukončení/opětovného spojení
   na PS5/tabletu/LG. Nekombinovat tuto opravu s výměnou překladače nebo FPS.
