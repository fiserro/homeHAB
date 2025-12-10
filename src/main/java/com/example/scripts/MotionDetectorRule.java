package com.example.scripts;

import java.time.temporal.ChronoUnit;

import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.scheduler.ScheduledCompletableFuture;

import com.example.lib.DelayedActions;

import helper.rules.annotations.ItemStateUpdateTrigger;
import helper.rules.annotations.Rule;
import lombok.extern.slf4j.Slf4j;

/**
 * Praktický příklad: Rule pro automatické rozsvícení a zhasnutí světla
 * založené na detekci pohybu.
 *
 * Funkce:
 * - Když detektor pohybu detekuje pohyb (ON), rozsvítí se světlo
 * - Po 5 minutách bez pohybu se světlo automaticky zhasne
 * - Pokud se v mezičase detekuje další pohyb, timer se resetuje
 *
 * @author Robert Fišer
 */
@Slf4j
public class MotionDetectorRule {

    // Auto-injected by Java223
    private ScriptBusEvent events;

    // Uchovává naplánované zhasnutí pro možnost zrušení
    private ScheduledCompletableFuture<Void> scheduledAutoOff = null;

    // Timeout v minutách
    private static final int AUTO_OFF_TIMEOUT_MINUTES = 5;

    /**
     * Rule triggered when motion detector changes state to ON
     */
    @Rule(
        name = "motion.detector.auto.light",
        description = "Automatically turns light ON on motion, and OFF after " + AUTO_OFF_TIMEOUT_MINUTES + " minutes"
    )
    @ItemStateUpdateTrigger(itemName = "motionDetector", state = "ON")
    public void onMotionDetected() {
        log.info("Motion detected! Turning light ON");

        // 1. Rozsvít světlo
        events.sendCommand("hallwayLight", "ON");

        // 2. Zruš předchozí auto-off timer, pokud existuje
        if (scheduledAutoOff != null && !scheduledAutoOff.isDone()) {
            scheduledAutoOff.cancel(false);
            log.info("Cancelled previous auto-off timer (motion detected again)");
        }

        // 3. Naplánuj nový auto-off timer
        log.info("Scheduling auto-off in {} minutes", AUTO_OFF_TIMEOUT_MINUTES);
        scheduledAutoOff = DelayedActions.wait(AUTO_OFF_TIMEOUT_MINUTES, ChronoUnit.MINUTES).then(() -> {
            log.info("No motion for {} minutes, turning light OFF", AUTO_OFF_TIMEOUT_MINUTES);
            events.sendCommand("hallwayLight", "OFF");
        });
    }

    /**
     * Varianta s okamžitým zhasnutím při manuálním vypnutí
     */
    @Rule(
        name = "motion.detector.manual.off",
        description = "Cancel auto-off when light is manually turned OFF"
    )
    @ItemStateUpdateTrigger(itemName = "hallwayLight", state = "OFF")
    public void onLightManuallyTurnedOff() {
        if (scheduledAutoOff != null && !scheduledAutoOff.isDone()) {
            scheduledAutoOff.cancel(false);
            log.info("Light manually turned OFF, cancelled auto-off timer");
        }
    }
}
