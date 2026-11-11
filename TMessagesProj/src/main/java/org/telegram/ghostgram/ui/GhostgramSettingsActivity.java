package org.telegram.ghostgram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.ghostgram.voice.VoiceChangerDSP;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * GhostgramSettingsActivity — современный экран настроек Ghostgram.
 * Полностью интегрирован в дизайн-систему Telegram с поддержкой всех тем.
 */
public class GhostgramSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    // Секция: Ghost Mode
    private int ghostHeaderRow;
    private int ghostModeMasterRow;
    private int showGhostButtonRow;
    private int hideReadRow;
    private int hideTypingRow;
    private int hideOnlineRow;
    private int ghostSectionShadowRow;

    // Секция: Anti-Delete
    private int antiDeleteHeaderRow;
    private int antiDeleteRow;
    private int antiDeleteShadowRow;

    // Секция: Безопасность и экран
    private int extraHeaderRow;
    private int allowScreenshotsRow;
    private int force120HzRow;
    private int forwardNoAuthorRow;
    private int hidePhoneNumberRow;
    private int customPhoneRow;
    private int customPhoneValueRow;
    private int extraShadowRow;

    // Секция: Изменитель голоса
    private int voiceHeaderRow;
    private int voicePresetRow;
    private int voiceShadowRow;

    // Секция: Подмена устройства
    private int deviceHeaderRow;
    private int deviceSpoofRow;
    private int devicePresetRow;
    private int deviceShadowRow;

    // Секция: Геолокация
    private int locationHeaderRow;
    private int fakeLocationRow;
    private int locationShadowRow;

    // Секция: Кастомизация
    private int customHeaderRow;
    private int compactDialogsRow;
    private int hideRecommendationsRow;
    private int customShadowRow;

    private int rowCount;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_SHADOW = 2;
    private static final int TYPE_SETTINGS = 3;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;

        // Ghost Mode
        ghostHeaderRow = rowCount++;
        ghostModeMasterRow = rowCount++;
        showGhostButtonRow = rowCount++;
        hideReadRow = rowCount++;
        hideTypingRow = rowCount++;
        hideOnlineRow = rowCount++;
        ghostSectionShadowRow = rowCount++;

        // Anti-Delete
        antiDeleteHeaderRow = rowCount++;
        antiDeleteRow = rowCount++;
        antiDeleteShadowRow = rowCount++;

        // Extra
        extraHeaderRow = rowCount++;
        allowScreenshotsRow = rowCount++;
        force120HzRow = rowCount++;
        forwardNoAuthorRow = rowCount++;
        hidePhoneNumberRow = rowCount++;
        customPhoneRow = rowCount++;
        customPhoneValueRow = rowCount++;
        extraShadowRow = rowCount++;

        // Voice changer
        voiceHeaderRow = rowCount++;
        voicePresetRow = rowCount++;
        voiceShadowRow = rowCount++;

        // Device spoofing
        deviceHeaderRow = rowCount++;
        deviceSpoofRow = rowCount++;
        devicePresetRow = rowCount++;
        deviceShadowRow = rowCount++;

        // Fake location
        locationHeaderRow = rowCount++;
        fakeLocationRow = rowCount++;
        locationShadowRow = rowCount++;

        // Customization
        customHeaderRow = rowCount++;
        compactDialogsRow = rowCount++;
        hideRecommendationsRow = rowCount++;
        customShadowRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Ghostgram Settings");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setDurations(200);
        listView.setItemAnimator(animator);

        listView.setAdapter(listAdapter = new ListAdapter(context));

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == voicePresetRow) {
                showVoicePresetDialog();
                return;
            } else if (position == devicePresetRow) {
                showDevicePresetDialog();
                return;
            } else if (position == customPhoneValueRow) {
                showCustomPhoneDialog();
                return;
            }
            if (!(view instanceof TextCheckCell)) return;
            TextCheckCell cell = (TextCheckCell) view;

            if (position == ghostModeMasterRow) {
                GhostConfig.ghostModeEnabled = !GhostConfig.ghostModeEnabled;
                cell.setChecked(GhostConfig.ghostModeEnabled);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.ghostModeChanged);
            } else if (position == showGhostButtonRow) {
                GhostConfig.showGhostButton = !GhostConfig.showGhostButton;
                cell.setChecked(GhostConfig.showGhostButton);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.ghostModeChanged);
            } else if (position == hideReadRow) {
                GhostConfig.hideReadReceipts = !GhostConfig.hideReadReceipts;
                cell.setChecked(GhostConfig.hideReadReceipts);
            } else if (position == hideTypingRow) {
                GhostConfig.hideTypingStatus = !GhostConfig.hideTypingStatus;
                cell.setChecked(GhostConfig.hideTypingStatus);
            } else if (position == hideOnlineRow) {
                GhostConfig.hideOnlineStatus = !GhostConfig.hideOnlineStatus;
                cell.setChecked(GhostConfig.hideOnlineStatus);
            } else if (position == antiDeleteRow) {
                GhostConfig.antiDeleteEnabled = !GhostConfig.antiDeleteEnabled;
                cell.setChecked(GhostConfig.antiDeleteEnabled);
            } else if (position == allowScreenshotsRow) {
                GhostConfig.allowScreenshots = !GhostConfig.allowScreenshots;
                cell.setChecked(GhostConfig.allowScreenshots);
            } else if (position == force120HzRow) {
                GhostConfig.force120Hz = !GhostConfig.force120Hz;
                cell.setChecked(GhostConfig.force120Hz);
                if (GhostConfig.force120Hz && getParentActivity() != null) {
                    Display120HzFix.applyHighRefreshRate(getParentActivity());
                }
            } else if (position == forwardNoAuthorRow) {
                GhostConfig.forwardNoAuthorEnabled = !GhostConfig.forwardNoAuthorEnabled;
                cell.setChecked(GhostConfig.forwardNoAuthorEnabled);
            } else if (position == hidePhoneNumberRow) {
                GhostConfig.hidePhoneNumber = !GhostConfig.hidePhoneNumber;
                cell.setChecked(GhostConfig.hidePhoneNumber);
            } else if (position == customPhoneRow) {
                GhostConfig.customPhoneEnabled = !GhostConfig.customPhoneEnabled;
                cell.setChecked(GhostConfig.customPhoneEnabled);
            } else if (position == deviceSpoofRow) {
                GhostConfig.deviceSpoofEnabled = !GhostConfig.deviceSpoofEnabled;
                cell.setChecked(GhostConfig.deviceSpoofEnabled);
            } else if (position == fakeLocationRow) {
                GhostConfig.fakeLocationEnabled = !GhostConfig.fakeLocationEnabled;
                cell.setChecked(GhostConfig.fakeLocationEnabled);
            } else if (position == compactDialogsRow) {
                GhostConfig.compactDialogs = !GhostConfig.compactDialogs;
                cell.setChecked(GhostConfig.compactDialogs);
            } else if (position == hideRecommendationsRow) {
                GhostConfig.hideChannelRecommendations = !GhostConfig.hideChannelRecommendations;
                cell.setChecked(GhostConfig.hideChannelRecommendations);
            }
            GhostConfig.saveConfig();
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void showVoicePresetDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Изменитель голоса");
        builder.setItems(VoiceChangerDSP.PRESET_NAMES, (dialog, which) -> {
            VoiceChangerDSP.setPreset(which);
            if (listAdapter != null) listAdapter.notifyItemChanged(voicePresetRow);
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showCustomPhoneDialog() {
        if (getParentActivity() == null) return;
        android.widget.EditText editText = new android.widget.EditText(getParentActivity());
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        editText.setHint("+7 999 123 45 67");
        editText.setText(GhostConfig.customPhone);
        editText.setSelection(editText.getText().length());
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        FrameLayout container = new FrameLayout(getParentActivity());
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, org.telegram.messenger.AndroidUtilities.dp(8), pad, 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свой номер телефона");
        builder.setView(container);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            GhostConfig.customPhone = editText.getText().toString().trim();
            GhostConfig.saveConfig();
            if (listAdapter != null) listAdapter.notifyItemChanged(customPhoneValueRow);
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showDevicePresetDialog() {
        if (getParentActivity() == null) return;
        String[] names = new String[GhostConfig.DEVICE_PRESETS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = GhostConfig.DEVICE_PRESETS[i][0];
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Пресет устройства");
        builder.setItems(names, (dialog, which) -> {
            GhostConfig.deviceSpoofIndex = which;
            GhostConfig.saveConfig();
            if (listAdapter != null) listAdapter.notifyItemChanged(devicePresetRow);
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == ghostModeMasterRow || position == showGhostButtonRow ||
                   position == hideReadRow || position == hideTypingRow || position == hideOnlineRow ||
                   position == antiDeleteRow || position == allowScreenshotsRow ||
                   position == force120HzRow || position == forwardNoAuthorRow || position == hidePhoneNumberRow ||
                   position == customPhoneRow || position == customPhoneValueRow ||
                   position == voicePresetRow || position == deviceSpoofRow || position == devicePresetRow ||
                   position == fakeLocationRow || position == compactDialogsRow || position == hideRecommendationsRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ghostHeaderRow || position == antiDeleteHeaderRow || position == extraHeaderRow ||
                    position == voiceHeaderRow || position == deviceHeaderRow ||
                    position == locationHeaderRow || position == customHeaderRow) {
                return TYPE_HEADER;
            } else if (position == ghostSectionShadowRow || position == antiDeleteShadowRow || position == extraShadowRow ||
                    position == voiceShadowRow || position == deviceShadowRow ||
                    position == locationShadowRow || position == customShadowRow) {
                return TYPE_SHADOW;
            } else if (position == voicePresetRow || position == devicePresetRow || position == customPhoneValueRow) {
                return TYPE_SETTINGS;
            }
            return TYPE_CHECK;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == ghostHeaderRow) {
                        cell.setText("👻 Режим Призрака (Ghost Mode)");
                    } else if (position == antiDeleteHeaderRow) {
                        cell.setText("🗑️ Анти-Удаление");
                    } else if (position == extraHeaderRow) {
                        cell.setText("⚡ Приватность и экран");
                    } else if (position == voiceHeaderRow) {
                        cell.setText("🎙 Изменитель голоса");
                    } else if (position == deviceHeaderRow) {
                        cell.setText("📱 Подмена устройства");
                    } else if (position == locationHeaderRow) {
                        cell.setText("📍 Геолокация");
                    } else if (position == customHeaderRow) {
                        cell.setText("🎨 Кастомизация");
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == voicePresetRow) {
                        int p = GhostConfig.voiceChangerPreset;
                        String name = p >= 0 && p < VoiceChangerDSP.PRESET_NAMES.length ? VoiceChangerDSP.PRESET_NAMES[p] : "";
                        cell.setTextAndValue("Пресет голоса", name, false);
                    } else if (position == customPhoneValueRow) {
                        String v = GhostConfig.customPhone;
                        cell.setTextAndValue("Свой номер", android.text.TextUtils.isEmpty(v) ? "Не задан" : v, false);
                    } else if (position == devicePresetRow) {
                        int i = GhostConfig.deviceSpoofIndex;
                        String name = i >= 0 && i < GhostConfig.DEVICE_PRESETS.length ? GhostConfig.DEVICE_PRESETS[i][0] : "";
                        cell.setTextAndValue("Пресет устройства", name, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == ghostModeMasterRow) {
                        cell.setTextAndValueAndCheck(
                            "Мастер Ghost Mode",
                            "Скрытный режим во всех чатах",
                            GhostConfig.ghostModeEnabled,
                            true,
                            true
                        );
                    } else if (position == showGhostButtonRow) {
                        cell.setTextAndValueAndCheck(
                            "Кнопка быстрого режима в чатах",
                            "Отображать значок призрака в верхней панели",
                            GhostConfig.showGhostButton,
                            true,
                            true
                        );
                    } else if (position == hideReadRow) {
                        cell.setTextAndCheck("Не отправлять прочтение (без синих галочек)", GhostConfig.hideReadReceipts, true);
                    } else if (position == hideTypingRow) {
                        cell.setTextAndCheck("Скрывать 'печатает...' и запись аудио", GhostConfig.hideTypingStatus, true);
                    } else if (position == hideOnlineRow) {
                        cell.setTextAndCheck("Скрывать онлайн-статус (всегда оффлайн)", GhostConfig.hideOnlineStatus, false);
                    } else if (position == antiDeleteRow) {
                        cell.setTextAndValueAndCheck(
                            "Сохранять удалённые сообщения",
                            "Помечает удалённые сообщения значком 🗑️",
                            GhostConfig.antiDeleteEnabled,
                            true,
                            false
                        );
                    } else if (position == allowScreenshotsRow) {
                        cell.setTextAndValueAndCheck(
                            "Разрешить скриншоты",
                            "Обход FLAG_SECURE в секретных чатах",
                            GhostConfig.allowScreenshots,
                            true,
                            true
                        );
                    } else if (position == force120HzRow) {
                        cell.setTextAndValueAndCheck(
                            "Принудительно 120 Гц",
                            "Максимальная плавность на дисплее",
                            GhostConfig.force120Hz,
                            true,
                            true
                        );
                    } else if (position == forwardNoAuthorRow) {
                        cell.setTextAndValueAndCheck(
                            "Пересылка без имени",
                            "Пункт 'Без имени' в меню сообщений (drop_author)",
                            GhostConfig.forwardNoAuthorEnabled,
                            true,
                            true
                        );
                    } else if (position == hidePhoneNumberRow) {
                        cell.setTextAndValueAndCheck(
                            "Скрыть номер телефона",
                            "Маскировать телефон в профиле и настройках (+* *** *** ** **)",
                            GhostConfig.hidePhoneNumber,
                            true,
                            true
                        );
                    } else if (position == customPhoneRow) {
                        cell.setTextAndValueAndCheck(
                            "Показывать свой номер",
                            "Визуально подменяет номер в профиле и настройках (на сервере не меняется)",
                            GhostConfig.customPhoneEnabled,
                            true,
                            true
                        );
                    } else if (position == deviceSpoofRow) {
                        cell.setTextAndValueAndCheck(
                            "Подмена устройства",
                            "Применяется при следующем подключении (перезапустите приложение)",
                            GhostConfig.deviceSpoofEnabled,
                            true,
                            true
                        );
                    } else if (position == fakeLocationRow) {
                        cell.setTextAndValueAndCheck(
                            "Подмена геолокации",
                            "Подставлять фейковые координаты при отправке локации",
                            GhostConfig.fakeLocationEnabled,
                            true,
                            false
                        );
                    } else if (position == compactDialogsRow) {
                        cell.setTextAndValueAndCheck(
                            "Компактный список чатов",
                            "Уменьшенные строки диалогов",
                            GhostConfig.compactDialogs,
                            true,
                            true
                        );
                    } else if (position == hideRecommendationsRow) {
                        cell.setTextAndValueAndCheck(
                            "Скрыть рекомендации каналов",
                            "Убирает блок рекомендованных каналов",
                            GhostConfig.hideChannelRecommendations,
                            true,
                            false
                        );
                    }
                    break;
                }
            }
        }
    }
}
