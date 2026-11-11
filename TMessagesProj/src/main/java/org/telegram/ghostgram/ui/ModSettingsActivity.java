package org.telegram.ghostgram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ghostgram.ModConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

/**
 * Экран «Настройки мода». Шаг 1: переключатель Liquid Glass, ползунки размытия и
 * прозрачности, автоотключение. Раздел «Обои» добавляется на шаге 5.
 */
public class ModSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    // Секция: Liquid Glass
    private int glassHeaderRow;
    private int glassSwitchRow;
    private int glassBlurRow;
    private int glassOpacityRow;
    private int glassInfoRow;

    // Секция: автоотключение
    private int autoHeaderRow;
    private int autoBatteryRow;
    private int autoLowEndRow;
    private int autoShadowRow;

    private int rowCount;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_SHADOW = 2;
    private static final int TYPE_SLIDER = 3;
    private static final int TYPE_INFO = 4;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        glassHeaderRow = rowCount++;
        glassSwitchRow = rowCount++;
        glassBlurRow = rowCount++;
        glassOpacityRow = rowCount++;
        glassInfoRow = rowCount++;

        autoHeaderRow = rowCount++;
        autoBatteryRow = rowCount++;
        autoLowEndRow = rowCount++;
        autoShadowRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки мода");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
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
            if (position == glassSwitchRow) {
                ModConfig.glassEnabled = !ModConfig.glassEnabled;
                cell.setChecked(ModConfig.glassEnabled);
            } else if (position == autoBatteryRow) {
                ModConfig.glassAutoOffBatterySaver = !ModConfig.glassAutoOffBatterySaver;
                cell.setChecked(ModConfig.glassAutoOffBatterySaver);
            } else if (position == autoLowEndRow) {
                ModConfig.glassAutoOffLowEnd = !ModConfig.glassAutoOffLowEnd;
                cell.setChecked(ModConfig.glassAutoOffLowEnd);
            }
            ModConfig.save();
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    /** Подпись справа от ползунка: «Размытие 50%». */
    private static String sliderTitle(String name, int value, String unit) {
        return name + ": " + value + unit;
    }

    /**
     * Ячейка с заголовком и ползунком (SeekBarView, прогресс 0..1).
     * Значение пишется в ModConfig только когда палец отпущен, чтобы не писать
     * SharedPreferences на каждый пиксель движения.
     */
    private class SliderRowCell extends FrameLayout {
        private final SeekBarView seekBar;
        private final android.widget.TextView titleView;
        private int rowPosition = -1;

        SliderRowCell(Context context) {
            super(context);
            setWillNotDraw(false);

            titleView = new android.widget.TextView(context);
            titleView.setTextSize(16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    android.view.Gravity.LEFT | android.view.Gravity.TOP, 21, 14, 21, 0));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    applyProgress(progress, stop);
                }
            });
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                    android.view.Gravity.LEFT | android.view.Gravity.TOP, 6, 40, 6, 0));
        }

        void bind(int position) {
            rowPosition = position;
            float progress;
            if (position == glassBlurRow) {
                progress = ModConfig.glassBlur / 100f;
                titleView.setText(sliderTitle("Сила размытия", ModConfig.glassBlur, "%"));
            } else {
                int range = ModConfig.GLASS_OPACITY_MAX - ModConfig.GLASS_OPACITY_MIN;
                progress = (ModConfig.glassOpacity - ModConfig.GLASS_OPACITY_MIN) / (float) range;
                titleView.setText(sliderTitle("Прозрачность панелей", ModConfig.glassOpacity, "%"));
            }
            seekBar.setProgress(progress);
        }

        private void applyProgress(float progress, boolean stop) {
            if (rowPosition == glassBlurRow) {
                ModConfig.glassBlur = Math.round(progress * 100f);
                titleView.setText(sliderTitle("Сила размытия", ModConfig.glassBlur, "%"));
            } else if (rowPosition == glassOpacityRow) {
                int range = ModConfig.GLASS_OPACITY_MAX - ModConfig.GLASS_OPACITY_MIN;
                ModConfig.glassOpacity = ModConfig.GLASS_OPACITY_MIN + Math.round(progress * range);
                titleView.setText(sliderTitle("Прозрачность панелей", ModConfig.glassOpacity, "%"));
            }
            if (stop) {
                ModConfig.save();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
        }
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

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == glassSwitchRow || position == autoBatteryRow || position == autoLowEndRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == glassHeaderRow || position == autoHeaderRow) {
                return TYPE_HEADER;
            } else if (position == autoShadowRow) {
                return TYPE_SHADOW;
            } else if (position == glassBlurRow || position == glassOpacityRow) {
                return TYPE_SLIDER;
            } else if (position == glassInfoRow) {
                return TYPE_INFO;
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
                case TYPE_SLIDER:
                    view = new SliderRowCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
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
                    if (position == glassHeaderRow) {
                        cell.setText("Liquid Glass");
                    } else if (position == autoHeaderRow) {
                        cell.setText("Автоотключение стекла");
                    }
                    break;
                }
                case TYPE_SLIDER: {
                    ((SliderRowCell) holder.itemView).bind(position);
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(ModConfig.isBlurSupported()
                            ? "Размытие обоев под панелями считается один раз и кэшируется."
                            : "Android 11 и ниже: размытия нет, панели рисуются полупрозрачными с рамкой.");
                    cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == glassSwitchRow) {
                        cell.setTextAndValueAndCheck("Liquid Glass", "Стеклянные панели и размытие", ModConfig.glassEnabled, true, true);
                    } else if (position == autoBatteryRow) {
                        cell.setTextAndValueAndCheck("При экономии батареи", "Отключать стекло в режиме энергосбережения", ModConfig.glassAutoOffBatterySaver, true, true);
                    } else if (position == autoLowEndRow) {
                        cell.setTextAndValueAndCheck("На слабых устройствах", "Отключать стекло на устройствах с малым объёмом памяти", ModConfig.glassAutoOffLowEnd, true, false);
                    }
                    break;
                }
            }
        }
    }
}
