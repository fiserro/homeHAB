package com.example.lib;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.openhab.core.scheduler.Scheduler;
import org.openhab.core.scheduler.ScheduledCompletableFuture;

import lombok.extern.slf4j.Slf4j;

/**
 * Fluent API pro odložené akce v OpenHAB automation scriptech.
 *
 * Příklady použití:
 * <pre>
 * // Základní použití s TimeUnit
 * wait(60, TimeUnit.SECONDS).then(() -> log.info("Executed after 60 seconds"));
 *
 * // Použití s ChronoUnit (Java 8+ Time API)
 * wait(5, ChronoUnit.MINUTES).then(() -> itemRegistry.get("light").send(OnOffType.OFF));
 *
 * // Zrušitelná akce
 * var scheduled = wait(30, TimeUnit.SECONDS).then(() -> doSomething());
 * scheduled.cancel(false); // zruší odloženou akci
 *
 * // Error handling
 * wait(10, TimeUnit.SECONDS)
 *     .then(() -> riskyOperation())
 *     .exceptionally(ex -> {
 *         log.error("Error occurred", ex);
 *         return null;
 *     });
 * </pre>
 *
 * @author Robert Fišer
 */
@Slf4j
public class DelayedActions {

    private final Scheduler scheduler;
    private final Duration delay;

    /**
     * Vytvoří novou instanci pro odložené akce.
     * Instance je automaticky injektována OpenHAB scriptem.
     */
    public DelayedActions(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.delay = Duration.ZERO;
    }

    private DelayedActions(Scheduler scheduler, Duration delay) {
        this.scheduler = scheduler;
        this.delay = delay;
    }

    /**
     * Statická factory metoda pro vytvoření čekání s TimeUnit.
     *
     * @param amount počet jednotek času
     * @param unit časová jednotka (SECONDS, MINUTES, HOURS, atd.)
     * @return builder pro specifikaci akce
     */
    public static DelayedActionBuilder wait(long amount, TimeUnit unit) {
        Duration duration = Duration.of(amount, toChronoUnit(unit));
        return new DelayedActionBuilder(duration);
    }

    /**
     * Statická factory metoda pro vytvoření čekání s ChronoUnit.
     *
     * @param amount počet jednotek času
     * @param unit časová jednotka (SECONDS, MINUTES, HOURS, atd.)
     * @return builder pro specifikaci akce
     */
    public static DelayedActionBuilder wait(long amount, ChronoUnit unit) {
        Duration duration = Duration.of(amount, unit);
        return new DelayedActionBuilder(duration);
    }

    /**
     * Statická factory metoda pro vytvoření čekání s Duration.
     *
     * @param duration doba čekání
     * @return builder pro specifikaci akce
     */
    public static DelayedActionBuilder wait(Duration duration) {
        return new DelayedActionBuilder(duration);
    }

    /**
     * Konvertuje TimeUnit na ChronoUnit
     */
    private static ChronoUnit toChronoUnit(TimeUnit unit) {
        return switch (unit) {
            case NANOSECONDS -> ChronoUnit.NANOS;
            case MICROSECONDS -> ChronoUnit.MICROS;
            case MILLISECONDS -> ChronoUnit.MILLIS;
            case SECONDS -> ChronoUnit.SECONDS;
            case MINUTES -> ChronoUnit.MINUTES;
            case HOURS -> ChronoUnit.HOURS;
            case DAYS -> ChronoUnit.DAYS;
        };
    }

    /**
     * Builder pro fluent API konstrukci odložených akcí.
     */
    public static class DelayedActionBuilder {
        private final Duration delay;
        private Scheduler injectedScheduler;

        DelayedActionBuilder(Duration delay) {
            this.delay = delay;
        }

        /**
         * Nastaví scheduler (automaticky injektováno Java223).
         */
        void setScheduler(Scheduler scheduler) {
            this.injectedScheduler = scheduler;
        }

        /**
         * Provede akci po uplynutí času.
         *
         * @param action akce k provedení (Runnable)
         * @return ScheduledCompletableFuture pro možnost zrušení nebo sledování stavu
         */
        public ScheduledCompletableFuture<Void> then(Runnable action) {
            if (injectedScheduler == null) {
                throw new IllegalStateException(
                    "Scheduler not injected. Ensure DelayedActions is properly injected by Java223 script engine.");
            }

            log.debug("Scheduling action to run after {}", delay);

            return injectedScheduler.after(() -> {
                try {
                    action.run();
                    log.debug("Delayed action executed successfully");
                    return null;
                } catch (Exception e) {
                    log.error("Error executing delayed action", e);
                    throw e;
                }
            }, delay);
        }

        /**
         * Provede akci s parametrem po uplynutí času.
         *
         * @param action akce k provedení (Consumer)
         * @param parameter parametr předaný akci
         * @return ScheduledCompletableFuture pro možnost zrušení nebo sledování stavu
         */
        public <T> ScheduledCompletableFuture<Void> thenAccept(Consumer<T> action, T parameter) {
            return then(() -> action.accept(parameter));
        }
    }

    /**
     * Instance metoda pro použití pokud je DelayedActions injektována jako field.
     *
     * @param amount počet jednotek času
     * @param unit časová jednotka
     * @return builder pro specifikaci akce
     */
    public DelayedActionBuilder delay(long amount, TimeUnit unit) {
        Duration duration = Duration.of(amount, toChronoUnit(unit));
        DelayedActionBuilder builder = new DelayedActionBuilder(duration);
        builder.setScheduler(this.scheduler);
        return builder;
    }

    /**
     * Instance metoda pro použití pokud je DelayedActions injektována jako field.
     *
     * @param amount počet jednotek času
     * @param unit časová jednotka
     * @return builder pro specifikaci akce
     */
    public DelayedActionBuilder delay(long amount, ChronoUnit unit) {
        Duration duration = Duration.of(amount, unit);
        DelayedActionBuilder builder = new DelayedActionBuilder(duration);
        builder.setScheduler(this.scheduler);
        return builder;
    }
}
