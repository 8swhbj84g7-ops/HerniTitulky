# Instalace LG přijímače z Androidu

V Android průvodci pro LG webOS je krok **Nainstalovat / aktualizovat**.
Uživatel zadá místní IPv4 adresu a šestiznakovou Passphrase z Developer Mode.
Na TV musí běžet Developer Mode a Key Server. Účet a heslo LG zadává pouze na TV.
Tlačítko **Nedaří se? Ruční instalace z počítače** zachovává záložní postup.
[Uživatelský návod](NAVOD-LG.md) popisuje přípravu a první spuštění.

## Balíček a sestavení

Android Gradle `preBuild` zavolá `python3 webos/build.py`: testy přijímače,
sestavení připnutým CLI a kontrola IPK proti zdrojům. Ověří manifest, ID, verzi,
velikost a SHA256; do assets přidá `lg/receiver.ipk` a `lg/receiver-manifest.json`.
IPK 0.1.0 má 31 060 bajtů. Generované soubory jsou pouze v build adresářích.
Build potřebuje Python 3.9+, Node.js 20+ a dostupné CLI přes npx.
Při instalaci z hotové APK se nic nestahuje z internetu a není potřeba počítač.
Založení účtu LG a získání Developer Mode jsou samostatné přípravné kroky.

`LgZdrojBalicku` odděluje instalaci od zdroje; nyní existuje pouze přibalený asset.
Vzdálené aktualizace, vlastní backend ani výběr cizího IPK nejsou implementované.
Manifest uvádí také protokoly a schopnosti; nevydáváme jej za zjištěnou verzi TV.

## Instalační tok

- `LgInstalator` mimo hlavní vlákno získá zašifrovaný RSA klíč z místního
  Key Serveru (`9991/webos_rsa`) a odemkne jej Passphrase.
- Udržovaná knihovna [JSch](https://github.com/mwiede/jsch) připojí `prisoner:9922`.
  SSH veřejný otisk se při prvním úspěšném připojení zapamatuje. Změna se odmítne;
  nové spárování vyžaduje výslovné potvrzení uživatelem. První spojení důvěřuje
  domácí síti; nejde o nezávislé ověření výrobce TV. C5 Developer Mode nabízí
  pouze starší podpis `ssh-rsa`; kompatibilita je povolená jen pro tuto SSH relaci,
  bez vypnutí kontroly otisku a bez změny globální konfigurace JSch.
- SFTP nahraje vlastní náhodně pojmenovaný IPK do `/media/developer/temp`, přečte
  jej zpět a porovná bajty. Předchozí přijímač se neodinstalovává.
- `com.webos.appInstallService/dev/install` musí potvrdit konečný úspěch.
  Samotné přijetí požadavku nestačí. Následuje kontrola skutečného `appinfo.json`
  proti očekávanému ID/verzi a pokus o spuštění přes Application Manager.
- Pokud spuštění selže, potvrzená instalace zůstává úspěšná a průvodce požádá
  o ruční spuštění. Výběr HDMI a discovery jsou další kroky.

Operace má limit 120 s a možnost zrušit čekání. Již zahájená instalace LG může
pokračovat i po zavření Android dialogu. Při přerušení takového požadavku IPK
ponecháváme, aby jej LG mohla dočíst; po potvrzeném konci odstraníme pouze vlastní
přenesený soubor. Neprovádíme hromadný úklid TV.

Passphrase ani soukromý klíč se neukládají do souborů, preferencí či logů.
Vstupní pole se nezálohují; veřejný SSH otisk není přihlašovací tajemství.
Veřejné/hostitelské adresy, přesměrování HTTP a příliš velké odpovědi se odmítají.
MVP podporuje místní IPv4; IPv6 a DNS jména nejsou implementované.

## Ověření a omezení

JVM testy pokrývají adresy, manifest a změněný obsah, HTTP odpovědi,
koncové stavy instalace, ID/verzi, odemknutí syntetického klíče, zrušení
před připojením a změnu SSH identity. Skutečná přejímka musí oddělit aktualizaci
na C5 od první instalace na čisté TV a od výpadků v jednotlivých fázích.
Na C5 uživatel potvrdil aktualizaci, správné potvrzení výsledku i spuštění
přijímače z test06; podrobnosti jsou v [TESTOVANI.md](TESTOVANI.md). První
instalace na čisté TV a jiné modely tím nejsou ověřené.

## Zdroje

- [LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Oficiální webOS CLI](https://github.com/webos-tools/cli): `lib/base/novacom.js`,
  `lib/install.js`, `files/conf/command-service.json`.
