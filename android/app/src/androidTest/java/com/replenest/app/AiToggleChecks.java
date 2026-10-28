package com.replenest.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicReference;

final class AiToggleChecks {
    final Instrumentation test;
    MainActivity activity;
    int checks;
    AiToggleChecks(Instrumentation test) { this.test = test; }
    void run() {
        Bundle result = new Bundle();
        try {
            AiSettings.clear(test.getTargetContext());
            activity = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(() -> {
                activity.aiSettings();
                field("API 基础地址（含版本路径）", "https://fixture.invalid/v1");
                field("模型 ID（可从列表选择，也可手动填写）", "text-test");
                field("API Key（留空保留当前密钥）", "toggle-test-secret");
                activity.activeDialog.getButton(-1).performClick();
            });
            check(AiSettings.hasKey(activity) && !AiSettings.enabled(activity), "Saving configuration alone must keep AI disabled");
            main(() -> { activity.aiSettings(); click(activity.activeDialog.getWindow().getDecorView(), "开启 AI"); });
            check(AiSettings.enabled(activity), "Enable button should immediately enable saved configuration");
            check("text-test".equals(AiSettings.model(activity)), "Enabling must preserve selected model");
            check("toggle-test-secret".equals(AiSettings.key(activity)), "Enabling must preserve key");
            main(() -> {
                activity.activeDialog.dismiss(); activity.aiInput();
                check(find(activity.activeDialog.getWindow().getDecorView(), "描述你的用品与使用习惯") != null, "Configured and enabled AI must open the input form");
                activity.activeDialog.dismiss(); activity.aiSettings();
                // Disabling must work even if unsaved form fields are invalid.
                field("API 基础地址（含版本路径）", "invalid unsaved draft");
                click(activity.activeDialog.getWindow().getDecorView(), "关闭 AI");
            });
            check(!AiSettings.enabled(activity), "Disable button should immediately disable AI");
            check(AiSettings.hasKey(activity) && "text-test".equals(AiSettings.model(activity)), "Disable must retain configuration");
            main(() -> { activity.activeDialog.dismiss(); activity.aiSettings(); check(find(activity.activeDialog.getWindow().getDecorView(), "开启 AI") != null, "Reopened settings should reflect disabled state"); activity.activeDialog.dismiss(); activity.tab = 3; activity.render(); click(activity.root, "开启 AI"); });
            check(AiSettings.enabled(activity), "Settings page should enable AI directly");
            main(() -> click(activity.root, "关闭 AI"));
            check(!AiSettings.enabled(activity), "Settings page should disable AI directly");
            main(activity::finish);
            activity = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(!AiSettings.enabled(activity) && AiSettings.hasKey(activity), "Disabled state and configuration must survive restart");
            main(() -> { activity.aiSettings(); click(activity.activeDialog.getWindow().getDecorView(), "开启 AI"); activity.activeDialog.dismiss(); });
            check(AiSettings.enabled(activity), "Retained configuration must be reusable after restart");
            AiSettings.clear(activity);
            main(() -> { activity.tab = 3; activity.render(); });
            result.putString("success", "AI toggle UI checks passed (no network requests)");
            result.putInt("checks", checks); test.finish(Activity.RESULT_OK,result);
        } catch (Throwable e) {
            result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage()); result.putInt("checks",checks);
            test.finish(Activity.RESULT_CANCELED,result);
        }
    }
    void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure.set(e); } });
        test.waitForIdleSync();
        if (failure.get() != null) throw new AssertionError(failure.get().getMessage(),failure.get());
    }
    void check(boolean valid,String message) { checks++; if (!valid) throw new AssertionError(message); }
    void field(String label,String value) {
        View v = find(activity.activeDialog.getWindow().getDecorView(),label);
        if (!(v instanceof EditText)) throw new AssertionError("Missing field: " + label); ((EditText)v).setText(value);
    }
    void click(View root,String label) {
        View v = find(root,label); if (v == null) throw new AssertionError("Missing button: " + label); v.performClick();
    }
    View find(View root,String label) {
        if (root.getContentDescription() != null && label.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) { ViewGroup g=(ViewGroup)root; for(int n=0;n<g.getChildCount();n++) { View found=find(g.getChildAt(n),label); if(found!=null)return found; } }
        return null;
    }
}
