package home.calm.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import home.calm.tv.AppRepo.App;

public final class HomeActivity extends Activity {
    private static final long WALLPAPER_INTERVAL_MS = 60_000;
    private static final float TILE_W = 132, TILE_H = 74, RADIUS = 12;
    private static final float AURA = 12, TILE_GAP = 20; // aura padding eats into the visual gap
    private static final String VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"; // remote's Input key

    private AppRepo repo;
    private Dock dock;
    private Wallpapers wallpapers;
    private View dockShell;
    private HorizontalScrollView dockScroll;
    private LinearLayout dockRow;
    private View allApps;
    private ScrollView allScroll;
    private GridLayout grid;
    private TextView clockTime;
    private TextView appsTitle;
    private TextView caption;
    private View badgeBar;
    private GradientDrawable dockEdge;
    private boolean anime;      // theme switch: accent glow, aura and pop vs. the calm nature look
    private int focusRing;      // px, from the theme
    private int dockEdgeWidth;  // px, from the theme
    private int dockEdgeAlpha;  // 0-255, from the theme
    private Intent inputsPanel; // TV input picker, or null on devices without one
    private int accent;
    private float density;
    private final DecelerateInterpolator ease = new DecelerateInterpolator();
    private final OvershootInterpolator pop = new OvershootInterpolator(1.8f);

    private final ViewOutlineProvider rounded = new ViewOutlineProvider() {
        @Override
        public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(RADIUS));
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        density = getResources().getDisplayMetrics().density;
        dockShell = findViewById(R.id.dock_shell);
        dockScroll = findViewById(R.id.dock_scroll);
        dockRow = findViewById(R.id.dock);
        allApps = findViewById(R.id.all_apps);
        allScroll = findViewById(R.id.all_scroll);
        grid = findViewById(R.id.grid);
        clockTime = findViewById(R.id.clock_time);
        appsTitle = findViewById(R.id.apps_title);
        caption = findViewById(R.id.caption);
        badgeBar = findViewById(R.id.badge_bar);
        dockEdge = (GradientDrawable) findViewById(R.id.dock_glass).getBackground().mutate();
        anime = getResources().getBoolean(R.bool.theme_anime);
        focusRing = getResources().getDimensionPixelSize(R.dimen.focus_ring);
        dockEdgeWidth = getResources().getDimensionPixelSize(R.dimen.dock_edge);
        dockEdgeAlpha = Math.max(0, Math.min(255, getResources().getInteger(R.integer.dock_edge_alpha)));
        inputsPanel = findInputsPanel();

        repo = new AppRepo(this);
        dock = new Dock(this);
        wallpapers = new Wallpapers(getResources(), findViewById(R.id.wp_a), findViewById(R.id.wp_b),
                this::onWallpaper, WALLPAPER_INTERVAL_MS, false);
        accent = wallpapers.accent();
        applyAccent();

