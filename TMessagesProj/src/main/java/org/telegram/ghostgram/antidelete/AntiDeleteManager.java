package org.telegram.ghostgram.antidelete;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.text.TextUtils;
import android.util.LruCache;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * AntiDeleteManager — перехватывает удалённые и отредактированные сообщения,
 * сохраняет оригиналы в отдельную SQLite таблицу.
 *
 * Вызывается из MessagesController.processUpdateArray() перед обработкой
 * TL_updateDeleteMessages и TL_updateDeleteChannelMessages.
 */
public class AntiDeleteManager {

    private static final String TAG = "AntiDelete";
    private static final AntiDeleteManager instance = new AntiDeleteManager();
    private volatile GhostDeletedDatabase db;
    // Accessed on the UI thread; cache negative lookups as well as saved markers.
    private final LruCache<String, Integer> decorationCache = new LruCache<>(2048);
    private final HashMap<String, ArrayList<Runnable>> pendingDecorations = new HashMap<>();
    private int decorationGeneration;

    public static AntiDeleteManager getInstance() {
        return instance;
    }

    public synchronized void init(Context context) {
        if (db == null) {
            db = new GhostDeletedDatabase(context.getApplicationContext());
        }
    }

    private void invalidateDecorations() {
        AndroidUtilities.runOnUIThread(() -> {
            decorationGeneration++;
            decorationCache.evictAll();
        });
    }

    /** Never opens SQLite from a message-cell bind. Callback runs on the UI thread. */
    public void loadDecoration(MessageObject message, Runnable onLoaded) {
        if (db == null) return;
        final int messageId = message.getId();
        final long dialogId = message.getDialogId();
        final int account = message.currentAccount;
        final long channelId = message.messageOwner.peer_id == null ? 0 : message.messageOwner.peer_id.channel_id;
        final String key = message.currentAccount + ":" + dialogId + ":" + messageId;
        Integer flags = decorationCache.get(key);
        if (flags != null) {
            message.isGhostDeleted = GhostConfig.antiDeleteEnabled && (flags & 1) != 0;
            message.isGhostEdited = GhostConfig.antiEditEnabled && (flags & 2) != 0;
            return;
        }
        Runnable apply = () -> {
            Integer loaded = decorationCache.get(key);
            if (loaded == null) {
                loadDecoration(message, onLoaded);
                return;
            }
            boolean deleted = GhostConfig.antiDeleteEnabled && (loaded & 1) != 0;
            boolean edited = GhostConfig.antiEditEnabled && (loaded & 2) != 0;
            if (deleted || edited || message.isGhostDeleted != deleted || message.isGhostEdited != edited) {
                message.isGhostDeleted = deleted;
                message.isGhostEdited = edited;
                onLoaded.run();
            }
        };
        ArrayList<Runnable> pending = pendingDecorations.get(key);
        if (pending != null) {
            pending.add(apply);
            return;
        }
        pending = new ArrayList<>();
        pending.add(apply);
        pendingDecorations.put(key, pending);
        final int generation = decorationGeneration;
        Utilities.globalQueue.postRunnable(() -> {
            int flagsResult = 0;
            try (Cursor cursor = db.getReadableDatabase().rawQuery(
                    "SELECT 1 FROM deleted_messages WHERE account = ? AND message_id = ? AND channel_id = ? LIMIT 1",
                    new String[]{String.valueOf(account), String.valueOf(messageId), String.valueOf(channelId)})) {
                if (cursor.moveToFirst()) flagsResult |= 1;
            } catch (Exception e) {
                FileLog.e(e);
            }
            try (Cursor cursor = db.getReadableDatabase().rawQuery(
                    "SELECT 1 FROM edit_history WHERE account = ? AND message_id = ? AND channel_id = ? LIMIT 1",
                    new String[]{String.valueOf(account), String.valueOf(messageId), String.valueOf(dialogId)})) {
                if (cursor.moveToFirst()) flagsResult |= 2;
            } catch (Exception e) {
                FileLog.e(e);
            }
            final int result = flagsResult;
            AndroidUtilities.runOnUIThread(() -> {
                if (generation == decorationGeneration) {
                    decorationCache.put(key, result);
                }
                ArrayList<Runnable> callbacks = pendingDecorations.remove(key);
                if (callbacks != null) {
                    for (Runnable callback : callbacks) callback.run();
                }
            });
        });
    }

    /**
     * Перехватывает удаление сообщений.
     * Вызвать ПЕРЕД тем, как MessagesController удалит сообщения.
     *
     * @param messageIds   список ID сообщений, которые сервер хочет удалить
     * @param channelId    ID канала (0 для личных чатов)
     * @param currentAccount текущий аккаунт
     * @return список ID, которые НЕ нужно удалять (если antiDelete включён — все)
     */
    public ArrayList<Integer> interceptDeletedMessages(ArrayList<Integer> messageIds, long channelId, int currentAccount) {
        if (!GhostConfig.antiDeleteEnabled || messageIds == null || messageIds.isEmpty()) {
            return null; // Не перехватываем, удалять как обычно
        }

        FileLog.d(TAG + ": intercepting " + messageIds.size() + " deleted messages, channelId=" + channelId);

        // Получаем сообщения из локальной базы Telegram перед удалением
        try {
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            // Сохраняем в нашу базу
            for (int msgId : messageIds) {
                saveDeletedMessageId(msgId, channelId, currentAccount);
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": error intercepting messages", e);
        }

        // Возвращаем все ID — они не будут удалены из UI
        return messageIds;
    }

