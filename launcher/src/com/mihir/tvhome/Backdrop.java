package com.mihir.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The background (photo, scrim and the glow behind the dock) drawn on its own 1080p surface.
 * TV GPUs are slow at filling a 4K screen with images and gradients: as plain views they were repainted
 * on every frame of every focus animation (~150 ms a frame). Here they're drawn only when something
 * changes, at 1080p, and the display hardware scales and composites the surface for free.
 */
final class Backdrop extends SurfaceView implements SurfaceHolder.Callback {
    static final int W = 1920, H = 1080;

    private final int[] scrim;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Bitmap photo, previous;
    private float mix = 1f;            // crossfade from previous to photo
    private int glowColor = Color.TRANSPARENT;
    private float glowAlpha = 1f;      // dimmed while browsing the grid
    private float shade;               // extra darkening behind the grid
    private boolean ready;
    private ValueAnimator fade;

    Backdrop(Context c, int[] scrim) {
        super(c);
        this.scrim = scrim;
        getHolder().setFixedSize(W, H);
        getHolder().addCallback(this);
    }

    void setPhoto(Bitmap b, long fadeMs) {
        if (fade != null) fade.cancel();
        previous = photo;
        photo = b;
        if (previous == null || fadeMs <= 0) {
            previous = null;
            mix = 1f;
            draw();
            return;
        }
        // The only animation; it runs once every few minutes, at a gentle 20 fps.
        fade = ValueAnimator.ofFloat(0f, 1f).setDuration(fadeMs);
        fade.setFrameDelay(50);
        fade.addUpdateListener(a -> { mix = (float) a.getAnimatedValue(); draw(); });
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { previous = null; mix = 1f; draw(); }
        });
        fade.start();
    }

    void setGlow(int color) {
        if (color == glowColor) return;
        glowColor = color;
        draw();
    }

    void setDim(float glowAlpha, float shade) {
        if (glowAlpha == this.glowAlpha && shade == this.shade) return;
        this.glowAlpha = glowAlpha;
        this.shade = shade;
        draw();
    }

    private void draw() {
        if (!ready) return;
        Canvas c = getHolder().lockHardwareCanvas();
        if (c == null) return;
        try {
            c.drawColor(Color.BLACK);
            if (previous != null) drawPhoto(c, previous, 255);
            if (photo != null) drawPhoto(c, photo, Math.round(mix * 255));
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0, 0, 0, H, scrim, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            if (Color.alpha(glowColor) > 0 && glowAlpha > 0) {
                int[] g = {withAlpha(glowColor, 0.5f * glowAlpha), withAlpha(glowColor, 0.14f * glowAlpha), Color.TRANSPARENT};
                paint.setShader(new RadialGradient(W * 0.5f, H * 0.78f, W * 0.62f, g, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, paint);
            }
            paint.setShader(null);
            if (shade > 0) c.drawColor(Color.argb(Math.round(shade * 0xB3), 0, 0, 0));
        } finally {
            getHolder().unlockCanvasAndPost(c);
        }
    }

    /** Centre-crop the photo to 16:9. */
    private void drawPhoto(Canvas c, Bitmap b, int alpha) {
        float scale = Math.max((float) W / b.getWidth(), (float) H / b.getHeight());
        int sw = Math.round(W / scale), sh = Math.round(H / scale);
        int sx = (b.getWidth() - sw) / 2, sy = (b.getHeight() - sh) / 2;
        paint.setShader(null);
        paint.setAlpha(alpha);
        c.drawBitmap(b, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, W, H), paint);
    }

    /**
     * A root layout for windows with a Backdrop behind them. Android trims a window over a SurfaceView to the
     * views' unzoomed bounds, which cut off focus zooms at their edges; this keeps the whole window.
     */
    static android.widget.FrameLayout root(Context c) {
        return new android.widget.FrameLayout(c) {
            @Override public boolean gatherTransparentRegion(android.graphics.Region region) {
                if (region != null) region.setEmpty();
                return false;
            }
        };
    }

    private static int withAlpha(int c, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    @Override public void surfaceCreated(SurfaceHolder h) { ready = true; draw(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { draw(); }
    @Override public void surfaceDestroyed(SurfaceHolder h) { ready = false; if (fade != null) fade.cancel(); }
}
