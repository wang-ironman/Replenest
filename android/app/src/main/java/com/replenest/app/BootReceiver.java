package com.replenest.app;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try { ReminderReceiver.schedule(context, new Store(context).reminders); }
        catch (Exception ignored) { }
    }
}
