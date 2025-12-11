package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.AggregationType;

/**
 * Heat Recovery Ventilator (HRV) input types.
 * Defines all input types for the HRV system including their aggregation functions.
 */
public enum HrvInputType {
  // Boolean modes (priority řízení)
  MANUAL_MODE(Boolean.class, AggregationType.MAX, 100),
  TEMPORARY_MANUAL_MODE(Boolean.class, AggregationType.MAX, 200),
  BOOST_MODE(Boolean.class, AggregationType.MAX, 80),
  TEMPORARY_BOOST_MODE(Boolean.class, AggregationType.MAX, 80),

  // Boolean sensors (bezpečnostní vstupy)
  SMOKE_DETECTOR(Boolean.class, AggregationType.MAX, 0),
  EXHAUST_HOOD(Boolean.class, AggregationType.MAX, 60),
  WINDOW_OPEN(Boolean.class, AggregationType.SUM, 0), // okno otevřené = ventilace na 0

  // Number sensors (enviornmentální vstupy)
  HUMIDITY(Number.class, AggregationType.MAX, 0),
  CO2(Number.class, AggregationType.MAX, 0),

  // Manual control
  MANUAL_POWER(Number.class, AggregationType.MAX, 0);

  private final Class<?> dataType;
  private final AggregationType aggregationType;
  private final int defaultPower; // výchozí výkon při aktivaci

  HrvInputType(Class<?> dataType, AggregationType aggregationType, int defaultPower) {
    this.dataType = dataType;
    this.aggregationType = aggregationType;
    this.defaultPower = defaultPower;
  }

  public Class<?> getDataType() {
    return dataType;
  }

  public AggregationType getAggregationType() {
    return aggregationType;
  }

  public int getDefaultPower() {
    return defaultPower;
  }
}
