package org.telegram.ghostgram;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.NotificationCenter;

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
    public static boolean showGhostButton = true;          // Показывать кнопку в шапке чатов

    // ===== Anti-Delete =====
    public static boolean antiDeleteEnabled = true;        // Перехватывать удалённые сообщения
    public static boolean antiDeleteShowIcon = true;        // Показывать 🗑️ иконку
    public static boolean antiEditEnabled = true;           // Сохранять историю редактирований

    // ===== Security =====
    public static boolean allowScreenshots = true;          // Разрешить скриншоты в секретных чатах
    public static boolean allowSaveMedia = true;            // Сохранять ограниченный контент
    public static boolean bypassNoForwards = true;          // Обходить запрет пересылки
    public static boolean forwardNoAuthorEnabled = true;    // Пересылка без имени автора
    public static boolean customPhoneEnabled = false;       // Показывать свой номер вместо реального (только визуально)
    public static String customPhone = "";                  // Произвольный номер, как ввёл пользователь
    public static boolean hidePhoneNumber = false;          // Скрытие номера телефона в профиле и настройках

    // ===== UI / Performance =====
    public static boolean force120Hz = true;                // Принудительно 120Hz
    public static boolean disableBlur = true;               // Отключить размытие
    public static boolean disableStories = true;            // Отключить Stories
    public static boolean useCustomTheme = true;            // Использовать кастомную тему

    // ===== Features 6 - 10 =====
    public static boolean localPremiumEnabled = true;       // Разблокировка платных возможностей (иконки, без рекламы)
    public static boolean compactDialogs = false;           // Компактный список чатов
    public static boolean hideChannelRecommendations = true;// Скрытие рекомендаций каналов
    public static int dialogAvatarRadius = 26;              // Радиус скругления аватарок

    public static boolean deviceSpoofEnabled = false;       // Подмена устройства
    public static int deviceSpoofIndex = 0;                 // Индекс пресета устройства
    public static final String[][] DEVICE_PRESETS = new String[][] {
        {"Samsung Galaxy S24 Ultra", "Samsung SM-S928B", "SDK 34 (Android 14)", "11.2.1 (5234)"},
        {"Google Pixel 9 Pro", "Google Pixel 9 Pro", "SDK 35 (Android 15)", "11.2.1 (5234)"},
        {"iPhone 16 Pro Max", "iPhone 16 Pro Max", "iOS 18.1", "11.2.0 (2981)"},
        {"Telegram Desktop (Windows)", "PC 64bit", "Windows 11 x64", "5.6.3 x64"}
    };

    public static boolean fakeLocationEnabled = false;      // Подмена геолокации
    public static double fakeLatitude = 55.7558;            // Широта по умолчанию (Москва)
    public static double fakeLongitude = 37.6173;           // Долгота по умолчанию

    public static int voiceChangerPreset = 0;               // Изменитель голоса: 0-Off, 1-Bass, 2-High, 3-Robot, 4-Echo, 5-Whisper

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
        showGhostButton = prefs.getBoolean("show_ghost_button", true);

        // Anti-Delete
        antiDeleteEnabled = prefs.getBoolean("anti_delete_enabled", true);
        antiDeleteShowIcon = prefs.getBoolean("anti_delete_show_icon", true);
        antiEditEnabled = prefs.getBoolean("anti_edit_enabled", true);

        // Security
        allowScreenshots = prefs.getBoolean("allow_screenshots", true);
        allowSaveMedia = prefs.getBoolean("allow_save_media", true);
        bypassNoForwards = prefs.getBoolean("bypass_no_forwards", true);
        forwardNoAuthorEnabled = prefs.getBoolean("forward_no_author", true);
        hidePhoneNumber = prefs.getBoolean("hide_phone_number", false);

        // UI
        force120Hz = prefs.getBoolean("force_120hz", true);
        disableBlur = prefs.getBoolean("disable_blur", true);
        disableStories = prefs.getBoolean("disable_stories", true);
        useCustomTheme = prefs.getBoolean("use_custom_theme", true);

        // Features 6 - 10
        localPremiumEnabled = prefs.getBoolean("local_premium_enabled", true);
        compactDialogs = prefs.getBoolean("compact_dialogs", false);
        hideChannelRecommendations = prefs.getBoolean("hide_channel_recommendations", true);
        dialogAvatarRadius = prefs.getInt("dialog_avatar_radius", 26);
        deviceSpoofEnabled = prefs.getBoolean("device_spoof_enabled", false);
        deviceSpoofIndex = prefs.getInt("device_spoof_index", 0);
        fakeLocationEnabled = prefs.getBoolean("fake_location_enabled", false);
        fakeLatitude = Double.longBitsToDouble(prefs.getLong("fake_latitude", Double.doubleToLongBits(55.7558)));
        fakeLongitude = Double.longBitsToDouble(prefs.getLong("fake_longitude", Double.doubleToLongBits(37.6173)));
        voiceChangerPreset = prefs.getInt("voice_changer_preset", 0);
        customPhoneEnabled = prefs.getBoolean("custom_phone_enabled", false);
        customPhone = prefs.getString("custom_phone", "");
    }

    public static void saveConfig() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("ghost_mode_enabled", ghostModeEnabled);
        editor.putBoolean("hide_read_receipts", hideReadReceipts);
        editor.putBoolean("hide_typing_status", hideTypingStatus);
        editor.putBoolean("hide_online_status", hideOnlineStatus);
        editor.putBoolean("hide_recording_status", hideRecordingStatus);
        editor.putBoolean("show_ghost_button", showGhostButton);

        editor.putBoolean("anti_delete_enabled", antiDeleteEnabled);
        editor.putBoolean("anti_delete_show_icon", antiDeleteShowIcon);
        editor.putBoolean("anti_edit_enabled", antiEditEnabled);

        editor.putBoolean("allow_screenshots", allowScreenshots);
        editor.putBoolean("allow_save_media", allowSaveMedia);
        editor.putBoolean("bypass_no_forwards", bypassNoForwards);
        editor.putBoolean("forward_no_author", forwardNoAuthorEnabled);
        editor.putBoolean("hide_phone_number", hidePhoneNumber);

        editor.putBoolean("force_120hz", force120Hz);
        editor.putBoolean("disable_blur", disableBlur);
        editor.putBoolean("disable_stories", disableStories);
        editor.putBoolean("use_custom_theme", useCustomTheme);

        editor.putBoolean("local_premium_enabled", localPremiumEnabled);
        editor.putBoolean("compact_dialogs", compactDialogs);
        editor.putBoolean("hide_channel_recommendations", hideChannelRecommendations);
        editor.putInt("dialog_avatar_radius", dialogAvatarRadius);
        editor.putBoolean("device_spoof_enabled", deviceSpoofEnabled);
        editor.putInt("device_spoof_index", deviceSpoofIndex);
        editor.putBoolean("fake_location_enabled", fakeLocationEnabled);
        editor.putLong("fake_latitude", Double.doubleToLongBits(fakeLatitude));
        editor.putLong("fake_longitude", Double.doubleToLongBits(fakeLongitude));
        editor.putInt("voice_changer_preset", voiceChangerPreset);
        editor.putBoolean("custom_phone_enabled", customPhoneEnabled);
        editor.putString("custom_phone", customPhone);

        editor.apply();
    }

    public static void toggleGhostMode() {
        ghostModeEnabled = !ghostModeEnabled;
        saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.ghostModeChanged);
    }

    public static void setBooleanConfig(String key, boolean value) {
        if (prefs == null) return;
        prefs.edit().putBoolean(key, value).apply();
        loadConfig();
    }

    public static String formatMaskedPhone(String rawPhone) {
        if (android.text.TextUtils.isEmpty(rawPhone)) {
            return "";
        }
        if (customPhoneEnabled && !android.text.TextUtils.isEmpty(customPhone)) {
            return customPhone;
        }
        String formatted = org.telegram.PhoneFormat.PhoneFormat.getInstance().format("+" + rawPhone);
        if (!hidePhoneNumber) {
            return formatted;
        }
        // Маскируем номер: оставляем код страны (+7/...) и последние 2 цифры
        String clean = rawPhone.replaceAll("[^0-9]", "");
        if (clean.length() <= 4) {
            return "***";
        }
        String last2 = clean.substring(clean.length() - 2);
        String prefix = clean.substring(0, Math.min(1, clean.length() - 4));
        return "+" + prefix + " *** *** ** " + last2;
    }

    public static String getSpoofedModel(String defaultModel) {
        if (!deviceSpoofEnabled || deviceSpoofIndex < 0 || deviceSpoofIndex >= DEVICE_PRESETS.length) {
            return defaultModel;
        }
        return DEVICE_PRESETS[deviceSpoofIndex][1];
    }

    public static String getSpoofedSystemVersion(String defaultSys) {
        if (!deviceSpoofEnabled || deviceSpoofIndex < 0 || deviceSpoofIndex >= DEVICE_PRESETS.length) {
            return defaultSys;
        }
        return DEVICE_PRESETS[deviceSpoofIndex][2];
    }

    public static String getSpoofedAppVersion(String defaultApp) {
        if (!deviceSpoofEnabled || deviceSpoofIndex < 0 || deviceSpoofIndex >= DEVICE_PRESETS.length) {
            return defaultApp;
        }
        return DEVICE_PRESETS[deviceSpoofIndex][3];
    }
}
