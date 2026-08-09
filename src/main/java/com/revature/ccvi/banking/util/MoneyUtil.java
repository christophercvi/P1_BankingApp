package com.revature.ccvi.banking.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Currency helpers. Every monetary value entering or leaving the application passes through
 * {@link #normalize(BigDecimal)} so scale stays at two and comparisons behave predictably.
 */
public final class MoneyUtil {

    public static final int SCALE = 2;

    private static final DecimalFormat DISPLAY = new DecimalFormat("#,##0.00");

    private MoneyUtil() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean hasValidScale(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= SCALE;
    }

    public static String format(BigDecimal value) {
        return value == null ? "$0.00" : "$" + DISPLAY.format(normalize(value));
    }
}
