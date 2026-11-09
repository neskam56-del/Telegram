package org.telegram.ghostgram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ghostgram.GhostConfig;
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
    private int extraShadowRow;

    private int rowCount;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_SHADOW = 2;

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
        extraShadowRow = rowCount++;
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
            if (!(view instanceof TextCheckCell)) return;
            TextCheckCell cell = (TextCheckCell) view;

            if (position == ghostModeMasterRow) {
                GhostConfig.ghostModeEnabled = !GhostConfig.ghostModeEnabled;
                cell.setChecked(GhostConfig.ghostModeEnabled);
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
            }
            GhostConfig.saveConfig();
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
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
            return position == ghostModeMasterRow || position == hideReadRow ||
                   position == hideTypingRow || position == hideOnlineRow ||
                   position == antiDeleteRow || position == allowScreenshotsRow ||
                   position == force120HzRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ghostHeaderRow || position == antiDeleteHeaderRow || position == extraHeaderRow) {
                return TYPE_HEADER;
            } else if (position == ghostSectionShadowRow || position == antiDeleteShadowRow || position == extraShadowRow) {
                return TYPE_SHADOW;
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
                            false
                        );
                    }
                    break;
                }
            }
        }
    }
}
