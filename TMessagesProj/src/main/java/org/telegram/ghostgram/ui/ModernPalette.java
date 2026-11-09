package org.telegram.ghostgram.ui;

import android.graphics.Color;

/**
 * ModernPalette — цвета кастомной тёмной темы Ghostgram.
 * Взяты из HTML-мокапа пользователя.
 *
 * Палитра:
 * - Background:  #0e1014  (тёмно-серый/чёрный)
 * - Cards:       #171a21  (тёмно-серый)
 * - Surface:     #1e2128  (чуть светлее)
 * - Accent:      #2b8cff  (синий)
 * - Badge:       #ff5a5f  (красный)
 * - Link:        #5eb0ff  (голубой)
 * - Online:      #34d27b  (зелёный)
 * - Text:        #ffffff  (белый)
 * - TextSecondary: #8e9099 (серый)
 * - Divider:     #23262e  (тёмно-серый)
 */
public class ModernPalette {

    // Основные цвета фона
    public static final int BACKGROUND = 0xFF0E1014;
    public static final int CARD = 0xFF171A21;
    public static final int SURFACE = 0xFF1E2128;
    public static final int ELEVATED = 0xFF23262E;

    // Акцентные цвета
    public static final int ACCENT = 0xFF2B8CFF;
    public static final int ACCENT_LIGHT = 0xFF5EB0FF;
    public static final int BADGE = 0xFFFF5A5F;
    public static final int ONLINE = 0xFF34D27B;

    // Текст
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF8E9099;
    public static final int TEXT_HINT = 0xFF5A5D66;

    // Bubble (пузырьки сообщений)
    public static final int BUBBLE_OUT = 0xFF2B8CFF;       // Исходящее сообщение
    public static final int BUBBLE_OUT_TEXT = 0xFFFFFFFF;
    public static final int BUBBLE_IN = 0xFF1E2128;         // Входящее сообщение  
    public static final int BUBBLE_IN_TEXT = 0xFFFFFFFF;

    // Разделители и рамки
    public static final int DIVIDER = 0xFF23262E;
    public static final int BORDER = 0xFF2A2D35;

    // Навигация
    public static final int NAV_BAR = 0xFF171A21;
    public static final int NAV_ICON_ACTIVE = 0xFF2B8CFF;
    public static final int NAV_ICON_INACTIVE = 0xFF5A5D66;

    // Табы папок
    public static final int TAB_ACTIVE_BG = 0xFF2B8CFF;
    public static final int TAB_ACTIVE_TEXT = 0xFFFFFFFF;
    public static final int TAB_INACTIVE_TEXT = 0xFF8E9099;

    // Аватарка и статусы
    public static final int AVATAR_PLACEHOLDER = 0xFF2B8CFF;
    public static final int DELETED_ICON_COLOR = 0xFFFF5A5F;  // 🗑️ иконка для удалённых сообщений

    // FAB (Floating Action Button)
    public static final int FAB_BACKGROUND = 0xFF2B8CFF;
    public static final int FAB_ICON = 0xFFFFFFFF;

    /**
     * Возвращает цвет с альфа-каналом.
     */
    public static int withAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}

