# homeHAB - OpenHAB Java Automation Scripts

Projekt pro vývoj Java automation scriptů pro OpenHAB s podporou Java223 JSR223 engine.

## Požadavky

- JDK 21
- Maven 3.x
- OpenHAB 5.0.1 (running nebo zkompilovaný lokálně)
- Java223 automation bundle 5.0.1-BETA4

## Struktura projektu

```
homeHAB/
├── src/main/java/com/example/
│   ├── lib/              # Sdílené knihovny pro použití v OpenHAB automation/lib/java
│   │   └── DelayedActions.java
│   └── scripts/          # Automation scripty pro OpenHAB automation/jsr223
│       ├── DelayedActionsExample.java
│       └── MotionDetectorRule.java
├── src/test/java/        # Unit testy
├── pom.xml
└── README.md
```

## Kompilace projektu

```bash
mvn clean compile
```

## Testování

```bash
mvn test
```

## Features

### DelayedActions - Fluent API pro odložené akce

Knihovna poskytuje jednoduché fluent API pro plánování odložených akcí v OpenHAB automation scriptech.

#### Příklady použití

**Základní použití:**

```java
import static com.example.lib.DelayedActions.wait;
import static java.util.concurrent.TimeUnit.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyScript {
    private ItemRegistry itemRegistry; // Auto-injected by Java223

    public void example() {
        // Zhasni světlo po 60 sekundách
        wait(60, SECONDS).then(() -> {
            log.info("Turning light OFF");
            itemRegistry.get("myLight").send(OnOffType.OFF);
        });
    }
}
```

**S ChronoUnit:**

```java
import java.time.temporal.ChronoUnit;

// Zhasni světlo po 5 minutách
wait(5, ChronoUnit.MINUTES).then(() -> {
    log.info("5 minutes elapsed");
    itemRegistry.get("myLight").send(OnOffType.OFF);
});
```

**Zrušitelná akce:**

```java
var scheduled = wait(30, SECONDS).then(() -> doSomething());

// Později můžeš akci zrušit
scheduled.cancel(false);
```

**Error handling:**

```java
wait(10, SECONDS)
    .then(() -> riskyOperation())
    .exceptionally(ex -> {
        log.error("Error occurred", ex);
        return null;
    });
```

**Řetězení akcí:**

```java
wait(5, SECONDS).then(() -> {
    log.info("Turning light ON");
    itemRegistry.get("light").send(OnOffType.ON);

    // Další akce po dalších 10 sekundách
    wait(10, SECONDS).then(() -> {
        log.info("Turning light OFF");
        itemRegistry.get("light").send(OnOffType.OFF);
    });
});
```

#### Praktický příklad - Motion Detector Rule

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionDetectorRule {

    private ItemRegistry itemRegistry; // Auto-injected by Java223
    private ScheduledCompletableFuture<Void> scheduledAutoOff = null;

    @Rule(name = "motion.auto.light")
    @ItemStateUpdateTrigger(itemName = "motionDetector", state = "ON")
    public void onMotionDetected() {
        log.info("Motion detected!");

        // Rozsvít světlo
        itemRegistry.get("hallwayLight").send(OnOffType.ON);

        // Zruš předchozí timer
        if (scheduledAutoOff != null && !scheduledAutoOff.isDone()) {
            scheduledAutoOff.cancel(false);
        }

        // Naplánuj zhasnutí za 5 minut
        scheduledAutoOff = wait(5, ChronoUnit.MINUTES).then(() -> {
            log.info("No motion, turning light OFF");
            itemRegistry.get("hallwayLight").send(OnOffType.OFF);
        });
    }
}
```

## Deployment do OpenHAB

### 1. Zkompiluj projekt

```bash
mvn clean package
```

### 2. Zkopíruj knihovnu do OpenHAB

```bash
# Zkopíruj DelayedActions do lib adresáře
cp target/homeHAB-1.0-SNAPSHOT.jar $OPENHAB_CONF/automation/lib/java/
```

### 3. Zkopíruj scripty do automation adresáře

```bash
# Zkopíruj .java soubory přímo
cp src/main/java/com/example/scripts/*.java $OPENHAB_CONF/automation/jsr223/
```

### 4. OpenHAB automaticky načte scripty

OpenHAB detekuje nové `.java` soubory a zkompiluje je pomocí Java223 engine.

## Technické detaily

### Závislosti

Projekt využívá následující OpenHAB komponenty:

- **org.openhab.core** (5.0.1) - Core OpenHAB API
- **org.openhab.core.automation** (5.0.1) - Automation framework
- **org.openhab.core.thing** (5.0.1) - Thing framework
- **org.openhab.core.automation.module.script** (5.0.1) - Script module support
- **org.openhab.automation.java223** (5.0.1) - Java223 JSR223 engine

Všechny závislosti jsou nakonfigurované jako `provided` scope, protože jsou poskytnuté OpenHAB runtime.

### Java223 Features

Projekt využívá tyto Java223 features:

- **Auto-injection** - Automatická injekce OpenHAB služeb (Scheduler, ItemRegistry, atd.)
- **Rule annotations** - Deklarativní definice rules pomocí `@Rule`, `@ItemStateUpdateTrigger`
- **Compiled caching** - Sub-millisecond execution po první kompilaci
- **Library support** - Sdílení kódu přes `automation/lib/java`
- **Lombok @Slf4j** - Jednoduché logování přes `log.info()`, `log.error()`, atd.

## Další zdroje

- [OpenHAB Java223 Documentation](https://github.com/dalgwen/openhab-addons/blob/5.0.1-java223-BETA4/bundles/org.openhab.automation.java223/README.md)
- [OpenHAB JSR223 Documentation](https://www.openhab.org/docs/configuration/jsr223.html)
- [OpenHAB Scheduler API](https://www.openhab.org/javadoc/latest/org/openhab/core/scheduler/scheduler)

## Licence

Tento projekt je určen pro osobní použití s OpenHAB automation.