    /**
     * Сохраняет ID удалённого сообщения.
     */
    private void saveDeletedMessageId(int messageId, long channelId, int currentAccount) {
        if (db == null) return;
        try {
            SQLiteDatabase sqlDb = db.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("message_id", messageId);
            values.put("channel_id", channelId);
            values.put("account", currentAccount);
            values.put("deleted_time", System.currentTimeMillis());
            sqlDb.insertWithOnConflict("deleted_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            invalidateDecorations();
        } catch (Exception e) {
            FileLog.e(TAG + ": error saving deleted message id", e);
        }
    }

    /**
     * Сохраняет текст сообщения перед редактированием (история правок).
     */
    public void saveEditHistory(int messageId, long channelId, String originalText, int currentAccount) {
        if (!GhostConfig.antiEditEnabled || db == null || TextUtils.isEmpty(originalText)) return;
        try {
            SQLiteDatabase sqlDb = db.getWritableDatabase();
            Cursor cursor = sqlDb.rawQuery(
                "SELECT original_text FROM edit_history WHERE message_id = ? ORDER BY edit_time DESC LIMIT 1",
                new String[]{String.valueOf(messageId)}
            );
            if (cursor.moveToFirst()) {
                String lastText = cursor.getString(0);
                if (TextUtils.equals(lastText, originalText)) {
                    cursor.close();
                    return;
                }
            }
            cursor.close();

            ContentValues values = new ContentValues();
            values.put("message_id", messageId);
            values.put("channel_id", channelId);
            values.put("account", currentAccount);
            values.put("original_text", originalText);
            values.put("edit_time", System.currentTimeMillis());
            sqlDb.insert("edit_history", null, values);
            invalidateDecorations();
            FileLog.d(TAG + ": saved edit history for msg " + messageId);
        } catch (Exception e) {
            FileLog.e(TAG + ": error saving edit history", e);
        }
    }

    /**
     * Проверяет, есть ли история правок у сообщения.
     */
    public boolean hasEditHistory(int messageId) {
        return hasEditHistory(messageId, 0);
    }

    public boolean hasEditHistory(int messageId, long channelId) {
        if (db == null) return false;
        try {
            SQLiteDatabase sqlDb = db.getReadableDatabase();
            String query = channelId != 0 ?
                "SELECT 1 FROM edit_history WHERE message_id = ? AND channel_id = ? LIMIT 1" :
                "SELECT 1 FROM edit_history WHERE message_id = ? LIMIT 1";
            String[] args = channelId != 0 ?
                new String[]{String.valueOf(messageId), String.valueOf(channelId)} :
                new String[]{String.valueOf(messageId)};
            Cursor cursor = sqlDb.rawQuery(query, args);
            boolean exists = cursor.moveToFirst();
            cursor.close();
            return exists;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет, было ли сообщение удалено (для UI-декорации).
     */
    public boolean isMessageDeleted(int messageId) {
        if (db == null) return false;
        try {
            SQLiteDatabase sqlDb = db.getReadableDatabase();
            Cursor cursor = sqlDb.rawQuery(
                "SELECT 1 FROM deleted_messages WHERE message_id = ? LIMIT 1",
                new String[]{String.valueOf(messageId)}
            );
            boolean exists = cursor.moveToFirst();
            cursor.close();
            return exists;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает историю правок сообщения.
     */
    public ArrayList<EditEntry> getEditHistory(int messageId) {
        return getEditHistory(messageId, 0);
    }

    public ArrayList<EditEntry> getEditHistory(int messageId, long channelId) {
        ArrayList<EditEntry> history = new ArrayList<>();
        if (db == null) return history;
        try {
            SQLiteDatabase sqlDb = db.getReadableDatabase();
            String query = channelId != 0 ?
                "SELECT original_text, edit_time FROM edit_history WHERE message_id = ? AND channel_id = ? ORDER BY edit_time DESC" :
                "SELECT original_text, edit_time FROM edit_history WHERE message_id = ? ORDER BY edit_time DESC";
            String[] args = channelId != 0 ?
                new String[]{String.valueOf(messageId), String.valueOf(channelId)} :
                new String[]{String.valueOf(messageId)};
            Cursor cursor = sqlDb.rawQuery(query, args);
            while (cursor.moveToNext()) {
                EditEntry entry = new EditEntry();
                entry.originalText = cursor.getString(0);
                entry.editTime = cursor.getLong(1);
                history.add(entry);
            }
            cursor.close();
        } catch (Exception e) {
            FileLog.e(TAG + ": error getting edit history", e);
        }
        return history;
    }

    public static class EditEntry {
        public String originalText;
        public long editTime;
    }

    /**
     * Внутренняя SQLite база для хранения удалённых сообщений и истории правок.
     */
    private static class GhostDeletedDatabase extends SQLiteOpenHelper {
        private static final String DB_NAME = "ghostgram_deleted.db";
        private static final int DB_VERSION = 1;

        public GhostDeletedDatabase(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS deleted_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id INTEGER NOT NULL," +
                "channel_id INTEGER DEFAULT 0," +
                "account INTEGER DEFAULT 0," +
                "deleted_time INTEGER NOT NULL," +
                "UNIQUE(message_id, channel_id) ON CONFLICT REPLACE" +
            ")");

            db.execSQL("CREATE TABLE IF NOT EXISTS edit_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id INTEGER NOT NULL," +
                "channel_id INTEGER DEFAULT 0," +
                "account INTEGER DEFAULT 0," +
                "original_text TEXT," +
                "edit_time INTEGER NOT NULL" +
            ")");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted_msg_id ON deleted_messages(message_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_edit_msg_id ON edit_history(message_id)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Будущие миграции
        }
    }
}
