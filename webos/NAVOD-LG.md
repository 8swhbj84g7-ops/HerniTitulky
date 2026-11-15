# PlayCaption na LG televizi – návod pro uživatele

Tato verze zobrazuje přeložené titulky přes HDMI obraz na LG webOS. Překlad obstarává
Android telefon nebo tablet. Nejde o aplikaci z LG Store; instalace používá Developer Mode.
Je to experimentální podpora, zatím ověřená na LG C5.

## Co potřebuješ

- LG TV s webOS a aplikací **Developer Mode** z obchodu LG Apps.
- **Android telefon nebo tablet**, na kterém už funguje PlayCaption a překlad hry.
  iPhone neumí spustit Android APK. Telefon/tablet nemusí mířit kamerou na televizi.
- Pro instalaci buď Android s aplikací webOS Dev Mode Manager, nebo počítač s Windows, macOS či Linuxem. Počítač při hraní není potřeba.
- Konzoli zapojenou přes HDMI do LG a zařízení ve stejné domácí síti.
  LG může být připojená kabelem a odesílající zařízení přes Wi-Fi; nepoužívej oddělenou síť pro hosty.
- Instalační soubor **cz.playcaption.overlaytest_0.1.0_all.ipk** poskytnutý projektem
  u změny nebo vydání na GitHubu. Pokud je zabalený v ZIPu, nejprve ho rozbal.

APK patří do Android zařízení, IPK patří do LG. Na LG se nepřihlašuješ k PSN.
Hra běží přes HDMI; Remote Play dodává kopii obrazu Android zařízení pro čtení titulků.

## 1. Připrav televizi

