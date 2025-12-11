package io.github.fiserro.homehab.hrv;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.util.Map;

/**
 * State of the HRV system at a given moment.
 */
@Value
@Builder
public class HrvState {
    Map<HrvInputType, Object> aggregatedInputs;
    int calculatedPower;
    String activeMode;  // např. "BOOST", "MANUAL", "AUTO"
    Instant lastUpdate;
}