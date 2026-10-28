package com.replenest.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class Forecast {
    private Forecast() {}

    public static double remaining(double stock, double dailyRate, LocalDate anchor, LocalDate today) {
        requireNonNegative(stock, "stock");
        requirePositive(dailyRate, "dailyRate");
        requireDates(anchor, today);
        long elapsedDays = Math.max(0L, ChronoUnit.DAYS.between(anchor, today));
        return Math.max(0.0, stock - dailyRate * elapsedDays);
    }

    public static long daysLeft(double remaining, double dailyRate) {
        requireNonNegative(remaining, "remaining");
        requirePositive(dailyRate, "dailyRate");
        return ceilDays(remaining / dailyRate);
    }

    public static LocalDate dueDate(double stock, double dailyRate, LocalDate anchor) {
        requireNonNegative(stock, "stock");
        requirePositive(dailyRate, "dailyRate");
        requireDate(anchor, "anchor");
        return anchor.plusDays(ceilDays(stock / dailyRate));
    }

    public static boolean needsBuy(
            double stock, double dailyRate, LocalDate anchor, LocalDate today, int leadDays) {
        requireNonNegative(stock, "stock");
        requirePositive(dailyRate, "dailyRate");
        requireDates(anchor, today);
        if (leadDays < 0) {
            throw new IllegalArgumentException("leadDays must be non-negative");
        }
        return !today.isBefore(dueDate(stock, dailyRate, anchor).minusDays(leadDays));
    }

    public static double correctedRate(
            double oldRate,
            double oldStock,
            double actualRemaining,
            LocalDate anchor,
            LocalDate today) {
        requirePositive(oldRate, "oldRate");
        requireNonNegative(oldStock, "oldStock");
        requireNonNegative(actualRemaining, "actualRemaining");
        requireDates(anchor, today);
        long elapsedDays = ChronoUnit.DAYS.between(anchor, today);
        if (elapsedDays < 3 || oldStock <= actualRemaining) {
            return oldRate;
        }
        double observedRate = (oldStock - actualRemaining) / elapsedDays;
        return 0.7 * oldRate + 0.3 * observedRate;
    }

    private static long ceilDays(double days) {
        if (!Double.isFinite(days) || days > Long.MAX_VALUE) {
            throw new IllegalArgumentException("day count is out of range");
        }
        return (long) Math.ceil(days);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireDate(LocalDate date, String name) {
        if (date == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static void requireDates(LocalDate first, LocalDate second) {
        requireDate(first, "anchor");
        requireDate(second, "today");
    }
}
