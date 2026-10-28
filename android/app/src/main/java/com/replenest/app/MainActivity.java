package com.replenest.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final int INK = Color.rgb(35, 34, 49), MUTED = Color.rgb(124, 122, 137);
    static final int BG = Color.rgb(248, 247, 242), PURPLE = Color.rgb(91, 80, 207);
    static final int LILAC = Color.rgb(238, 235, 255);
    static final int ORANGE = Color.rgb(178, 93, 43), PALE = Color.rgb(255, 239, 220);
    Store store;
    LinearLayout root, body;
    AlertDialog activeDialog;
    int tab;
    final ExecutorService network = Executors.newSingleThreadExecutor();
    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M月d日");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) tab = state.getInt("tab", 0);
        if (getIntent().getBooleanExtra("shopping", false)) tab = 2;
        try { store = new Store(this); render(); ReminderReceiver.schedule(this, store.reminders); }
        catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("无法读取本地数据")
                .setMessage("为保护已有记录，应用没有重置数据。可尝试重新打开应用，或从已有备份恢复。")
                .setPositiveButton("选择备份", (d, w) -> importFile())
                .setNegativeButton("关闭", (d, w) -> finish()).setCancelable(false).show();
        }
    }

    @Override protected void onResume() { super.onResume(); if (store != null) render(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("tab", tab); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }

    int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        v.setLineSpacing(dp(3), 1); return v;
    }
    TextView button(String label, boolean primary, Runnable action) {
        TextView v = text(label, 14, primary ? Color.WHITE : PURPLE, true);
        v.setGravity(Gravity.CENTER); v.setMinHeight(dp(48)); v.setPadding(dp(16), dp(10), dp(16), dp(10));
        v.setBackground(bg(primary ? PURPLE : LILAC, 14)); v.setContentDescription(label);
        v.setOnClickListener(view -> action.run()); v.setFocusable(true); return v;
    }
    void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    void note(LinearLayout parent, String s) { TextView v = text(s, 13, MUTED, false); parent.addView(v); gap(parent, 12); }
    void section(String title, String subtitle) { gap(body, 24); body.addView(text(title, 20, INK, true)); if (!subtitle.isEmpty()) { gap(body, 5); note(body, subtitle); } else gap(body, 12); }
    LinearLayout card(LinearLayout parent, int color) {
        LinearLayout c = column(); c.setPadding(dp(18), dp(18), dp(18), dp(18)); c.setBackground(bg(color, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12); parent.addView(c, lp); return c;
    }
    void pair(LinearLayout parent, TextView a, TextView b) {
        LinearLayout r = row(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.rightMargin = dp(8);
        r.addView(a, lp); r.addView(b, new LinearLayout.LayoutParams(0, -2, 1)); parent.addView(r);
    }
    String fmt(double n) {
        if (n > 0 && n < 0.001) return "<0.001";
        return new java.text.DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance(Locale.CHINA)).format(n);
    }
    String inputValue(double n) { return java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString(); }
    String date(LocalDate d) { return d.format(SHORT_DATE); }
    LocalDate today() { return LocalDate.now(); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    void error(Exception e) { new AlertDialog.Builder(this).setTitle("未完成操作").setMessage(e.getMessage() == null ? "请检查输入后重试" : e.getMessage()).setPositiveButton("知道了", null).show(); }
    void persist() {
        try { store.save(); ReminderReceiver.schedule(this, store.reminders); render(); }
        catch (Exception e) { try { store = new Store(this); render(); } catch (Exception ignored) { } error(e); }
    }

    void render() {
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(22), dp(24), dp(22), dp(24)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = row(); nav.setPadding(dp(8), dp(8), dp(8), dp(8)); nav.setBackgroundColor(Color.WHITE);
        String[] labels = {"⌂\n总览", "▦\n家中用品", "✓\n采购清单", "⚙\n设置"};
        for (int n = 0; n < labels.length; n++) {
            final int selected = n;
            TextView v = text(labels[n], 13, tab == n ? PURPLE : MUTED, tab == n);
            v.setGravity(Gravity.CENTER); v.setMinHeight(dp(56)); v.setContentDescription(new String[]{"总览", "家中用品", "采购清单", "设置"}[n]);
            if (tab == n) v.setBackground(bg(LILAC, 16));
            v.setOnClickListener(view -> { tab = selected; render(); }); nav.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(nav); setContentView(root);
        if (tab == 0) home(); else if (tab == 1) inventory(); else if (tab == 2) shopping(); else settings();
        root.requestApplyInsets();
    }

    void heading(String title, String sub) {
        body.addView(text("REPLENEST  /  家有余量", 11, PURPLE, true)); gap(body, 12);
        body.addView(text(title, 30, INK, true)); gap(body, 8); note(body, sub);
    }
    ArrayList<Item> sorted() {
        ArrayList<Item> result = new ArrayList<>(store.items);
        result.sort(Comparator.comparing((Item i) -> i.paused).thenComparing(Item::due)); return result;
    }

    void home() {
        heading("家中余量", date(today()) + "  ·  共 " + store.items.size() + " 项用品");
        body.addView(button("+  登记常用品", true, this::chooseTemplate));
        section("接下来要留意", "按预计用完日期排序");
        int shown = 0;
        for (Item item : sorted()) if (!item.paused && shown++ < 4) itemCard(item, false);
        if (shown == 0) {
            note(body, "还没有登记用品。添加后可查看预计余量。");
        }
    }

    void inventory() {
        heading("家中用品", "共 " + store.items.size() + " 项");
        body.addView(button("+  登记常用品", true, this::chooseTemplate)); gap(body, 20);
        if (store.items.isEmpty()) note(body, "这里还没有用品。添加后，预计余量会每天更新。");
        for (Item item : sorted()) itemCard(item, false);
    }

    String status(Item i) {
        if (i.paused) return "提醒已暂停";
        if (i.ordered) return "已下单 · 等待到货";
        if (i.daysLeft(today()) == 0) return i.cycle ? "预计该更换了" : "预计已用完 · 待确认";
        return "预计可用 " + i.daysLeft(today()) + " 天";
    }
    void itemCard(Item i, boolean purchasing) {
        LinearLayout c = card(body, Color.WHITE);
        LinearLayout top = row(); TextView name = text(i.name, 18, INK, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text(i.cycle ? "周期更换" : i.category, 11, PURPLE, true); tag.setPadding(dp(10), dp(5), dp(10), dp(5)); tag.setBackground(bg(LILAC, 8)); top.addView(tag); c.addView(top);
        if (!i.brand.isEmpty()) { gap(c, 4); c.addView(text(i.brand, 12, MUTED, false)); }
        gap(c, 12); c.addView(text(status(i), 17, i.needsBuy(today()) && !i.ordered ? ORANGE : PURPLE, true));
        gap(c, 5); c.addView(text(i.cycle ? "预计更换 " + date(i.due()) : "约剩 " + fmt(i.packageCount(today())) + i.packageUnit + (i.packSize == 1 && i.unit.equals(i.packageUnit) ? "" : "（" + fmt(i.remaining(today())) + i.unit + "）") + " · " + date(i.due()) + "用完", 12, MUTED, false));
        gap(c, 12);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000);
        bar.setProgress((int)Math.min(1000, i.remaining(today()) / Math.max(i.packSize, i.stock) * 1000));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(i.needsBuy(today()) ? Color.rgb(226, 154, 94) : PURPLE));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LILAC)); c.addView(bar, new LinearLayout.LayoutParams(-1, dp(5)));
        gap(c, 14);
        if (purchasing) pair(c, button(i.ordered ? "确认到货" : "我已下单", true, () -> { if (i.ordered) refill(i); else { i.ordered = true; persist(); } }), button("购买渠道", false, () -> channels(i)));
        else pair(c, button("查看 / 修正", false, () -> detail(i)), button(i.cycle ? "已更换" : "已补货", true, () -> refill(i)));
    }

    void shopping() {
        heading("采购清单", "到货后确认，余量会自动更新。");
        ArrayList<Item> list = new ArrayList<>(); for (Item i : sorted()) if (i.inList(today())) list.add(i);
        if (list.isEmpty()) { gap(body, 25); body.addView(text("暂无待购用品", 21, INK, true)); }
        else { body.addView(button("分享采购清单", false, () -> shareList(list))); gap(body, 16); for (Item i : list) itemCard(i, true); }
        if (!list.isEmpty()) { gap(body, 16); note(body, "购买渠道为搜索跳转，规格、价格与配送时间以平台为准。"); }
    }
    void shareList(ArrayList<Item> list) {
        StringBuilder s = new StringBuilder("家有余量 · 采购清单\n");
        for (Item i : list) s.append("• ").append(i.brand).append(" ").append(i.name).append(i.ordered ? "（已下单）" : "").append("\n");
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.toString()), "分享清单"));
    }

    void settings() {
        heading("按你的节奏", "不用注册。家庭记录保存在这台设备上。");
        LinearLayout c = card(body, Color.WHITE); c.addView(text("本地补货提醒", 19, INK, true)); gap(c, 6);
        boolean permission = getSystemService(NotificationManager.class).areNotificationsEnabled();
        note(c, store.reminders ? permission ? "已开启 · 每天上午 9 点附近汇总" : "已开启计划，但系统通知权限未允许" : "默认关闭 · 开启后只在需要补货时提醒");
        c.addView(button(store.reminders ? "关闭提醒" : "开启本地提醒", !store.reminders, () -> {
            store.reminders = !store.reminders; persist(); if (store.reminders) notificationPermission();
        })); gap(c, 8); c.addView(button("发送测试通知", false, () -> { notificationPermission(); try { ReminderReceiver.deliver(this, true); } catch (Exception e) { error(e); } }));
        gap(c, 6); note(c, "系统省电策略可能延迟提醒。强制停止应用后，需要重新打开应用恢复计划。");
        c = card(body, Color.WHITE); c.addView(text("AI 辅助录入", 19, INK, true)); gap(c, 6);
        note(c, AiSettings.enabled(this) ? "已开启 · " + AiSettings.model(this) : "已关闭 · 当前使用本地规则预测");
        c.addView(button(AiSettings.enabled(this) ? "关闭 AI" : "开启 AI", !AiSettings.enabled(this), () -> {
            try {
                boolean next = !AiSettings.enabled(this); AiSettings.setEnabled(this, next); render();
                toast(next ? "AI 已开启，可使用辅助录入" : "AI 已关闭，API 配置已保留");
            } catch (Exception e) { toast("请先填写并保存 API 地址、密钥和模型"); aiSettings(); }
        })); gap(c, 8);
        c.addView(button("配置 API 与模型", false, this::aiSettings)); gap(c, 8);
        note(c, "仅主动使用 AI 时发送本次输入；可能产生服务商费用。AI 建议需确认，不自动更改余量。");
        c = card(body, Color.WHITE); c.addView(text("数据由你保管", 19, INK, true)); gap(c, 8);
        pair(c, button("导出备份", false, this::exportFile), button("恢复备份", false, this::importFile)); gap(c, 8);
        note(c, "备份包含用品和最近 100 条操作，不包含 API 密钥。卸载会删除本地数据，请先导出备份。");
        c.addView(button("查看最近记录", false, this::history));
        gap(body, 10); body.addView(button("体验示例数据", false, () -> new AlertDialog.Builder(this).setTitle("添加演示用品？")
            .setMessage("会在现有用品之外添加标有“示例”的记录，可逐项删除；不会替换你的数据。")
            .setPositiveButton("添加示例", (d,w) -> demo()).setNegativeButton("取消", null).show()));
        gap(body, 18); note(body, "Replenest · 家有余量  0.1.5\n预测是估算，实际余量以你的确认为准。");
    }

    void notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 40);
        else if (!getSystemService(NotificationManager.class).areNotificationsEnabled())
            startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] results) { super.onRequestPermissionsResult(r,p,results); if (store != null) render(); }

    // Template quantities are editable starting points, never claimed as measured household use.
    Item template(int n) {
        Item i = new Item();
        String[] names = {"咖啡胶囊", "洗衣液", "垃圾袋", "卫生纸", "猫粮", "净水器滤芯", "我的常用品"};
        String[] units = {"粒", "毫升", "只", "卷", "克", "天", "件"};
        String[] packageUnits = {"盒", "瓶", "包", "包", "袋", "件", "包"};
        double[] packs = {30, 2000, 60, 12, 2000, 180, 1};
        double[] periods = {15, 60, 60, 30, 30, 180, 30};
        i.name = names[n]; i.unit = units[n]; i.packageUnit = packageUnits[n]; i.packSize = packs[n]; i.stock = packs[n];
        i.dailyRate = packs[n] / periods[n]; i.cycle = n == 5; i.leadDays = n == 5 ? 14 : 3;
        if (n == 6) { i.unit = "份"; i.packageUnit = "份"; }
        i.category = n == 0 ? "饮食" : n == 4 ? "宠物" : n == 5 ? "设备" : "日用"; return i;
    }
    void chooseTemplate() {
        String[] labels = {"咖啡胶囊", "洗衣液", "垃圾袋", "卫生纸", "猫粮", "净水器滤芯（按周期）", "自定义用品", "用 AI 描述常用品"};
        new AlertDialog.Builder(this).setTitle("从哪一件开始？").setItems(labels, (d,n) -> {
            if (n == 7) aiInput(); else editor(template(n), true);
        }).setNegativeButton("取消", null).show();
    }

    LinearLayout form() { LinearLayout c = column(); c.setPadding(dp(22), dp(8), dp(22), dp(12)); return c; }
    EditText field(LinearLayout parent, String label, String value, boolean number) {
        parent.addView(text(label, 12, MUTED, false));
        EditText e = new EditText(this); e.setText(value); e.setTextSize(16); e.setTextColor(INK); e.setSingleLine(true); e.setMinHeight(dp(50)); e.setContentDescription(label);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2)); gap(parent, 10); return e;
    }
    AlertDialog dialog(String title, LinearLayout form, String positive) {
        ScrollView s = new ScrollView(this); s.addView(form);
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton(positive, null).setNegativeButton("取消", null).create();
        activeDialog = d; d.show(); return d;
    }
    double number(EditText e, boolean positive) {
        double n = e.getTag() instanceof NumberInput ? ((NumberInput)e.getTag()).value()
            : NumericValue.parse(e.getText().toString(), true, positive, 1e9);
        if (positive && n <= 0) { e.setError("请输入大于 0 的数字"); throw new IllegalArgumentException("请输入大于 0 的数字"); }
        return n;
    }
    EditText numericField(LinearLayout parent, String label, String value, boolean decimal, boolean positive, double maximum, double... presets) {
        NumberInput control = new NumberInput(this,label,value,decimal,positive,maximum,presets);
        parent.addView(control, new LinearLayout.LayoutParams(-1,-2)); gap(parent,12); return control.input;
    }
    Spinner unitChoice(LinearLayout parent, String label, String selected, String... choices) {
        parent.addView(text(label,12,MUTED,false));
        ArrayList<String> values = new ArrayList<>(Arrays.asList(choices));
        if (!values.contains(selected)) values.add(selected);
        Spinner spinner = new Spinner(this); spinner.setContentDescription(label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        spinner.setSelection(values.indexOf(selected)); parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48))); gap(parent,12);
        return spinner;
    }
    void editor(Item source, boolean fresh) {
        LinearLayout f = form();
        EditText name = field(f, "用品名称", source.name, false);
        EditText brand = field(f, "品牌 / 购买关键词（可选）", source.brand, false);
        CheckBox cycle = new CheckBox(this); cycle.setText("按周期更换（滤芯等）"); cycle.setChecked(source.cycle); f.addView(cycle); gap(f, 10);
        LinearLayout quantities = column(); f.addView(quantities);
        boolean[] recordSpec = {source.packSize != 1 || !source.unit.equals(source.packageUnit)};
        TextView specToggle = button("商品规格（选填）",false,() -> {}); specToggle.setContentDescription("商品规格（选填）"); quantities.addView(specToggle); gap(quantities,8);
        LinearLayout specifications = column(); quantities.addView(specifications); specifications.setVisibility(View.GONE);
        Spinner unit = unitChoice(specifications,"规格单位",source.unit,"毫升","升","克","千克","粒","只","卷","件","份");
        EditText pack = numericField(specifications, "每份规格", inputValue(source.packSize), true,true,1e9,1,10,30,100,500,1000,2000);
        Spinner packageUnit = unitChoice(quantities,"数量单位",source.packageUnit,"份","包","瓶","盒","袋","卷","个","件","粒","只");
        EditText remaining = numericField(quantities,"现有数量",inputValue(source.packageCount(today())),true,false,1e9,0,0.5,1,2,3,5,10);
        TextView total = text("", 14, PURPLE, true); total.setContentDescription("自动计算总量"); quantities.addView(total); gap(quantities, 12);
        Runnable updateTotal = () -> {
            String unitName = recordSpec[0] ? unit.getSelectedItem().toString() : packageUnit.getSelectedItem().toString();
            try {
                double size = recordSpec[0] ? number(pack,true) : 1;
                total.setText("合计 " + fmt(size * number(remaining,false)) + unitName);
                specToggle.setText(recordSpec[0] ? "规格：" + fmt(size) + unitName + "/" + packageUnit.getSelectedItem() + "  ▾" : "商品规格（选填）  ▾");
            } catch (Exception ignored) { total.setText("填写数量和规格后自动计算合计"); }
        };
        specToggle.setOnClickListener(v -> {
            boolean open = specifications.getVisibility() != View.VISIBLE;
            if (open) recordSpec[0] = true;
            specifications.setVisibility(open ? View.VISIBLE : View.GONE); updateTotal.run();
        });
        specifications.addView(button("不记录规格",false,() -> { recordSpec[0] = false; specifications.setVisibility(View.GONE); updateTotal.run(); }));
        EditText period = numericField(quantities, "一份预计用几天", java.math.BigDecimal.valueOf(source.packSize / source.dailyRate).round(new java.math.MathContext(12)).stripTrailingZeros().toPlainString(), true,true,36500,1,3,7,15,30,60,90);
        android.text.TextWatcher totalWatcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { updateTotal.run(); }
            public void afterTextChanged(android.text.Editable s) {}
        };
        pack.addTextChangedListener(totalWatcher); remaining.addTextChangedListener(totalWatcher);
        AdapterView.OnItemSelectedListener selectedUnit = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int n,long id) { updateTotal.run(); }
            public void onNothingSelected(AdapterView<?> p) {}
        };
        unit.setOnItemSelectedListener(selectedUnit); packageUnit.setOnItemSelectedListener(selectedUnit); updateTotal.run();
        LinearLayout periods = column(); f.addView(periods);
        EditText cyclePeriod = numericField(periods, "更换周期（天）", inputValue(source.packSize), true,true,36500,7,30,60,90,180,365);
        EditText cycleRemaining = numericField(periods, "当前剩余天数", inputValue(source.remaining(today())), true,false,36500,0,7,15,30,60,90,180);
        quantities.setVisibility(source.cycle ? View.GONE : View.VISIBLE); periods.setVisibility(source.cycle ? View.VISIBLE : View.GONE);
        cycle.setOnCheckedChangeListener((b,checked) -> { quantities.setVisibility(checked ? View.GONE : View.VISIBLE); periods.setVisibility(checked ? View.VISIBLE : View.GONE); });
        EditText lead = numericField(f, "提前提醒天数（配送 + 安全余量）", Integer.toString(source.leadDays), false,false,3650,0,1,3,5,7,14,30);
        AlertDialog d = dialog(fresh ? "登记常用品" : "编辑用品", f, "保存用品");
        d.getButton(-1).setOnClickListener(v -> {
            try {
                Item i = fresh ? new Item() : Item.from(source.json());
                i.name = name.getText().toString().trim(); i.brand = brand.getText().toString().trim();
                i.cycle = cycle.isChecked(); i.packageUnit = i.cycle ? "件" : packageUnit.getSelectedItem().toString();
                i.unit = i.cycle ? "天" : recordSpec[0] ? unit.getSelectedItem().toString() : i.packageUnit;
                i.packSize = i.cycle ? number(cyclePeriod,true) : recordSpec[0] ? number(pack, true) : 1;
                i.stock = i.cycle ? number(cycleRemaining,false) : number(remaining, false) * i.packSize;
                i.dailyRate = i.cycle ? 1 : i.packSize / number(period, true); i.anchor = today();
                double days = number(lead, false); if (days != Math.rint(days)) throw new IllegalArgumentException("提前天数请填写整数");
                i.leadDays = (int) days; i.category = source.category; i = Item.from(i.json());
                if (fresh && store.items.size() >= 500) throw new IllegalArgumentException("最多支持 500 项用品");
                if (fresh) store.items.add(i); else store.items.set(store.items.indexOf(source), i);
                store.event(i, fresh ? "登记用品" : "修改用量设置"); persist(); d.dismiss();
            } catch (Exception e) { error(e); }
        });
    }

    void detail(Item i) {
        LinearLayout f = form(); f.addView(text(status(i), 24, PURPLE, true)); gap(f, 16);
        note(f, i.cycle ? "周期用品 · 最近登记 " + i.anchor + "\n当前每天递减 1 天；确认更换后重新计算。" : "预测依据\n" + fmt(i.packSize) + i.unit + " / " + i.packageUnit + " ÷ 每天 " + fmt(i.dailyRate) + i.unit + "\n余量基准：" + i.anchor + " 登记 " + fmt(i.stock) + i.unit);
        note(f, "预计" + (i.cycle ? "更换" : "用完") + "：" + i.due() + "\n提醒日期：" + i.due().minusDays(i.leadDays) + "\n已修正 " + i.corrections + " 次 · 估算以实际确认为准");
        if (i.snoozeUntil != null && today().isBefore(i.snoozeUntil)) note(f, "通知已延后至 " + i.snoozeUntil);
        AlertDialog d = dialog(i.name, f, "关闭"); d.getButton(-1).setOnClickListener(v -> d.dismiss());
        f.addView(button("修正实际余量", true, () -> { d.dismiss(); correct(i); })); gap(f, 8);
        f.addView(button(i.cycle ? "确认已经更换" : "确认补货到家", false, () -> { d.dismiss(); refill(i); })); gap(f, 8);
        f.addView(button("编辑用品与用量", false, () -> { d.dismiss(); editor(i, false); })); gap(f, 8);
        f.addView(button(i.listed ? "取消手动加入清单" : "手动加入采购清单", false, () -> { i.listed = !i.listed; persist(); d.dismiss(); })); gap(f, 8);
        if (i.ordered) { f.addView(button("取消已下单标记", false, () -> { i.ordered = false; persist(); d.dismiss(); })); gap(f, 8); }
        f.addView(button("通知延后 3 天", false, () -> { i.snoozeUntil = today().plusDays(3); persist(); d.dismiss(); })); gap(f, 8);
        f.addView(button(i.paused ? "恢复补货提醒" : "暂停补货提醒", false, () -> { i.paused = !i.paused; persist(); d.dismiss(); })); gap(f, 8);
        f.addView(button("删除这件用品", false, () -> new AlertDialog.Builder(this).setTitle("删除“" + i.name + "”？").setMessage("用品及预测将被移除。").setPositiveButton("删除", (x,w) -> { store.items.remove(i); persist(); d.dismiss(); }).setNegativeButton("保留", null).show()));
    }
    void correct(Item i) {
        LinearLayout f = form(); note(f, "按今天实际看到的数量填写。数量增加请使用“已补货”，以免影响用量校准。");
        EditText actual = numericField(f, i.cycle ? "实际剩余天数" : "实际剩余数量（" + i.packageUnit + "）", inputValue(i.cycle ? i.remaining(today()) : i.packageCount(today())), true,false,i.cycle ? 36500 : 1e9,0,0.5,1,2,3,5,10);
        AlertDialog d = dialog("修正实际余量", f, "确认修正"); d.getButton(-1).setOnClickListener(v -> {
            try {
                double n = number(actual, false) * (i.cycle ? 1 : i.packSize);
                double rate = i.cycle ? 1 : Forecast.correctedRate(i.dailyRate, i.stock, n, i.anchor, today());
                Item test = Item.from(i.json()); test.stock = n; test.dailyRate = rate; test.anchor = today(); Item.from(test.json());
                i.dailyRate = rate; i.stock = n; i.anchor = today(); i.corrections++;
                store.event(i, "修正余量为 " + fmt(n) + i.unit); persist(); d.dismiss();
            } catch (Exception e) { error(e); }
        });
    }
    void refill(Item i) {
        LinearLayout f = form(); note(f, i.cycle ? "确认更换后，重新开始 " + fmt(i.packSize) + " 天周期。" : "到货后填写新增数量，原有余量会保留。");
        EditText packs = i.cycle ? null : numericField(f, "新增数量（" + i.packageUnit + "，支持小数）", "1", true,true,1e9,0.5,1,2,3,5,10);
        AlertDialog d = dialog(i.cycle ? "确认更换" : "确认补货到家", f, "确认");
        d.getButton(-1).setOnClickListener(v -> {
            try {
                double n = i.cycle ? i.packSize : i.remaining(today()) + number(packs, true) * i.packSize;
                Item test = Item.from(i.json()); test.stock = n; test.anchor = today(); Item.from(test.json());
                i.stock = n; i.anchor = today(); i.ordered = false; i.listed = false; i.snoozeUntil = null;
                store.event(i, i.cycle ? "更换完成" : "补货到家"); persist(); d.dismiss();
            } catch (Exception e) { error(e); }
        });
    }
    void channels(Item i) {
        String key = i.brand + " " + i.name + (i.cycle || (i.packSize == 1 && i.unit.equals(i.packageUnit)) ? "" : " " + fmt(i.packSize) + i.unit);
        new AlertDialog.Builder(this).setTitle("搜索相同规格")
            .setMessage("搜索词：" + key + "\n\n跳转后请自行核对每包装数量、单价与运费。余量不会读取购物订单。")
            .setPositiveButton("京东", (d,w) -> openUrl("https://search.jd.com/Search?keyword=" + Uri.encode(key)))
            .setNeutralButton("淘宝", (d,w) -> openUrl("https://s.taobao.com/search?q=" + Uri.encode(key))).setNegativeButton("取消", null).show();
    }
    void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (ActivityNotFoundException e) { toast("设备上没有可打开链接的浏览器"); } }

    void history() {
        StringBuilder s = new StringBuilder();
        for (int n = 0; n < store.history.length(); n++) { JSONObject e = store.history.optJSONObject(n); if (e != null) s.append(e.optString("day")).append("  ").append(e.optString("name")).append("\n").append(e.optString("action")).append("\n\n"); }
        new AlertDialog.Builder(this).setTitle("最近记录").setMessage(s.length() == 0 ? "还没有操作记录" : s.toString()).setPositiveButton("关闭", null).show();
    }
    void demo() {
        try {
            if (store.items.size() > 495) throw new IllegalArgumentException("用品数量过多，无法添加示例");
            for (int n = 0; n < 5; n++) { Item i = template(n); i.name += "（示例）"; i.stock = i.dailyRate * new int[]{3, 18, 8, 2, 12}[n]; store.items.add(i); }
            persist(); tab = 0; render();
        } catch (Exception e) { error(e); }
    }

    void exportFile() { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "Replenest-" + today() + ".json"), 11); }
    void importFile() { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 12); }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code,result,data); if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (code == 11) {
                byte[] bytes = store.json().toString(2).getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) { if (out == null) throw new IOException("无法写入备份"); out.write(bytes); }
                toast("备份已导出（不包含 API 配置）");
            } else if (code == 12) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if (in == null) throw new IOException("无法读取备份"); byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) != -1) { if (out.size() + n > 1024 * 1024) throw new IOException("备份大于 1MB"); out.write(b,0,n); }
                }
                JSONObject parsed = Store.parse(out.toString("UTF-8"));
                new AlertDialog.Builder(this).setTitle("恢复 " + parsed.getJSONArray("items").length() + " 件用品？")
                    .setMessage("将替换本机的用品和历史记录。建议先导出现有备份。API 配置保持不变。")
                    .setPositiveButton("确认替换", (d,w) -> { try {
                        if (store == null) {
                            if (!getSharedPreferences("household", MODE_PRIVATE).edit().putString("data", parsed.toString()).commit()) throw new IOException("恢复失败，原数据已保留");
                            store = new Store(this);
                        } else store.replace(parsed);
                        ReminderReceiver.schedule(this, store.reminders); render(); toast("备份已恢复");
                    } catch (Exception e) { error(e); } }).setNegativeButton("取消", null).show();
            }
        } catch (Exception e) { error(e); }
    }

    // AI settings and entry points are explicit: ordinary rendering never starts network calls.
    void aiSettings() {
        LinearLayout f = form();
        TextView aiState = text(AiSettings.enabled(this) ? "当前状态：AI 已开启" : "当前状态：AI 已关闭 · 使用本地规则", 16, PURPLE, true);
        f.addView(aiState); gap(f, 10);
        TextView toggle = button(AiSettings.enabled(this) ? "关闭 AI" : "开启 AI", true, () -> {});
        f.addView(toggle); gap(f, 8);
        note(f, "开关立即生效。关闭会保留 API 配置；只保存配置不会自动开启 AI。");
        note(f, "支持 OpenAI 兼容的文本对话接口。只有点击获取模型或生成建议时才联网；服务商可能收费。");
        TextView preset = button("选择服务平台预设", false, () -> {}); f.addView(preset); gap(f, 12);
        EditText endpoint = field(f, "API 基础地址（含版本路径）", AiSettings.endpoint(this).replaceFirst("/chat/completions$", ""), false);
        EditText key = field(f, "API Key（留空保留当前密钥）", "", false); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        note(f, AiSettings.hasKey(this) ? "已保存加密密钥。切换服务地址时需要重新填写。" : "尚未保存密钥。密钥不会出现在导出备份中。");
        String savedList = getSharedPreferences("ui", MODE_PRIVATE).getString("modelsUrl", "");
        EditText modelsUrl = field(f, "模型列表地址（可选，默认 基础地址/models）", savedList, false);
        EditText model = field(f, "模型 ID（可从列表选择，也可手动填写）", AiSettings.model(this), false);
        TextView fetch = button("获取模型列表并选择", false, () -> {}); f.addView(fetch); gap(f, 8);
        note(f, "请选择支持文本对话的模型。部分平台的模型列表地址与对话地址不同；列表不可用时仍可手动填写模型 ID。");
        preset.setOnClickListener(v -> {
            String[] labels = {"DeepSeek", "硅基流动", "Kimi / Moonshot", "智谱 GLM", "阿里云百炼（填写业务空间地址）", "OpenAI", "Gemini（兼容接口）", "豆包 / 火山方舟", "自定义兼容服务"};
            String[] urls = {"https://api.deepseek.com", "https://api.siliconflow.cn/v1", "https://api.moonshot.cn/v1", "https://open.bigmodel.cn/api/paas/v4", "", "https://api.openai.com/v1", "https://generativelanguage.googleapis.com/v1beta/openai", "https://ark.cn-beijing.volces.com/api/v3", ""};
            new AlertDialog.Builder(this).setTitle("选择平台").setItems(labels, (d,n) -> {
                endpoint.setText(urls[n]); modelsUrl.setText(""); model.setText(""); key.setText("");
                if (n == 4) toast("请按百炼控制台填写当前业务空间的对话地址和模型列表地址");
            }).show();
        });
        AlertDialog d = dialog("API 与模型", f, "保存配置");
        toggle.setOnClickListener(v -> { try {
            boolean next = !AiSettings.enabled(this);
            if (next) {
                String url = modelsAddress(modelsUrl);
                AiSettings.save(this, true, endpoint.getText().toString(), model.getText().toString(), key.getText().toString());
                getSharedPreferences("ui", MODE_PRIVATE).edit().putString("modelsUrl", url).apply();
            } else AiSettings.setEnabled(this, false);
            aiState.setText(next ? "当前状态：AI 已开启" : "当前状态：AI 已关闭 · 使用本地规则");
            toggle.setText(next ? "关闭 AI" : "开启 AI"); toggle.setContentDescription(next ? "关闭 AI" : "开启 AI");
            render(); toast(next ? "配置已保存，AI 已开启" : "AI 已关闭，API 配置已保留");
        } catch (Exception e) { error(e); } });
        fetch.setOnClickListener(v -> {
            try {
                String base = endpoint.getText().toString().trim(); String normalized = AiSettings.normalizeEndpoint(base);
                String entered = key.getText().toString().trim();
                if (entered.isEmpty() && !normalized.equals(AiSettings.endpoint(this))) throw new IllegalArgumentException("服务地址已变更，请填写该平台的 API Key");
                String credential = entered.isEmpty() ? AiSettings.key(this) : entered;
                String listEndpoint = modelsUrl.getText().toString().trim(); fetch.setEnabled(false); fetch.setText("正在获取模型…");
                network.execute(() -> { try {
                    String[] models = AiClient.listModels(base, credential, listEndpoint);
                    runOnUiThread(() -> { if (isDestroyed() || !d.isShowing()) return; fetch.setEnabled(true); fetch.setText("获取模型列表并选择");
                        modelPicker(models, model);
                    });
                } catch (Exception e) { runOnUiThread(() -> { if (isDestroyed() || !d.isShowing()) return; fetch.setEnabled(true); fetch.setText("重新获取模型列表");
                    new AlertDialog.Builder(this).setTitle("未能获取模型").setMessage("请检查地址、密钥及网络。平台可能未提供兼容列表接口；也可以直接填写模型 ID。\n\n" + safeNetworkError(e)).setPositiveButton("继续手动填写", null).show();
                }); } });
            } catch (Exception e) { error(e); }
        });
        d.getButton(-1).setOnClickListener(v -> { try {
            String url = modelsAddress(modelsUrl);
            AiSettings.save(this, AiSettings.enabled(this), endpoint.getText().toString(), model.getText().toString(), key.getText().toString());
            getSharedPreferences("ui", MODE_PRIVATE).edit().putString("modelsUrl", url).apply(); d.dismiss(); render();
            toast(AiSettings.enabled(this) ? "配置已保存，AI 已开启" : "配置已保存，AI 仍关闭；点击“开启 AI”即可使用");
        } catch (Exception e) { error(e); } });
        gap(f, 8); f.addView(button("删除本机 API 配置", false, () -> new AlertDialog.Builder(this).setTitle("删除 API 地址与密钥？").setMessage("用品数据不受影响，之后继续使用规则预测。")
            .setPositiveButton("删除配置", (x,w) -> { try { AiSettings.clear(this); getSharedPreferences("ui",MODE_PRIVATE).edit().clear().apply(); d.dismiss(); render(); } catch (Exception e) { error(e); } }).setNegativeButton("取消",null).show()));
    }

    String modelsAddress(EditText field) throws Exception {
        String url = field.getText().toString().trim();
        if (!url.isEmpty()) {
            java.net.URI u = new java.net.URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null)
                throw new IllegalArgumentException("模型列表地址必须为有效 HTTPS 地址");
        }
        return url;
    }

    void modelPicker(String[] models, EditText destination) {
        LinearLayout f = form(); EditText search = field(f, "搜索模型", "", false);
        ListView list = new ListView(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>(Arrays.asList(models)));
        list.setAdapter(adapter); f.addView(list, new LinearLayout.LayoutParams(-1, dp(320)));
        AlertDialog pick = new AlertDialog.Builder(this).setTitle("选择文本模型 · " + models.length + " 项").setView(f).setNegativeButton("取消", null).create(); pick.show();
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { adapter.getFilter().filter(s); } public void afterTextChanged(android.text.Editable s) {}
        });
        list.setOnItemClickListener((p,v,n,id) -> { destination.setText(adapter.getItem(n)); pick.dismiss(); });
    }
    String safeNetworkError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.matches(".*HTTP [0-9]{3}.*") || msg.matches(".*HTTP 状态码：[0-9]{3}.*"))) return msg;
        return "服务未返回可用结果，或连接失败。请检查后重试。";
    }
    void aiInput() {
        if (!AiSettings.enabled(this)) {
            boolean configured = AiSettings.hasKey(this) && !AiSettings.model(this).isBlank();
            new AlertDialog.Builder(this).setTitle(configured ? "AI 已配置，当前处于关闭状态" : "AI 尚未配置")
                .setMessage(configured ? "已保存的 API 和模型可以继续使用。点击开启后进入辅助录入，也可保持规则模式。" : "请先填写 API 地址、密钥和模型。也可以继续使用模板手动登记。")
                .setPositiveButton(configured ? "开启并继续" : "配置 API 与模型", (d,w) -> {
                    if (!configured) { aiSettings(); return; }
                    try { AiSettings.setEnabled(this, true); render(); aiInput(); } catch (Exception e) { error(e); }
                }).setNegativeButton("返回",null).show(); return;
        }
        LinearLayout f = form(); note(f, "本次输入将发送给你配置的服务。请说明包装规格、当前余量和使用频率；不要填写无关的个人信息。");
        EditText input = field(f, "描述你的用品与使用习惯", "", false); input.setSingleLine(false); input.setMinLines(4);
        input.setHint("例如：咖啡胶囊一盒30粒，现在还有24粒，每天喝2粒，快递需要3天。");
        AlertDialog d = dialog("用一句话登记", f, "生成用量建议");
        d.getButton(-1).setOnClickListener(v -> {
            String description = input.getText().toString().trim(); if (description.isEmpty() || description.length() > 2000) { toast("请输入 1–2000 字描述"); return; }
            d.getButton(-1).setEnabled(false); d.getButton(-1).setText("正在生成…");
            network.execute(() -> { try {
                JSONObject result = AiClient.suggest(this, description);
                runOnUiThread(() -> { if (isDestroyed() || !d.isShowing()) return; d.getButton(-1).setEnabled(true); d.getButton(-1).setText("生成用量建议");
                    if (!result.optString("question").isBlank()) { new AlertDialog.Builder(this).setTitle("还需要确认一点").setMessage(result.optString("question")).setPositiveButton("补充描述",null).show(); return; }
                    try {
                        Item i = new Item(); i.name = result.getString("name"); i.brand = result.getString("brand");
                        i.cycle = "cycle".equals(result.getString("kind")); i.unit = i.cycle ? "天" : result.getString("unit");
                        i.packageUnit = result.optString("packageUnit", "包");
                        i.packSize = result.getDouble("packSize"); i.stock = result.getDouble("stock");
                        i.dailyRate = i.cycle ? 1 : i.packSize / result.getDouble("daysPerPack"); i.leadDays = result.getInt("leadDays");
                        Item validated = Item.from(i.json());
                        new AlertDialog.Builder(this).setTitle("AI 建议 · 请核对")
                            .setMessage(i.name + "\n" + (i.cycle ? "当前剩余 " + fmt(i.stock) + "天" : "现有 " + fmt(i.stock / i.packSize) + i.packageUnit + "，合计 " + fmt(i.stock) + i.unit) + "\n" + (i.cycle ? "更换周期 " + fmt(i.packSize) + " 天" : "预计每天 " + fmt(i.dailyRate) + i.unit) + "\n\n" + result.getString("explanation") + "\n\n下一步仍可修改，保存后才会登记。")
                            .setPositiveButton("核对并填写表单", (x,w) -> { d.dismiss(); editor(validated,true); }).setNegativeButton("返回修改描述",null).show();
                    } catch (Exception e) { toast("建议无法转换为有效用量，请补充描述或手动填写。"); }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (isDestroyed() || !d.isShowing()) return;
                d.getButton(-1).setEnabled(true); d.getButton(-1).setText("重试生成");
                new AlertDialog.Builder(this).setTitle("AI 暂时不可用").setMessage(safeNetworkError(e) + "\n本地规则预测不受影响，可以先手动登记。")
                    .setPositiveButton("手动登记", (x,w) -> { d.dismiss(); editor(template(6),true); }).setNegativeButton("继续修改描述",null).show();
            }); } });
        });
    }
}
