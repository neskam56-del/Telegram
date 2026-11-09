package org.telegram.ghostgram.ui;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.messenger.FileLog;

/**
 * Display120HzFix — принудительно включает 120Hz на устройствах с поддержкой
 * высокой частоты обновления экрана (Redmi Note 10 Pro — 120Hz AMOLED).
 *
 * Telegram по умолчанию использует 60Hz для экономии батареи.
 * Этот фикс заставляет систему всегда использовать максимальный refresh rate.
 */
public class Display120HzFix {

    private static final String TAG = "Display120Hz";

    /**
     * Устанавливает максимальный refresh rate для Activity.
     * Вызывать в onCreate() или onResume() каждой Activity.
     *
     * @param activity текущая Activity
     */
    public static void applyHighRefreshRate(Activity activity) {
        if (!GhostConfig.force120Hz) return;
        if (activity == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Window window = activity.getWindow();
                if (window != null) {
                    WindowManager.LayoutParams params = window.getAttributes();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ (API 30) — используем setFrameRateCategory
                        // Устанавливаем preferredRefreshRate на максимум
                        Display display = activity.getDisplay();
                        if (display != null) {
                            Display.Mode[] modes = display.getSupportedModes();
                            float maxRate = 60f;
                            Display.Mode bestMode = null;
                            for (Display.Mode mode : modes) {
                                if (mode.getRefreshRate() > maxRate) {
                                    maxRate = mode.getRefreshRate();
                                    bestMode = mode;
                                }
                            }
                            if (bestMode != null) {
                                params.preferredDisplayModeId = bestMode.getModeId();
                                params.preferredRefreshRate = maxRate;
                                window.setAttributes(params);
                                FileLog.d(TAG + ": set refresh rate to " + maxRate + "Hz");
                            }
                        }
                    } else {
                        // Android 6-10 — используем preferredRefreshRate
                        params.preferredRefreshRate = 120f;
                        window.setAttributes(params);
                        FileLog.d(TAG + ": set preferredRefreshRate to 120Hz (legacy)");
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": error setting refresh rate", e);
        }
    }

    /**
     * Отключает аппаратное размытие (blur) для производительности.
     * На средних устройствах типа Snapdragon 732G blur жрёт FPS.
     */
    public static boolean shouldDisableBlur() {
        return GhostConfig.disableBlur;
    }

    /**
     * Должны ли мы отключать Stories (прелоадинг, UI).
     * Экономит RAM и CPU.
     */
    public static boolean shouldDisableStories() {
        return GhostConfig.disableStories;
    }
}

