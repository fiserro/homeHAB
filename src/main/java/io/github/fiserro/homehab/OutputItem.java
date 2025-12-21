package io.github.fiserro.homehab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface OutputItem {

    /**
     * OpenHAB channel binding for this output item.
     * Example: "mqtt:topic:dac:power"
     */
    String channel() default "";
}
