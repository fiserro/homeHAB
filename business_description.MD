vytvor DESIGN.MD file a vnem navrhni reseni pro rizeni rekuperace pomoci Rule tridy napsane v jave.
# Vstup
Na vstupu mam nekolik zigbee zarizeni, ktere jsou pres mqtt broker pripojeny do poenHABu. 
Kazde zarizeni ma jeden nebo vice kanalu.
Kazda hodnota ma svuj kanal. jedna se o nekolik senzoru vlhkosti - number, nekolik senzoru CO2 a
nekolik prepinacu - detektor koure, priznak, ze je pustena digestor, senzory otevrenych oken, priznak manulaniho rezimu, boost apod - to jsou vsechno boolean hodnoty. Primanualnim rezimu do toho jeste
vstupuje konkretni hodnota pro rekuperaci v rozmezi 0-100. 


# Vystup
Predpokladam, ze vystupni hodnota se zapise do MQTT kanalu jako cislo v rozsahu 0-100.
Vystupni hodnota odpovida vykonu ventilatoru rekuperace a je v procentech.

# Implementace
Implementace bude v jave.

## InputType enum

```java
import java.lang.reflect.Type;

public interface AggregationFunction<T> {
  T aggregate(T v1, T v2);
}

public enum AggregationType {
  MIN,
  MAX,
  SUM,
  COUNT;

  T aggregate(T v1, T v2) {
    // switch on type - find the aggregation function
    // Note: AVG temporarily removed - requires knowing total count
  }
}

public enum InputType {
  MANUAL_MODE(Boolean.class, AggregationType.MAX),
  TEMPORARY_MANUAL_MODE(Boolean.class, AggregationType.MAX),
  BOOST_MODE(Boolean.class, AggregationType.MAX),
  TEMPORARY_BOOST_MODE(Boolean.class, AggregationType.MAX),
  BOOLEAN_SENSOR(Boolean.class, AggregationType.MAX),
  HUMIDITY(Number.class, AggregationType.MAX),
  CO2(Number.class, AggregationType.MAX);
  private final Type dataType;
  private final AggregationType aggregationType;
}

```

# Rule
Logika pro rule bude pracovat s interni multimapou, kde klicem bude InputType a hodnotou bude seznam kanalu prislusneho typu.

```java
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

@Builder
@RequiredArgsConstructor
public class HeatRecoveryVentilationRule {
    // pridej zavislost na guava pro MultiMap
    @Singular
    private final MultiMap<InputType, String> inputChannels = HashMultimap.create();
}
```
Uvnitr rule bude custom logika, ktera posibira vsechny vstupy a zapise do kanalu vystup

## Trigger
kterykoliv z kanalu se zmeni, spusti se logika pro prepocet vystupni hodnoty.

## Vytvoreni instance Rule
```java
Rule rule = HeatRecoveryVentilationRule.builder()
    .inputChannel(InputType.MANUAL_MODE, "zigbee/livingroom/manual_mode")
    .inputChannel(InputType.BOOST_MODE, "zigbee/livingroom/boost_mode")
    .inputChannel(InputType.HUMIDITY, "zigbee/livingroom/humidity_humidity_bathroom1")
    .inputChannel(InputType.HUMIDITY, "zigbee/livingroom/humidity_humidity_bathroom2")
    .inputChannel(InputType.HUMIDITY, "zigbee/livingroom/humidity")
    .inputChannel(InputType.HUMIDITY, "zigbee/livingroom/humidity")
    .inputChannel(InputType.CO2, "zigbee/livingroom/co2")
    .build();
```
Popis jakym zpusobem by se dala takto vytvorena instance HeatRecoveryVentilationRule pouzit v OpenHABu.
Jde o to, ze bude mozne pridavat/odebirat/prejemnovat nova zarizeni urciteho typu a neni zadouci pokazde
compilovat novy jar. Proto ma byt bussines logika opreba jen o InputType a konkretni nazvy kanalu e budou definovat az na urovni OpenHAB.

## Globalni promenne
V openhabu potebuji mit moznost definovat globální promenne.
V tuto chvili me napada 1 use case, ale casem jich bude jiste pribyvat. 
S promennymi je potreba umet pracovat, takze kazda promenna bude mit take svuj InputType.
Jeste nevim, jestli je dobry napad je predavat take pomoci kanalu, nebi bude lepsi je mit definvoane jako Item.
- Navrhni reseni
- Aktualni use case: casova hodnota udavajici za jak dlouho se ma TEMPORARY_MANUAL_MODE a TEMPORARY_BOOST_MODE automaticky vypnout.