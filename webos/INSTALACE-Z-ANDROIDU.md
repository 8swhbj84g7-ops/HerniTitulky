# Instalace LG přijímače z Androidu

## Co lze použít už teď

Instalace bez počítače je technicky možná, bez rootu TV. Samostatný komunitní
[webOS Dev Mode Manager](https://symonxdd.github.io/aphanes/) umí místní IPK:
spárování přes Developer Mode, přenos SFTP a instalaci přes službu LG.
[Postup pro uživatele](NAVOD-LG.md) odděluje tuto cestu od ověřené instalace z PC.
Mobilní správce jsme na své TV ještě ručně neotestovali; není součástí PlayCaption.

## Vlastní tlačítko v PlayCaption — návrh, zatím neimplementováno

Nejmenší průvodce: **Nainstalovat / aktualizovat PlayCaption LG** → IP TV →
Passphrase z Developer Mode → instalace s průběhem → ověření nainstalované verze.
Prvotní vyhledání nelze opřít o PlayCaption discovery: dosud nenainstalovaný
přijímač nic nevysílá. První verze může adresu vyžádat ručně. Nevolit automaticky
první nalezenou TV, pokud jich má uživatel více.

Technické kroky, potvrzené zdroji a stávajícím oficiálním CLI:

1. Uživatel na LG zapne Developer Mode a Key Server. Účet LG zadává na TV.
2. Z uživatelem zadané lokální IP získat klíč z Key Serveru na portu 9991;
   odemknout jej Passphrase. SSH spojení běží jako `prisoner` na portu 9922.
3. Použít udržovanou SSH/SFTP knihovnu; PlayCaption ji nyní nemá. Běžná Android
   zásuvka ani UDP příjemce neumí SSH nahradit. Síťová práce patří mimo hlavní vlákno.
4. Nahrát pouze připravený IPK stejného ID přes SFTP, ověřit shodu přenesených bajtů
   a zavolat instalační službu Developer Mode. Průběh zpracovat až do konečného
   úspěchu/selhání; samotné přijetí požadavku není dokončená instalace.
5. Ověřit ID a verzi instalované aplikace. Původní přijímač předem neodinstalovávat.
   Nabídnout spuštění, HDMI vybere uživatel v LG aplikaci.

Pro první verzi je nejjednodušší vložit konkrétní ověřený IPK do Android assets.
Odpadne závislost instalace na stahovacím serveru; Android build ale musí zaručit
shodu IPK a jeho verze. Vzdálené aktualizace lze doplnit později.

Klíč a Passphrase nepatří do Git repozitáře, logů ani běžné zálohy Android dat.
Minimální instalátor je může držet jen po dobu instalace; uložené párování by
potřebovalo šifrované uložení a kontrolu SSH identity TV. Na změněný host key se
nesmí automaticky připojit bez nového potvrzeného párování.

## Co musí projít před zpřístupněním tlačítka

- První instalace i aktualizace při aktivním Developer Mode na skutečné TV.
- Chybná IP/Passphrase, vypnutý Key Server, nedostupná TV, výpadek během přenosu,
  odmítnutí instalace a nové spuštění po chybě bez falešného hlášení úspěchu.
- Více TV, změna IP, zavření Android obrazovky a ochrana citlivých údajů.

## Zdroje

- [LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Oficiální webOS CLI](https://github.com/webos-tools/cli): `lib/base/novacom.js`
  (Key Server) a `lib/install.js` (přenos, instalace a konečný stav).
- [Mobilní správce: princip instalace](https://symonxdd.github.io/aphanes/concepts/)
- [Mobilní správce: párování](https://symonxdd.github.io/aphanes/pairing/)

Ověření zdrojů: 15. 11. 2026. Tento dokument nepředstavuje hotový Android instalátor.
