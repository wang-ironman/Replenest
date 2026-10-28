package com.replenest.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.app.UiAutomation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class NumericInputChecks {
    private final Instrumentation test;
    private UiAutomation automation;
    private MainActivity activity;
    private int checks;
    private final String ownName = "数字输入检查-" + System.currentTimeMillis();

    public NumericInputChecks(Instrumentation test) { this.test = test; }

    public void run() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_CANCELED;
        try {
            automation = test.getUiAutomation();
            activity = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            int originalCount = itemCount();
            AtomicReference<AlertDialog> editorRef = new AtomicReference<>();
            AtomicReference<EditText> quantityRef = new AtomicReference<>();
            AtomicReference<EditText> leadRef = new AtomicReference<>();
            AtomicReference<EditText> nameRef = new AtomicReference<>();
            main(() -> {
                activity.editor(activity.template(6), true);
                AlertDialog editor = activity.activeDialog;
                editorRef.set(editor);
                quantityRef.set(editText(editor.getWindow().getDecorView(), "现有数量"));
                leadRef.set(editTextStarting(editor.getWindow().getDecorView(), "提前提醒天数"));
                nameRef.set(editText(editor.getWindow().getDecorView(), "用品名称"));
            });
            AlertDialog editor = editorRef.get();
            EditText quantity = quantityRef.get();
            EditText lead = leadRef.get();
            EditText name = nameRef.get();
            check(editor != null && showing(editor), "The item editor should open");
            check(quantity != null, "The current quantity field should be present");
            check(lead != null, "The lead days field should be present");
            check(name != null, "The item name field should be present");

            String beforeStep = text(quantity);
            main(() -> click(editor.getWindow().getDecorView(), "增加现有数量"));
            check(!beforeStep.equals(text(quantity)), "The plus button should change the quantity");
            main(() -> click(editor.getWindow().getDecorView(), "减少现有数量"));
            check(beforeStep.equals(text(quantity)), "The minus button should reverse the plus step");

            for (String invalid : new String[] {"2abc", "2瓶", "1.2.3"}) {
                main(() -> replaceAll(quantity, invalid));
                check(beforeStep.equals(text(quantity)), "Invalid paste must preserve quantity: " + invalid);
            }

            main(() -> quantity.setText("0.5"));
            check("0.5".equals(text(quantity)), "A valid decimal quantity should be accepted");
            check(Math.abs(numberValue(quantity) - 0.5) < 1e-9,
                "The quantity value accessor should parse a valid decimal");

            String leadBefore = text(lead);
            main(() -> replaceAll(lead, "1.5"));
            check(leadBefore.equals(text(lead)), "Integer lead days must reject decimal paste");

            main(() -> quantity.setText("1"));
            main(() -> click(editor.getWindow().getDecorView(), "选择现有数量"));
            check(clickAccessibilityText("0.5"), "The 0.5 preset should be selectable from the popup");
            test.waitForIdleSync();
            check("0.5".equals(text(quantity)), "Selecting the 0.5 preset should fill the field");

            main(() -> click(editor.getWindow().getDecorView(), "选择现有数量"));
            check(clickAccessibilityText("滑动选择…"), "The popup should open the sliding selector");
            check(waitForAccessibilityText("滑动选择现有数量", 2500), "The sliding selector dialog should appear");
            boolean wheelScrolled = scrollNumberPickerForward();
            if (wheelScrolled) {
                try { automation.waitForIdle(300, 3000); }
                catch (java.util.concurrent.TimeoutException e) { SystemClock.sleep(500); }
                test.waitForIdleSync();
            }
            check(clickAccessibilityText("确定"), "The sliding selector should expose a confirm action");
            test.waitForIdleSync();
            double selected = numberValue(quantity);
            if (wheelScrolled) check(Math.abs(selected - 1.5) < 1e-9,
                "Scrolling the integer picker should update the selected value (selected=" + selected + ")");
            else check(Math.abs(selected - 0.5) < 1e-9,
                "Confirming the sliding selector should fill its selected value");

            main(() -> {
                NumberInput positive = new NumberInput(activity, "正数校验", "0", true, true, 10, new double[0]);
                assertValueRejected(positive, "Zero should be rejected when a positive value is required");
                positive.input.setText("1");
                assertValueRejected(new NumberInput(activity, "上限校验", "11", true, false, 10, new double[0]),
                    "A value above the configured maximum should be rejected");
            });

            main(() -> {
                quantity.setText("");
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            test.waitForIdleSync();
            check(itemCount() == originalCount, "Saving an empty quantity must not create an item");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            test.waitForIdleSync();
            check(showing(editor), "The editor should remain open after dismissing the validation message");
            check(text(quantity).isEmpty(), "The invalid quantity should remain visible in the editor");

            main(() -> {
                name.setText(ownName);
                quantity.setText("0.5");
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            check(itemCount() == originalCount + 1, "A valid item should save successfully");
            AtomicReference<Item> savedRef = new AtomicReference<>();
            main(() -> {
                for (Item item : activity.store.items) if (ownName.equals(item.name)) savedRef.set(item);
            });
            Item saved = savedRef.get();
            check(saved != null, "The uniquely named test item should be saved");
            check(Math.abs(saved.stock - 0.5) < 1e-9, "The saved item should retain the entered decimal quantity");

            result.putString("success", "Numeric input UI checks passed");
            result.putInt("checks", checks);
            resultCode = Activity.RESULT_OK;
        } catch (Throwable e) {
            result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage() + uiDiagnostic());
            result.putInt("checks", checks);
        } finally {
            cleanup();
        }
        test.finish(resultCode, result);
    }

    private void cleanup() {
        if (activity == null) return;
        try {
            main(() -> {
                boolean removed = false;
                for (Iterator<Item> it = activity.store.items.iterator(); it.hasNext();) {
                    if (ownName.equals(it.next().name)) { it.remove(); removed = true; }
                }
                if (removed) activity.persist();
                if (activity.activeDialog != null && activity.activeDialog.isShowing()) activity.activeDialog.dismiss();
                activity.finish();
            });
        } catch (Throwable ignored) {}
    }

    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        test.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable e) { failure.set(e); }
        });
        test.waitForIdleSync();
        if (failure.get() != null) throw new AssertionError(failure.get().getMessage(), failure.get());
    }

    private void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }

    private int itemCount() {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        main(() -> count.set(activity.store.items.size()));
        return count.get();
    }

    private String text(EditText field) {
        AtomicReference<String> text = new AtomicReference<>("");
        main(() -> text.set(field.getText().toString()));
        return text.get();
    }

    private double numberValue(EditText field) {
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        main(() -> value.set(((NumberInput) field.getTag()).value()));
        return value.get();
    }

    private boolean showing(AlertDialog dialog) {
        AtomicReference<Boolean> showing = new AtomicReference<>(false);
        main(() -> showing.set(dialog.isShowing()));
        return showing.get();
    }

    private void assertValueRejected(NumberInput field, String message) {
        checks++;
        try { field.value(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private EditText editText(View root, String description) {
        if (root instanceof EditText && description.contentEquals(root.getContentDescription())) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int n = 0; n < group.getChildCount(); n++) {
                EditText found = editText(group.getChildAt(n), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText editTextStarting(View root, String descriptionPrefix) {
        if (root instanceof EditText && root.getContentDescription() != null
            && root.getContentDescription().toString().startsWith(descriptionPrefix)) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int n = 0; n < group.getChildCount(); n++) {
                EditText found = editTextStarting(group.getChildAt(n), descriptionPrefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void click(View root, String description) {
        View target = find(root, description);
        if (target == null) throw new AssertionError("Missing control: " + description);
        target.performClick();
    }

    private View find(View root, String description) {
        if (root.getContentDescription() != null && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int n = 0; n < group.getChildCount(); n++) {
                View found = find(group.getChildAt(n), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void replaceAll(EditText field, String value) {
        field.getText().replace(0, field.getText().length(), value);
    }

    private boolean clickAccessibilityText(String text) {
        long deadline = SystemClock.uptimeMillis() + 2500;
        while (SystemClock.uptimeMillis() < deadline) {
            for (AccessibilityNodeInfo root : windowRoots()) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    for (AccessibilityNodeInfo target = node; target != null; target = target.getParent()) {
                        if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                    }
                }
            }
            SystemClock.sleep(100);
        }
        return false;
    }

    private boolean waitForAccessibilityText(String text, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            for (AccessibilityNodeInfo root : windowRoots())
                if (!root.findAccessibilityNodeInfosByText(text).isEmpty()) return true;
            SystemClock.sleep(100);
        }
        return false;
    }

    private List<AccessibilityNodeInfo> windowRoots() {
        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
        if (automation == null) return roots;
        AccessibilityNodeInfo active = automation.getRootInActiveWindow();
        if (active != null) roots.add(active);
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) roots.add(root);
        }
        return roots;
    }

    private boolean scrollNumberPickerForward() {
        for (AccessibilityNodeInfo root : windowRoots())
            if (scrollNumberPickerForward(root)) return true;
        return false;
    }

    private boolean scrollNumberPickerForward(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (NumberPicker.class.getName().contentEquals(node.getClassName()))
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        for (int n = 0; n < node.getChildCount(); n++) {
            if (scrollNumberPickerForward(node.getChild(n))) return true;
        }
        return false;
    }

    private String uiDiagnostic() {
        if (automation == null) return "; UI diagnostics unavailable";
        StringBuilder out = new StringBuilder("; windows[");
        int windows = 0;
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if (windows >= 3) break;
            if (windows++ > 0) out.append(" | ");
            CharSequence title = window.getTitle();
            out.append("title=").append(isKnownUiText(title) ? title : "<other>");
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) appendKnownTexts(root, out, new int[] {0});
        }
        return out.append(']').toString();
    }

    private void appendKnownTexts(AccessibilityNodeInfo node, StringBuilder out, int[] count) {
        if (node == null || count[0] >= 6) return;
        CharSequence text = node.getText();
        if (isKnownUiText(text)) {
            out.append(", text=").append(text);
            count[0]++;
        }
        for (int n = 0; n < node.getChildCount() && count[0] < 6; n++)
            appendKnownTexts(node.getChild(n), out, count);
    }

    private boolean isKnownUiText(CharSequence text) {
        if (text == null) return false;
        String value = text.toString();
        return value.equals("0.5") || value.equals("滑动选择…") || value.equals("现有数量")
            || value.equals("滑动选择现有数量") || value.equals("确定") || value.equals("取消")
            || value.equals("选择现有数量") || value.equals("登记常用品");
    }
}
