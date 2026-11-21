# Instalace Herních titulků 1.0 na Xiaomi TV Stick 4K

Podepsaná aplikace se v nabídce jmenuje **Herní titulky · TV Stick**. Instaluje se vedle původní vývojové aplikace **Herní titulky**. Má vlastní úložiště. V novém balíčku tedy bude potřeba znovu provést párování a upravit nastavení titulků.

## Instalace

1. Připrav soubor `HerniTitulky-TVStick-release.apk`. Použij APK předané s touto verzí a ověř její SHA-256 podle souboru `SHA256SUMS.txt`, pokud si soubor přenášíš mezi zařízeními.
2. Otevři APK na Sticku ve správci souborů. Pokud se Android zeptá, povol instalaci z tohoto správce. Případně z Macu s připraveným ADB použij níže uvedený příkaz. Použij skutečnou IP a port svého Sticku.
3. Otevři **Herní titulky · TV Stick**. Při prvním hraní bude aplikace v případě potřeby stahovat jazykový model. Další překlady probíhají na Sticku. Stav stahování uvidíš v herní diagnostice.
4. Zapni ve hře anglické titulky. Pokud zkoušíš hraní poprvé, začni na výchozím **720p · 30 FPS**. Pro místní párování použij domácí síť, které důvěřuješ.

```sh
adb connect IP_STICKU:PORT
adb -s IP_STICKU:PORT install -r HerniTitulky-TVStick-release.apk
```

**Odinstalace původní aplikace není k nové instalaci potřeba.** Pokud si ji později sám odinstaluješ, Android odstraní její nastavení, případné původní párovací klíče a data aplikace. Stejně tak odinstalace produkční aplikace smaže její klíče a nastavení. Budoucí aktualizace instaluj přes stávající produkční aplikaci. Musí mít stejný podpis.

## Ruční párování

1. Připrav si **PSN Account ID v Base64**. Pokud ho potřebuješ dohledat, chiaki-ng popisuje možnost **Public Lookup** podle veřejného PSN jména. [Návod chiaki-ng](https://chiaki-ng.github.io/chiaki-ng/setup/configuration/#registering-your-playstation)
2. Na PS5 přepni na profil, kterému Account ID patří. V **Nastavení → Systém → Remote Play** zapni vzdálené hraní a zvol **Propojit zařízení**. [Návod Sony](https://www.playstation.com/en-gb/support/games/remote-play-settings/)
3. V aplikaci vyber **Připojit PlayStation 5**. Zadej Account ID, privátní IPv4 adresu PS5 a osmimístný kód z obrazovky konzole. IP zjistíš na PS5 v nastavení sítě ve stavu připojení. **Spárovat** použije místní spojení s konzolí.
4. V nastavení Sticku spáruj DualSense jako příslušenství přes Bluetooth a ověř tlačítka a páčky v **Nastavení → Vyzkoušet tlačítka a páčky**. Podporu DualSense na konkrétním sestavení Androidu TV 11 je potřeba ověřit na zařízení. [Návod Sony](https://www.playstation.com/en-us/support/hardware/pair-dualsense-controller-bluetooth/)

V nabídce **Nastavení → Zrušit párování** lze odstranit klíče z tohoto Sticku. Před dalším hraním bude potřeba nové párování. Po dokončení instalace můžeš v nastavení Sticku vypnout ADB ladění, pokud ho dále nepotřebuješ.
