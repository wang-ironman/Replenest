package com.replenest.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public final class NumberInput extends LinearLayout {
    private static final int PURPLE = Color.rgb(91, 80, 207);
    private static final int LILAC = Color.rgb(238, 235, 255);
    private static final int INK = Color.rgb(42, 39, 62);

    public final EditText input;
    private final Activity activity;
    private final String label;
    private final boolean decimal;
    private final boolean positive;
    private final double maximum;
    private final double[] presets;

    public NumberInput(Activity context, String label, String initial, boolean decimal,
                       boolean positive, double maximum, double[] presets) {
        super(context);
        this.activity = context;
        this.label = label;
        this.decimal = decimal;
        this.positive = positive;
        this.maximum = maximum;
        this.presets = presets == null ? new double[0] : presets.clone();
        setOrientation(VERTICAL);

        TextView caption = new TextView(context);
        caption.setText(label);
        caption.setTextSize(14);
        caption.setTextColor(INK);
        caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        caption.setContentDescription(label);
        addView(caption, new LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowParams = new LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        addView(row, rowParams);

        TextView minus = action("−", "减少" + label);
        row.addView(minus, new LayoutParams(dp(48), dp(48)));

        input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setGravity(Gravity.CENTER);
        input.setMinHeight(dp(48));
        input.setPadding(dp(6), dp(4), dp(6), dp(4));
        input.setBackground(rounded(LILAC, 12));
        input.setContentDescription(label);
        input.setInputType(EditorInfo.TYPE_CLASS_NUMBER
            | (decimal ? EditorInfo.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(initial == null ? "" : initial);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        inputParams.leftMargin = dp(4);
        inputParams.rightMargin = dp(4);
        row.addView(input, inputParams);
        input.setTag(this);
        input.setFilters(new InputFilter[] { numericFilter() });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    value();
                    input.setError(null);
                } catch (IllegalArgumentException ignored) {}
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                try {
                    value();
                    input.setError(null);
                } catch (IllegalArgumentException e) {
                    input.setError(e.getMessage());
                }
            }
        });

        TextView plus = action("＋", "增加" + label);
        LinearLayout.LayoutParams sideParams = new LayoutParams(dp(48), dp(48));
        sideParams.leftMargin = dp(4);
        row.addView(plus, sideParams);

        TextView choose = action("⌄", "选择" + label);
        choose.setContentDescription("选择" + label);
        LinearLayout.LayoutParams chooseParams = new LayoutParams(dp(48), dp(48));
        chooseParams.leftMargin = dp(4);
        row.addView(choose, chooseParams);

        minus.setOnClickListener(v -> step(-1));
        plus.setOnClickListener(v -> step(1));
        choose.setOnClickListener(v -> showOptions(choose));
    }

    public double value() {
        return NumericValue.parse(input.getText().toString(), decimal, positive, maximum);
    }

    private InputFilter numericFilter() {
        return (source, start, end, dest, dstart, dend) -> {
            String candidate = dest.subSequence(0, dstart).toString()
                + source.subSequence(start, end)
                + dest.subSequence(dend, dest.length());
            String pattern = decimal ? "[0-9]*(\\.[0-9]*)?" : "[0-9]*";
            if (candidate.matches(pattern)) return null;
            input.setError("请输入数字，单位无需填写");
            return dest.subSequence(dstart, dend);
        };
    }

    private void step(int direction) {
        BigDecimal current;
        try {
            value();
            current = new BigDecimal(input.getText().toString().trim());
        } catch (IllegalArgumentException e) {
            current = direction > 0 ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        BigDecimal next = current.add(BigDecimal.valueOf(direction));
        String candidate = next.stripTrailingZeros().toPlainString();
        try {
            NumericValue.parse(candidate, decimal, positive, maximum);
            input.setText(candidate);
            input.setSelection(candidate.length());
            input.setError(null);
        } catch (IllegalArgumentException e) {
            input.setError(e.getMessage());
        }
    }

    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        ArrayList<String> options = new ArrayList<>();
        int itemId = 1;
        for (double preset : presets) {
            if (options.size() >= 8) break;
            String option = format(preset);
            try {
                NumericValue.parse(option, decimal, positive, maximum);
                if (!options.contains(option)) {
                    options.add(option);
                    menu.getMenu().add(0, itemId++, options.size() - 1, option);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        menu.getMenu().add(0, 10001, options.size(), "滑动选择…");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10001) {
                showWheel();
            } else {
                String option = item.getTitle().toString();
                input.setText(option);
                input.setSelection(option.length());
                input.setError(null);
            }
            return true;
        });
        menu.show();
    }

    private void showWheel() {
        LinearLayout wheels = new LinearLayout(activity);
        wheels.setOrientation(HORIZONTAL);
        wheels.setGravity(Gravity.CENTER);
        wheels.setPadding(dp(16), dp(8), dp(16), dp(8));

        NumberPicker whole = new NumberPicker(activity);
        whole.setMinValue(0);
        whole.setMaxValue((int) Math.min(10000, Math.floor(Math.max(0, maximum))));
        wheels.addView(whole, new LayoutParams(0, -2, 1));
        NumberPicker fraction = null;
        if (decimal) {
            fraction = new NumberPicker(activity);
            fraction.setMinValue(0);
            fraction.setMaxValue(9);
            fraction.setFormatter(v -> "." + v);
            wheels.addView(fraction, new LayoutParams(0, -2, 1));
        }

        try {
            double current = value();
            int integerPart = (int) Math.floor(current);
            whole.setValue(Math.min(whole.getMaxValue(), integerPart));
            if (fraction != null) {
                int tenth = (int) Math.round((current - integerPart) * 10);
                fraction.setValue(Math.min(9, tenth));
            }
        } catch (IllegalArgumentException ignored) {
            whole.setValue(positive ? Math.min(1, whole.getMaxValue()) : 0);
            if (fraction != null) fraction.setValue(0);
        }

        NumberPicker decimalWheel = fraction;
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("滑动选择" + label)
            .setView(wheels)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                wheels.clearFocus();
                String candidate = Integer.toString(whole.getValue());
                if (decimalWheel != null) candidate += "." + decimalWheel.getValue();
                try {
                    NumericValue.parse(candidate, decimal, positive, maximum);
                    input.setText(candidate);
                    input.setSelection(candidate.length());
                    input.setError(null);
                    dialog.dismiss();
                } catch (IllegalArgumentException e) {
                    input.setError(e.getMessage());
                    android.widget.Toast.makeText(activity,e.getMessage(),android.widget.Toast.LENGTH_SHORT).show();
                }
            }));
        dialog.show();
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private TextView action(String text, String description) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(20);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(PURPLE);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(48));
        view.setMinHeight(dp(48));
        view.setBackground(rounded(LILAC, 12));
        view.setContentDescription(description);
        view.setFocusable(true);
        view.setClickable(true);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
