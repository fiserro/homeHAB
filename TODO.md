## ESP32 Panel
- [ ] Ověřit že HTTP fetch funguje (zkontrolovat logy)
- [ ] Odstranit debug logging po ověření funkčnosti

## Generovani mqtt.things a mqtt.items obsahuje hardcoded url string a device id
- [ ] Brat v potaz .env soubor
- [ ] Zvazit moznost .gitignore a generovat v build fazi
- [ ] zvazit moznost template


## Presun kalibrace na stranu openhabu
### Uloha python scriptu
- prijmout PWM value pro GPIO pres MQTT a nastavit tuto hodnotu na vystupni pin
- komunikacni protokol MQTT: 
  - /homehab/hrv/pwm/gpio18 # number hodnota 0-100
  - /homehab/hrv/pwm/gpio19 # number hodnota 0-100
### Uloha openhabu
- HrvCalculator vypocita vystupni hodnoty pro GPIO18 a GPIO19 na zaklade vsech vstupu
- zohlednuje source GPIO18 a GPIO19
- zohlednuje kalibracni tabulku

2. **pwm-settings.html**: Test slider posila hodnoty do `hrvOutputTest` itemu.
   Kdyz je source nastaven na "TEST", Python bridge pouzije linearni kalibraci.

**Instrukce pro kalibraci:**
1. V pwm-settings.html nastavit GPIO source na "Test"
2. Pridat radek s PWM % (napr. 50%)
3. Kliknout na zelene ▶ tlacitko - nastavi PWM a automaticky prepne na TEST mode
4. Zmerit skutecne napeti na vystupu multimetrem
5. Zadat namerou hodnotu do "Voltage" pole
6. Opakovat pro vice hodnot PWM (0%, 25%, 50%, 75%, 100%)
7. Ulozit kalibraci a prepnout source zpet na pozadovany rezim

### TODO List
- [x] Zjistit na jakem miste se nastavuje linearni kalibracni tabulka pro test output
- [x] HrvModule: hrvOutput* uz nemaji svuj output mqtt channel
- [x] HrvModule: hrvOutputGpio18, hrvOutputGpio19 - maji output channel viz zmeny komunikacniho protokolu
- [x] HrvModule: prejmenovat gpio18Source -> sourceGpio18, gpio19Source -> sourceGpio19
- [x] HrvModule: smazat dualMotorMode - neni potreba
- [x] HrvCalculator: po vypocteni power, intake a exhaust, vypocist i hodnoty pro gpio18 a gpio19
- [x] HrvCalculator: pro namapovani mezi output value a gpio se pouzije source [power, intake, exhaust, test, off]
- [x] HrvCalculator: pro finalni korekci se pouzije kalibracni tabulka, kdyz je gpio 18 nebo 19 nastaven na test, nepouziva se kalibracni tabulka
- [x] pwm-settings.html - aktualizovat po zmenach v HrvModule
- [x] python script - vsechno smazat a nechat jen primitivni funkcionalitu - prijmuti mqtt zpravy nastavit pwm vystupni hodnotu
- [x] aktualizovat dokumentaci MQTT
- [x] pridat dokumentaci popisujici kalibraci (docs/PWM-CALIBRATION.md)
- [ ] aktualizovat dokumentaci big picture