package org.telegram.ghostgram.ghostmode;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

/**
 * GhostModeManager — перехватывает исходящие запросы к серверу Telegram
 * и блокирует те, которые раскрывают присутствие пользователя.
 */
public class GhostModeManager {

    private static final String TAG = "GhostMode";

    /**
     * Проверяет, нужно ли заблокировать отправку TLObject.
     * Вызывается из ConnectionsManager.sendRequestInternal() перед native_sendRequest.
     *
     * @param object TLObject, который собирается быть отправлен
     * @return true, если запрос нужно заблокировать (не отправлять)
     */
    public static boolean shouldBlockRequest(TLObject object) {
        if (!GhostConfig.ghostModeEnabled) {
            return false;
        }

        if (object == null) {
            return false;
        }

        // Блокируем отправку "прочитано" 
        if (GhostConfig.hideReadReceipts) {
            if (object instanceof TLRPC.TL_messages_readHistory) {
                FileLog.d(TAG + ": blocked TL_messages_readHistory");
                return true;
            }
            if (object instanceof TLRPC.TL_messages_readEncryptedHistory) {
                FileLog.d(TAG + ": blocked TL_messages_readEncryptedHistory");
                return true;
            }
            if (object instanceof TLRPC.TL_channels_readHistory) {
                FileLog.d(TAG + ": blocked TL_channels_readHistory");
                return true;
            }
            if (object instanceof TLRPC.TL_messages_readDiscussion) {
                FileLog.d(TAG + ": blocked TL_messages_readDiscussion");
                return true;
            }
            if (object instanceof TLRPC.TL_messages_readMessageContents) {
                FileLog.d(TAG + ": blocked TL_messages_readMessageContents");
                return true;
            }
            if (object instanceof TLRPC.TL_messages_readReactions) {
                FileLog.d(TAG + ": blocked TL_messages_readReactions");
                return true;
            }
        }

        // Блокируем отправку "печатает..."
        if (GhostConfig.hideTypingStatus) {
            if (object instanceof TLRPC.TL_messages_setTyping) {
                FileLog.d(TAG + ": blocked TL_messages_setTyping");
                return true;
            }
        }

        return false;
    }

    /**
     * Проверяет, нужно ли подменить статус "онлайн" при отправке updateStatus.
     * Если Ghost Mode включён и hideOnlineStatus = true, всегда отправляем offline = true.
     *
     * @param object TLObject
     * @return модифицированный TLObject или null если модификация не нужна
     */
    public static TLObject modifyRequestIfNeeded(TLObject object) {
        if (!GhostConfig.ghostModeEnabled || !GhostConfig.hideOnlineStatus) {
            return null;
        }

        if (object instanceof TL_account.updateStatus) {
            TL_account.updateStatus updateStatus = (TL_account.updateStatus) object;
            // Всегда говорим серверу что мы оффлайн
            updateStatus.offline = true;
            FileLog.d(TAG + ": forced offline status");
            return updateStatus;
        }

        return null;
    }
}
