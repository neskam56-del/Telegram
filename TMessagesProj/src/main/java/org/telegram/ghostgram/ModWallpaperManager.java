package org.telegram.ghostgram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import org.telegram.ghostgram.ui.ModGlassStyle;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private static int fileGeneration;
    private static int requestedGeneration = -1;
    private static int requestedBlur = -1;
    private static int requestedTarget = -1;
    private static String requestedKey;
    /** Drawable, ждущие готовую картинку. Слабые ссылки, чтобы не удерживать экраны. */
    private static final ArrayList<WeakReference<Drawable>> waiting = new ArrayList<>();

    /** Файл обоев во внутреннем хранилище приложения (его пишет выбор обоев). */
    public static File getWallpaperFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME);
    }

    /** Проверяет, сохранён ли пользовательский файл обоев. */
    public static boolean hasWallpaperImage() {
        File file = getWallpaperFile();
        return file.isFile() && file.length() > 0;
    }

    /** Удаляет сохранённый файл обоев и сбрасывает кэш. */
    public static void deleteWallpaperFile() {
        try {
            File file = getWallpaperFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        releaseCache();
    }

    /**
     * Асинхронно декодирует выбранную пользователем картинку из галереи с учётом EXIF-поворота,
     * сжимает до разумного разрешения экрана и сохраняет во внутренний файл обоев.
     */
    public static void saveWallpaperFromUri(@NonNull Context context, @NonNull Uri uri, @Nullable Utilities.Callback<Boolean> onDone) {
        final Context appCtx = context.getApplicationContext();
        final int screenMax = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        final int maxSide = Math.max(2560, screenMax * 2);

        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            File tmpFile = new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME + ".tmp");
            Bitmap decoded = null;
            try {
                int orientation = ExifInterface.ORIENTATION_NORMAL;
                try (InputStream exifStream = appCtx.getContentResolver().openInputStream(uri)) {
                    if (exifStream != null) {
                        ExifInterface exif = new ExifInterface(exifStream);
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    }
                } catch (Throwable ignore) {
                }

                final BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream boundsStream = appCtx.getContentResolver().openInputStream(uri)) {
                    if (boundsStream != null) {
                        BitmapFactory.decodeStream(boundsStream, null, bounds);
                    }
                }

                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    int sample = 1;
                    int longest = Math.max(bounds.outWidth, bounds.outHeight);
                    while (longest / (sample * 2) >= maxSide) {
                        sample *= 2;
                    }
                    final BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    try (InputStream decodeStream = appCtx.getContentResolver().openInputStream(uri)) {
                        if (decodeStream != null) {
                            decoded = BitmapFactory.decodeStream(decodeStream, null, opts);
                        }
                    }
                }

                if (decoded != null) {
                    decoded = applyExifMatrix(decoded, orientation, maxSide);
                    try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        ok = decoded.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.flush();
                    }
                    if (ok && tmpFile.length() > 0) {
                        File dest = getWallpaperFile();
                        if (dest.exists() && !dest.delete()) {
                            // Перезапишем поверх, если не удалился
                        }
                        if (!tmpFile.renameTo(dest)) {
                            try (InputStream in = new java.io.FileInputStream(tmpFile);
                                 FileOutputStream out = new FileOutputStream(dest)) {
                                byte[] buf = new byte[16384];
                                int r;
                                while ((r = in.read(buf)) != -1) {
                                    out.write(buf, 0, r);
                                }
                                out.flush();
                            }
                            tmpFile.delete();
                        }
                        ok = dest.isFile() && dest.length() > 0;
                    } else {
                        ok = false;
                        tmpFile.delete();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
                ok = false;
                try {
                    tmpFile.delete();
                } catch (Throwable ignore) {
                }
            } finally {
                if (decoded != null && !decoded.isRecycled()) {
                    decoded.recycle();
                }
            }

            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (success) {
                    releaseCache();
                    ModConfig.wallpaperMode = ModConfig.WP_IMAGE;
                    ModConfig.save();
                }
                if (onDone != null) {
                    onDone.run(success);
                }
            });
        });
    }

    private static Bitmap applyExifMatrix(@NonNull Bitmap src, int orientation, int maxSide) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                break;
        }
        int bw = src.getWidth();
        int bh = src.getHeight();
        int longest = Math.max(bw, bh);
        if (longest > maxSide && maxSide > 0) {
            float scale = maxSide / (float) longest;
            matrix.postScale(scale, scale);
        }
        if (matrix.isIdentity()) {
            return src;
        }
        try {
            Bitmap transformed = Bitmap.createBitmap(src, 0, 0, bw, bh, matrix, true);
            if (transformed != src) {
                src.recycle();
            }
            return transformed;
        } catch (Throwable t) {
            FileLog.e(t);
            return src;
        }
    }

    /** Новый Drawable обоев. Сам ничего не держит, кроме настроек и общего кэша. */
    public static Drawable createDrawable() {
        return new ModWallpaperDrawable(true);
    }

    /** Вариант без точечной сетки (используется для фонового размытия под стеклянными панелями). */
    public static Drawable createDrawable(boolean includeDotGrid) {
        return new ModWallpaperDrawable(includeDotGrid);
    }

    /** Сбрасывает кэш, например при нехватке памяти. Drawable соберут картинку заново при следующей отрисовке. */
    public static void releaseCache() {
        fileGeneration++;
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
        // Длинная сторона цели: экран целиком без размытия, четверть экрана при размытии (быстрее и мягче).
        final int screenMax = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        final int blur = ModConfig.wallpaperBlur;
        final int target = Math.max(64, blur > 0 ? screenMax / 4 : screenMax);
        // The private file is replaced only by save/delete, both of which release the cache.
        if (requestedGeneration != fileGeneration || requestedBlur != blur || requestedTarget != target) {
            requestedGeneration = fileGeneration;
            requestedBlur = blur;
            requestedTarget = target;
            requestedKey = fileGeneration + ":" + blur + ":" + target;
        }
        final String key = requestedKey;

        if (key.equals(cachedKey)) {
            return cached;
        }
        // Без дублей и мёртвых ссылок: obtain вызывается на каждом кадре, пока картинка собирается.
        boolean already = false;
        for (int i = waiting.size() - 1; i >= 0; i--) {
            final Drawable d = waiting.get(i).get();
            if (d == null) {
                waiting.remove(i);
            } else if (d == requester) {
                already = true;
            }
        }
        if (!already) {
            waiting.add(new WeakReference<>(requester));
        }
        if (!key.equals(buildingKey)) {
            buildingKey = key;
            Utilities.globalQueue.postRunnable(() -> {
                final Bitmap result = build(getWallpaperFile(), target, blur);
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
        // Запоминаем ключ даже при null, чтобы повреждённый файл не вызывал бесконечное фоновое декодирование.
        cached = result;
        cachedKey = key;
        for (int i = 0; i < waiting.size(); i++) {
            final Drawable drawable = waiting.get(i).get();
            if (drawable != null) {
                drawable.invalidateSelf();
            }
        }
        waiting.clear();
    }

    /** Фон: дефолтный Liquid Glass / Flat фон либо пользовательские обои (цвет, градиент, картинка). */
    private static class ModWallpaperDrawable extends Drawable {

        private final boolean includeDotGrid;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint();
        private final Rect src = new Rect();
        private final RadialGradient[] defaultRadials = new RadialGradient[ModGlassStyle.WP_RADIAL_COLORS.length];
        private int radialLeft = Integer.MIN_VALUE;
        private int radialTop = Integer.MIN_VALUE;
        private int radialW = -1;
        private int radialH = -1;
        private int alpha = 255;

        private int shaderHeight;
        private int shaderTop;
        private int shaderColor1;
        private int shaderColor2;

        ModWallpaperDrawable(boolean includeDotGrid) {
            this.includeDotGrid = includeDotGrid;
        }

        private void ensureDefaultRadials(Rect bounds, int w, int h) {
            if (radialLeft == bounds.left && radialTop == bounds.top && radialW == w && radialH == h) {
                return;
            }
            radialLeft = bounds.left;
            radialTop = bounds.top;
            radialW = w;
            radialH = h;
            float refSize = Math.max(w, h);
            for (int i = 0; i < ModGlassStyle.WP_RADIAL_COLORS.length; i++) {
                float cx = bounds.left + w * ModGlassStyle.WP_RADIAL_SPECS[i][0];
                float cy = bounds.top + h * ModGlassStyle.WP_RADIAL_SPECS[i][1];
                float radius = Math.max(1f, refSize * ModGlassStyle.WP_RADIAL_SPECS[i][2]);
                int c = ModGlassStyle.WP_RADIAL_COLORS[i];
                defaultRadials[i] = new RadialGradient(
                        cx, cy, radius,
                        new int[] {c, c & 0x00FFFFFF},
                        new float[] {0f, 1f},
                        Shader.TileMode.CLAMP
                );
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            final int w = bounds.width();
            final int h = bounds.height();
            if (w <= 0 || h <= 0) {
                return;
            }
            paint.setAlpha(alpha);
            switch (ModConfig.wallpaperMode) {
                case ModConfig.WP_NONE:
                default:
                    paint.setShader(null);
                    paint.setColor(ModGlassStyle.BG_BASE_COLOR);
                    paint.setAlpha(alpha);
                    canvas.drawRect(bounds, paint);

                    if (ModConfig.isGlassActive()) {
                        ensureDefaultRadials(bounds, w, h);
                        radialPaint.setAlpha(alpha);
                        for (int i = 0; i < defaultRadials.length; i++) {
                            radialPaint.setShader(defaultRadials[i]);
                            canvas.drawRect(bounds, radialPaint);
                        }
                        if (includeDotGrid) {
                            dotPaint.setShader(ModGlassStyle.getDotGridShader());
                            dotPaint.setAlpha(Math.round(255f * ModGlassStyle.DOT_LAYER_ALPHA * (alpha / 255f)));
                            canvas.drawRect(bounds, dotPaint);
                        }
                    }
                    break;
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
                    if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                        // Пока картинка собирается, показываем базовый тёмный цвет.
                        paint.setShader(null);
                        paint.setColor(ModGlassStyle.BG_BASE_COLOR);
                        paint.setAlpha(alpha);
                        canvas.drawRect(bounds, paint);
                    } else {
                        paint.setShader(null);
                        paint.setColor(0xFFFFFFFF);
                        paint.setAlpha(alpha);
                        // center-crop: масштаб по большей стороне, лишнее обрезается
                        final int bw = bitmap.getWidth();
                        final int bh = bitmap.getHeight();
                        final float scale = Math.max(w / (float) bw, h / (float) bh);
                        final int srcW = Math.max(1, Math.min(bw, Math.round(w / scale)));
                        final int srcH = Math.max(1, Math.min(bh, Math.round(h / scale)));
                        final int left = (bw - srcW) / 2;
                        final int top = (bh - srcH) / 2;
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
