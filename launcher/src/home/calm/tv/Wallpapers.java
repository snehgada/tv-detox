package home.calm.tv;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cross-fades the bundled wallpapers on two stacked ImageViews (a below, b above).
 * Holds at most two RGB_565 bitmaps (~1.8 MB each at 1280x720) and only one
 * while stopped, which matters on a 1 GB device.
 */
final class Wallpapers {
    /** Told about each wallpaper as it starts fading in: series name and its accent colour. */
    interface Listener {
        void onWallpaper(String caption, int accent);
    }

    private static final long FADE_MS = 1400;
    private static final float ZOOM = 1.08f;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();

    private final Resources res;
    private final ImageView bottom;
    private final ImageView top;
    private final Listener listener;
    private final int[] ids; // from the theme's R.array.wallpapers; captions/accents line up by index
    private final String[] captions;
    private final int[] accents;
    private final long intervalMs;
    private final boolean kenBurns;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::advance;
    private final ObjectAnimator[] zooms = new ObjectAnimator[2];

    private int index;
    private boolean frontIsTop;
    private boolean running;
    private int generation; // bumps on stop() so late decodes are dropped

    Wallpapers(Resources res, ImageView a, ImageView b, Listener listener, long intervalMs, boolean kenBurns) {
        this.res = res;
        this.bottom = a;
        this.top = b;
        this.listener = listener;
        this.ids = drawables(res, R.array.wallpapers);
        this.captions = res.getStringArray(R.array.wallpaper_captions);
        this.accents = colors(res, R.array.wallpaper_accents);
        this.intervalMs = intervalMs;
        this.kenBurns = kenBurns;
        this.index = new Random().nextInt(ids.length); // cosmetic only
    }

    /** Accent of the current (or about-to-load) wallpaper, for views built before it appears. */
    int accent() { return accents[index % accents.length]; }

    void start() {
        if (running) return;
        running = true;
        if (front().getDrawable() == null) load(index, false);
        else schedule();
    }

    /** Stops rotating and drops the hidden bitmap; the visible one stays. */
    void stop() {
        running = false;
        generation++;
        main.removeCallbacks(tick);
        top.animate().cancel();
        for (ObjectAnimator z : zooms) if (z != null) z.cancel();
        top.setAlpha(frontIsTop ? 1f : 0f);
        back().setImageDrawable(null);
    }

    void releaseAll() {
        stop();
        bottom.setImageDrawable(null);
        top.setImageDrawable(null);
    }

    private static int[] drawables(Resources res, int arrayId) {
        TypedArray ta = res.obtainTypedArray(arrayId);
        try {
            int[] out = new int[ta.length()];
            for (int i = 0; i < out.length; i++) out[i] = ta.getResourceId(i, 0);
            return out;
        } finally {
            ta.recycle();
        }
    }

    private static int[] colors(Resources res, int arrayId) {
        TypedArray ta = res.obtainTypedArray(arrayId);
        try {
            int[] out = new int[ta.length()];
            for (int i = 0; i < out.length; i++) out[i] = ta.getColor(i, Color.WHITE);
            return out;
        } finally {
            ta.recycle();
        }
    }

    private ImageView front() { return frontIsTop ? top : bottom; }
    private ImageView back() { return frontIsTop ? bottom : top; }

    private void schedule() {
        main.removeCallbacks(tick);
        if (running) main.postDelayed(tick, intervalMs);
    }

    private void advance() {
        index = (index + 1) % ids.length;
        load(index, true);
    }

    private void load(final int i, final boolean fade) {
        final int gen = generation;
        final int resId = ids[i];
        DECODER.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            final Bitmap bmp = BitmapFactory.decodeResource(res, resId, o);
            main.post(() -> {
                if (gen == generation && running) present(bmp, i, fade);
            });
        });
    }

    private void present(Bitmap bmp, int i, boolean fade) {
        if (bmp == null) { schedule(); return; }
        if (listener != null) listener.onWallpaper(captions[i % captions.length], accents[i % accents.length]);

        if (!fade) {
            front().setImageBitmap(bmp);
            top.setAlpha(frontIsTop ? 1f : 0f);
            zoom(front());
            schedule();
            return;
        }

        final ImageView incoming = back();
        incoming.setImageBitmap(bmp);
        zoom(incoming);
        if (incoming == top) {
            top.setAlpha(0f);
            top.animate().alpha(1f).setDuration(FADE_MS)
                    .withEndAction(() -> bottom.setImageDrawable(null)).start();
        } else {
            top.animate().alpha(0f).setDuration(FADE_MS)
                    .withEndAction(() -> top.setImageDrawable(null)).start();
        }
        frontIsTop = !frontIsTop;
        schedule();
    }

    private void zoom(View v) {
        if (!kenBurns) return;
        int slot = v == top ? 1 : 0;
        if (zooms[slot] != null) zooms[slot].cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        ObjectAnimator z = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, ZOOM),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, ZOOM));
        z.setDuration(intervalMs + FADE_MS);
        z.setInterpolator(new LinearInterpolator());
        z.start();
        zooms[slot] = z;
    }
}
