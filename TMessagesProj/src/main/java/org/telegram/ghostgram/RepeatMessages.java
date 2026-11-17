package org.telegram.ghostgram;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public final class RepeatMessages {
    private static final String MARKER = "ghostgram_repeat_session";
    private static final String NO_CLEAR_DRAFT = "ghostgram_repeat_no_clear_draft";
    private static final int MAX_MESSAGES = 30;
    private static final long INTERVAL_MS = 1000;
    private static final long PENDING_TIMEOUT_MS = 120000;
    private static final HashMap<String, Session> sessions = new HashMap<>();

    private RepeatMessages() {}

    public static boolean isCommand(String text) {
        return text.equals(".spam stop") || text.equals(".spam start") || text.startsWith(".spam start ");
    }

    public static boolean isRepeatMessage(SendMessagesHelper.SendMessageParams params) {
        return params.retryMessageObject == null && params.params != null && params.params.containsKey(MARKER);
    }

    public static boolean shouldClearDraft(SendMessagesHelper.SendMessageParams params) {
        return params.params == null || !params.params.containsKey(NO_CLEAR_DRAFT);
    }

    public static boolean handle(int account, long dialogId, String command) {
        boolean stop = command.equals(".spam stop");
        if (!isCommand(command)) return false;

        Session current = null;
        for (Session session : sessions.values()) {
            if (!session.stopped && session.account == account && session.dialogId == dialogId) {
                current = session;
                break;
            }
        }
        if (stop) {
            if (current == null) {
                toast("Нет активной серии сообщений");
            } else {
                current.stop();
            }
            return true;
        }

        String text = command.length() > 12 ? command.substring(12).trim() : "";
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(dialogId);
        if (!DialogObject.isUserDialog(dialogId) || user == null || user.bot || user.deleted || dialogId == UserConfig.getInstance(account).getClientUserId()) {
            toast("Команда доступна только в обычном личном чате");
        } else if (text.isEmpty() || text.length() > controller.getMaxMessageLength()) {
            toast("Укажите непустой текст допустимой длины: .spam start текст");
        } else if (current != null) {
            toast("Серия уже запущена. Используйте .spam stop");
        } else if (isPaid(controller, dialogId)) {
            toast("Повторная отправка в платном чате недоступна");
        } else {
            Session session = new Session(account, dialogId, text);
            sessions.put(session.token, session);
            session.begin();
        }
        return true;
    }

    private static boolean isPaid(MessagesController controller, long dialogId) {
        return controller.getSendPaidMessagesStars(dialogId) > 0 ||
                DialogObject.getMessagesStarsPrice(controller.isUserContactBlocked(dialogId)) > 0;
    }

    public static void onLocalMessage(int account, MessageObject message) {
        if (message.messageOwner.params == null) return;
        Session session = sessions.get(message.messageOwner.params.get(MARKER));
        if (session != null && !session.stopped && session.account == account && message.getDialogId() == session.dialogId) {
            session.pending.add(message.getId());
        }
    }

    public static void onMessageStored(int account, HashMap<String, String> params, int oldId, int newId) {
        if (params == null) return;
        Session session = sessions.get(params.get(MARKER));
        if (session != null && session.account == account && session.pending.remove(oldId)) {
            if (newId > 0) session.sent.add(newId);
            if (session.stopped && session.pending.isEmpty()) session.finish();
        }
    }

    private static void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
    }

    private static final class Session implements NotificationCenter.NotificationCenterDelegate {
        final int account;
        final long dialogId;
        final String text;
        final String token = UUID.randomUUID().toString();
        final HashSet<Integer> pending = new HashSet<>();
        final ArrayList<Integer> sent = new ArrayList<>();
        final Runnable tick = this::sendNext;
        final Runnable timeout = this::finishTimedOut;
        int count;
        int readAtStop;
        boolean stopped;

        Session(int account, long dialogId, String text) {
            this.account = account;
            this.dialogId = dialogId;
            this.text = text;
        }

        void begin() {
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(this, NotificationCenter.messageSendError);
            center.addObserver(this, NotificationCenter.appDidLogout);
            toast("Серия запущена: максимум 30 сообщений, по одному в секунду");
            sendNext();
        }

        void sendNext() {
            if (stopped || count >= MAX_MESSAGES) return;
            if (!UserConfig.getInstance(account).isClientActivated() || isPaid(MessagesController.getInstance(account), dialogId)) {
                stop();
                toast("Отправка остановлена: чат недоступен или стал платным");
                return;
            }
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text, dialogId);
            params.searchLinks = false;
            params.notify = true;
            params.params = new HashMap<>();
            params.params.put(MARKER, token);
            if (count > 0) {
                params.params.put(NO_CLEAR_DRAFT, "1");
            }
            int previousPending = pending.size();
            SendMessagesHelper.getInstance(account).sendMessage(params);
            if (pending.size() == previousPending) {
                stop();
                toast("Отправка не началась; серия остановлена");
                return;
            }
            count++;
            if (count < MAX_MESSAGES) {
                AndroidUtilities.runOnUIThread(tick, INTERVAL_MS);
            } else {
                toast("Лимит серии достигнут. Используйте .spam stop");
            }
        }

        void stop() {
            if (stopped) return;
            stopped = true;
            AndroidUtilities.cancelRunOnUIThread(tick);
            Integer read = MessagesController.getInstance(account).dialogs_read_outbox_max.get(dialogId);
            readAtStop = read == null ? 0 : read;
            if (pending.isEmpty()) {
                finish();
            } else {
                toast("Серия остановлена; ожидаются подтверждения отправки");
                AndroidUtilities.runOnUIThread(timeout, PENDING_TIMEOUT_MS);
            }
        }

        void finishTimedOut() {
            finish();
            toast("Не все сообщения подтвердились; проверьте чат вручную");
        }

        void finish() {
            AndroidUtilities.cancelRunOnUIThread(timeout);
            boolean read = false;
            for (int id : sent) {
                if (id > 0 && id <= readAtStop) {
                    read = true;
                    break;
                }
            }
            if (read && !sent.isEmpty()) {
                MessagesController.getInstance(account).deleteMessages(new ArrayList<>(sent), null, null, dialogId, 0, true, ChatActivity.MODE_DEFAULT);
                toast("Серия остановлена; запрошено удаление сообщений для обоих");
            } else {
                toast("Серия остановлена; подтверждения прочтения нет");
            }
            cleanup();
        }

        void cleanup() {
            AndroidUtilities.cancelRunOnUIThread(tick);
            AndroidUtilities.cancelRunOnUIThread(timeout);
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.removeObserver(this, NotificationCenter.messageSendError);
            center.removeObserver(this, NotificationCenter.appDidLogout);
            sessions.remove(token);
        }

        @Override
        public void didReceivedNotification(int id, int eventAccount, Object... args) {
            if (id == NotificationCenter.appDidLogout) {
                stopped = true;
                cleanup();
            } else if (id == NotificationCenter.messageSendError && pending.remove((Integer) args[0])) {
                if (!stopped) {
                    stop();
                    toast("Ошибка отправки; серия остановлена");
                } else if (pending.isEmpty()) {
                    finish();
                }
            }
        }
    }
}
