# Herní titulky na Xiaomi TV Stick 4K

## Instalace

1. Zapoj Stick do HDMI vstupu monitoru nebo televize a připoj napájení. Na obrazovce vyber vstup Sticku. Připoj Stick k domácí Wi-Fi 5 GHz. PS5 musí být dosažitelná ve stejné síti.
2. Ze svého počítače zpřístupni soubor `HerniTitulky-debug.apk` například ve sdílené složce. Na Sticku jej otevři ve správci souborů s podporou sítě. Pokud se Android zeptá, povol tomuto správci instalaci aplikací z tohoto zdroje.
3. Po instalaci otevři **Herní titulky** v nabídce aplikací. Na hlavní obrazovce vyber **Připojit PlayStation 5**.

Místo správce souborů můžeš použít počítač s Android Platform Tools. Ve vývojářských možnostech Sticku zapni dostupnou variantu síťového ladění. Zadej skutečnou adresu a port z jeho obrazovky. Pokud přístroj nabídne párovací kód pro ADB, nejprve použij `adb pair ADRESA:PÁROVACÍ_PORT`. Pro připojení použij port pro ladění. Potvrď žádost na televizi. [Návod Androidu k ADB](https://developer.android.com/tools/adb)

```sh
adb connect ADRESA:PORT
adb -s ADRESA:PORT install -r HerniTitulky-debug.apk
```

## Připojení PS5

1. Na zapnuté PS5 vyber účet, na kterém chceš hrát. V **Nastavení → Systém → Remote Play** zapni vzdálené hraní. [Podpora PlayStation](https://www.playstation.com/cs-cz/support/games/playstation-remote-play-on-mobile-devices/)
2. Na Sticku otevři **Připojit PlayStation 5 → Přihlásit se u Sony na Sticku** a dokonči přihlášení u Sony. Po návratu se vyplní identifikátor účtu. Pokud Sony přihlášení ve vestavěném prohlížeči odmítne, lze ručně zadat známé osmibajtové **PSN Account ID v Base64**. Přezdívka ani e-mail do tohoto pole nepatří.
3. Zvol **Najít PS5 v domácí síti**, případně zadej IP adresu konzole ručně. Na PS5 vyber **Propojit zařízení**. Opiš osmimístný kód do Sticku a zvol **Spárovat**. Nové párování přepíše předchozí konzoli až po úspěchu.
4. Ve hře zapni **anglické titulky**. Aplikace čte spodní třetinu obrazu. Dialog dostupný pouze jako zvuk nebo text umístěný jinde tímto způsobem nepřečte.

## DualSense a hraní

1. Na Sticku otevři **Nastavení → Připojit ovladač Bluetooth** nebo systémovou nabídku **Dálkové ovládání a příslušenství → Přidat příslušenství**.
2. Na vypnutém a od USB odpojeném DualSense podrž **Create + PS**, dokud světelná lišta nezačne blikat. Na Sticku vyber **Wireless Controller**. [Návod Sony](https://www.playstation.com/en-us/support/hardware/pair-dualsense-controller-bluetooth/)
3. V aplikaci otevři **Nastavení → Vyzkoušet tlačítka a páčky**. Zkontroluj obě páčky, směrový kříž, tlačítka a spouště L2/R2. Pokud se pravá páčka nehýbe správně, přepni **Pravá páčka** mezi dvěma nabízenými možnostmi. Sony uvádí oficiální podporu od Androidu 12. Zkouška na Sticku s Androidem 11 je proto důležitá.
4. Dálkovým ovladačem Xiaomi vyber **Spustit hraní s českými titulky**. Začni s **720p · 30 FPS**. Pokud má profil konzole vlastní přístupový kód, aplikace tě během připojení vyzve k jeho zadání. Tlačítko **Zpět** na dálkovém ovladači opustí hru a uvolní relaci.

Zvuk hry přehrává Android na aktivním výstupu. Pro poslech přes HDMI vyber na zobrazovači reproduktory nebo výstup pro sluchátka a zkontroluj hlasitost. Monitor potřebuje vlastní reproduktory nebo připojený zvukový výstup. Hra i titulky přicházejí z jednoho HDMI vstupu Sticku.

## Nastavení a výpadky

- Podle potřeby nastav jazyk, velikost, polohu a krytí pozadí. Příběhový režim ponechává repliky déle podle délky a používá omezenou frontu. Při rychlé sérii dialogů může některé přeskočit.
- **Diagnostika ve hře** ukáže vykreslené snímky za sekundu, dobu od zachycení výřezu po překlad, paměť a rozpoznaný ovladač. Na hlavní obrazovce najdeš poslední měření po odchodu ze hry.
- Po výpadku hry nebo sítě může aplikace třikrát opakovat připojení. Potom zkontroluj zapnutou PS5 a Wi-Fi a stiskni **OK**. U ručního režimu lze stisknout **OK** hned po chybě. Před přechodem z původního režimu ukonči jeho aktivní překlad, aby konzole uvolnila staré spojení.

## Soukromí

Obraz hry, zvuk, vstupy ovladače a párování proudí v domácí síti mezi Stickem a konzolí. Rozpoznání textu probíhá na Sticku. Standardní překlad posílá rozpoznaný anglický text Googlu, při výpadku může použít Microsoft a MyMemory. Příběhový překlad zkouší Google a místní jazykový model. Pro místní záložní překlad musí být dokončeno stažení modelu. Internetové služby přijímají i běžné údaje spojení, například IP adresu. ML Kit může kontaktovat služby Google pro stažení modelů, konfiguraci a vlastní diagnostiku SDK.

Přihlašovací stránka a výměna kódu za identifikátor účtu používají služby Sony. Párovací klíče herního režimu jsou šifrované klíčem Android Keystore. Výpis nativního síťového protokolu je v herní knihovně vypnutý. Diagnostika ukládá číselné měření, stav a název ovladače.

Herní režim má vlastní údaje pro párování. Položka **Původní režimy aplikace** otevře samostatné nastavení pro původní způsoby použití. Podrobnosti o skutečně ověřených zařízeních a omezeních jsou ve [zprávě o testech](TV-STICK-TESTY.md).
