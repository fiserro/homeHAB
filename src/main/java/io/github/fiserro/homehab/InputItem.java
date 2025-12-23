package io.github.fiserro.homehab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface InputItem {

    /**
     * OpenHAB channel binding for this input item.
     * Example: "mqtt:topic:mosquitto:panel_commands:tempManualMode"
     */
    String channel() default "";
}
