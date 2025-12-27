package io.github.fiserro.homehab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a read-only OpenHAB item.
 * These items are managed by the system and cannot be changed via UI.
 * Used for computed values like scheduled off-times for temporary modes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnlyItem {

    /**
     * OpenHAB channel binding for this read-only item.
     * Example: "mqtt:topic:mosquitto:hrv_bridge:calibrationStep"
     */
    String channel() default "";
}
