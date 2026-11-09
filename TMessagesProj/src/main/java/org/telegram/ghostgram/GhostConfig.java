package org.telegram.ghostgram;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Ghostgram — центральная конфигурация всех модов.
 * Все настройки хранятся в SharedPreferences "ghostgram_config".
 */
public class GhostConfig {

    private static SharedPreferences prefs;
    private static final String PREFS_NAME = "ghostgram_config";

    // ===== Ghost Mode =====
    public static boolean ghostModeEnabled = false;       // Мастер-переключатель
    public static boolean hideReadReceipts = true;         // Не отправлять "прочитано"
    public static boolean hideTypingStatus = true;         // Не показывать "печатает..."
    public static boolean hideOnlineStatus = true;         // Скрывать онлайн-статус
    public static boolean hideRecordingStatus = true;      // Скрывать "записывает голосовое..."

    // ===== Anti-Delete =====
    public static boolean antiDeleteEnabled = true;        // Перехватывать удалённые сообщения
    public static boolean antiDeleteShowIcon = true;        // Показывать 🗑️ иконку
    public static boolean antiEditEnabled = true;           // Сохранять историю редактирований

    // ===== Security =====
    public static boolean allowScreenshots = true;          // Разрешить скриншоты в секретных чатах
    public static boolean allowSaveMedia = true;            // Сохранять ограниченный контент
    public static boolean bypassNoForwards = true;          // Обходить запрет пересылки

    // ===== UI / Performance =====
    public static boolean force120Hz = true;                // Принудительно 120Hz
    public static boolean disableBlur = true;               // Отключить размытие
    public static boolean disableStories = true;            // Отключить Stories
    public static boolean useCustomTheme = true;            // Использовать кастомную тему

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfig();
    }

    public static void loadConfig() {
        if (prefs == null) return;

        // Ghost Mode
        ghostModeEnabled = prefs.getBoolean("ghost_mode_enabled", false);
        hideReadReceipts = prefs.getBoolean("hide_read_receipts", true);
        hideTypingStatus = prefs.getBoolean("hide_typing_status", true);
        hideOnlineStatus = prefs.getBoolean("hide_online_status", true);
        hideRecordingStatus = prefs.getBoolean("hide_recording_status", true);

        // Anti-Delete
        antiDeleteEnabled = prefs.getBoolean("anti_delete_enabled", true);
        antiDeleteShowIcon = prefs.getBoolean("anti_delete_show_icon", true);
        antiEditEnabled = prefs.getBoolean("anti_edit_enabled", true);

        // Security
        allowScreenshots = prefs.getBoolean("allow_screenshots", true);
        allowSaveMedia = prefs.getBoolean("allow_save_media", true);
        bypassNoForwards = prefs.getBoolean("bypass_no_forwards", true);

        // UI
        force120Hz = prefs.getBoolean("force_120hz", true);
        disableBlur = prefs.getBoolean("disable_blur", true);
        disableStories = prefs.getBoolean("disable_stories", true);
        useCustomTheme = prefs.getBoolean("use_custom_theme", true);
    }

    public static void saveConfig() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("ghost_mode_enabled", ghostModeEnabled);
        editor.putBoolean("hide_read_receipts", hideReadReceipts);
        editor.putBoolean("hide_typing_status", hideTypingStatus);
        editor.putBoolean("hide_online_status", hideOnlineStatus);
        editor.putBoolean("hide_recording_status", hideRecordingStatus);

        editor.putBoolean("anti_delete_enabled", antiDeleteEnabled);
        editor.putBoolean("anti_delete_show_icon", antiDeleteShowIcon);
        editor.putBoolean("anti_edit_enabled", antiEditEnabled);

        editor.putBoolean("allow_screenshots", allowScreenshots);
        editor.putBoolean("allow_save_media", allowSaveMedia);
        editor.putBoolean("bypass_no_forwards", bypassNoForwards);

        editor.putBoolean("force_120hz", force120Hz);
        editor.putBoolean("disable_blur", disableBlur);
        editor.putBoolean("disable_stories", disableStories);
        editor.putBoolean("use_custom_theme", useCustomTheme);

        editor.apply();
    }

    public static void toggleGhostMode() {
        ghostModeEnabled = !ghostModeEnabled;
        saveConfig();
    }

    public static void setBooleanConfig(String key, boolean value) {
        if (prefs == null) return;
        prefs.edit().putBoolean(key, value).apply();
        loadConfig();
    }
}

