package org.telegram.ghostgram;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

import org.telegram.ghostgram.ui.ModGlassStyle;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ModConfig — настройки визуальной части мода (Liquid Glass, обои).
 * Хранится в отдельном файле SharedPreferences, чтобы не смешиваться с GhostConfig,
 * и синхронизируется с нативным LiteMode.FLAG_LIQUID_GLASS.
 */
public class ModConfig {

    public interface Listener {
        void onModConfigChanged();
    }

    private static final String PREFS_NAME = "ghostgram_mod_ui";

    private static SharedPreferences prefs;
    private static Context appContext;
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static boolean liteModeListenerRegistered;

    // ---- Liquid Glass ----
    /** Главный переключатель стеклянных панелей (синхронизируется с LiteMode.FLAG_LIQUID_GLASS). */
    public static boolean glassEnabled = true;
    /** Сила размытия под панелями, 0..100 (50 = ~20dp по спецификации). */
    public static int glassBlur = ModGlassStyle.GLASS_DEFAULT_BLUR_PERCENT;
    /** Прозрачность белой заливки панели в процентах, 4..30 (по спецификации 9%). */
    public static int glassOpacity = ModGlassStyle.GLASS_DEFAULT_OPACITY_PERCENT;
    /** Отключать стекло при включённой экономии батареи. */
    public static boolean glassAutoOffBatterySaver = true;
    /** Отключать стекло на слабых устройствах. */
    public static boolean glassAutoOffLowEnd = true;

    // ---- Обои ----
    public static final int WP_NONE = 0;
    public static final int WP_COLOR = 1;
    public static final int WP_GRADIENT = 2;
    public static final int WP_IMAGE = 3;

    /** Источник обоев: нет (дефолтные градиентные обои), цвет, градиент или картинка. */
    public static int wallpaperMode = WP_NONE;
    public static int wallpaperColor1 = 0xFF1B2A49;
    public static int wallpaperColor2 = 0xFF6A3093;
    /** Затемнение кастомных обоев, 0..100 %. */
    public static int wallpaperDim = 30;
    /** Размытие самих кастомных обоев, 0..100. */
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
        syncLiteModeFlag();
        if (!liteModeListenerRegistered) {
            liteModeListenerRegistered = true;
            try {
                LiteMode.addOnPowerSaverAppliedListener(applied -> bumpAndNotify());
            } catch (Throwable ignore) {
            }
        }
    }

    private static void load() {
        if (prefs == null) return;
        boolean defaultGlass = true;
        if (!prefs.contains("glass_enabled")) {
            try {
                if (LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS)) {
                    defaultGlass = true;
                }
            } catch (Throwable ignore) {
            }
        }
        glassEnabled = prefs.getBoolean("glass_enabled", defaultGlass);
        glassBlur = clamp(prefs.getInt("glass_blur", ModGlassStyle.GLASS_DEFAULT_BLUR_PERCENT), 0, 100);
        glassOpacity = clamp(prefs.getInt("glass_opacity", ModGlassStyle.GLASS_DEFAULT_OPACITY_PERCENT), GLASS_OPACITY_MIN, GLASS_OPACITY_MAX);
        glassAutoOffBatterySaver = prefs.getBoolean("glass_auto_off_battery", true);
        glassAutoOffLowEnd = prefs.getBoolean("glass_auto_off_low_end", true);
        wallpaperMode = clamp(prefs.getInt("wp_mode", WP_NONE), WP_NONE, WP_IMAGE);
        wallpaperColor1 = prefs.getInt("wp_color1", 0xFF1B2A49);
        wallpaperColor2 = prefs.getInt("wp_color2", 0xFF6A3093);
        wallpaperDim = clamp(prefs.getInt("wp_dim", 30), 0, 100);
        wallpaperBlur = clamp(prefs.getInt("wp_blur", 0), 0, 100);
    }

    public static void setGlassEnabled(boolean enabled) {
        if (glassEnabled == enabled) return;
        glassEnabled = enabled;
        save();
    }

    public static void save() {
        if (prefs != null) {
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
        }
        syncLiteModeFlag();
        bumpAndNotify();
    }

    private static void syncLiteModeFlag() {
        try {
            if (LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS) != glassEnabled) {
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, glassEnabled);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void bumpAndNotify() {
        revision++;
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : listeners) {
                if (listener != null) {
                    listener.onModConfigChanged();
                }
            }
        });
    }

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
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
        if (appContext != null) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) {
                return true;
            }
        }
        try {
            if (LiteMode.isPowerSaverApplied()) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
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
