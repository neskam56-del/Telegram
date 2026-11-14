package org.telegram.ghostgram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ghostgram.ModConfig;
import org.telegram.ghostgram.ModWallpaperManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

/**
 * Экран «Настройки мода»: Liquid Glass, раздел «Обои» (выбор источника, цвета,
 * загрузка картинки из галереи, ползунки затемнения и размытия) и автоотключение.
 */
public class ModSettingsActivity extends BaseFragment implements ModConfig.Listener {

    private static final int REQUEST_PICK_WALLPAPER = 941;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private WallpaperPreviewCell activePreviewCell;

    // Секция: Liquid Glass
    private int glassHeaderRow;
    private int glassSwitchRow;
    private int glassBlurRow;
    private int glassOpacityRow;
    private int glassInfoRow;

    // Секция: Обои
    private int wpHeaderRow;
    private int wpPreviewRow;
    private int wpModeRow;
    private int wpColor1Row;
    private int wpColor2Row;
    private int wpPickImageRow;
    private int wpResetImageRow;
    private int wpDimRow;
    private int wpBlurRow;
    private int wpInfoRow;

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
    private static final int TYPE_SETTINGS = 5;
    private static final int TYPE_COLOR = 6;
    private static final int TYPE_WP_PREVIEW = 7;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        ModConfig.addListener(this);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        ModConfig.removeListener(this);
        activePreviewCell = null;
        super.onFragmentDestroy();
    }

    @Override
    public void onModConfigChanged() {
        if (activePreviewCell != null) {
            activePreviewCell.invalidate();
        }
    }

    private void resetRowIds() {
        glassHeaderRow = -1;
        glassSwitchRow = -1;
        glassBlurRow = -1;
        glassOpacityRow = -1;
        glassInfoRow = -1;

        wpHeaderRow = -1;
        wpPreviewRow = -1;
        wpModeRow = -1;
        wpColor1Row = -1;
        wpColor2Row = -1;
        wpPickImageRow = -1;
        wpResetImageRow = -1;
        wpDimRow = -1;
        wpBlurRow = -1;
        wpInfoRow = -1;

        autoHeaderRow = -1;
        autoBatteryRow = -1;
        autoLowEndRow = -1;
        autoShadowRow = -1;
    }

    private void updateRows() {
        resetRowIds();
        rowCount = 0;

        glassHeaderRow = rowCount++;
        glassSwitchRow = rowCount++;
        glassBlurRow = rowCount++;
        glassOpacityRow = rowCount++;
        glassInfoRow = rowCount++;

        wpHeaderRow = rowCount++;
        wpPreviewRow = rowCount++;
        wpModeRow = rowCount++;
        if (ModConfig.wallpaperMode == ModConfig.WP_COLOR) {
            wpColor1Row = rowCount++;
        } else if (ModConfig.wallpaperMode == ModConfig.WP_GRADIENT) {
            wpColor1Row = rowCount++;
            wpColor2Row = rowCount++;
        } else if (ModConfig.wallpaperMode == ModConfig.WP_IMAGE) {
            wpPickImageRow = rowCount++;
            if (ModWallpaperManager.hasWallpaperImage()) {
                wpResetImageRow = rowCount++;
            }
        }
        wpDimRow = rowCount++;
        wpBlurRow = rowCount++;
        wpInfoRow = rowCount++;

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
            if (position == wpModeRow) {
                showWallpaperModeDialog();
                return;
            } else if (position == wpColor1Row) {
                showColorPickerSheet(true);
                return;
            } else if (position == wpColor2Row) {
                showColorPickerSheet(false);
                return;
            } else if (position == wpPickImageRow) {
                openGalleryForWallpaper();
                return;
            } else if (position == wpResetImageRow) {
                confirmDeleteWallpaperImage();
                return;
            }

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
            } else {
                return;
            }
            ModConfig.save();
            if (position == glassSwitchRow && listAdapter != null) {
                listAdapter.notifyItemRangeChanged(glassBlurRow, 2);
            }
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private static String getWallpaperModeName(int mode) {
        switch (mode) {
            case ModConfig.WP_COLOR:
                return "Цвет";
            case ModConfig.WP_GRADIENT:
                return "Градиент";
            case ModConfig.WP_IMAGE:
                return "Картинка";
            case ModConfig.WP_NONE:
            default:
                return "Стандартные";
        }
    }

    private void showWallpaperModeDialog() {
        if (getParentActivity() == null) return;
        final CharSequence[] items = new CharSequence[] {
                "Стандартные",
                "Сплошной цвет",
                "Градиент",
                "Картинка из галереи"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Обои настроек");
        builder.setItems(items, (dialog, which) -> {
            int newMode;
            switch (which) {
                case 1:
                    newMode = ModConfig.WP_COLOR;
                    break;
                case 2:
                    newMode = ModConfig.WP_GRADIENT;
                    break;
                case 3:
                    newMode = ModConfig.WP_IMAGE;
                    break;
                case 0:
                default:
                    newMode = ModConfig.WP_NONE;
                    break;
            }
            if (newMode == ModConfig.WP_IMAGE && !ModWallpaperManager.hasWallpaperImage()) {
                ModConfig.wallpaperMode = ModConfig.WP_IMAGE;
                ModConfig.save();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                openGalleryForWallpaper();
                return;
            }
            ModConfig.wallpaperMode = newMode;
            ModConfig.save();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showColorPickerSheet(final boolean firstColor) {
        final Context context = getParentActivity();
        if (context == null) return;

        final int initialColor = firstColor ? ModConfig.wallpaperColor1 : ModConfig.wallpaperColor2;
        final int[] pickedColor = new int[] { initialColor };

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));

        TextView titleView = new TextView(context);
        titleView.setText(firstColor
                ? (ModConfig.wallpaperMode == ModConfig.WP_GRADIENT ? "Верхний цвет градиента" : "Цвет обоев")
                : "Нижний цвет градиента");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 22, 4, 22, 8));

        ColorPicker colorPicker = new ColorPicker(context, false, (color, num, applyNow) -> {
            pickedColor[0] = color | 0xFF000000;
            if (firstColor) {
                ModConfig.wallpaperColor1 = pickedColor[0];
            } else {
                ModConfig.wallpaperColor2 = pickedColor[0];
            }
            if (activePreviewCell != null) {
                activePreviewCell.invalidate();
            }
        }) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
            }
        };
        colorPicker.setColor(initialColor, 0);
        colorPicker.setType(-1, true, 1, 1, false, 0, false);
        container.addView(colorPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 300));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        final boolean[] applied = new boolean[] { false };
        final BottomSheet sheet = new BottomSheet(context, true) {
            @Override
            public void dismiss() {
                if (!applied[0]) {
                    if (firstColor) {
                        ModConfig.wallpaperColor1 = initialColor;
                    } else {
                        ModConfig.wallpaperColor2 = initialColor;
                    }
                    if (activePreviewCell != null) {
                        activePreviewCell.invalidate();
                    }
                }
                super.dismiss();
            }
        };

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText("Отмена");
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelBtn.setTypeface(AndroidUtilities.bold());
        cancelBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        cancelBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
        cancelBtn.setOnClickListener(v -> sheet.dismiss());
        buttonsRow.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, 0, 0, 8, 0));

        TextView saveBtn = new TextView(context);
        saveBtn.setText("Применить");
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        saveBtn.setTypeface(AndroidUtilities.bold());
        saveBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        saveBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
        saveBtn.setOnClickListener(v -> {
            applied[0] = true;
            int finalColor = colorPicker.getColor() | 0xFF000000;
            if (firstColor) {
                ModConfig.wallpaperColor1 = finalColor;
            } else {
                ModConfig.wallpaperColor2 = finalColor;
            }
            ModConfig.save();
            if (listAdapter != null) {
                int targetRow = firstColor ? wpColor1Row : wpColor2Row;
                if (targetRow >= 0) {
                    listAdapter.notifyItemChanged(targetRow);
                }
            }
            sheet.dismiss();
        });
        buttonsRow.addView(saveBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38));

        container.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.RIGHT, 16, 6, 16, 0));

        sheet.setCustomView(container);
        showDialog(sheet);
    }

    private void openGalleryForWallpaper() {
        if (getParentActivity() == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Выберите обои"), REQUEST_PICK_WALLPAPER);
        } catch (Throwable t) {
            FileLog.e(t);
            try {
                Intent fallback = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                fallback.setType("image/*");
                startActivityForResult(fallback, REQUEST_PICK_WALLPAPER);
            } catch (Throwable t2) {
                FileLog.e(t2);
                BulletinFactory.of(this).createErrorBulletin("Не удалось открыть галерею").show();
            }
        }
    }

    private void confirmDeleteWallpaperImage() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Удалить картинку обоев?");
        builder.setMessage("Сохранённый файл обоев будет удалён, а фон переключится на стандартные обои.");
        builder.setPositiveButton("Удалить", (dialog, which) -> {
            ModWallpaperManager.deleteWallpaperFile();
            ModConfig.wallpaperMode = ModConfig.WP_NONE;
            ModConfig.save();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && getParentActivity() != null) {
                final Uri uri = data.getData();
                final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);
                progressDialog.showDelayed(180);

                ModWallpaperManager.saveWallpaperFromUri(getParentActivity(), uri, success -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Throwable ignore) {
                    }
                    if (isFinishing()) return;
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (activePreviewCell != null) {
                        activePreviewCell.invalidate();
                    }
                    if (success) {
                        BulletinFactory.of(ModSettingsActivity.this).createSimpleBulletin(R.raw.contact_check, "Обои сохранены").show();
                    } else {
                        BulletinFactory.of(ModSettingsActivity.this).createErrorBulletin("Не удалось загрузить изображение").show();
                    }
                });
            } else if (ModConfig.wallpaperMode == ModConfig.WP_IMAGE && !ModWallpaperManager.hasWallpaperImage()) {
                ModConfig.wallpaperMode = ModConfig.WP_NONE;
                ModConfig.save();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    /** Подпись над ползунком: «Сила размытия: 50%». */
    private static String sliderTitle(String name, int value, String unit) {
        return name + ": " + value + unit;
    }

    /**
     * Живое превью обоев и стеклянной карточки прямо в настройках мода,
     * чтобы изменения цвета, картинки, затемнения и размытия были видны мгновенно.
     */
    private static class WallpaperPreviewCell extends View {
        private final Drawable wallpaperDrawable;
        private final Path clipPath = new Path();
        private final RectF cardRect = new RectF();
        private final RectF glassRect = new RectF();
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WallpaperPreviewCell(Context context) {
            super(context);
            wallpaperDrawable = ModWallpaperManager.createDrawable(true);
            wallpaperDrawable.setCallback(new Drawable.Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    invalidate();
                }

                @Override
                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                    postDelayed(what, Math.max(0L, when - android.os.SystemClock.uptimeMillis()));
                }

                @Override
                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                    removeCallbacks(what);
                }
            });

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(1));

            glassStrokePaint.setStyle(Paint.Style.STROKE);
            glassStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));
            glassStrokePaint.setColor(0x38FFFFFF);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(14));

            subTextPaint.setColor(0xCCFFFFFF);
            subTextPaint.setTextSize(AndroidUtilities.dp(12));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(136), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            float padH = AndroidUtilities.dp(18);
            float padV = AndroidUtilities.dp(12);
            float radius = AndroidUtilities.dp(18);
            cardRect.set(padH, padV, getWidth() - padH, getHeight() - padV);

            int save = canvas.save();
            clipPath.rewind();
            clipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);

            wallpaperDrawable.setBounds(
                    Math.round(cardRect.left),
                    Math.round(cardRect.top),
                    Math.round(cardRect.right),
                    Math.round(cardRect.bottom)
            );
            wallpaperDrawable.draw(canvas);

            // Мини-карточка Liquid Glass поверх превью обоев
            float gMarginH = AndroidUtilities.dp(18);
            float gHeight = AndroidUtilities.dp(54);
            float gTop = cardRect.centerY() - gHeight / 2f;
            glassRect.set(cardRect.left + gMarginH, gTop, cardRect.right - gMarginH, gTop + gHeight);
            float gRadius = AndroidUtilities.dp(14);

            int fillAlpha = ModConfig.isGlassActive()
                    ? Math.round(255f * (ModConfig.glassOpacity / 100f))
                    : 0xCC;
            glassFillPaint.setColor(ModConfig.isGlassActive()
                    ? ((fillAlpha << 24) | 0x00FFFFFF)
                    : ((fillAlpha << 24) | (ModGlassStyle.FLAT_FILL_COLOR & 0x00FFFFFF)));
            canvas.drawRoundRect(glassRect, gRadius, gRadius, glassFillPaint);
            canvas.drawRoundRect(glassRect, gRadius, gRadius, glassStrokePaint);

            float textX = glassRect.left + AndroidUtilities.dp(14);
            canvas.drawText("Предпросмотр Liquid Glass", textX, glassRect.top + AndroidUtilities.dp(23), textPaint);
            String sub = "Обои: " + getWallpaperModeName(ModConfig.wallpaperMode)
                    + " • Затемнение " + ModConfig.wallpaperDim + "%";
            canvas.drawText(sub, textX, glassRect.top + AndroidUtilities.dp(41), subTextPaint);

            canvas.restoreToCount(save);

            borderPaint.setColor(Theme.getColor(Theme.key_divider));
            canvas.drawRoundRect(cardRect, radius, radius, borderPaint);
        }
    }

    /**
     * Ячейка с заголовком и ползунком (SeekBarView, прогресс 0..1).
     * Значение пишется в SharedPreferences только когда палец отпущен.
     */
    private class SliderRowCell extends FrameLayout {
        private final SeekBarView seekBar;
        private final TextView titleView;
        private int rowPosition = -1;
        private boolean needDivider;

        SliderRowCell(Context context) {
            super(context);
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.TOP, 21, 14, 21, 0));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    applyProgress(progress, stop);
                }

                @Override
                public CharSequence getContentDescription() {
                    return titleView.getText();
                }
            });
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                    Gravity.LEFT | Gravity.TOP, 6, 40, 6, 0));
        }

        void bind(int position, boolean divider) {
            rowPosition = position;
            needDivider = divider;
            float progress = 0f;
            boolean enabled = true;

            if (position == glassBlurRow) {
                progress = ModConfig.glassBlur / 100f;
                titleView.setText(sliderTitle("Сила размытия", ModConfig.glassBlur, "%"));
                enabled = ModConfig.glassEnabled && ModConfig.isBlurSupported();
            } else if (position == glassOpacityRow) {
                int range = ModConfig.GLASS_OPACITY_MAX - ModConfig.GLASS_OPACITY_MIN;
                progress = (ModConfig.glassOpacity - ModConfig.GLASS_OPACITY_MIN) / (float) range;
                titleView.setText(sliderTitle("Плотность заливки панелей", ModConfig.glassOpacity, "%"));
                enabled = ModConfig.glassEnabled;
            } else if (position == wpDimRow) {
                progress = ModConfig.wallpaperDim / 100f;
                titleView.setText(sliderTitle("Затемнение обоев", ModConfig.wallpaperDim, "%"));
                enabled = true;
            } else if (position == wpBlurRow) {
                progress = ModConfig.wallpaperBlur / 100f;
                titleView.setText(sliderTitle("Размытие обоев", ModConfig.wallpaperBlur, "%"));
                enabled = ModConfig.wallpaperMode == ModConfig.WP_IMAGE && ModWallpaperManager.hasWallpaperImage();
            }

            seekBar.setProgress(progress);
            seekBar.setEnabled(enabled);
            seekBar.setAlpha(enabled ? 1f : 0.45f);
            titleView.setAlpha(enabled ? 1f : 0.45f);
            invalidate();
        }

        private void applyProgress(float progress, boolean stop) {
            if (!seekBar.isEnabled()) return;
            progress = Math.max(0f, Math.min(1f, progress));
            if (rowPosition == glassBlurRow) {
                int value = Math.round(progress * 100f);
                titleView.setText(sliderTitle("Сила размытия", value, "%"));
                if (stop) {
                    ModConfig.glassBlur = value;
                }
            } else if (rowPosition == glassOpacityRow) {
                int range = ModConfig.GLASS_OPACITY_MAX - ModConfig.GLASS_OPACITY_MIN;
                int value = ModConfig.GLASS_OPACITY_MIN + Math.round(progress * range);
                titleView.setText(sliderTitle("Плотность заливки панелей", value, "%"));
                ModConfig.glassOpacity = value;
                if (activePreviewCell != null) {
                    activePreviewCell.invalidate();
                }
            } else if (rowPosition == wpDimRow) {
                int value = Math.round(progress * 100f);
                titleView.setText(sliderTitle("Затемнение обоев", value, "%"));
                ModConfig.wallpaperDim = value;
                if (activePreviewCell != null) {
                    activePreviewCell.invalidate();
                }
            } else if (rowPosition == wpBlurRow) {
                int value = Math.round(progress * 100f);
                titleView.setText(sliderTitle("Размытие обоев", value, "%"));
                if (stop) {
                    ModConfig.wallpaperBlur = value;
                }
            }
            if (stop) {
                ModConfig.save();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
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
            return position == glassSwitchRow
                    || position == wpModeRow
                    || position == wpColor1Row
                    || position == wpColor2Row
                    || position == wpPickImageRow
                    || position == wpResetImageRow
                    || position == autoBatteryRow
                    || position == autoLowEndRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == glassHeaderRow || position == wpHeaderRow || position == autoHeaderRow) {
                return TYPE_HEADER;
            } else if (position == autoShadowRow) {
                return TYPE_SHADOW;
            } else if (position == glassBlurRow || position == glassOpacityRow || position == wpDimRow || position == wpBlurRow) {
                return TYPE_SLIDER;
            } else if (position == glassInfoRow || position == wpInfoRow) {
                return TYPE_INFO;
            } else if (position == wpModeRow || position == wpPickImageRow || position == wpResetImageRow) {
                return TYPE_SETTINGS;
            } else if (position == wpColor1Row || position == wpColor2Row) {
                return TYPE_COLOR;
            } else if (position == wpPreviewRow) {
                return TYPE_WP_PREVIEW;
            }
            return TYPE_CHECK;
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.itemView instanceof WallpaperPreviewCell) {
                activePreviewCell = (WallpaperPreviewCell) holder.itemView;
                activePreviewCell.invalidate();
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView == activePreviewCell) {
                activePreviewCell = null;
            }
            super.onViewDetachedFromWindow(holder);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_COLOR:
                    view = new TextColorCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_WP_PREVIEW:
                    view = new WallpaperPreviewCell(mContext);
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
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == glassHeaderRow) {
                        cell.setText("Liquid Glass");
                    } else if (position == wpHeaderRow) {
                        cell.setText("Обои");
                    } else if (position == autoHeaderRow) {
                        cell.setText("Автоотключение стекла");
                    }
                    break;
                }
                case TYPE_SLIDER: {
                    boolean divider = position == glassBlurRow || position == wpDimRow;
                    ((SliderRowCell) holder.itemView).bind(position, divider);
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == glassInfoRow) {
                        cell.setText(ModConfig.isBlurSupported()
                                ? "Размытие обоев под панелями считается один раз и кэшируется."
                                : "Android 11 и ниже: размытия нет, панели рисуются полупрозрачными с рамкой.");
                    } else if (position == wpInfoRow) {
                        cell.setText(ModConfig.wallpaperMode == ModConfig.WP_IMAGE
                                ? "Картинка сжимается под экран и размывается в фоне без нагрузки при прокрутке."
                                : "Ползунок размытия обоев активен при выборе своей картинки из галереи.");
                    }
                    cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == wpModeRow) {
                        cell.setTextAndValue("Источник обоев", getWallpaperModeName(ModConfig.wallpaperMode), true);
                    } else if (position == wpPickImageRow) {
                        String status = ModWallpaperManager.hasWallpaperImage() ? "Загружена" : "Не выбрана";
                        cell.setTextAndValue("Выбрать картинку из галереи", status, true);
                    } else if (position == wpResetImageRow) {
                        cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        cell.setText("Удалить сохранённую картинку", true);
                    }
                    break;
                }
                case TYPE_COLOR: {
                    TextColorCell cell = (TextColorCell) holder.itemView;
                    if (position == wpColor1Row) {
                        String label = ModConfig.wallpaperMode == ModConfig.WP_GRADIENT ? "Верхний цвет" : "Цвет обоев";
                        cell.setTextAndColor(label, ModConfig.wallpaperColor1, true);
                    } else if (position == wpColor2Row) {
                        cell.setTextAndColor("Нижний цвет", ModConfig.wallpaperColor2, true);
                    }
                    break;
                }
                case TYPE_WP_PREVIEW: {
                    holder.itemView.invalidate();
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
