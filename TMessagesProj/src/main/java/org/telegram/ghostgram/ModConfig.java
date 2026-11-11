package org.telegram.ghostgram;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

/**
 * ModConfig — настройки визуальной части мода (Liquid Glass, обои).
 * Хранится в отдельном файле SharedPreferences, чтобы не смешиваться с GhostConfig.
 */
public class ModConfig {

    private static final String PREFS_NAME = "ghostgram_mod_ui";

    private static SharedPreferences prefs;
    private static Context appContext;

    // ---- Liquid Glass ----
    /** Главный переключатель стеклянных панелей. По умолчанию выключен: интерфейс как обычно. */
    public static boolean glassEnabled = false;
    /** Сила размытия под панелями, 0..100. */
    public static int glassBlur = 50;
    /** Прозрачность белой заливки панели в процентах, 4..30 (по ТЗ около 8-12). */
    public static int glassOpacity = 10;
    /** Отключать стекло при включённой экономии батареи. */
    public static boolean glassAutoOffBatterySaver = true;
    /** Отключать стекло на слабых устройствах. */
    public static boolean glassAutoOffLowEnd = true;

    // ---- Обои ----
    public static final int WP_NONE = 0;
    public static final int WP_COLOR = 1;
    public static final int WP_GRADIENT = 2;
    public static final int WP_IMAGE = 3;

    /** Источник обоев: нет, цвет, градиент или картинка из внутреннего хранилища. */
    public static int wallpaperMode = WP_NONE;
    public static int wallpaperColor1 = 0xFF1B2A49;
    public static int wallpaperColor2 = 0xFF6A3093;
    /** Затемнение обоев, 0..100 %. */
    public static int wallpaperDim = 30;
    /** Размытие самих обоев, 0..100. */
    public static int wallpaperBlur = 0;

    public static final int GLASS_OPACITY_MIN = 4;
    public static final int GLASS_OPACITY_MAX = 30;

    /**
     * Счётчик изменений. Экраны и кэши сравнивают его со своим значением,
     * чтобы понять, что настройки поменялись, и перерисоваться только в этом случае.
     */
    private static int revision = 0;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    private static void load() {
        if (prefs == null) return;
        glassEnabled = prefs.getBoolean("glass_enabled", false);
        glassBlur = clamp(prefs.getInt("glass_blur", 50), 0, 100);
        glassOpacity = clamp(prefs.getInt("glass_opacity", 10), GLASS_OPACITY_MIN, GLASS_OPACITY_MAX);
        glassAutoOffBatterySaver = prefs.getBoolean("glass_auto_off_battery", true);
        glassAutoOffLowEnd = prefs.getBoolean("glass_auto_off_low_end", true);
        wallpaperMode = clamp(prefs.getInt("wp_mode", WP_NONE), WP_NONE, WP_IMAGE);
        wallpaperColor1 = prefs.getInt("wp_color1", 0xFF1B2A49);
        wallpaperColor2 = prefs.getInt("wp_color2", 0xFF6A3093);
        wallpaperDim = clamp(prefs.getInt("wp_dim", 30), 0, 100);
        wallpaperBlur = clamp(prefs.getInt("wp_blur", 0), 0, 100);
    }

    public static void save() {
        if (prefs == null) return;
        prefs.edit()
                .putBoolean("glass_enabled", glassEnabled)
                .putInt("glass_blur", glassBlur)
                .putInt("glass_opacity", glassOpacity)
                .putBoolean("glass_auto_off_battery", glassAutoOffBatterySaver)
                .putBoolean("glass_auto_off_low_end", glassAutoOffLowEnd)
                .putInt("wp_mode", wallpaperMode)
                .putInt("wp_color1", wallpaperColor1)
                .putInt("wp_color2", wallpaperColor2)
                .putInt("wp_dim", wallpaperDim)
                .putInt("wp_blur", wallpaperBlur)
                .apply();
        revision++;
    }

    public static int getRevision() {
        return revision;
    }

    /**
     * Итоговое решение: рисовать ли стекло прямо сейчас.
     * Учитывает переключатель, экономию батареи и слабое устройство.
     */
    public static boolean isGlassActive() {
        if (!glassEnabled) return false;
        if (glassAutoOffBatterySaver && isBatterySaverOn()) return false;
        if (glassAutoOffLowEnd && isLowEndDevice()) return false;
        return true;
    }

    /** Размытие через RenderEffect доступно с Android 12 (API 31), ниже работает только запасной вариант. */
    public static boolean isBlurSupported() {
        return android.os.Build.VERSION.SDK_INT >= 31;
    }

    private static boolean isBatterySaverOn() {
        if (appContext == null) return false;
        PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    private static boolean isLowEndDevice() {
        if (appContext == null) return false;
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
