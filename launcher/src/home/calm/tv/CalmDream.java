package home.calm.tv;

import android.service.dreams.DreamService;
import android.view.View;
import android.widget.TextView;

/** Screensaver: the theme's wallpapers, slower, with a gentle Ken Burns zoom. */
public final class CalmDream extends DreamService {
    private Wallpapers wallpapers;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.dream);
        TextView caption = findViewById(R.id.caption);
        View bar = findViewById(R.id.badge_bar);
        TextView clock = findViewById(R.id.clock_time);
        boolean anime = getResources().getBoolean(R.bool.theme_anime);
        float glow = 14 * getResources().getDisplayMetrics().density;
        wallpapers = new Wallpapers(getResources(), findViewById(R.id.wp_a), findViewById(R.id.wp_b),
                (text, accent) -> {
                    caption.setText(text);
                    bar.setBackgroundColor(accent);
                    if (anime) clock.setShadowLayer(glow, 0, 0, accent);
                }, 30_000, true);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        wallpapers.start();
    }

    @Override
    public void onDreamingStopped() {
        wallpapers.releaseAll();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (wallpapers != null) wallpapers.releaseAll();
        super.onDetachedFromWindow();
    }
}
