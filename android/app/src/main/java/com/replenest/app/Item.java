package com.replenest.app;

import org.json.JSONObject;
import java.time.LocalDate;
import java.util.UUID;

public final class Item {
    public String id = UUID.randomUUID().toString();
    public String name = "", brand = "", category = "日用", unit = "件", packageUnit = "包";
    public double packSize = 1, stock = 1, dailyRate = 1;
    public LocalDate anchor = LocalDate.now();
    public LocalDate snoozeUntil;
    public int leadDays = 3, corrections = 0;
    public boolean cycle, listed, ordered, paused;

    public double remaining(LocalDate day) { return Forecast.remaining(stock, dailyRate, anchor, day); }
    public double packageCount(LocalDate day) { return remaining(day) / packSize; }
    public long daysLeft(LocalDate day) { return Forecast.daysLeft(remaining(day), dailyRate); }
    public LocalDate due() { return Forecast.dueDate(stock, dailyRate, anchor); }
    public boolean needsBuy(LocalDate day) { return !paused && Forecast.needsBuy(stock, dailyRate, anchor, day, leadDays); }
    public boolean inList(LocalDate day) { return listed || ordered || needsBuy(day); }

    public JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("brand", brand)
            .put("category", category).put("unit", unit).put("packageUnit", packageUnit)
            .put("packSize", packSize)
            .put("stock", stock).put("dailyRate", dailyRate).put("anchor", anchor.toString())
            .put("leadDays", leadDays).put("corrections", corrections).put("cycle", cycle)
            .put("listed", listed).put("ordered", ordered).put("paused", paused)
            .put("snoozeUntil", snoozeUntil == null ? "" : snoozeUntil.toString());
    }

    public static Item from(JSONObject j) throws Exception {
        Item i = new Item();
        i.id = j.getString("id"); i.name = j.getString("name"); i.brand = j.optString("brand", "");
        i.category = j.getString("category"); i.unit = j.getString("unit");
        i.packageUnit = j.optString("packageUnit", "包");
        i.packSize = j.getDouble("packSize"); i.stock = j.getDouble("stock");
        i.dailyRate = j.getDouble("dailyRate"); i.anchor = LocalDate.parse(j.getString("anchor"));
        i.leadDays = j.getInt("leadDays"); i.corrections = j.optInt("corrections", 0);
        i.cycle = j.getBoolean("cycle"); i.listed = j.optBoolean("listed");
        i.ordered = j.optBoolean("ordered"); i.paused = j.optBoolean("paused");
        String snooze = j.optString("snoozeUntil", "");
        if (!snooze.isEmpty()) i.snoozeUntil = LocalDate.parse(snooze);
        if (i.id.isBlank() || i.id.length() > 100 || i.name.isBlank() || i.name.length() > 80
            || i.brand.length() > 80 || i.category.length() > 30 || i.unit.isBlank() || i.unit.length() > 12
            || i.packageUnit.isBlank() || i.packageUnit.length() > 12
            || !Double.isFinite(i.packSize) || i.packSize <= 0 || i.packSize > 1e9
            || !Double.isFinite(i.stock) || i.stock < 0 || i.stock > 1e9
            || !Double.isFinite(i.dailyRate) || i.dailyRate <= 0 || i.dailyRate > 1e9
            || i.stock / i.dailyRate > 36500 || i.leadDays < 0 || i.leadDays > 3650
            || i.corrections < 0 || i.anchor.isAfter(LocalDate.of(9999, 1, 1))
            || i.anchor.isBefore(LocalDate.of(2000, 1, 1)) || (i.cycle && i.dailyRate != 1))
            throw new IllegalArgumentException("物品数据不合法，请检查数量、日期或周期");
        return i;
    }
}
