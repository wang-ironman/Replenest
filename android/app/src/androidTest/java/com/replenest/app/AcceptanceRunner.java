package com.replenest.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AcceptanceRunner extends Instrumentation {
    private final AtomicBoolean workerStarted = new AtomicBoolean();
    private int checks;
    private MainActivity activity;
    private Context target;
    private boolean apiSuite;
    private boolean toggleSuite;
    private boolean numericSuite;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        apiSuite = arguments != null && "api".equals(arguments.getString("suite"));
        toggleSuite = arguments != null && "toggle".equals(arguments.getString("suite"));
        numericSuite = arguments != null && "numeric".equals(arguments.getString("suite"));
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!workerStarted.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            if (apiSuite) new ApiContractRunner(this).run();
            else if (toggleSuite) new AiToggleChecks(this).run();
            else if (numericSuite) new NumericInputChecks(this).run();
            else runAcceptance();
        }, "replenest-acceptance");
        worker.start();
    }

    private void runAcceptance() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;
        try {
            target = getTargetContext();
            resetTestPreferences();
            verifyStoreRoundTrip();
            startBlankActivity();
            check(activity.store.items.isEmpty(), "Activity did not start with an empty household");
            addAndCheckConsumable();
            verifyInvalidItemsPreserveSavedData();
            markOrderedAndRefill();
            correctAndRestart();
            addAndReplaceCycleItem();
            verifyPackageCountsAndLayout();
            verifyAiSettingsAndExport();
            verifyJsonParsing();
            clearAiSettings();
            prepareDemoScreen();
            result.putString("success", "Acceptance checks passed");
        } catch (Throwable failure) {
            resultCode = Activity.RESULT_CANCELED;
            result.putString("failure", safeFailure(failure));
        }
        result.putInt("checks", checks);
        finish(resultCode, result);
    }

    private void resetTestPreferences() throws Exception {
        check(target.getSharedPreferences("household", Context.MODE_PRIVATE)
                .edit().clear().commit(), "Could not clear test household data");
        target.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().clear().commit();
        AiSettings.clear(target);
    }

    private void verifyStoreRoundTrip() throws Exception {
        Store store = new Store(target);
        check(store.items.isEmpty(), "Household store should start empty");
        store.save();
        check(new Store(target).items.isEmpty(), "Empty household store did not reload");
    }

    private void startBlankActivity() {
        Intent intent = new Intent(getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (MainActivity) startActivitySync(intent);
        waitForIdleSync();
    }

    private void addAndCheckConsumable() throws Exception {
        onMain(() -> {
            activity.editor(activity.template(0), true);
            setField("现有数量", "0.2");
            setField("一份预计用几天", "15");
            clickDialogPositive();
        });
        waitForIdleSync();
        Item item = activity.store.items.get(0);
        equal(1, activity.store.items.size(), "Editor should save one item");
        near(6.0, item.stock, "Editor should preserve current quantity");
        near(2.0, item.dailyRate, "Daily use should be pack size divided by days per pack");
        LocalDate today = LocalDate.now();
        check(item.due().equals(today.plusDays(3)), "Due date should be three days away");
        check(item.needsBuy(today), "Lead time should make the item due for purchase today");

        onMain(() -> {
            activity.tab = 2;
            activity.render();
        });
        waitForIdleSync();
        check(activity.tab == 2, "Shopping tab should be selected");
        check(viewContainsText(activity.root, item.name), "Shopping list should show the due item");
    }

    private void verifyInvalidItemsPreserveSavedData() throws Exception {
        Store store = new Store(target);
        String savedBefore = target.getSharedPreferences("household", Context.MODE_PRIVATE)
                .getString("data", "");

        JSONObject invalidStock = store.json();
        invalidStock.getJSONArray("items").getJSONObject(0).put("stock", -1);
        expectIllegalArgument(() -> store.replace(invalidStock), "Negative stock should be rejected");
        check(savedBefore.equals(target.getSharedPreferences("household", Context.MODE_PRIVATE)
                .getString("data", "")), "Invalid stock changed saved household data");

        JSONObject duplicateIds = store.json();
        JSONArray rows = duplicateIds.getJSONArray("items");
        rows.put(new JSONObject(rows.getJSONObject(0).toString()));
        expectIllegalArgument(() -> store.replace(duplicateIds), "Duplicate IDs should be rejected");
        check(savedBefore.equals(target.getSharedPreferences("household", Context.MODE_PRIVATE)
                .getString("data", "")), "Duplicate IDs changed saved household data");
        check(new Store(target).items.size() == 1, "Valid item should remain after invalid replacements");
    }

    private void markOrderedAndRefill() throws Exception {
        Item item = activity.store.items.get(0);
        double before = item.remaining(LocalDate.now());
        onMain(() -> clickText(activity.root, "我已下单"));
        waitForIdleSync();
        check(item.ordered, "Order action should set ordered state");
        near(before, new Store(target).items.get(0).remaining(LocalDate.now()),
                "Marking ordered must not add stock");

        onMain(() -> {
            activity.refill(item);
            setField("新增数量（盒，支持小数）", "1");
            clickDialogPositive();
        });
        waitForIdleSync();
        Item saved = new Store(target).items.get(0);
        near(before + item.packSize, saved.stock, "Refill should add one package to current remainder");
        check(!saved.ordered, "Refill should clear ordered state");
    }

    private void correctAndRestart() throws Exception {
        Item item = activity.store.items.get(0);
        onMain(() -> {
            activity.correct(item);
            setField("实际剩余数量（盒）", Double.toString(1.0 / 3.0));
            clickDialogPositive();
        });
        waitForIdleSync();
        near(10, new Store(target).items.get(0).stock, "Correction should save actual quantity");

        onMain(activity::finish);
        waitForIdleSync();
        startBlankActivity();
        near(10, activity.store.items.get(0).stock, "Corrected quantity should persist across restart");
    }

    private void addAndReplaceCycleItem() throws Exception {
        onMain(() -> {
            activity.editor(activity.template(5), true);
            setField("用品名称", "测试净水器滤芯");
            clickDialogPositive();
        });
        waitForIdleSync();
        Item cycle = activity.store.items.get(1);
        check(cycle.cycle, "Template 5 should be a cycle item");
        near(180, cycle.stock, "Cycle item should start with the remaining interval");
        onMain(() -> {
            activity.refill(cycle);
            clickDialogPositive();
        });
        waitForIdleSync();
        Item savedCycle = new Store(target).items.get(1);
        near(180, savedCycle.stock, "Cycle replacement should restart the full interval");
        check(savedCycle.anchor.equals(LocalDate.now()), "Cycle replacement should reset its anchor date");
    }

    private void verifyPackageCountsAndLayout() throws Exception {
        Item existing = activity.store.items.get(0);
        JSONObject legacy = existing.json(); legacy.remove("packageUnit");
        Item restored = Item.from(legacy);
        near(existing.stock, restored.stock, "Old backup quantities must not be multiplied again");
        check("包".equals(restored.packageUnit), "Old backup should have a safe package-unit default");
        onMain(() -> {
            activity.editor(activity.template(1), true);
            check("60".contentEquals(findEditText(activity.activeDialog.getWindow().getDecorView(), "一份预计用几天").getText()), "Usage period should not expose floating-point artifacts");
            setField("现有数量", "2");
            check(viewContainsText(activity.activeDialog.getWindow().getDecorView(), "合计 4000毫升"), "Editor should calculate total from package count");
            clickDialogPositive();
        });
        Item bottles = activity.store.items.get(2);
        near(4000, bottles.stock, "Two 2000ml bottles should save 4000ml");
        near(2, bottles.packageCount(LocalDate.now()), "Saved bottle count should remain two");
        check("瓶".equals(new Store(target).items.get(2).packageUnit), "Package unit should persist");
        final Item editingBottles = bottles;
        onMain(() -> { activity.editor(editingBottles,false); clickDialogPositive(); });
        bottles = activity.store.items.get(2);
        near(4000,bottles.stock,"Editing without changes must not multiply total stock again");
        final Item refillBottles = bottles;
        onMain(() -> { activity.refill(refillBottles); setField("新增数量（瓶，支持小数）","0.5"); clickDialogPositive(); });
        near(5000,new Store(target).items.get(2).stock,"Half a bottle refill should add 1000ml");
        onMain(() -> { activity.correct(refillBottles); setField("实际剩余数量（瓶）","1.5"); clickDialogPositive(); });
        near(3000,new Store(target).items.get(2).stock,"Correcting to 1.5 bottles should save 3000ml");
        Item plain = activity.template(6); plain.name = "无规格用品";
        onMain(() -> {
            activity.editor(plain,true);
            setField("现有数量","2"); setField("一份预计用几天","60"); clickDialogPositive();
        });
        Item withoutSpec = new Store(target).items.get(3);
        near(2,withoutSpec.stock,"Optional specification should not be required for a quantity");
        near(120,withoutSpec.daysLeft(LocalDate.now()),"Two portions lasting 60 days each should predict 120 days without measurement units");
        check("份".equals(withoutSpec.packageUnit) && "份".equals(withoutSpec.unit),"Custom item should have a usable default unit");
        onMain(() -> {
            activity.tab=0; activity.render();
            check(!viewContainsText(activity.root,"查看采购清单  →"),"Home procurement promotional card should be removed");
            check(!viewContainsText(activity.root,"预测有依据，也允许不准"),"Home footer explanation card should be removed");
            activity.tab=2; activity.render();
            check(!viewContainsText(activity.root,"刚好够用，不多囤一点"),"Shopping promotional card should be removed");
        });
    }

    private void verifyAiSettingsAndExport() throws Exception {
        expectIllegalState(() -> AiClient.suggest(target, "咖啡胶囊"),
                "Disabled AI suggestions should fail without network access");
        AiSettings.save(target, true, "https://example.com/v1", "demo", "fake-test-secret");
        check(AiSettings.enabled(target), "AI should be enabled after saving settings");
        check("fake-test-secret".equals(AiSettings.key(target)), "Encrypted key should decrypt correctly");
        String keyPreference = target.getSharedPreferences("replenest_ai_settings", Context.MODE_PRIVATE)
                .getString("encrypted_key", "");
        check(!keyPreference.contains("fake-test-secret"), "SharedPreferences must not contain plaintext API key");

        String priorEndpoint = AiSettings.endpoint(target);
        expectException(() -> AiSettings.save(
                target, true, "https://other.example/v1", "demo", ""),
                "Changing endpoint without a new key should be rejected");
        check(priorEndpoint.equals(AiSettings.endpoint(target)), "Rejected endpoint change should preserve settings");
        check("fake-test-secret".equals(AiSettings.key(target)), "Rejected endpoint change should preserve prior key");

        String exported = new Store(target).json().toString();
        check(!exported.contains("fake-test-secret"), "Household export payload must exclude API key");
    }

    private void verifyJsonParsing() throws Exception {
        JSONObject valid = new JSONObject()
                .put("name", "咖啡胶囊").put("brand", "测试品牌").put("kind", "consumable")
                .put("unit", "粒").put("packSize", 30).put("stock", 12)
                .put("daysPerPack", 15).put("leadDays", 3).put("explanation", "按描述估算")
                .put("question", "");
        JSONObject parsed = (JSONObject) invokePrivate(AiClient.class, "parseContent",
                new Class<?>[]{String.class}, "```json\n" + valid + "\n```");
        Object checked = invokePrivate(AiClient.class, "validateSuggestion",
                new Class<?>[]{JSONObject.class}, parsed);
        check(checked instanceof JSONObject, "Valid suggestion should parse and validate");

        JSONObject invalid = new JSONObject(valid.toString()).put("stock", -1);
        JSONObject parsedInvalid = (JSONObject) invokePrivate(AiClient.class, "parseContent",
                new Class<?>[]{String.class}, invalid.toString());
        expectInvocationFailure(() -> invokePrivate(AiClient.class, "validateSuggestion",
                new Class<?>[]{JSONObject.class}, parsedInvalid), "Negative suggested stock should be rejected");

        JSONObject question = new JSONObject().put("question", "現在還剩多少？");
        Object questionResult = invokePrivate(AiClient.class, "validateSuggestion",
                new Class<?>[]{JSONObject.class}, question);
        check(questionResult instanceof JSONObject, "Clarification response should not require inventory fields");

        JSONArray standard = new JSONArray()
                .put(new JSONObject().put("id", "m1"))
                .put(new JSONObject().put("id", "m2"))
                .put(new JSONObject().put("id", "m1"));
        String[] standardIds = (String[]) invokePrivate(AiClient.class, "parseModelIds",
                new Class<?>[]{JSONObject.class}, new JSONObject().put("data", standard));
        check(standardIds.length == 2 && "m1".equals(standardIds[0]) && "m2".equals(standardIds[1]),
                "Standard model parser should preserve order and remove duplicates");

        JSONObject aliyunData = new JSONObject().put("models", new JSONArray()
                .put(new JSONObject().put("model", "qwen-plus"))
                .put(new JSONObject().put("model", "qwen-turbo")));
        String[] aliyunIds = (String[]) invokePrivate(AiClient.class, "parseModelIds",
                new Class<?>[]{JSONObject.class}, new JSONObject().put("data", aliyunData));
        check(aliyunIds.length == 2 && "qwen-plus".equals(aliyunIds[0]),
                "Aliyun nested model parser should read model names");

        JSONObject output = new JSONObject()
                .put("total", 101).put("page_no", 1).put("page_size", 100)
                .put("models", new JSONArray().put(new JSONObject().put("model", "qwen-plus")));
        String[] outputIds = (String[]) invokePrivate(AiClient.class, "parseModelIds",
                new Class<?>[]{JSONObject.class}, new JSONObject().put("output", output));
        check(outputIds.length == 1 && "qwen-plus".equals(outputIds[0]),
                "Aliyun output.models parser should read model names");
    }

    private void clearAiSettings() throws Exception {
        AiSettings.clear(target);
        check(!AiSettings.enabled(target), "Clearing AI settings should disable AI");
        check(!AiSettings.hasKey(target), "Clearing AI settings should remove the stored key");
    }

    private void prepareDemoScreen() throws Exception {
        if (activity == null || activity.isFinishing()) {
            startBlankActivity();
        }
        runOnMainSync(() -> {
            activity.store.items.clear();
            activity.store.history = new JSONArray();
            activity.demo();
        });
        waitForIdleSync();
        check(activity.store.items.size() == 5, "Demo screen should contain five sample items");
        check(activity.tab == 0, "Demo screen should return to the home tab");
    }

    private void onMain(CheckedAction action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        Throwable exception = failure.get();
        if (exception instanceof Exception) throw (Exception) exception;
        if (exception instanceof Error) throw (Error) exception;
        if (exception != null) throw new RuntimeException(exception);
    }

    private void setField(String description, String value) {
        View dialogWindow = activity.activeDialog == null || activity.activeDialog.getWindow() == null
                ? null : activity.activeDialog.getWindow().getDecorView();
        EditText field = dialogWindow == null ? null : findEditText(dialogWindow, description);
        if (field == null) {
            field = findEditText(activity.getWindow().getDecorView(), description);
        }
        if (field == null) {
            throw new AssertionError("Could not find input field: " + description);
        }
        field.setText(value);
    }

    private void clickDialogPositive() {
        AlertDialog dialog = activity.activeDialog;
        if (dialog == null || dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null) {
            throw new AssertionError("Expected active dialog with a positive button");
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    private static EditText findEditText(View view, String description) {
        if (view instanceof EditText && description.contentEquals(view.getContentDescription())) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean viewContainsText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (viewContainsText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static void clickText(View view, String text) {
        if (view instanceof TextView && view.isClickable()
                && text.contentEquals(((TextView) view).getText())) {
            view.performClick();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                try {
                    clickText(group.getChildAt(i), text);
                    return;
                } catch (AssertionError ignored) {
                    // Continue searching sibling views.
                }
            }
        }
        throw new AssertionError("Could not find button: " + text);
    }

    private static Object invokePrivate(Class<?> type, String name, Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw exception;
        }
    }

    private void expectInvocationFailure(CheckedAction action, String message) throws Exception {
        checks++;
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private void expectIllegalArgument(CheckedAction action, String message) throws Exception {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private void expectIllegalState(CheckedAction action, String message) throws Exception {
        checks++;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private void expectException(CheckedAction action, String message) throws Exception {
        checks++;
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(int expected, int actual, String message) {
        check(expected == actual, message);
    }

    private void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 0.000001, message);
    }

    private String safeFailure(Throwable failure) {
        if (failure instanceof AssertionError && failure.getMessage() != null) {
            return failure.getMessage();
        }
        return "Acceptance runner failed: " + failure.getClass().getSimpleName();
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
