package com.replenest.app;

import java.util.regex.Pattern;

public final class NumericValue {
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern DECIMAL = Pattern.compile("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");

    private NumericValue() {}

    public static double parse(String text, boolean decimal, boolean positive, double max) {
        String value = text == null ? "" : text.trim();
        Pattern format = decimal ? DECIMAL : INTEGER;
        if (!format.matcher(value).matches())
            throw new IllegalArgumentException("请输入有效数字");

        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请输入有效数字");
        }
        if (!Double.isFinite(number))
            throw new IllegalArgumentException("请输入有限数字");
        if (number > max)
            throw new IllegalArgumentException("数值不能超过 " + max);
        if (positive && number <= 0)
            throw new IllegalArgumentException("数值必须大于 0");
        return number;
    }
}
