# DelayedActions - Design dokumentace

## Přehled

`DelayedActions` je fluent API knihovna pro plánování odložených akcí v OpenHAB automation scriptech. Navržena pro jednoduché použití s intuitivní syntaxí inspirovanou moderními async frameworky.

## Požadavky

Uživatel požadoval:
- Obecný script, který počká definovaný počet sekund (číslo, jednotka)
- Poté provede akci
- Ideálně použitelné jako inline příkaz přes fluent API
- Syntaxe: `wait(60, SECOND).then(() -> runScriptB())`

## Technické řešení

### Využité OpenHAB API

1. **Scheduler interface** (`org.openhab.core.scheduler.Scheduler`)
   - `after(Callable<T> callable, Duration delay)` - naplánuje akci s delay
   - Vrací `ScheduledCompletableFuture<T>` - zrušitelný future

2. **Java223 auto-injection**
   - Automatická injekce `Scheduler` služby do knihovny
   - Využití `Java223Script` base class v příkladech

3. **CompletableFuture pattern**
   - Asynchronní zpracování
   - Error handling přes `exceptionally()`
   - Kombinovatelnost s dalšími futures

### Architektura

```
DelayedActions (main class)
├── wait(...) static factory methods
│   ├── wait(long, TimeUnit)
│   ├── wait(long, ChronoUnit)
│   └── wait(Duration)
├── delay(...) instance methods (pro injektovanou instanci)
└── DelayedActionBuilder (fluent builder)
    ├── then(Runnable)
    └── thenAccept(Consumer<T>, T)
```

### Klíčové vlastnosti

1. **Fluent API**: Chain-able metody pro čitelný kód
2. **Type-safe**: Využití Java generic types
3. **Flexible time units**: Podpora `TimeUnit`, `ChronoUnit`, `Duration`
4. **Error handling**: Built-in podpora pro exceptionally()
5. **Cancellable**: Vrací `ScheduledCompletableFuture` pro možnost zrušení
6. **Logging**: Integrované logování pro debugging

## Příklady použití

### Základní použití

```java
import static com.example.lib.DelayedActions.wait;
import static java.util.concurrent.TimeUnit.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyScript {
    private ItemRegistry itemRegistry; // Auto-injected by Java223

    public void example() {
        wait(60, SECONDS).then(() -> {
            log.info("Turning light OFF");
            itemRegistry.get("light").send(OnOffType.OFF);
        });
    }
}
```

### Zrušitelná akce

```java
var scheduled = wait(30, SECONDS).then(() -> doSomething());

// Později:
scheduled.cancel(false);
```

### Error handling

```java
wait(10, SECONDS)
    .then(() -> riskyOperation())
    .exceptionally(ex -> {
        log.error("Error", ex);
        return null;
    });
```

### Praktický use case - Motion detector

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionDetectorRule {
    private ItemRegistry itemRegistry; // Auto-injected
    private ScheduledCompletableFuture<Void> autoOff = null;

    @Rule
    @ItemStateUpdateTrigger(itemName = "motionDetector", state = "ON")
    public void onMotion() {
        log.info("Motion detected!");
        itemRegistry.get("light").send(OnOffType.ON);

        if (autoOff != null) autoOff.cancel(false);

        autoOff = wait(5, ChronoUnit.MINUTES).then(() -> {
            log.info("No motion, turning light OFF");
            itemRegistry.get("light").send(OnOffType.OFF);
        });
    }
}
```

## Deployment

### 1. Kompilace knihovny

```bash
mvn clean compile
mvn package
```

Výstup: `target/homeHAB-1.0-SNAPSHOT.jar`

### 2. Deployment do OpenHAB

**Varianta A: JAR library**
```bash
cp target/homeHAB-1.0-SNAPSHOT.jar $OPENHAB_CONF/automation/lib/java/
```

**Varianta B: Source code**
```bash
cp src/main/java/com/example/lib/DelayedActions.java $OPENHAB_CONF/automation/lib/java/
```

### 3. Použití v scriptech

```bash
cp src/main/java/com/example/scripts/*.java $OPENHAB_CONF/automation/jsr223/
```

OpenHAB automaticky zkompiluje a načte scripty.

## Testování

### Unit testy

```bash
mvn test
```

Testy využívají mock `Scheduler` pro testování bez skutečného čekání.

### Manuální testování v OpenHAB

1. Zkopíruj knihovnu do `automation/lib/java/`
2. Zkopíruj test script do `automation/jsr223/`
3. Sleduj OpenHAB logy: `tail -f $OPENHAB_USERDATA/logs/openhab.log`
4. Ověř že script byl zkompilován a načten
5. Triggeruj akci a sleduj výstupy

## Budoucí rozšíření

Možná vylepšení:

1. **Periodic scheduling**: Opakující se akce
   ```java
   repeat(30, SECONDS).until(condition).execute(() -> check());
   ```

2. **Conditional execution**: Podmíněné provedení
   ```java
   wait(60, SECONDS).when(() -> isNight()).then(() -> action());
   ```

3. **Group actions**: Akcre nad skupinou items
   ```java
   wait(60, SECONDS).forAllItems(lightGroup).send(OFF);
   ```

4. **Smart delays**: Adaptivní čekání
   ```java
   waitUntil(sunset()).plus(30, MINUTES).then(() -> action());
   ```

5. **Debouncing**: Pro časté události
   ```java
   debounce(5, SECONDS).then(() -> consolidatedAction());
   ```

## Reference

- [OpenHAB Java223 README](https://github.com/dalgwen/openhab-addons/blob/5.0.1-java223-BETA4/bundles/org.openhab.automation.java223/README.md)
- [OpenHAB Scheduler API](https://www.openhab.org/javadoc/latest/org/openhab/core/scheduler/scheduler)
- [CompletableFuture documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

## Autor

Robert Fišer - 2025
