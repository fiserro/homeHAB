# Automaticke generovani openhab elementů
Tento dokument popisuje, jak automaticky generovat OpenHAB elementy
- Things
- Channels
- Items
- Groups

## Obecne pokyny
- kazde kazdy generovaci skript ma svuj prave jeden vystupni soubor
- Skript generuje vzyd na cisto - kdyz se na vstupu zmeni podminky jako odpovejene zarizeni nebo programator smaze zaznam z HrvConfigu
  - potom se odstrani i odpovidajici elementy z vystupinho souboru


## Zdroje pro generovani

### Zigbee2mqtt Skript: `generate-zigbee-config.py`
- externi vstupy jsou dane zigbee zarizenimi pripojenymi do mosquitta

**Vstup:**
- Zigbee2MQTT zařízení (získané přes SSH nebo MQTT)
- Mapování: zadne mapovani pres yaml soubor -`openhab-dev/conf/hrv-zigbee-mapping.yaml` se nebude k nicemu pouzivat - smazat

**Logika:**
- Získá všechna Zigbee zařízení (získaná přes SSH nebo MQTT)
- Pro kazde zarizeni smoke senzor, mumidity apod. vygeneruje skript 1 Thing
- Kazde zarizeni pak poskytuje 1-N metrik typu: boolean:detekce_kource, number:humidity, co2, napeti ,stav baterie, teplota apod.
- Prokazdou metriku kazdeho zarizeni se vytvori channel a item

#### Things
- uid: mqtt:zigbee:<deviceId>; priklad: mqtt:zigbee:0xa4c138aa8b540e22
- label: lidksy nazev; priklad: Smoke detector, Temperature and humidity sensor

#### Channels
- uid: mqtt:zigbee:<metricCategory>:<deviceId>; priklad: mqtt:zigbee:smoke:0xa4c138aa8b540e22
- label: zigbee, <metricCategory>; priklad: [zigbee, smoke], [zigbee, humidity], [zigbee, co2]
- vse ostatni jako channelTypeUID, stateTopic a transformationPattern budou fungovat stejen jako doted

#### Items
- uid: mqttZigbee<MetricCategory>_<deviceId>; priklad mqttZigbeeSmoke_0xa4c138aa8b540e22
- label: zigbee, <metricCategory>; priklad: [zigbee, smoke], [zigbee, humidity], [zigbee, co2]
- icon: zvolit vhodnou ikonu podle metricCategory - prohledej zdrojaky openhab-core, pripadne internet

#### Groups Items 
vytvor group pro kazdy metricCategory a vloz do nich itemy dle jejich category 
- uid: vhodne uid
- label: vhodny label
- icon: stejne jako items dane kategorie

- **Výstupní soubory:**
- `openhab-dev/conf/things/zigbee-devices.things` - Thing definice pro všechna Zigbee zařízení a jejich channels
- `openhab-dev/conf/items/zigbee-devices.items` - - Vsechny zigbee items

**Co NEGENERUJE:**
- zadne hrv item - to vy je nejaka chujovina z predchozi sesion



### Uzivateleske vstupy (GUI) Skript: `generate-hrv-config.py`
- vsechny uzivatelske vstupy programator definuje v souboru HrvConfig.java

**Vstup:**
- `src/main/java/io/github/fiserro/homehab/hrv/HrvConfig.java`
- Parsuje @Option metody s default hodnotami

**Výstupní soubor:**
- `openhab-dev/conf/items/hrv-config.items`

**Co generuje:**
- Konfigurační parametry s prefixem `hrvConfig`
- Příklady:
  - `hrvConfigHumidityThreshold` - "Humidity Threshold [%d %%]"
  - `hrvConfigCo2Threshold` - "CO2 Threshold [%d ppm]"

**Kategorizace:**
- Threshold values (obsahují "threshold")
- Power levels (ostatní bez "timeout")
- Timeouts (obsahují "timeout")