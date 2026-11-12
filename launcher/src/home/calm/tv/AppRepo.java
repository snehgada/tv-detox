package home.calm.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leanback-launchable apps, reloaded only when PackageManager reports a change. */
final class AppRepo {
    static final class App {
        final String pkg;
        final ComponentName component;
        final String label;
        final Drawable banner; // 16:9 TV banner, or null
        final Drawable icon;   // fallback when there is no banner

        App(String pkg, ComponentName component, String label, Drawable banner, Drawable icon) {
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
        }
    }

    private final PackageManager pm;
    private final String self;
    private int sequence = -1;
    private List<App> apps = Collections.emptyList();
    private Map<String, App> byPkg = Collections.emptyMap();

    AppRepo(Context context) {
        pm = context.getPackageManager();
        self = context.getPackageName();
    }

    List<App> all() { return apps; }

    App get(String pkg) { return byPkg.get(pkg); }

    void invalidate() { sequence = -1; }

    /** @return true if the app list was reloaded. */
    boolean refresh() {
        if (sequence >= 0 && pm.getChangedPackages(sequence) == null) return false;
        ChangedPackages now = pm.getChangedPackages(0);
        sequence = now == null ? 0 : now.getSequenceNumber();

        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        LinkedHashMap<String, App> map = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName) || map.containsKey(ai.packageName)) continue;
            Drawable banner = ai.loadBanner(pm);
            Drawable icon = banner == null ? ai.loadIcon(pm) : null;
            map.put(ai.packageName, new App(ai.packageName,
                    new ComponentName(ai.packageName, ai.name),
                    String.valueOf(ri.loadLabel(pm)), banner, icon));
        }
        List<App> list = new ArrayList<>(map.values());
        Collator collator = Collator.getInstance();
        list.sort((a, b) -> collator.compare(a.label, b.label));
        apps = list;
        byPkg = map;
        return true;
    }
}
