package home.calm.tv;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Soft accent glow ("ki aura") behind a tile. It is laid out {@code inset} larger than the
 * tile on every side so the blur has room; HomeActivity fades it in on focus.
 */
final class AuraView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float inset;
    private final float radius;

    AuraView(Context context, float inset, float radius) {
        super(context);
        this.inset = inset;
        this.radius = radius;
        // A NORMAL blur reaches ~1.7x its radius past the shape; keep that inside the view or the edge shows as a box.
        paint.setMaskFilter(new BlurMaskFilter(inset * 0.32f, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur mask needs the software path; the view is small
        setAlpha(0f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float edge = inset * 0.55f;
        rect.set(edge, edge, getWidth() - edge, getHeight() - edge);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
