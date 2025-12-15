package io.github.fiserro.homehab;

import java.util.function.Function;

public interface Calculator extends Function<HabState, HabState> {
  HabState calculate(HabState state);

  @Override
  default HabState apply(HabState state) {
    return calculate(state);
  }
}
