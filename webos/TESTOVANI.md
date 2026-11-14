# Kontrola LG přijímače před předáním

## Automatické kontroly

```sh
node webos/test.cjs
node webos/test-udp.cjs
python3 webos/build.py
git diff --check
```

`test.cjs` testuje parser, Unicode a escapování řádků, neplatné/velké pakety,
číselné limity, dobu zobrazení, heartbeat, výpadek a obnovení přenosu, restart UI,
prodlužování překryvu, opožděné odpovědi a trvalé chybové hlášení. Síť a systémový
most LG zde nahrazují testovací objekty; není to důkaz kompatibility konkrétní TV.

`test-udp.cjs` spouští produkční službu se skutečnými UDP sockety. Pouze porty/cíle
přesměruje na loopback a náhrada Luna busu zachytí odpověď odběrateli. Ověřuje
discovery, přenos víceřádkového českého paketu a ukončení socketu/časovače po odhlášení.
Nezávisí na televizi, připojeném tabletu ani přístupu k lokální síti.

`build.py` znovu spustí oba testy a kontrolu syntaxe, sestaví IPK oficiálním CLI
3.2.6 a porovná veškerý obsah IPK se zdroji. Odmítne soubory navíc či jinou verzi.
Vytvoří ZIP s návodem, licencí, odpovídajícími zdroji a SHA256SUMS; ověří CRC ZIPu.
Vyžaduje Python 3.9+, Node.js 20+ a internet pro stažení CLI přes npx.

## Dosavadní důkazy ze skutečné TV – 14. 11. 2026

| Oblast | Důkaz / omezení |
|---|---|
| Sestava | LG OLED65C5ELB, SDK 11.2.0, firmware 43.21.96; Redmi Pad Pro, PS5. |
| Překrytí HDMI | Uživatel potvrdil testovací text přes HDMI a následně skutečné titulky z tabletu. |
| Discovery a UDP | Na počítači přijato ohlášení LG; testovací paket prošel přes TV službu až do viditelného titulku v DOM aplikace. |
| Systémový limit | TV log zaznamenal skrytí overlay přesně 120 s po spuštění verze 0.0.2. |
| Oprava ve verzi 0.0.3 | Volání restartUserActivityTimer vrátilo returnValue=true. Web Inspector kontroloval běh v 0/30/60/90/125/150 s; ve 150 s byl viditelný přijatý titulek. |
| Kontrola verze 0.0.4 | Stejný IPK jako v distribučním ZIPu nainstalovaný na C5. Kontrola přes Web Inspector v 0/30/60/90/125/150 s: aplikace zůstala viditelná, bez hlášení o odmítnutém časovači. |
| Tři řádky ve verzi 0.0.4 | Syntetický český UDP paket se třemi řádky prošel přes službu do UI. DOM text přesně odpovídal paketu; výška boxu pokryla tři řádky a box zůstal v ploše obrazovky. Nejde o test OCR ani překladu ze hry. |
| Zavření a opětovné spuštění 0.0.4 | Po zavření aplikace přes CLI přestalo přicházet UDP discovery; po dalším spuštění opět přicházelo z C5. |
| Úplnost překladu | Uživatel dodal chybu víceřádkového textu; zatím nebyla lokalizovaná ani opravovaná, viz PC-001 v BACKLOG.md. |

Tato tabulka nedokazuje několikahodinový běh, všechny HDR/VRR režimy, jiné modely,
Xbox nebo distribuci přes LG Store. Výsledky starší verze se automaticky nepřenášejí
na novou verzi; u vydávaného IPK znovu zaznamenej provedené kroky níže.

## Ruční přejímka aktuálního IPK na TV

- [ ] Zapsat verzi LG přijímače, verzi Android APK, model/firmware LG a model Android zařízení.
- [ ] Nainstalovat IPK ze sestaveného ZIPu a spustit ho z počítače nad aktivním HDMI.
- [ ] Najít LG přes přepínač 📺 Televize na Android obrazovce s překladem.
- [ ] Porovnat stejný český text na tabletu a LG; vyzkoušet české znaky i více řádků v jednom paketu.
- [ ] Změnit polohu, šířku a velikost zeleného boxu; pozor na překrytí nápovědy.
- [ ] Nechat běžet alespoň 3 minuty bez LG ovladače a potvrdit, že přijímač stále běží.
- [ ] Přerušit odesílání z tabletu: starý text zmizí do přibližně 5 s. Obnovit přenos.
- [ ] Zpět na LG ovladači zavře aplikaci; po dalším spuštění funguje discovery i příjem.
- [ ] Ověřit obraz, zvuk a běžně používaný HDR/VRR režim; zaznamenat, který byl zkoušen.
- [ ] Před širším vydáním otestovat delší hraní a alespoň další kombinaci model/firmware.

Bez skutečné TV lze potvrdit balení a logiku, nikoli chování nedokumentovaného LG overlay.
