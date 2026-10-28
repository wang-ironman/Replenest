import com.replenest.app.Forecast;

import java.time.LocalDate;

public final class ForecastTest {
    private static int assertions;

    public static void main(String[] args) {
        justUsedUp();
        leapMonth();
        futureAnchor();
        leadBoundary();
        rateCalibration();
        invalidNumbers();
        System.out.println("ForecastTest passed (" + assertions + " assertions)");
    }

    private static void justUsedUp() {
        LocalDate anchor = LocalDate.of(2026, 1, 1);
        equal(0.0, Forecast.remaining(3, 1, anchor, LocalDate.of(2026, 1, 4)));
        equal(0L, Forecast.daysLeft(0, 1));
        equal(LocalDate.of(2026, 1, 4), Forecast.dueDate(3, 1, anchor));
    }

    private static void leapMonth() {
        LocalDate anchor = LocalDate.of(2024, 2, 28);
        equal(1.0, Forecast.remaining(2, 1, anchor, LocalDate.of(2024, 2, 29)));
        equal(LocalDate.of(2024, 3, 1), Forecast.dueDate(2, 1, anchor));
    }

    private static void futureAnchor() {
        LocalDate anchor = LocalDate.of(2026, 6, 10);
        equal(5.0, Forecast.remaining(5, 1, anchor, LocalDate.of(2026, 6, 1)));
        equal(0L, Forecast.daysLeft(0, 2));
        equal(LocalDate.of(2026, 6, 10), Forecast.dueDate(0, 1, anchor));
    }

    private static void leadBoundary() {
        LocalDate anchor = LocalDate.of(2026, 1, 1);
        LocalDate due = LocalDate.of(2026, 1, 11);
        check(!Forecast.needsBuy(10, 1, anchor, due.minusDays(3).minusDays(1), 3));
        check(Forecast.needsBuy(10, 1, anchor, due.minusDays(3), 3));
        check(!Forecast.needsBuy(10, 1, anchor, due.minusDays(1), 0));
        check(Forecast.needsBuy(10, 1, anchor, due, 0));
    }

    private static void rateCalibration() {
        LocalDate anchor = LocalDate.of(2026, 1, 1);
        equal(1.06, Forecast.correctedRate(1, 10, 4, anchor, anchor.plusDays(5)));
        equal(1.0, Forecast.correctedRate(1, 10, 4, anchor, anchor.plusDays(2)));
        equal(1.0, Forecast.correctedRate(1, 10, 10, anchor, anchor.plusDays(5)));
        equal(1.0, Forecast.correctedRate(1, 10, 11, anchor, anchor.plusDays(5)));
    }

    private static void invalidNumbers() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        throwsIllegalArgument(() -> Forecast.remaining(Double.NaN, 1, day, day));
        throwsIllegalArgument(() -> Forecast.remaining(1, Double.POSITIVE_INFINITY, day, day));
        throwsIllegalArgument(() -> Forecast.daysLeft(-1, 1));
        throwsIllegalArgument(() -> Forecast.daysLeft(1, 0));
        throwsIllegalArgument(() -> Forecast.dueDate(Double.NEGATIVE_INFINITY, 1, day));
        throwsIllegalArgument(() -> Forecast.needsBuy(1, 1, day, day, -1));
        throwsIllegalArgument(() -> Forecast.correctedRate(1, 1, Double.NaN, day, day));
        throwsIllegalArgument(() -> Forecast.remaining(1, 1, null, day));
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void equal(long expected, long actual) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void equal(double expected, double actual) {
        assertions++;
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void equal(LocalDate expected, LocalDate actual) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void throwsIllegalArgument(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