1. Založ si účet na [LG Developer](https://webostv.developer.lge.com/).
2. Na LG otevři obchod **LG Apps**, najdi a nainstaluj **Developer Mode** od LG.
3. Otevři ji a přihlas se účtem LG Developer.
4. Zapni **Dev Mode Status** a potvrď restart TV.
5. Po restartu znovu otevři Developer Mode a zapni **Key Server**.
6. Poznamenej si IP adresu TV z nastavení sítě a **Passphrase** z Developer Mode.
   Passphrase slouží ke spárování instalačního zařízení s TV; neposílej ji do veřejných komentářů.

Podrobný postup LG: [Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app).

## 2. Nainstaluj přijímač

Vyber jednu cestu. LG příprava v kroku 1 je nutná pro obě. Obyčejné odeslání souboru přes Bluetooth ani otevření IPK v prohlížeči TV aplikaci nenainstaluje.

### A. Z Android telefonu nebo tabletu, bez počítače

Komunitní [webOS Dev Mode Manager](https://symonxdd.github.io/aphanes/) podporuje
instalaci místního IPK přímo z Androidu. Jde o samostatnou aplikaci třetí strany,
nikoliv o funkci, která už je zabudovaná v PlayCaption. Tento postup je podložený
[dokumentací správce](https://symonxdd.github.io/aphanes/concepts/); na naší sestavě
zatím není ručně odzkoušený.

1. Stáhni správce z jeho [oficiálních vydání](https://github.com/symonxdd/aphanes/releases).
2. Stáhni IPK PlayCaption do stejného Android zařízení a případný ZIP rozbal.
3. Ve správci přidej vlastní LG podle IP adresy z Developer Mode a její Passphrase.
   Na LG musí být zapnutý Key Server. Použij Developer Mode, ne root připojení.
4. V části aplikací zvol instalaci z místního souboru a vyber IPK PlayCaption.
5. Po úspěšné instalaci spusť **PlayCaption LG** ovladačem z domovské nabídky TV.

Názvy tlačítek se mohou lišit podle verze správce. Pokud postup selže, použij
počítačovou cestu níže. Přímá instalace tlačítkem z PlayCaption je samostatný
[navržený doplněk](INSTALACE-Z-ANDROIDU.md), zatím není implementovaná.

### B. Z počítače, graficky bez příkazové řádky

1. Stáhni [webOS Dev Manager pro svůj počítač](https://github.com/webosbrew/dev-manager-desktop/releases/latest).
   Je to komunitní nástroj; pro tento postup nepotřebuješ příkazovou řádku ani root TV.
2. V Dev Manageru přidej televizi přes **Add Device**. Zvol připojení přes Developer Mode,
   zadej IP televize a Passphrase. Pokud nástroj vyžádá port a uživatele, použij port
   **9922** a uživatele **prisoner**. Nevol připojení jako root.
3. Po připojení v části s aplikacemi použij **Install** a vyber rozbalený soubor
   **cz.playcaption.overlaytest_0.1.0_all.ipk**.
4. Počkej na dokončení. V seznamu aplikací má přibýt **PlayCaption LG**.
   Pokud už máš náš původní HDMI test, stejný identifikátor zajistí jeho aktualizaci.

Pokud klíč nejde získat, ověř zapnutý **Key Server**, aktuální Passphrase a společnou síť.
Názvy ovládacích prvků Dev Manageru se mohou mezi verzemi mírně lišit.

## 3. Spusť hru a titulky

1. Na odesílajícím zařízení spusť Android PlayCaption, připoj konzoli a ověř,
   že překlad funguje přímo na tomto zařízení.
2. Na LG otevři domeček a spusť **PlayCaption LG**.
3. V aplikaci vyber HDMI s konzolí a potvrď OK. Seznam se načítá z TV včetně názvů
   zařízení; žádné číslo HDMI není nastavené natvrdo. Aplikace přepne vstup a obnoví
   překryv titulků nad hrou. Poslední volba je příště předvybraná.
4. Na LG se objeví „Čekám na zařízení“. Pokud se výběr HDMI nepodaří,
   aplikace zobrazí chybu. Tlačítko **Pokračovat nad aktuálním obrazem** použij pouze
   tehdy, když už je pod aplikací vidět správný obraz hry.
5. Na Android zařízení ukonči úvodní nastavení tlačítkem **Hotovo – hrát**.
6. Na obrazovce s překladem najdi **vpravo nahoře přepínač 📺 Televize / 📱 Telefon**.
   Klepni na **📺 Televize**. Pro seznam dostupných televizí toto tlačítko podrž
   a vyber **LG PlayCaption · identifikátor** shodný s kódem na obrazovce LG.
   Každá TV má vlastní kód, takže rozlišíš i více LG ve stejné síti.
   Pokud je v síti jediná televize, může se připojit sama.
7. Při dalším textu ve hře se mají české titulky zobrazit i přes HDMI obraz na LG.
   Poloha, šířka a velikost odpovídají nastavení zeleného boxu na Android zařízení.

**Tlačítko Vyhledat televizi v úvodním nastavení Android aplikace je jiná funkce:**
přebírá přihlášení z Android TV. Pro LG nepoužívej tento postup ani volbu
**+ Přidat Android zařízení**. Přihlášení ke konzoli už máš v tabletu/telefonu.

Android zařízení nech odemčené s otevřeným PlayCaption, ideálně na nabíječce.
Počítač při hraní není potřeba. Pokud chceš změnit vstup, otevři znovu PlayCaption LG
a vyber ho uvnitř aplikace. Přepnutí přes systémové menu LG může překryv zavřít;
přijímač nesleduje všechny změny vstupů na pozadí.
Přijímač neposílá ovládací vstupy do konzole; hraješ dál svým ovladačem.

Ve Standardním režimu mají dialogy nejvýše dva řádky. Dlouhá replika se střídá po krátkých částech,
přednostně na konci věty. Nová replika nebo konec její platnosti ruší zbývající
stránky; historie se nehromadí. Velké písmo se omezí tak, aby box zabral nejvýše
18 % výšky obrazu. Dopisy v režimu Text a volby odpovědí mají vlastní zobrazení.

## Každé další hraní

1. Zapni konzoli a LG.
2. Spusť překlad na Android zařízení.
3. Na LG spusť **PlayCaption LG**, vyber HDMI konzole a na Android zařízení případně znovu vyber TV.
4. Po hraní stiskni **OK nebo Zpět → Ukončit PlayCaption**. Samotné Zpět během hry nyní otevře nabídku; Zpět v nabídce ji zavře a vrátí překryv.

Přijímač nemá automatické spuštění po zapnutí TV. Po instalaci se spouští ovladačem;
počítač při dalším hraní není potřeba.
Pokud na LG otevíráš Home nebo měníš aplikaci, může se překryv zavřít;
pak znovu otevři PlayCaption LG a vyber HDMI. Android přepínač neotevře vypnutý LG přijímač.

## Nabídka během hraní

Stiskni **OK nebo Zpět**. Šipkami vyber akci a potvrď OK:

- **Změnit HDMI** otevře původní výběr vstupu.
- **Zkušební titulek** na 5 s zobrazí lokální český vzorek. Ověřuje pouze zobrazení,
  nikoliv spojení, OCR nebo překlad. Příjem skutečných dat přitom pokračuje.
- **Dočasně skrýt titulky** skryje přijímané texty a rámečky. Čas Standardních
  titulků běží dál; po obnovení se nevrací dávno prošlý text.
- **Zpět ke hře** (nebo Zpět na ovladači) zavře nabídku.
- **Ukončit PlayCaption** zavře přijímač a zastaví jeho příjem.

Nahoře je stav, verze, stáří posledních dat/titulku a naposledy zvolený HDMI.
Příjem dat nepotvrzuje funkčnost OCR či konzole. Neznámá verze není nahrazena
odhadem. Výstraha o udržování překryvu zůstává viditelná.

Přijímač drží **jedno aktivní odesílající zařízení**. Další zdroj živé spojení
nepřebírá. Pro změnu zastav překlad na původním zařízení, počkej nejméně 5 s a
spusť nový zdroj. Tato ochrana pracuje s adresou v domácí síti; není to přihlášení
ani rozlišení dvou aplikací na stejném telefonu. Používej důvěryhodnou LAN.

## Developer Mode je potřeba prodlužovat

V aplikaci Developer Mode sleduj **Remain Session** a včas použij **EXTEND** při
připojení TV k internetu. Prodloužení zobrazení titulků není prodloužením Developer Mode.
Pokud režim vyprší a deaktivuje se, LG vývojově nainstalované aplikace odstraní.
Pak znovu zapni Developer Mode a zopakuj instalaci IPK. Běžné aktualizace PlayCaption
instaluj přes Install novějšího IPK a přijímač znovu spusť.

## Když něco nefunguje

| Problém | Co zkontrolovat |
|---|---|
| LG upozorní, že nepodporuje Story | Na Androidu zvol Standardní režim. Samostatný LG přijímač 0.1.0 Story frontu nepodporuje; samotné vyšší číslo verze nestačí. |
| V seznamu je jen „Jen telefon“ | Na LG otevři PlayCaption LG a vyber HDMI. Pak na Android zařízení znovu podrž 📺 Televize. Ověř společnou síť bez izolace klientů. |
| Android chce přebírat přihlášení z TV | Jsi v jiném průvodci. Dej Zrušit → Hotovo – hrát → přepínač 📺 Televize vpravo nahoře. |
| Překlad chybí na zařízení i LG | Nejprve řeš čtení/překlad na Android zařízení; LG dostává až jeho hotový výsledek. |
| Na zařízení překlad je, na LG není | Ověř vybranou LG a spuštěný přijímač. Po výpadku delším než 5 s se na LG schová starý text. |
| LG hlásí odmítnutí prodloužení překryvu | Firmware nepovolil potřebné volání. Zapiš model a verzi webOS/firmwaru; tento model zatím nelze označit za kompatibilní. |
| Překryv zmizí přibližně po 2 minutách | Ověř přijímač 0.0.3 nebo novější, znovu ho spusť. Opakování nahlas s modelem/firmwarem; může jít o nekompatibilní systémové volání. |
| Víceřádkový text skončí pouze posledním řádkem | Známý problém čekající na samostatné řešení. Porovnej text na Android zařízení a LG; neřeš ho opakovanou instalací přijímače. |

Pro nahlášení problému stačí model LG, verze firmwaru/webOS, verze obou aplikací,
model Android zařízení a rozdíl mezi tím, co zobrazí odesílající zařízení a LG. Neposílej PSN tokeny,
Passphrase ani soukromé klíče. Pokud posíláš fotku, stačí samotný text na obrazovce.

## Oblasti čtení a překlad odpovědí

- Zelená oblast nastavuje umístění českých dialogových titulků.
- Modrá oblast vybírá místo čtení původních titulků. Zapni ruční čtení;
  při přesouvání se na LG ukáže modrý rámeček.
- Žlutá oblast vybírá místo čtení odpovědí nebo dokumentu. Zapni ji a zvol
  **Volby odpovědí**, nebo **Text (dopis, SMS)**. Český text se zobrazí vedle
  zvolené oblasti; pokud vedle není místo, zůstane uvnitř ní.
- **Rámeček na TV: Ukazovat pořád** ponechá žlutý rámeček viditelný i bez
  přečtených odpovědí. Jinak svítí jen při nastavování.

## Rozsah LG přijímače

Umí titulky, nápovědu, polohu/velikost, dialogové volby, delší texty, náhledy
oblastí a barvy jmen. Podrobnosti a testy uvádí [matice funkcí](SHODA-ANDROID.md).
Ověřená kombinace je LG C5 + Redmi Pad Pro + PS5. Stejná verze webOS nezaručuje
kompatibilitu všech modelů; překryv i jeho udržování používají neoficiální rozhraní LG.
Xbox ani jiné LG modely zatím v této sestavě nebyly otestované.

Přijímač sám nečte HDMI a nepřekládá. UDP přenos původní aplikace nemá přihlašování,
proto používej důvěryhodnou domácí síť a nepřesměrovávej jeho porty na internet.

## Novější Android aplikace s profily televizí

Pokud Android nabízí **Nastavení televize**, zvol zvlášť **Televizní platforma →
LG webOS** a potom **Vybrat / změnit televizi**. U staršího přijímače potvrď,
že vybraná TV patří k LG; její název ani vyšší číslo verze platformu nedokazují.
Dvě LG mohou mít vlastní nastavení polohy a oblastí. Vypnutá televize v seznamu
uložených profilů neslouží k připojení přes starou IP; spusť ji a hledej znovu.
Verze přijímače může být zatím neověřená. Tato nabídka sama IPK neinstaluje.

V Android aplikaci s průvodcem otevři **Výstup titulků → Změnit → Jak připojit
televizi** při zvolené platformě LG webOS. Příprava sítě, účet LG, Developer Mode,
ruční instalace i první spuštění jsou rozdělené do pěti kroků. **PlayCaption LG
už mám** přeskočí přípravu k vyhledání přijímače. Návod si pamatuje rozpracovaný
krok; jeho dokončení samo neprokazuje instalaci ani příjem titulků.
