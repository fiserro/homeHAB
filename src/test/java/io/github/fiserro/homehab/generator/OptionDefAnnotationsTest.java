package io.github.fiserro.homehab.generator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.options.OptionDef;
import io.github.fiserro.options.OptionsFactory;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for OptionDef.annotations() behavior with overriding methods.
 *
 * <p>The issue: When a method is defined in a parent module (e.g., HrvModule.powerAd0())
 * and overridden in a child interface (e.g., HabState.powerAd0()) with additional annotations,
 * OptionDef.annotations() should return annotations from the overriding method.
 *
 * <p>Example:
 * <pre>
 * // In HrvModule:
 * @ReadOnlyItem
 * default int powerAd0() { return 0; }
 *
 * // In HabState (overrides):
 * @Override
 * @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:current_ad0")
 * default int powerAd0() { return HrvModule.super.powerAd0(); }
 * </pre>
 *
 * <p>When iterating over options from HabState, we expect to see the channel binding
 * from HabState's annotation, not the empty channel from HrvModule's annotation.
 */
class OptionDefAnnotationsTest {

    private TestHabState habState;
    private Map<String, OptionDef> optionsByName;

    @BeforeEach
    void setUp() {
        habState = OptionsFactory.create(TestHabState.class);
        optionsByName = habState.options().stream()
                .collect(Collectors.toMap(OptionDef::name, opt -> opt));
    }

    @Nested
    @DisplayName("Method resolution for overridden methods")
    class MethodResolution {

        @Test
        @DisplayName("opt.method() should return method from the interface we created options from")
        void optionDef_method_shouldReturnMethodFromTargetInterface() {
            OptionDef opt = optionsByName.get("insideTemperature");
            assertNotNull(opt, "insideTemperature option should exist");

            assertEquals(TestHabState.class, opt.method().getDeclaringClass(),
                    "opt.method() should return TestHabState method, not CommonModule");
        }
    }

    @Nested
    @DisplayName("Annotation resolution for overridden methods")
    class AnnotationResolution {

        @Test
        @DisplayName("opt.annotations() should see @MqttItem from override")
        void optionDef_annotations_shouldSeeMqttItemFromOverride() {
            OptionDef opt = optionsByName.get("insideTemperature");
            assertNotNull(opt, "insideTemperature option should exist");

            MqttItem mqttItem = opt.annotations().stream()
                    .filter(MqttItem.class::isInstance)
                    .map(MqttItem.class::cast)
                    .findFirst()
                    .orElse(null);

            assertNotNull(mqttItem, "opt.annotations() should see @MqttItem from TestHabState override");
            assertArrayEquals(new String[]{"aqara*Temperature", "soil*Temperature"}, mqttItem.value(),
                    "@MqttItem.value() should match TestHabState annotation");
        }

        @Test
        @DisplayName("@OutputItem.channel() should come from override, not parent")
        void optionDef_outputItemChannel_shouldComeFromOverride() {
            OptionDef opt = optionsByName.get("hrvOutputPower");
            assertNotNull(opt, "hrvOutputPower option should exist");

            OutputItem outputItem = opt.annotations().stream()
                    .filter(OutputItem.class::isInstance)
                    .map(OutputItem.class::cast)
                    .findFirst()
                    .orElse(null);

            assertNotNull(outputItem, "hrvOutputPower should have @OutputItem");
            assertEquals("mqtt:topic:hrv:power", outputItem.channel(),
                    "@OutputItem.channel() should come from TestHabState override");
        }
    }

    @Nested
    @DisplayName("Non-overridden methods work correctly")
    class NonOverriddenMethods {

        @Test
        @DisplayName("Annotations from parent module are visible for non-overridden methods")
        void optionDef_annotations_visibleForNonOverriddenMethods() {
            // outsideTemperature is defined in CommonModule with @ReadOnlyItem
            // TestHabState doesn't override it, so the parent annotation should be visible
            OptionDef opt = optionsByName.get("outsideTemperature");
            assertNotNull(opt, "outsideTemperature option should exist");

            ReadOnlyItem readOnlyItem = opt.annotations().stream()
                    .filter(ReadOnlyItem.class::isInstance)
                    .map(ReadOnlyItem.class::cast)
                    .findFirst()
                    .orElse(null);

            assertNotNull(readOnlyItem, "outsideTemperature should have @ReadOnlyItem from CommonModule");
            assertEquals("", readOnlyItem.channel(),
                    "Channel should be empty (from CommonModule, not overridden)");
        }
    }
}
