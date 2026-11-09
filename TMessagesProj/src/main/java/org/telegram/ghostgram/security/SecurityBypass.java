package org.telegram.ghostgram.security;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

/**
 * SecurityBypass — обходит ограничения на скриншоты, пересылку и сохранение медиа.
 *
 * 1. Убирает FLAG_SECURE с окна Activity (разрешает скриншоты в секретных чатах)
 * 2. Обнуляет флаг noforwards в чатах (разрешает пересылку/копирование)
 * 3. Позволяет сохранять ограниченный контент
 */
public class SecurityBypass {

    private static final String TAG = "SecurityBypass";

    /**
     * Вызывается после setContentView() в ChatActivity и SecretChatActivity.
     * Убирает FLAG_SECURE если разрешены скриншоты.
     */
    public static void removeScreenshotProtection(Activity activity) {
        if (!GhostConfig.allowScreenshots) return;
        if (activity == null) return;

        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                FileLog.d(TAG + ": cleared FLAG_SECURE");
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": error clearing FLAG_SECURE", e);
        }
    }

    /**
     * Обнуляет флаг noforwards в чате.
     * Вызывается при получении объекта Chat от сервера.
     *
     * @param chat объект TLRPC.Chat
     */
    public static void bypassNoForwards(TLRPC.Chat chat) {
        if (!GhostConfig.bypassNoForwards) return;
        if (chat == null) return;

        if (chat.noforwards) {
            chat.noforwards = false;
            FileLog.d(TAG + ": bypassed noforwards for chat " + chat.id);
        }
    }

    /**
     * Проверяет, разрешено ли сохранение медиа.
     * Используется вместо стандартной проверки noforwards.
     *
     * @param chat объект чата
     * @return true если можно сохранять
     */
    public static boolean canSaveMedia(TLRPC.Chat chat) {
        if (GhostConfig.allowSaveMedia) {
            return true; // Всегда разрешаем
        }
        // Стандартная проверка
        return chat == null || !chat.noforwards;
    }

    /**
     * Проверяет, можно ли делать скриншот.
     * Заменяет стандартную проверку в ChatActivity.
     *
     * @return true если скриншоты разрешены
     */
    public static boolean canTakeScreenshot() {
        return GhostConfig.allowScreenshots;
    }
}

