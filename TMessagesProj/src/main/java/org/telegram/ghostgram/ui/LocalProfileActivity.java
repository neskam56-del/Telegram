package org.telegram.ghostgram.ui;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.ghostgram.LocalProfileStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class LocalProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final String NOTICE = "Только локально в Ghostgram. Другие пользователи не увидят изменения. "
            + "Подарки и usernames не покупаются и не передаются; Stars и права владения не меняются.";
    private final long dialogId;
    private final long ownerId;
    private UniversalRecyclerView listView;
    private boolean catalog;
    private boolean destroyed;
    private int requestId;

    public LocalProfileActivity(int account, long dialogId) {
        setCurrentAccount(account);
        this.dialogId = dialogId;
        ownerId = UserConfig.getInstance(account).getClientUserId();
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        if (requestId != 0) ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("Локальный профиль");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (catalog) {
                        catalog = false;
                        listView.adapter.update(false);
                    } else finishFragment();
                }
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(context, currentAccount, classGuid, false,
                this::fillItems, this::onClick, null, getResourceProvider(), 3,
                androidx.recyclerview.widget.LinearLayoutManager.VERTICAL);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        fragmentView = frame;
        return frame;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(NOTICE));
        if (catalog) {
            items.add(UItem.asButton(5, "Назад к локальным объектам"));
            items.add(UItem.asHeader("Каталог Telegram: добавить визуально"));
            ArrayList<TL_stars.StarGift> gifts = StarsController.getInstance(currentAccount).gifts;
            if (gifts.isEmpty()) items.add(UItem.asShadow("Каталог пуст или недоступен. Подключитесь к сети и повторите попытку."));
            for (TL_stars.StarGift gift : gifts) {
                items.add(GiftSheet.GiftCell.Factory.asStarGift(0, gift, false, false, false, false, false));
            }
            return;
        }
        items.add(UItem.asButton(1, "Визуально подарить из каталога"));
        items.add(UItem.asButton(2, "Добавить коллекционный по ссылке"));
        if (dialogId == ownerId) {
            items.add(UItem.asButton(3, "NFT usernames (локально)", LocalProfileStore.usernames(currentAccount)));
        }
        items.add(UItem.asButton(4, "Удалить локальные данные этого профиля"));
        items.add(UItem.asShadow("Подарки ниже видны только на этом устройстве. Нажмите на карточку для удаления. "
                + "Повторное добавление одного подарка не создаёт дубликат. Лимит: " + LocalProfileStore.MAX_GIFTS + "."));
        for (LocalProfileStore.Gift gift : LocalProfileStore.gifts(currentAccount, dialogId)) {
            items.add(GiftSheet.GiftCell.Factory.asStarGift(0, gift, true));
        }
        items.add(UItem.asShadow("Идея: OpenEblanNFT (m4rker20-rgb/OpenEblanNFT). Независимая реализация Ghostgram, без загрузки плагина и внешних серверов."));
    }

    private boolean active() {
        return !destroyed && ownerId != 0 && ownerId == UserConfig.getInstance(currentAccount).getClientUserId();
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (!active()) return;
        if (item.object instanceof LocalProfileStore.Gift) {
            showGiftActions(this, dialogId, (LocalProfileStore.Gift) item.object, () -> listView.adapter.update(true));
        } else if (item.object instanceof TL_stars.StarGift) {
            TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle("Подарить только визуально?")
                    .setMessage(NOTICE).setPositiveButton("Добавить локально", (d, w) -> {
                        if (active()) addGift(gift);
                    }).setNegativeButton("Отмена", null).create());
        } else if (item.id == 1) {
            catalog = true;
            StarsController.getInstance(currentAccount).loadStarGifts();
            listView.adapter.update(true);
        } else if (item.id == 2) {
            prompt("Коллекционный подарок", "https://t.me/nft/PlushPepe-1", "", this::loadUnique);
        } else if (item.id == 3) {
            prompt("NFT usernames: только локально", "@name, @another_name", LocalProfileStore.usernames(currentAccount), value -> {
                LinkedHashSet<String> names = new LinkedHashSet<>();
                for (String part : value.split("[,\\s]+")) {
                    if (part.isEmpty()) continue;
                    String name = part.startsWith("@") ? part.substring(1) : part;
                    if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
                        message("Неверный username: допустимы 1-32 латинские буквы, цифры и _, первая буква.");
                        return;
                    }
                    names.add("@" + name.toLowerCase(Locale.US));
                }
                if (names.size() > 10) {
                    message("Можно добавить не более 10 usernames.");
                    return;
                }
                LocalProfileStore.setUsernames(currentAccount, android.text.TextUtils.join(", ", names));
                listView.adapter.update(true);
            });
        } else if (item.id == 4) {
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle("Удалить локальные данные?")
                    .setMessage("Настоящие подарки и usernames останутся без изменений.")
                    .setPositiveButton("Удалить", (d, w) -> {
                        if (!active()) return;
                        LocalProfileStore.clear(currentAccount, dialogId);
                        listView.adapter.update(true);
                    }).setNegativeButton("Отмена", null).create());
        } else if (item.id == 5) {
            catalog = false;
            listView.adapter.update(true);
        }
    }

    private void addGift(TL_stars.StarGift gift) {
        boolean added = LocalProfileStore.add(currentAccount, dialogId, gift);
        message(added ? "Подарок добавлен только локально" : "Уже добавлен, недоступны данные или достигнут лимит");
        if (added) catalog = false;
        listView.adapter.update(true);
    }

    private void loadUnique(String value) {
        if (requestId != 0) {
            message("Дождитесь загрузки предыдущего подарка");
            return;
        }
        Uri uri = Uri.parse(value.trim());
        String path = uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"t.me".equalsIgnoreCase(uri.getHost())
                || path == null || !path.matches("/nft/[A-Za-z0-9]+-[1-9][0-9]*")
                || uri.getQuery() != null || uri.getFragment() != null) {
            message("Нужна публичная ссылка https://t.me/nft/Название-123");
            return;
        }
        TL_stars.getUniqueStarGift req = new TL_stars.getUniqueStarGift();
        req.slug = path.substring(5);
        message("Загрузка данных из Telegram. Покупка не выполняется.");
        requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = 0;
            if (!active()) return;
            if (res instanceof TL_stars.TL_payments_uniqueStarGift
                    && ((TL_stars.TL_payments_uniqueStarGift) res).gift instanceof TL_stars.TL_starGiftUnique) {
                addGift(((TL_stars.TL_payments_uniqueStarGift) res).gift);
            } else message("Не удалось получить подарок. Проверьте ссылку и подключение к сети.");
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(requestId, classGuid);
    }

    private void prompt(String title, String hint, String value, org.telegram.messenger.Utilities.Callback<String> callback) {
        EditText input = new EditText(getParentActivity());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setTextSize(16);
        input.setHint(hint);
        input.setText(value);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(input, LayoutHelper.createFrame(-1, -2));
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(title).setView(container)
                .setPositiveButton("Сохранить локально", (d, w) -> {
                    if (active()) callback.run(input.getText().toString().trim());
                }).setNegativeButton("Отмена", null).create());
    }

    private void message(String text) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), text, Toast.LENGTH_LONG).show();
    }

    public static void showGiftActions(BaseFragment fragment, long dialogId, LocalProfileStore.Gift gift, Runnable updated) {
        if (fragment.getParentActivity() == null) return;
        int account = fragment.getCurrentAccount();
        long owner = UserConfig.getInstance(account).getClientUserId();
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setTitle("Локальный подарок")
                .setMessage(StarGiftSheet.getGiftName(gift.gift) + "\n\n" + NOTICE)
                .setPositiveButton("Закрыть", null)
                .setNegativeButton("Удалить локально", (d, w) -> {
                    if (owner == 0 || owner != UserConfig.getInstance(account).getClientUserId()) return;
                    LocalProfileStore.remove(account, dialogId, gift.gift.id);
                    updated.run();
                }).create());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (listView != null && active()) listView.adapter.update(true);
    }
}
