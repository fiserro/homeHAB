package com.example.lib;

import static java.util.concurrent.TimeUnit.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.scheduler.ScheduledCompletableFuture;
import org.openhab.core.scheduler.Scheduler;
import org.openhab.core.scheduler.SchedulerRunnable;

/**
 * Unit testy pro DelayedActions knihovnu.
 *
 * Používá mock Scheduler pro testování bez skutečného čekání.
 */
class DelayedActionsTest {

    private MockScheduler mockScheduler;
    private DelayedActions delayedActions;

    @BeforeEach
    void setUp() {
        mockScheduler = new MockScheduler();
        delayedActions = new DelayedActions(mockScheduler);
    }

    @Test
    void testWaitWithTimeUnit() {
        AtomicBoolean executed = new AtomicBoolean(false);

        var builder = DelayedActions.wait(60, SECONDS);
        builder.setScheduler(mockScheduler);

        builder.then(() -> executed.set(true));

        // Ověř že scheduler byl zavolán se správným delay
        assertEquals(Duration.ofSeconds(60), mockScheduler.lastDelay);
        assertTrue(mockScheduler.wasScheduled);

        // Simuluj vykonání akce
        mockScheduler.executeScheduled();
        assertTrue(executed.get(), "Action should have been executed");
    }

    @Test
    void testWaitWithChronoUnit() {
        AtomicBoolean executed = new AtomicBoolean(false);

        var builder = DelayedActions.wait(5, ChronoUnit.MINUTES);
        builder.setScheduler(mockScheduler);

        builder.then(() -> executed.set(true));

        assertEquals(Duration.ofMinutes(5), mockScheduler.lastDelay);
        assertTrue(mockScheduler.wasScheduled);

        mockScheduler.executeScheduled();
        assertTrue(executed.get());
    }

    @Test
    void testWaitWithDuration() {
        AtomicBoolean executed = new AtomicBoolean(false);
        Duration duration = Duration.ofHours(2);

        var builder = DelayedActions.wait(duration);
        builder.setScheduler(mockScheduler);

        builder.then(() -> executed.set(true));

        assertEquals(duration, mockScheduler.lastDelay);

        mockScheduler.executeScheduled();
        assertTrue(executed.get());
    }

    @Test
    void testInstanceMethodDelay() {
        AtomicBoolean executed = new AtomicBoolean(false);

        delayedActions.delay(30, SECONDS).then(() -> executed.set(true));

        assertEquals(Duration.ofSeconds(30), mockScheduler.lastDelay);

        mockScheduler.executeScheduled();
        assertTrue(executed.get());
    }

    @Test
    void testErrorHandling() {
        var builder = DelayedActions.wait(1, SECONDS);
        builder.setScheduler(mockScheduler);

        builder.then(() -> {
            throw new RuntimeException("Test error");
        });

        // Zkus vykonat akci - měla by vyhodit exception
        assertThrows(RuntimeException.class, () -> mockScheduler.executeScheduled());
    }

    @Test
    void testMultipleActions() {
        AtomicInteger counter = new AtomicInteger(0);

        var builder1 = DelayedActions.wait(1, SECONDS);
        builder1.setScheduler(mockScheduler);
        builder1.then(() -> counter.incrementAndGet());

        MockScheduler mockScheduler2 = new MockScheduler();
        var builder2 = DelayedActions.wait(2, SECONDS);
        builder2.setScheduler(mockScheduler2);
        builder2.then(() -> counter.incrementAndGet());

        mockScheduler.executeScheduled();
        assertEquals(1, counter.get());

        mockScheduler2.executeScheduled();
        assertEquals(2, counter.get());
    }

    /**
     * Mock Scheduler pro testování bez skutečného čekání.
     */
    private static class MockScheduler implements Scheduler {

        Duration lastDelay;
        Callable<?> lastCallable;
        boolean wasScheduled = false;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ScheduledCompletableFuture<T> after(Callable<T> callable, Duration delay) {
            this.lastCallable = callable;
            this.lastDelay = delay;
            this.wasScheduled = true;
            // Vrátíme null - v testech to stačí, protože testujeme jen scheduling, ne výsledek
            return null;
        }

        @Override
        public ScheduledCompletableFuture<Instant> at(Instant instant) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public <T> ScheduledCompletableFuture<T> at(Callable<T> callable, Instant instant) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public <T> ScheduledCompletableFuture<T> before(java.util.concurrent.CompletableFuture<T> promise,
                Duration timeout) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public ScheduledCompletableFuture<Instant> after(Duration delay) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public <T> ScheduledCompletableFuture<T> schedule(SchedulerRunnable callable,
                java.time.temporal.TemporalAdjuster temporalAdjuster) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public <T> ScheduledCompletableFuture<T> schedule(SchedulerRunnable callable, String identifier,
                java.time.temporal.TemporalAdjuster temporalAdjuster) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        void executeScheduled() {
            if (lastCallable != null) {
                try {
                    lastCallable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
