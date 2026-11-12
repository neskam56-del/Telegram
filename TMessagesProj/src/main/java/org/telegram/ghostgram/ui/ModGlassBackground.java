package org.telegram.ghostgram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.ghostgram.ModConfig;
import org.telegram.ghostgram.ModWallpaperManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/** Стекло показывает только обои: текст и соседние строки не попадают в размытие. */
public final class ModGlassBackground implements Drawable.Callback {
    private final View owner;
    private final Drawable wallpaper = ModWallpaperManager.createDrawable(false);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF destination = new RectF();
    private final float[] radii = new float[8];
    private Bitmap blurred;
    private String key;
    private int generation;
    private boolean dirty = true;
    private boolean released;

    public ModGlassBackground(View owner) {
        this.owner = owner;
        wallpaper.setCallback(this);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(ModGlassStyle.GLASS_BORDER_WIDTH_DP)));
    }

    public void invalidate() {
        dirty = true;
        owner.invalidate();
    }

    public void draw(Canvas canvas, RectF bounds, float topRadius, float bottomRadius,
                     float alpha, View root) {
        draw(canvas, bounds, topRadius, bottomRadius, alpha, root, owner);
    }

    public void draw(Canvas canvas, RectF bounds, float topRadius, float bottomRadius,
                     float alpha, View root, View target) {
        rect.set(bounds);
        for (int i = 0; i < 8; i++) {
            radii[i] = i < 4 ? topRadius : bottomRadius;
        }
        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        if (!ModConfig.isGlassActive()) {
            // Flat: solid fill + thin border, no blur, no highlight.
            paint.setColor(Theme.multAlpha(ModGlassStyle.FLAT_FILL_COLOR, alpha));
            canvas.drawPath(path, paint);
            border.setColor(Theme.multAlpha(ModGlassStyle.FLAT_BORDER_COLOR, alpha));
            canvas.drawPath(path, border);
            return;
        }
        canvas.save();
        canvas.clipPath(path);
        if (ModConfig.isBlurSupported() && ModConfig.glassBlur > 0) {
            prepare(root);
            if (blurred != null) {
                // Координаты обоев неподвижны, при прокрутке меняется только маска панели.
                float x = 0, y = 0;
                View current = target;
                while (current != root && current != null) {
                    x += current.getX();
                    y += current.getY();
                    current = current.getParent() instanceof View ? (View) current.getParent() : null;
                }
                destination.set(-x, -y, root.getWidth() - x, root.getRootView().getHeight() - y);
                paint.setColor(0xFFFFFFFF);
                paint.setAlpha(Math.round(255 * alpha));
                canvas.drawBitmap(blurred, null, destination, paint);
            }
        }
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(Math.round(255 * ModConfig.glassOpacity / 100f * alpha));
        canvas.drawPath(path, paint);
        // 1dp inner top highlight
        shine.setColor(Theme.multAlpha(ModGlassStyle.GLASS_TOP_HIGHLIGHT_COLOR, alpha));
        canvas.drawRect(rect.left, rect.top, rect.right,
                rect.top + Math.max(1f, AndroidUtilities.dpf2(ModGlassStyle.GLASS_TOP_HIGHLIGHT_HEIGHT_DP)), shine);
        canvas.restore();
        border.setColor(Theme.multAlpha(ModGlassStyle.GLASS_BORDER_COLOR, alpha));
        canvas.drawPath(path, border);
    }

    private void prepare(View root) {
        int width = root.getWidth();
        int height = root.getRootView().getHeight();
        if (width <= 0 || height <= 0 || released) return;
        String nextKey = width + ":" + height + ":" + ModConfig.getRevision() + ":" + Theme.isCurrentThemeDark();
        if (!dirty && nextKey.equals(key)) return;
        dirty = false;
        key = nextKey;
        final int token = ++generation;
        final float radius = AndroidUtilities.dpf2(ModGlassStyle.GLASS_BLUR_DP) * (ModConfig.glassBlur / 50f) / 3f;
        // Снимок маленький; декодирование картинки уже выполняет ModWallpaperManager в фоне.
        owner.post(() -> {
            if (released || token != generation) return;
            Bitmap source;
            try {
                source = Bitmap.createBitmap(Math.max(1, width / 3), Math.max(1, height / 3), Bitmap.Config.ARGB_8888);
                Canvas snapshot = new Canvas(source);
                snapshot.scale(source.getWidth() / (float) width, source.getHeight() / (float) height);
                wallpaper.setBounds(0, 0, width, height);
                wallpaper.draw(snapshot);
            } catch (Throwable e) {
                FileLog.e(e);
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                Bitmap result = null;
                try {
                    if (Build.VERSION.SDK_INT >= 31) result = GpuBlur.blur(source, radius);
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    source.recycle();
                }
                final Bitmap ready = result;
                AndroidUtilities.runOnUIThread(() -> {
                    if (released || token != generation) {
                        if (ready != null) ready.recycle();
                        return;
                    }
                    // Старый опубликованный Bitmap не recycle: его ещё может читать RenderThread.
                    blurred = ready;
                    owner.invalidate();
                });
            });
        });
    }

    public void release() {
        released = true;
        generation++;
        blurred = null;
        wallpaper.setCallback(null);
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        dirty = true;
        owner.invalidate();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { }

    /** GPU работает один раз на версию фона, все нативные ресурсы закрываются в этом же потоке. */
    @RequiresApi(31)
    private static class GpuBlur {
        static Bitmap blur(Bitmap source, float radius) {
            ImageReader reader = ImageReader.newInstance(source.getWidth(), source.getHeight(),
                    PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            HardwareRenderer renderer = new HardwareRenderer();
            RenderNode node = new RenderNode("ModWallpaperBlur");
            try {
                node.setPosition(0, 0, source.getWidth(), source.getHeight());
                ColorMatrix sat = new ColorMatrix();
                sat.setSaturation(ModGlassStyle.GLASS_SATURATION);
                RenderEffect blurEffect = RenderEffect.createBlurEffect(Math.max(0.1f, radius), Math.max(0.1f, radius), Shader.TileMode.CLAMP);
                node.setRenderEffect(RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat), blurEffect));
                Canvas recording = node.beginRecording();
                recording.drawBitmap(source, 0, 0, null);
                node.endRecording();
                renderer.setSurface(reader.getSurface());
                renderer.setContentRoot(node);
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
                try (Image image = reader.acquireNextImage()) {
                    if (image == null) return null;
                    try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                        if (buffer == null) return null;
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                        if (hardware == null) return null;
                        try {
                            return hardware.copy(Bitmap.Config.ARGB_8888, false);
                        } finally {
                            hardware.recycle();
                        }
                    }
                }
            } finally {
                renderer.destroy();
                node.discardDisplayList();
                reader.close();
            }
        }
    }
}
