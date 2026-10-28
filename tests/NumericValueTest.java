import com.replenest.app.NumericValue;

public final class NumericValueTest {
    private static int assertions;

    public static void main(String[] args) {
        equal(12.5, NumericValue.parse(" 12.5 ", true, true, 100));
        equal(100, NumericValue.parse("100", false, false, 100));
        equal(0, NumericValue.parse("0", true, false, 1));
        equal(0.25, NumericValue.parse(".25", true, false, 1));

        rejects("", true, false, 10);
        rejects(".", true, false, 10);
        rejects("1.", true, false, 10);
        rejects("0", true, true, 10);
        rejects("-1", true, false, 10);
        rejects("2abc", true, false, 10);
        rejects("2毫升", true, false, 10);
        rejects("1.2.3", true, false, 10);
        rejects("1.5", false, false, 10);
        rejects("NaN", true, false, 10);
        rejects("Infinity", true, false, 10);
        rejects("1e3", true, false, 10000);
        rejects("10.01", true, false, 10);

        System.out.println("NumericValueTest passed: " + assertions + " assertions");
    }

    private static void equal(double expected, double actual) {
        assertions++;
        if (Double.compare(expected, actual) != 0)
            throw new AssertionError("expected " + expected + " but got " + actual);
    }

    private static void rejects(String value, boolean decimal, boolean positive, double max) {
        assertions++;
        try {
            NumericValue.parse(value, decimal, positive, max);
            throw new AssertionError("expected rejection for: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
