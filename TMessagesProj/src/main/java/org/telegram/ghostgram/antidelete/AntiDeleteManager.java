package org.telegram.ghostgram.antidelete;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.text.TextUtils;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.messenger.FileLog;
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
    private static AntiDeleteManager instance;
    private GhostDeletedDatabase db;

    public static AntiDeleteManager getInstance() {
        if (instance == null) {
            instance = new AntiDeleteManager();
        }
        return instance;
    }

    public void init(Context context) {
        db = new GhostDeletedDatabase(context);
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

