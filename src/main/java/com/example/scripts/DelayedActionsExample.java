package com.example.scripts;

import static java.util.concurrent.TimeUnit.*;

import java.time.temporal.ChronoUnit;

import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.scheduler.ScheduledCompletableFuture;

import com.example.lib.DelayedActions;

import lombok.extern.slf4j.Slf4j;

/**
 * Příklady použití DelayedActions pro odložené akce.
 *
 * Tento script demonstruje různé způsoby použití fluent API.
 */
@Slf4j
public class DelayedActionsExample {

    // Auto-injected by Java223
    private ScriptBusEvent events;
    private DelayedActions delayedActions;

    /**
     * Příklad 1: Základní použití - zhasnutí světla po 60 sekundách
     */
    public void example1_SimpleDelay() {
        log.info("Example 1: Turning light OFF after 60 seconds");

        DelayedActions.wait(60, SECONDS).then(() -> {
            log.info("60 seconds elapsed, turning light OFF");
            events.sendCommand("myLightItem", "OFF");
        });
    }

    /**
     * Příklad 2: Použití s ChronoUnit - zhasnutí po 5 minutách
     */
    public void example2_ChronoUnit() {
        log.info("Example 2: Turning light OFF after 5 minutes");

        DelayedActions.wait(5, ChronoUnit.MINUTES).then(() -> {
            log.info("5 minutes elapsed, turning light OFF");
            events.sendCommand("myLightItem", "OFF");
        });
    }

    /**
     * Příklad 3: Zrušitelná akce
     */
    public void example3_CancellableAction() {
        log.info("Example 3: Scheduling cancellable action");

        // Naplánuj akci
        ScheduledCompletableFuture<Void> scheduled = DelayedActions.wait(30, SECONDS).then(() -> {
            log.info("This might not execute if cancelled");
            events.sendCommand("myLightItem", "OFF");
        });

        // Někde jinde v kódu můžeš akci zrušit:
        // scheduled.cancel(false);

        // Nebo počkat na dokončení:
        // scheduled.thenRun(() -> log.info("Action completed"));
    }

    /**
     * Příklad 4: Error handling
     */
    public void example4_ErrorHandling() {
        log.info("Example 4: Action with error handling");

        DelayedActions.wait(10, SECONDS).then(() -> {
            log.info("Executing risky operation");
            try {
                // Nějaká operace co může selhat
                if (Math.random() > 0.5) {
                    throw new RuntimeException("Random failure!");
                }
                events.sendCommand("myLightItem", "OFF");
            } catch (Exception ex) {
                log.error("Error in delayed action: {}", ex.getMessage());
                // Fallback akce
            }
        });
    }

    /**
     * Příklad 5: Řetězení akcí (cascade)
     */
    public void example5_ChainedActions() {
        log.info("Example 5: Chaining multiple delayed actions");

        // První akce po 5 sekundách
        DelayedActions.wait(5, SECONDS).then(() -> {
            log.info("First action: Light ON");
            events.sendCommand("myLightItem", "ON");

            // Druhá akce po dalších 10 sekundách
            DelayedActions.wait(10, SECONDS).then(() -> {
                log.info("Second action: Light OFF");
                events.sendCommand("myLightItem", "OFF");
            });
        });
    }

    /**
     * Příklad 6: Instance metoda s injektovanou knihovnou
     * Alternativní způsob použití s injektovanou instancí DelayedActions
     */
    public void example6_InstanceMethod() {
        log.info("Example 6: Using instance method");

        // Použití instance metody místo statické
        delayedActions.delay(30, SECONDS).then(() -> {
            log.info("Using instance method");
            events.sendCommand("myLightItem", "OFF");
        });
    }

    /**
     * Příklad 7: Parametrizovaná akce
     */
    public void example7_ParameterizedAction() {
        log.info("Example 7: Parameterized action");

        String itemName = "myLightItem";

        DelayedActions.wait(15, SECONDS).then(() -> {
            log.info("Turning OFF item: {}", itemName);
            events.sendCommand(itemName, "OFF");
        });
    }

    /**
     * Příklad 8: Praktický use case - automatické zhasnutí po detekci pohybu
     * Toto by se normálně použilo v rule s triggerem
     */
    private ScheduledCompletableFuture<Void> autoOffSchedule = null;

    public void example8_MotionDetectedAutoOff() {
        log.info("Motion detected! Turning light ON");

        // Rozsvít světlo
        events.sendCommand("myLightItem", "ON");

        // Zruš předchozí auto-off pokud existuje
        if (autoOffSchedule != null && !autoOffSchedule.isDone()) {
            autoOffSchedule.cancel(false);
            log.info("Cancelled previous auto-off");
        }

        // Naplánuj nové auto-off za 5 minut
        autoOffSchedule = DelayedActions.wait(5, ChronoUnit.MINUTES).then(() -> {
            log.info("No motion for 5 minutes, turning light OFF");
            events.sendCommand("myLightItem", "OFF");
        });
    }

    /**
     * Hlavní metoda - spustí příklady
     */
    public void main() {
        log.info("=== DelayedActions Examples ===");

        // Uncomment the example you want to test:
        // example1_SimpleDelay();
        // example2_ChronoUnit();
        // example3_CancellableAction();
        // example4_ErrorHandling();
        // example5_ChainedActions();
        // example6_InstanceMethod();
        // example7_ParameterizedAction();
        // example8_MotionDetectedAutoOff();

        log.info("Examples script loaded. Uncomment examples to test them.");
    }
}
