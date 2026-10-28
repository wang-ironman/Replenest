package com.replenest.app;

import android.app.*;
import android.content.*;
import java.time.*;
import java.util.ArrayList;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "replenishment";
    static final int ID = 10;

    public static void schedule(Context context, boolean enabled) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 1,
            new Intent(context, ReminderReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!enabled) { context.getSystemService(NotificationManager.class).cancel(ID); return; }
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(9).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        // A daily household reminder does not require exact-alarm access.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending);
    }

    public static void deliver(Context context, boolean test) throws Exception {
        Store store = new Store(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "补货提醒", NotificationManager.IMPORTANCE_DEFAULT));
        if (!manager.areNotificationsEnabled()) return;
        LocalDate today = LocalDate.now();
        ArrayList<String> names = new ArrayList<>();
        for (Item item : store.items) if (item.needsBuy(today) && !item.ordered
            && (item.snoozeUntil == null || !today.isBefore(item.snoozeUntil))) names.add(item.name);
        if (!test && (!store.reminders || names.isEmpty())) { manager.cancel(ID); return; }
        String text = test ? "本地通知已连接。正式提醒会汇总需要补货的用品。" : String.join("、", names);
        Intent launch = new Intent(context, MainActivity.class).putExtra("shopping", true);
        PendingIntent open = PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(ID, new Notification.Builder(context, CHANNEL)
            .setSmallIcon(com.replenest.app.R.drawable.ic_notification)
            .setContentTitle(test ? "家有余量 · 测试提醒" : "有 " + names.size() + " 项用品该补货了")
            .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build());
    }

    @Override public void onReceive(Context context, Intent intent) {
        try {
            deliver(context, false);
            schedule(context, new Store(context).reminders);
        } catch (Exception ignored) {
            // Never overwrite unreadable household data from a background receiver.
        }
    }
}
