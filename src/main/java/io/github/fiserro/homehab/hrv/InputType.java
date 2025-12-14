package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.AggregationType;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Heat Recovery Ventilator (HRV) input types. Defines all input types for the HRV system including
 * their aggregation functions.
 */
@Getter
@Accessors(fluent = true)
public enum InputType {
  // Boolean modes (priority řízení)
  MANUAL_MODE(Boolean.class, AggregationType.MAX, 0),
  TEMPORARY_MANUAL_MODE(Boolean.class, AggregationType.MAX, 0),
  BOOST_MODE(Boolean.class, AggregationType.MAX, 0),
  TEMPORARY_BOOST_MODE(Boolean.class, AggregationType.MAX, 0),

  // Boolean sensors (bezpečnostní vstupy)
  WINDOW_OPEN(Boolean.class, AggregationType.SUM, 0),

  // Number sensors (enviornmentální vstupy)
  HUMIDITY(Number.class, AggregationType.MAX, 40),
  CO2(Number.class, AggregationType.MAX, 500),
  TEMPERATURE(Number.class, AggregationType.AVG, 20),
  PRESSURE(Number.class, AggregationType.AVG, 1000),
  SMOKE(Number.class, AggregationType.MAX, 0),
  GAS(Number.class, AggregationType.MAX, 100),

  // Thresholds
  HUMIDITY_THRESHOLD(Number.class, AggregationType.MAX, 60),
  CO2_THRESHOLD_LOW(Number.class, AggregationType.MAX, 500),
  CO2_THRESHOLD_MID(Number.class, AggregationType.MAX, 700),
  CO2_THRESHOLD_HIGH(Number.class, AggregationType.MAX, 900),

  // Manual control
  HRV_MANUAL_POWER(Number.class, AggregationType.MAX, Constants.MID_POWER),
  HRV_BOOST_POWER(Number.class, AggregationType.MAX, Constants.HIGH_POWER),
  HRV_GAS_POWER(Number.class, AggregationType.MAX, Constants.HIGH_POWER),
  HRV_SMOKE_POWER(Number.class, AggregationType.MAX, Constants.ZERO_POWER),
  HRV_HUMIDITY_POWER(Number.class, AggregationType.MAX, Constants.HIGH_POWER),
  HRV_CO2_POWER_LOW(Number.class, AggregationType.MAX, Constants.LOW_POWER),
  HRV_CO2_POWER_MID(Number.class, AggregationType.MAX, Constants.MID_POWER),
  HRV_CO2_POWER_HIGH(Number.class, AggregationType.MAX, Constants.HIGH_POWER),
  HRV_BASE_POWER(Number.class, AggregationType.MAX, Constants.MID_POWER),
  ;

  private final Class<?> dataType;
  private final AggregationType aggregationType;
  private final int defaultValue;

  InputType(Class<?> dataType, AggregationType aggregationType, int defaultValue) {
    this.dataType = dataType;
    this.aggregationType = aggregationType;
    this.defaultValue = defaultValue;
  }

  private static class Constants {
    public static final int ZERO_POWER = 0;
    public static final int LOW_POWER = 15;
    public static final int MID_POWER = 50;
    public static final int HIGH_POWER = 95;
  }
}
