package org.telegram.ghostgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/** Display-only data. Never insert these objects into StarsController's server lists. */
public final class LocalProfileStore {
    public static final int MAX_GIFTS = 200;

    public static final class Gift extends TL_stars.TL_savedStarGift {
    }

    private static SharedPreferences preferences(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) throw new IllegalStateException("Account is not logged in");
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "ghost_local_profile_" + account + "_" + userId, Context.MODE_PRIVATE);
    }

    public static ArrayList<Gift> gifts(int account, long dialogId) {
        ArrayList<Gift> result = new ArrayList<>();
        if (dialogId == 0 || UserConfig.getInstance(account).getClientUserId() == 0) return result;
        try {
            JSONArray array = new JSONArray(preferences(account).getString("gifts_" + dialogId, "[]"));
            for (int i = 0; i < Math.min(array.length(), MAX_GIFTS); i++) {
                SerializedData data = null;
                try {
                    data = new SerializedData(Base64.decode(array.getString(i), Base64.NO_WRAP));
                    TL_stars.SavedStarGift saved = TL_stars.SavedStarGift.TLdeserialize(data, data.readInt32(true), true);
                    if (saved == null || saved.gift == null || saved.gift.getDocument() == null) continue;
                    Gift local = new Gift();
                    local.gift = saved.gift;
                    local.date = saved.date;
                    result.add(local);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (data != null) data.cleanup();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static int count(int account, long dialogId) {
        return gifts(account, dialogId).size();
    }

    public static boolean add(int account, long dialogId, TL_stars.StarGift gift) {
        if (gift == null || gift.getDocument() == null || dialogId == 0) return false;
        ArrayList<Gift> gifts = gifts(account, dialogId);
        if (gifts.size() >= MAX_GIFTS) return false;
        for (Gift existing : gifts) {
            if (existing.gift.id == gift.id && existing.gift.getClass() == gift.getClass()) return false;
        }
        Gift local = new Gift();
        local.gift = gift;
        local.date = (int) (System.currentTimeMillis() / 1000);
        gifts.add(0, local);
        saveGifts(account, dialogId, gifts);
        return true;
    }

    public static void remove(int account, long dialogId, long giftId) {
        ArrayList<Gift> gifts = gifts(account, dialogId);
        for (int i = gifts.size() - 1; i >= 0; i--) {
            if (gifts.get(i).gift.id == giftId) gifts.remove(i);
        }
        saveGifts(account, dialogId, gifts);
    }

    private static void saveGifts(int account, long dialogId, ArrayList<Gift> gifts) {
        JSONArray array = new JSONArray();
        for (Gift gift : gifts) {
            SerializedData data = new SerializedData();
            try {
                gift.serializeToStream(data);
                array.put(Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP));
            } finally {
                data.cleanup();
            }
        }
        preferences(account).edit().putString("gifts_" + dialogId, array.toString()).apply();
        StarsController.GiftsList list = StarsController.getInstance(account).getProfileGiftsList(dialogId, false);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, list);
    }

    public static String usernames(int account) {
        if (UserConfig.getInstance(account).getClientUserId() == 0) return "";
        return preferences(account).getString("usernames", "");
    }

    public static void setUsernames(int account, String value) {
        preferences(account).edit().putString("usernames", value).apply();
    }

    public static CharSequence usernameLabel(int account, CharSequence original) {
        String names = usernames(account);
        if (TextUtils.isEmpty(names)) {
            return original;
        }
        StringBuilder builder = new StringBuilder();
        String[] parts = names.split("[,\\s]+");
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(part.startsWith("@") ? part : "@" + part);
        }
        if (builder.length() == 0) {
            return original;
        }
        if (original == null || original.length() == 0) {
            return builder.toString();
        }
        return TextUtils.concat(original, "\n", builder.toString());
    }

    public static void clear(int account, long dialogId) {
        saveGifts(account, dialogId, new ArrayList<>());
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) setUsernames(account, "");
    }
}