        repo.refresh();
        rebuildDock(null);
        dockRow.post(this::focusFirstDockTile);
    }

    @Override
    protected void onStart() {
        super.onStart();
        wallpapers.start();
        if (repo.refresh()) {
            rebuildDock(focusedPkg());
            if (allApps.getVisibility() == View.VISIBLE) rebuildGrid();
        }
    }

    @Override
    protected void onStop() {
        wallpapers.stop();
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) wallpapers.releaseAll();
    }

    /** Home pressed while already home: reset to the start of the Dock. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        hideAllApps();
        dockScroll.scrollTo(0, 0);
        focusFirstDockTile();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && allApps.getVisibility() == View.VISIBLE) hideAllApps();
            return true; // home never finishes
        }
        if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof App) {
                showOptions((App) f.getTag(), allApps.getVisibility() != View.VISIBLE);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // --- Series accent -------------------------------------------------------

    private void onWallpaper(String series, int color) {
        caption.setText(series);
        accent = color;
        applyAccent();
    }

    /** Recolours everything that follows the current wallpaper: badge, clock glow, dock edge, focus rings. */
    private void applyAccent() {
        badgeBar.setBackgroundColor(accent);
        if (anime) {
            clockTime.setShadowLayer(dp(12), 0, 0, accent);
            appsTitle.setShadowLayer(dp(12), 0, 0, accent);
        }
        dockEdge.setStroke(dockEdgeWidth, (accent & 0x00FFFFFF) | (dockEdgeAlpha << 24));
        tintTiles(dockRow);
        tintTiles(grid);
    }

    private void tintTiles(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) tintTile(tileOf(container.getChildAt(i)));
    }

    /** Accent focus ring on the tile and accent colour on the aura behind it. */
    private void tintTile(View tile) {
        if (tile.getForeground() instanceof GradientDrawable) {
            ((GradientDrawable) tile.getForeground()).setStroke(focusRing, new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}}, new int[]{accent, Color.TRANSPARENT}));
        }
        if (tile.getParent() instanceof ViewGroup) {
            View aura = ((ViewGroup) tile.getParent()).getChildAt(0);
            if (aura instanceof AuraView) ((AuraView) aura).setColor(accent);
        }
    }

    // --- Dock ------------------------------------------------------------

    private List<App> visibleDock() {
        ArrayList<App> out = new ArrayList<>();
        for (String p : dock.order()) {
            App a = repo.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    private void rebuildDock(String focusPkg) {
        dockRow.removeAllViews();
        for (App a : visibleDock()) dockRow.addView(appTile(a, 10));
        if (inputsPanel != null) {
            dockRow.addView(actionTile(R.drawable.ic_inputs, getString(R.string.inputs), v -> safeStart(inputsPanel)));
        }
        dockRow.addView(actionTile(R.drawable.ic_apps, getString(R.string.all_apps), v -> showAllApps()));
        dockRow.addView(actionTile(R.drawable.ic_settings, getString(R.string.settings), v -> openSettings()));
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dockRow.getChildAt(i).getLayoutParams();
            lp.setMarginEnd(i == dockRow.getChildCount() - 1 ? 0 : Math.max(0, dp(TILE_GAP - 2 * AURA)));
        }
        if (focusPkg != null) dockRow.post(() -> focusTile(dockRow, focusPkg));
    }

    /** HorizontalScrollView ignores the focus scale-up; keep the whole lifted tile in view. */
    private void revealInDock(View wrapper) {
        int margin = dp(24);
        int visible = dockScroll.getWidth() - dockScroll.getPaddingLeft() - dockScroll.getPaddingRight();
        int x = dockScroll.getScrollX();
        int left = wrapper.getLeft() - margin, right = wrapper.getRight() + margin;
        if (right > x + visible) dockScroll.smoothScrollTo(right - visible, 0);
        else if (left < x) dockScroll.smoothScrollTo(Math.max(0, left), 0);
    }

    private void focusFirstDockTile() {
        if (dockRow.getChildCount() > 0) tileOf(dockRow.getChildAt(0)).requestFocus();
    }

    private String focusedPkg() {
        View f = getCurrentFocus();
        return f != null && f.getTag() instanceof App ? ((App) f.getTag()).pkg : null;
    }

    private void moveInDock(App app, int delta) {
        List<String> visible = new ArrayList<>();
        for (App a : visibleDock()) visible.add(a.pkg);
        int i = visible.indexOf(app.pkg), j = i + delta;
        if (i < 0 || j < 0 || j >= visible.size()) return;
        visible.set(i, visible.get(j));
        visible.set(j, app.pkg);
        dock.setVisibleOrder(visible);
        rebuildDock(app.pkg);
    }

    // --- All apps ----------------------------------------------------------

    private void setHomeChromeVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        dockShell.setVisibility(v);
        findViewById(R.id.clock).setVisibility(v);
        findViewById(R.id.badge).setVisibility(v);
    }

    private void showAllApps() {
        rebuildGrid();
        setHomeChromeVisible(false);
        allApps.setAlpha(0f);
        allApps.setVisibility(View.VISIBLE);
        allApps.animate().alpha(1f).setDuration(160).start();
        allScroll.scrollTo(0, 0);
        if (grid.getChildCount() > 0) grid.post(() -> tileOf(grid.getChildAt(0)).requestFocus());
    }

    private void hideAllApps() {
        if (allApps.getVisibility() != View.VISIBLE) return;
        allApps.setVisibility(View.GONE);
        grid.removeAllViews();
        setHomeChromeVisible(true);
        View allAppsTile = dockRow.getChildAt(Math.max(0, dockRow.getChildCount() - 2));
        if (allAppsTile != null) tileOf(allAppsTile).requestFocus();
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        for (App a : repo.all()) {
            View t = appTile(a, 2);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMarginEnd(Math.max(0, dp(18 - 2 * AURA)));
            grid.addView(t, lp);
        }
    }

    // --- Tiles -------------------------------------------------------------

    /** Wrapper = [tile, label]; the label only shows while the tile is focused. */
    private View appTile(App app, int labelGapDp) {
        FrameLayout tile = baseTile(dp(TILE_W));
        tile.setTag(app);
        tile.setContentDescription(app.label);
        if (app.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            tile.setBackgroundResource(R.drawable.tile_fallback);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            inner.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView name = label(app.label);
            name.setTextSize(11);
            name.setPadding(dp(8), dp(4), dp(8), 0);
            inner.addView(name);
            tile.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        tile.setOnClickListener(v -> launch(app));
        tile.setOnLongClickListener(v -> {
            showOptions(app, allApps.getVisibility() != View.VISIBLE);
            return true;
        });
        return wrap(tile, app.label, labelGapDp);
    }

    private View actionTile(int iconRes, String text, View.OnClickListener onClick) {
        FrameLayout tile = baseTile(dp(TILE_H));
        // Anime: opaque, so the aura glows around it rather than through it. Nature: frosted.
        tile.setBackgroundColor(anime ? 0xFF2A2B31 : 0x33FFFFFF);
        tile.setContentDescription(text);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        tile.addView(iv, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        tile.setOnClickListener(onClick);
        return wrap(tile, text, 10);
    }

    private FrameLayout baseTile(int widthPx) {
        FrameLayout tile = new FrameLayout(this);
        tile.setLayoutParams(new LinearLayout.LayoutParams(widthPx, dp(TILE_H)));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOutlineProvider(rounded);
        tile.setClipToOutline(true);
        tile.setForeground(getDrawable(R.drawable.tile_focus).mutate());
        return tile;
    }

    /** Wrapper = [stack = [aura, tile], label]. The stack pops on focus; the aura and label fade in. */
    private View wrap(FrameLayout tile, String text, int labelGapDp) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        w.setClipChildren(false);
        w.setClipToPadding(false);

        int pad = dp(AURA);
        ViewGroup.LayoutParams size = tile.getLayoutParams();
        FrameLayout stack = new FrameLayout(this);
        stack.setClipChildren(false);
        AuraView aura = new AuraView(this, pad, dp(RADIUS + 4));
        stack.addView(aura, new FrameLayout.LayoutParams(size.width + 2 * pad, size.height + 2 * pad));
        stack.addView(tile, new FrameLayout.LayoutParams(size.width, size.height, Gravity.CENTER));
        tintTile(tile);
        w.addView(stack, new LinearLayout.LayoutParams(-2, -2)); // not the vertical default MATCH_PARENT, which shrinks to the label

        TextView label = label(text);
        label.setAlpha(0f);
        label.setMaxWidth(dp(TILE_W + 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(labelGapDp);
        w.addView(label, lp);
        float lift = anime ? 1.14f : 1.12f;
        tile.setOnFocusChangeListener((v, has) -> {
            stack.animate().scaleX(has ? lift : 1f).scaleY(has ? lift : 1f)
                    .translationZ(has ? dp(6) : 0).setDuration(has && anime ? 220 : 160)
                    .setInterpolator(has && anime ? pop : ease).start();
            if (anime) {
                aura.animate().alpha(has ? 0.9f : 0f).setDuration(has ? 260 : 140).start();
            } else {
                v.animate().translationZ(has ? dp(14) : 0).setDuration(160).start(); // plain soft shadow
            }
            label.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            if (has && w.getParent() == dockRow) dockScroll.post(() -> revealInDock(w));
        });
        w.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return w;
    }

    /** Font, size, colour and shadow come from the theme's TileLabel style. */
    private TextView label(CharSequence text) {
        TextView t = new TextView(this, null, 0, R.style.TileLabel);
        t.setText(text);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static View tileOf(View wrapper) {
        ViewGroup stack = (ViewGroup) ((ViewGroup) wrapper).getChildAt(0);
        return stack.getChildAt(1);
    }

    private static void focusTile(ViewGroup container, String pkg) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View t = tileOf(container.getChildAt(i));
            if (t.getTag() instanceof App && ((App) t.getTag()).pkg.equals(pkg)) {
                t.requestFocus();
                return;
            }
        }
    }

    // --- Actions -------------------------------------------------------------

    private void showOptions(App app, boolean fromDock) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        if (fromDock) {
            List<App> visible = visibleDock();
            int pos = visible.indexOf(app);
            if (pos > 0) { labels.add(getString(R.string.move_left)); actions.add(() -> moveInDock(app, -1)); }
            if (pos >= 0 && pos < visible.size() - 1) { labels.add(getString(R.string.move_right)); actions.add(() -> moveInDock(app, 1)); }
        }
        if (dock.contains(app.pkg)) {
            labels.add(getString(R.string.remove_from_dock));
            actions.add(() -> { dock.remove(app.pkg); rebuildDock(null); if (fromDock) focusFirstDockTile(); });
        } else {
            labels.add(getString(R.string.add_to_dock));
            actions.add(() -> { dock.add(app.pkg); rebuildDock(null); });
        }
        labels.add(getString(R.string.app_info));
        actions.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.pkg, null))));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void launch(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!safeStart(i)) {
            repo.invalidate();
            if (repo.refresh()) rebuildDock(null);
        }
    }

    /**
     * The TV's input picker: whatever handles the standard VIEW_INPUTS action (what the remote's
     * Input key sends), else the first of R.array.input_panels. Null on sticks with no inputs.
     */
    private Intent findInputsPanel() {
        Intent standard = new Intent(VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (startable(standard)) return standard;
        for (String flat : getResources().getStringArray(R.array.input_panels)) {
            ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn == null) continue;
            Intent i = new Intent(VIEW_INPUTS).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startable(i)) return i;
        }
        return null;
    }

    private boolean startable(Intent i) {
        ActivityInfo ai = i.resolveActivityInfo(getPackageManager(), 0);
        return ai != null && ai.exported && ai.enabled && ai.permission == null;
    }

    private void openSettings() {
        safeStart(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
