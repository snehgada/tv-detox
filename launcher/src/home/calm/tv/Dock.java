package home.calm.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Ordered list of docked package names, persisted in private prefs. */
final class Dock {
    private static final Pattern PKG = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}(\\.[A-Za-z0-9_]{1,64}){1,15}");
    private static final int MAX = 24;
    private static final String KEY = "order";

    private final SharedPreferences prefs;
    private final ArrayList<String> order = new ArrayList<>();

    Dock(Context context) {
        prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY, null);
        String[] source = saved != null
                ? saved.split(",", MAX + 1)
                : context.getResources().getStringArray(R.array.default_dock);
        for (String p : source) {
            if (order.size() < MAX && p.length() <= 255 && PKG.matcher(p).matches() && !order.contains(p)) {
                order.add(p);
            }
        }
    }

    List<String> order() { return order; }

    boolean contains(String pkg) { return order.contains(pkg); }

    void add(String pkg) {
        if (!order.contains(pkg) && order.size() < MAX && PKG.matcher(pkg).matches()) order.add(pkg);
        save();
    }

    void remove(String pkg) {
        order.remove(pkg);
        save();
    }

    /** Replaces the order of the visible entries; hidden ones keep their place at the end. */
    void setVisibleOrder(List<String> visible) {
        ArrayList<String> next = new ArrayList<>(visible);
        for (String p : order) if (!next.contains(p)) next.add(p);
        order.clear();
        order.addAll(next);
        save();
    }

    private void save() {
        prefs.edit().putString(KEY, String.join(",", order)).apply();
    }
}
