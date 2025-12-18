# TODOS
### Prepracovat aggregace a group items
- group items budou existovat pouze pro mqtt zarizeni, jejichz hodnota je potreba agregovat
- behem generovani items z HabState, tyto fieldy generovat jako group item s definvoaneou aggregaci
- pak bude potreba rucne - od progamatora, aby udelal mappovani mezi group item a zigbee zarinimi
- ted se to deje za behu v HrvControl pomoci MqttItemMappings
- to ma nevyhodu, ze group item je ted generovani AI a neodpovida realite
- Je potreba vymyslet zpusob jak MqttItemMappings pouzit v generatoru groups
- uz nebude potreba provadet aggreace ve scriptu - budou se delat automaticky v openhabu na urovni group items
- na urovni HabState je potreba rozlisovat 2 typy mqtt zarizeni
  - single - nema agregaci a bude potreba manualne priradit prave jedno zarizeni
  - multi - obsahuje aggregacni anotaci a bude potreba manualne priradit minimalne jedno zarizeni