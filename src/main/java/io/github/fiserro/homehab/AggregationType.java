package io.github.fiserro.homehab;

/**
 * General-purpose aggregation type for combining multiple values.
 * Can be used for HRV and other systems in homeHAB.
 */
public enum AggregationType {
    MIN {
        @Override
        public Number aggregate(Number v1, Number v2) {
            return Math.min(v1.doubleValue(), v2.doubleValue());
        }
    },
    MAX {
        @Override
        public Number aggregate(Number v1, Number v2) {
            return Math.max(v1.doubleValue(), v2.doubleValue());
        }
    },
    SUM {
        @Override
        public Number aggregate(Number v1, Number v2) {
            return v1.doubleValue() + v2.doubleValue();
        }
    },
    COUNT {
        @Override
        public Number aggregate(Number v1, Number v2) {
            // Pro COUNT agregaci vrací počet hodnot
            // V1 obsahuje aktuální count, v2 obsahuje 1 (nová hodnota)
            return v1.intValue() + v2.intValue();
        }
    };

    public abstract Number aggregate(Number v1, Number v2);

    public Boolean aggregate(Boolean v1, Boolean v2) {
        if (this == MAX) return v1 || v2;  // OR - true pokud alespoň jedna true
        if (this == MIN) return v1 && v2;  // AND - true pokud obě true
        throw new UnsupportedOperationException(this.name() + " not supported for Boolean");
    }
}