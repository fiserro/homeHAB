package io.github.fiserro.homehab;

import java.util.Collection;
import java.util.Comparator;

/**
 * General-purpose aggregation type for combining multiple values.
 * Can be used for HRV and other systems in homeHAB.
 */
public enum AggregationType {
    MIN {
        @Override
        public Number aggregate(Collection<Number> numbers) {
            return min(numbers);
        }
    },
    MAX {
        @Override
        public Number aggregate(Collection<Number> numbers) {
            return max(numbers);
        }
    },
    SUM {
        @Override
        public Number aggregate(Collection<Number> numbers) {
            return sum(numbers);
        }
    },
    COUNT {
        @Override
        public Number aggregate(Collection<Number> numbers) {
            return count(numbers);
        }
    },
    AVG {
        @Override
        public Number aggregate(Collection<Number> numbers) {
            return avg(numbers);
        }
    }
    ;

    public abstract Number aggregate(Collection<Number> numbers);

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
}