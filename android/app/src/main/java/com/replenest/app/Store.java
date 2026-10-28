package com.replenest.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;

public final class Store {
    public final ArrayList<Item> items = new ArrayList<>();
    public JSONArray history = new JSONArray();
    public boolean reminders;
    private final SharedPreferences prefs;

    public Store(Context context) throws Exception {
        prefs = context.getSharedPreferences("household", Context.MODE_PRIVATE);
        String saved = prefs.getString("data", "");
        if (!saved.isEmpty()) load(parse(saved));
    }

    public static JSONObject parse(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (root.getInt("schema") != 1) throw new IllegalArgumentException("不支持的备份版本");
        JSONArray rows = root.getJSONArray("items");
        if (rows.length() > 500) throw new IllegalArgumentException("最多支持 500 项物品");
        HashSet<String> ids = new HashSet<>();
        for (int n = 0; n < rows.length(); n++) {
            Item item = Item.from(rows.getJSONObject(n));
            if (!ids.add(item.id)) throw new IllegalArgumentException("物品编号重复");
        }
        JSONArray events = root.optJSONArray("history");
        if (events != null && events.length() > 100) throw new IllegalArgumentException("备份记录超过上限");
        return root;
    }

    private void load(JSONObject root) throws Exception {
        items.clear();
        JSONArray rows = root.getJSONArray("items");
        for (int n = 0; n < rows.length(); n++) items.add(Item.from(rows.getJSONObject(n)));
        history = root.optJSONArray("history");
        if (history == null) history = new JSONArray();
        reminders = root.optBoolean("reminders", false);
    }

    public JSONObject json() throws Exception {
        JSONArray rows = new JSONArray();
        for (Item item : items) rows.put(item.json());
        return new JSONObject().put("schema", 1).put("items", rows).put("history", history)
            .put("reminders", reminders).put("exportedAt", LocalDate.now().toString());
    }

    public void save() throws Exception {
        if (!prefs.edit().putString("data", json().toString()).commit())
            throw new IllegalStateException("保存失败，请检查设备可用空间");
    }

    public void replace(JSONObject validated) throws Exception {
        // Commit the complete validated document before changing the in-memory state.
        parse(validated.toString());
        if (!prefs.edit().putString("data", validated.toString()).commit())
            throw new IllegalStateException("恢复失败，原数据已保留");
        load(validated);
    }

    public void event(Item item, String action) throws Exception {
        JSONObject event = new JSONObject().put("day", LocalDate.now().toString())
            .put("name", item.name).put("action", action);
        JSONArray next = new JSONArray(); next.put(event);
        for (int n = 0; n < Math.min(99, history.length()); n++) next.put(history.get(n));
        history = next;
    }
}
