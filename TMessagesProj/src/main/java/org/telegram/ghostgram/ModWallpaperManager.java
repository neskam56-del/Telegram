package org.telegram.ghostgram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Менеджер обоев мода.
 * Картинка декодируется и размывается один раз в фоновом потоке, готовый Bitmap кэшируется
 * и дальше только рисуется. Затемнение накладывается при отрисовке и ничего не пересчитывает.
 * Все методы, кроме фоновой сборки, вызываются из UI-потока.
 */
public class ModWallpaperManager {

    private static final String FILE_NAME = "ghostgram_wallpaper.jpg";

    /** Готовая картинка (уже уменьшенная и размытая) и ключ параметров, для которых она собрана. */
    private static Bitmap cached;
    private static String cachedKey;
    /** Ключ сборки, которая идёт прямо сейчас; null, если ничего не собирается. */
    private static String buildingKey;
    /** Drawable, ждущие готовую картинку. Слабые ссылки, чтобы не удерживать экраны. */
    private static final ArrayList<WeakReference<Drawable>> waiting = new ArrayList<>();

    /** Файл обоев во внутреннем хранилище приложения (его пишет выбор обоев). */
    public static File getWallpaperFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME);
    }

    /** Новый Drawable обоев. Сам ничего не держит, кроме настроек и общего кэша. */
    public static Drawable createDrawable() {
        return new ModWallpaperDrawable();
    }

    /** Сбрасывает кэш, например при нехватке памяти. Drawable соберут картинку заново при следующей отрисовке. */
    public static void releaseCache() {
        cached = null;
        cachedKey = null;
        buildingKey = null;
    }

    /**
     * Возвращает готовую картинку или null, если она ещё собирается (или режим не «картинка»).
     * Пока собирается новая версия, возвращается предыдущая, чтобы фон не мигал.
     */
    @Nullable
    private static Bitmap obtain(@NonNull Drawable requester) {
        if (ModConfig.wallpaperMode != ModConfig.WP_IMAGE) {
            cached = null;
            cachedKey = null;
            return null;
        }
        final File file = getWallpaperFile();
        if (!file.isFile()) {
            return null;
        }
        // Длинная сторона цели: экран целиком без размытия, четверть экрана при размытии (быстрее и мягче).
        final int screenMax = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        final int blur = ModConfig.wallpaperBlur;
        final int target = Math.max(64, blur > 0 ? screenMax / 4 : screenMax);
        final String key = file.lastModified() + ":" + file.length() + ":" + blur + ":" + target;

        if (key.equals(cachedKey)) {
            return cached;
        }
        // Без дублей: obtain вызывается на каждом кадре, пока картинка собирается.
        boolean already = false;
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).get() == requester) {
                already = true;
                break;
            }
        }
        if (!already) {
            waiting.add(new WeakReference<>(requester));
        }
        if (!key.equals(buildingKey)) {
            buildingKey = key;
            Utilities.globalQueue.postRunnable(() -> {
                final Bitmap result = build(file, target, blur);
                AndroidUtilities.runOnUIThread(() -> onBuilt(key, result));
            });
        }
        return cached;
    }

    /** Фоновая сборка: декодирование с уменьшением и размытие. Ошибки не пробрасываются. */
    @Nullable
    private static Bitmap build(File file, int target, int blur) {
        try {
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            final int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / (sample * 2) >= target) {
                sample *= 2;
            }
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inMutable = true; // нужно для размытия на месте
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null && blur > 0) {
                // Радиус считается от размера уменьшенной картинки: 1..25 при ползунке 1..100.
                Utilities.stackBlurBitmap(bitmap, Math.max(1, Math.round(blur * 0.25f)));
            }
            return bitmap;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void onBuilt(String key, @Nullable Bitmap result) {
        if (!key.equals(buildingKey)) {
            return; // за время сборки параметры изменились, результат устарел
        }
        buildingKey = null;
        if (result != null) {
            cached = result;
            cachedKey = key;
        }
        for (int i = 0; i < waiting.size(); i++) {
            final Drawable drawable = waiting.get(i).get();
            if (drawable != null) {
                drawable.invalidateSelf();
            }
        }
        waiting.clear();
    }

    /** Фон: цвет, градиент или картинка. Центрирование с обрезкой, затемнение сверху. */
    private static class ModWallpaperDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint();
        private final Rect src = new Rect();
        private int alpha = 255;

        private int shaderHeight;
        private int shaderTop;
        private int shaderColor1;
        private int shaderColor2;

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            final int w = bounds.width();
            final int h = bounds.height();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (ModConfig.wallpaperMode == ModConfig.WP_NONE) {
                // Обои отключены: обычный фон экрана, чтобы он не оставался прозрачным.
                paint.setShader(null);
                paint.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray));
                paint.setAlpha(alpha);
                canvas.drawRect(bounds, paint);
                return;
            }
            paint.setAlpha(alpha);
            switch (ModConfig.wallpaperMode) {
                case ModConfig.WP_COLOR:
                    paint.setShader(null);
                    paint.setColor(ModConfig.wallpaperColor1);
                    paint.setAlpha(alpha);
                    canvas.drawRect(bounds, paint);
                    break;
                case ModConfig.WP_GRADIENT:
                    // Шейдер пересоздаётся только при смене размера или цветов.
                    if (paint.getShader() == null || shaderHeight != h || shaderTop != bounds.top
                            || shaderColor1 != ModConfig.wallpaperColor1 || shaderColor2 != ModConfig.wallpaperColor2) {
                        shaderHeight = h;
                        shaderTop = bounds.top;
                        shaderColor1 = ModConfig.wallpaperColor1;
                        shaderColor2 = ModConfig.wallpaperColor2;
                        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                                shaderColor1, shaderColor2, Shader.TileMode.CLAMP));
                    }
                    paint.setAlpha(alpha);
                    canvas.drawRect(bounds, paint);
                    break;
                case ModConfig.WP_IMAGE: {
                    final Bitmap bitmap = obtain(this);
                    if (bitmap == null || bitmap.isRecycled()) {
                        // Пока картинка собирается, показываем первый цвет, чтобы не было пустого экрана.
                        paint.setShader(null);
                        paint.setColor(ModConfig.wallpaperColor1);
                        paint.setAlpha(alpha);
                        canvas.drawRect(bounds, paint);
                    } else {
                        paint.setShader(null);
                        paint.setColor(0xFFFFFFFF);
                        paint.setAlpha(alpha);
                        // center-crop: масштаб по большей стороне, лишнее обрезается
                        final float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
                        final int srcW = Math.min(bitmap.getWidth(), Math.round(w / scale));
                        final int srcH = Math.min(bitmap.getHeight(), Math.round(h / scale));
                        final int left = (bitmap.getWidth() - srcW) / 2;
                        final int top = (bitmap.getHeight() - srcH) / 2;
                        src.set(left, top, left + srcW, top + srcH);
                        canvas.drawBitmap(bitmap, src, bounds, paint);
                    }
                    break;
                }
            }
            final int dim = ModConfig.wallpaperDim;
            if (dim > 0) {
                dimPaint.setColor(0xFF000000);
                dimPaint.setAlpha(Math.round(255f * dim / 100f * alpha / 255f));
                canvas.drawRect(bounds, dimPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
