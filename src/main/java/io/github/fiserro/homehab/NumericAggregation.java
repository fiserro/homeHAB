package io.github.fiserro.homehab;

import java.util.Collection;
import java.util.Comparator;
import lombok.val;

/**
 * General-purpose aggregation type for combining multiple values. Can be used for HRV and other
 * systems in homeHAB.
 */
public enum NumericAggregation {
  NONE,
  MIN,
  MAX,
  SUM,
  COUNT,
  AVG;

  private static Number min(Collection<Number> numbers) {
    return numbers.stream().min(Comparator.comparingDouble(Number::doubleValue)).orElse(null);
  }

  private static Number max(Collection<Number> numbers) {
    return numbers.stream().max(Comparator.comparingDouble(Number::doubleValue)).orElse(null);
  }

  private static Number sum(Collection<Number> numbers) {
    return numbers.stream().mapToDouble(Number::doubleValue).sum();
  }

  private static Number count(Collection<Number> numbers) {
    return numbers.size();
  }

  private static Number avg(Collection<Number> numbers) {
    return sum(numbers).doubleValue() / numbers.size();
  }

  public Number aggregate(Collection<Object> values) {
    val numbers = values.stream()
        .map(v -> switch (v) {
          case Number n -> n;
          case Boolean b -> b ? 1 : 0;
          default -> null;
        })
        .toList();
    return switch (this) {
      case NONE -> null;
      case MIN -> min(numbers);
      case MAX -> max(numbers);
      case SUM -> sum(numbers);
      case COUNT -> count(numbers);
      case AVG -> avg(numbers);
    };
  }
}
