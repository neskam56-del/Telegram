package org.telegram.ghostgram.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

/**
 * Single source of truth for Liquid Glass / Flat re-skin colors, dimensions (360dp base),
 * typography sizes, and crisp vector glyphs. Theme-independent by specification.
 */
public final class ModGlassStyle {

    private ModGlassStyle() {}

    // ==================== 1. Global & Wallpaper ====================

    public static final int BG_BASE_COLOR = 0xFF0A0B0F;

    /** Radial gradient centers (x%, y%), colors, and radius (% of screen). */
    public static final float[][] WP_RADIAL_SPECS = new float[][] {
        {0.20f, 0.12f, 0.38f},
        {0.85f, 0.30f, 0.34f},
        {0.30f, 0.62f, 0.36f},
        {0.80f, 0.85f, 0.32f}
    };

    public static final int[] WP_RADIAL_COLORS = new int[] {
        0xFF3B4A6B,
        0xFF5A3D6B,
        0xFF2B5A56,
        0xFF6B4A2E
    };

    public static final float DOT_SIZE_DP = 1f;
    public static final float DOT_STEP_DP = 22f;
    /** White 18% alpha per dot, drawn on a 50% opacity layer. */
    public static final int DOT_COLOR = 0x2EFFFFFF;
    public static final float DOT_LAYER_ALPHA = 0.50f;

    public static final int SCREEN_H_PAD_DP = 14;
    public static final int CONTENT_BOTTOM_PAD_DP = 100;

    // ==================== 2. Profile Header ====================

    public static final int AVATAR_SIZE_DP = 76;
    public static final float AVATAR_RING_WIDTH_DP = 2f;
    /** White 35% alpha. */
    public static final int AVATAR_RING_COLOR = 0x59FFFFFF;
    public static final int AVATAR_GRADIENT_START = 0xFF6B8CFF;
    public static final int AVATAR_GRADIENT_END = 0xFFB06BFF;
    public static final float AVATAR_INITIAL_SP = 30f;

    public static final int HEADER_TEXT_START_GAP_DP = 14;
    public static final float HEADER_NAME_SP = 21f;
    public static final int HEADER_NAME_COLOR = 0xFFFFFFFF;
    public static final int HEADER_PHONE_TOP_MARGIN_DP = 2;
    public static final float HEADER_PHONE_SP = 13f;
    /** White 65% alpha. */
    public static final int HEADER_PHONE_COLOR = 0xA6FFFFFF;
    public static final float HEADER_USERNAME_SP = 13f;
    public static final int HEADER_USERNAME_COLOR = 0xFF7AB8FF;
    public static final int HEADER_SIDE_PAD_DP = 6;
    public static final int HEADER_BOTTOM_PAD_DP = 14;

    public static final int ACTION_CIRCLE_SIZE_DP = 44;
    public static final int ACTION_CIRCLE_GAP_DP = 10;
    public static final int ACTION_ROW_START_PAD_DP = 6;
    public static final int ACTION_ROW_BOTTOM_PAD_DP = 14;
    public static final int ACTION_ICON_SIZE_DP = 20;

    // ==================== 3. Cards & Rows ====================

    public static final float CARD_RADIUS_DP = 22f;
    public static final int CARD_BOTTOM_MARGIN_DP = 10;

    // Glass style (Liquid Glass ON)
    public static final int GLASS_DEFAULT_OPACITY_PERCENT = 9;
    public static final int GLASS_DEFAULT_BLUR_PERCENT = 50;
    public static final float GLASS_BLUR_DP = 20f;
    public static final float GLASS_SATURATION = 1.5f;
    public static final float GLASS_BORDER_WIDTH_DP = 0.5f;
    /** White 20% alpha. */
    public static final int GLASS_BORDER_COLOR = 0x33FFFFFF;
    public static final float GLASS_TOP_HIGHLIGHT_HEIGHT_DP = 1f;
    /** White 12% alpha. */
    public static final int GLASS_TOP_HIGHLIGHT_COLOR = 0x1FFFFFFF;

    // Flat style (Liquid Glass OFF)
    public static final int FLAT_FILL_COLOR = 0xFF15181F;
    public static final float FLAT_BORDER_WIDTH_DP = 0.5f;
    public static final int FLAT_BORDER_COLOR = 0xFF232834;

    // Rows
    public static final int ROW_HEIGHT_DP = 52;
    public static final int ROW_TALL_HEIGHT_DP = 64;
    public static final int ROW_H_PAD_DP = 16;
    public static final int ROW_GAP_DP = 14;
    public static final float ROW_TITLE_SP = 15f;
    public static final int ROW_TITLE_COLOR = 0xFFFFFFFF;
    public static final float ROW_SUBTITLE_SP = 12f;
    public static final int ROW_SUBTITLE_COLOR = 0xFFAAB1C0;
    public static final float ROW_VALUE_SP = 13f;
    public static final int ROW_VALUE_COLOR = 0xFFAAB1C0;
    public static final float ROW_DIVIDER_HEIGHT_DP = 0.5f;
    /** White 10% alpha. */
    public static final int ROW_DIVIDER_COLOR = 0x1AFFFFFF;

    // Icon tiles & chevrons
    public static final int ICON_TILE_SIZE_DP = 30;
    public static final float ICON_TILE_RADIUS_DP = 9f;
    public static final int ICON_GLYPH_SIZE_DP = 18;
    public static final int CHEVRON_SIZE_DP = 18;
    /** White 40% alpha. */
    public static final int CHEVRON_COLOR = 0x66FFFFFF;

    // Tile palette (Card 1, Card 2, Card 3 + standard Telegram rows)
    public static final int TILE_MY_PROFILE = 0xFF3B82F6;
    public static final int TILE_MOD_SETTINGS = 0xFF8B5CF6;
    public static final int TILE_SAVED_MESSAGES = 0xFFF5A623;
    public static final int TILE_RECENT_CALLS = 0xFF3B6FD6;
    public static final int TILE_DEVICES = 0xFF2F80ED;
    public static final int TILE_FOLDERS = 0xFF2B9BE8;
    public static final int TILE_LIQUID_GLASS = 0xFF14B8A6;
    public static final int TILE_NOTIFICATIONS = 0xFFE5484D;

    public static final int TILE_PRIVACY = 0xFF64748B;
    public static final int TILE_DATA = 0xFF22C55E;
    public static final int TILE_CHAT_SETTINGS = 0xFF0EA5E9;
    public static final int TILE_LITE_MODE = 0xFFF97316;
    public static final int TILE_LANGUAGE = 0xFFA855F7;
    public static final int TILE_PREMIUM = 0xFF9333EA;
    public static final int TILE_STARS = 0xFFEAB308;
    public static final int TILE_BUSINESS = 0xFFEC4899;
    public static final int TILE_GIFT = 0xFF10B981;
    public static final int TILE_HELP = 0xFF38BDF8;
    public static final int TILE_SECONDARY = 0xFF475569;

    // Switch
    public static final int SWITCH_WIDTH_DP = 46;
    public static final int SWITCH_HEIGHT_DP = 28;
    public static final float SWITCH_RADIUS_DP = 14f;
    /** White 20% alpha. */
    public static final int SWITCH_OFF_COLOR = 0x33FFFFFF;
    public static final int SWITCH_ON_COLOR = 0xFF34C47A;
    public static final int SWITCH_THUMB_SIZE_DP = 22;
    public static final int SWITCH_THUMB_INSET_DP = 3;
    public static final int SWITCH_THUMB_COLOR = 0xFFFFFFFF;
    public static final long SWITCH_ANIM_DURATION_MS = 200L;

    // ==================== 4. Bottom Bar ====================

    public static final int BAR_LEFT_MARGIN_DP = 14;
    public static final int BAR_RIGHT_MARGIN_DP = 78;
    public static final int BAR_BOTTOM_MARGIN_DP = 14;
    public static final int BAR_HEIGHT_DP = 62;
    public static final float BAR_RADIUS_DP = 31f;
    public static final int BAR_PADDING_DP = 6;

    public static final int TAB_ICON_SIZE_DP = 21;
    public static final float TAB_LABEL_SP = 10.5f;
    public static final float TAB_ACTIVE_RADIUS_DP = 25f;
    /** White 16% alpha. */
    public static final int TAB_GLASS_ACTIVE_BG = 0x29FFFFFF;
    public static final int TAB_GLASS_ACTIVE_FG = 0xFFFFFFFF;
    /** White 65% alpha. */
    public static final int TAB_INACTIVE_FG = 0xA6FFFFFF;
    public static final int TAB_FLAT_ACTIVE_BG = 0xFF1F2738;
    public static final int TAB_FLAT_ACTIVE_FG = 0xFF5EB0FF;

    public static final int BADGE_BG_COLOR = 0xFFFF5A5F;
    public static final float BADGE_TEXT_SP = 9.5f;
    public static final float BADGE_RADIUS_DP = 8f;
    public static final int BADGE_H_PAD_DP = 4;
    public static final int BADGE_MIN_HEIGHT_DP = 16;

    public static final int SEARCH_BTN_SIZE_DP = 62;
    public static final int SEARCH_BTN_RIGHT_MARGIN_DP = 14;
    public static final int SEARCH_BTN_BOTTOM_MARGIN_DP = 14;
    public static final int SEARCH_ICON_SIZE_DP = 24;

    // ==================== Cached Dot-Grid Shader ====================

    private static BitmapShader cachedDotShader;
    private static int cachedDotStepPx;

    @NonNull
    public static Shader getDotGridShader() {
        int stepPx = Math.max(6, Math.round(AndroidUtilities.dpf2(DOT_STEP_DP)));
        if (cachedDotShader != null && cachedDotStepPx == stepPx) {
            return cachedDotShader;
        }
        cachedDotStepPx = stepPx;
        Bitmap tile = Bitmap.createBitmap(stepPx, stepPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tile);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(DOT_COLOR);
        float r = Math.max(0.5f, AndroidUtilities.dpf2(DOT_SIZE_DP) * 0.5f);
        float center = stepPx * 0.5f;
        c.drawCircle(center, center, r, p);
        cachedDotShader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return cachedDotShader;
    }

    public static int multAlpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ==================== Crisp Vector Glyphs ====================

    public static final int GLYPH_PLUS = 1;
    public static final int GLYPH_CAMERA = 2;
    public static final int GLYPH_AT = 3;
    public static final int GLYPH_GHOST = 4;
    public static final int GLYPH_LIQUID_GLASS = 5;
    public static final int GLYPH_CHEVRON_RIGHT = 6;
    public static final int GLYPH_SEARCH = 7;

    @NonNull
    public static Drawable createGlyphDrawable(int glyphType, int sizeDp, int color) {
        return new GlyphDrawable(glyphType, sizeDp, color);
    }

    private static final class GlyphDrawable extends Drawable {
        private final int type;
        private final int sizeDp;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();
        private int baseColor;
        private int alpha = 255;

        GlyphDrawable(int type, int sizeDp, int color) {
            this.type = type;
            this.sizeDp = sizeDp;
            this.baseColor = color;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
            syncColors();
        }

        private void syncColors() {
            int c = multAlpha(baseColor, alpha / 255f);
            strokePaint.setColor(c);
            fillPaint.setColor(c);
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(sizeDp);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(sizeDp);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            if (w <= 0 || h <= 0) return;

            float s = Math.min(w, h);
            float cx = b.left + w * 0.5f;
            float cy = b.top + h * 0.5f;
            strokePaint.setStrokeWidth(Math.max(1.2f, s * 0.09f));

            switch (type) {
                case GLYPH_PLUS: {
                    float arm = s * 0.30f;
                    canvas.drawLine(cx - arm, cy, cx + arm, cy, strokePaint);
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, strokePaint);
                    break;
                }
                case GLYPH_CAMERA: {
                    float bw = s * 0.76f;
                    float bh = s * 0.54f;
                    float left = cx - bw * 0.5f;
                    float top = cy - bh * 0.42f;
                    oval.set(left, top, left + bw, top + bh);
                    canvas.drawRoundRect(oval, s * 0.11f, s * 0.11f, strokePaint);
                    path.rewind();
                    path.moveTo(cx - bw * 0.22f, top);
                    path.lineTo(cx - bw * 0.13f, top - s * 0.11f);
                    path.lineTo(cx + bw * 0.13f, top - s * 0.11f);
                    path.lineTo(cx + bw * 0.22f, top);
                    canvas.drawPath(path, strokePaint);
                    canvas.drawCircle(cx, top + bh * 0.52f, s * 0.14f, strokePaint);
                    break;
                }
                case GLYPH_AT: {
                    float outerR = s * 0.37f;
                    float innerR = s * 0.16f;
                    canvas.drawCircle(cx, cy, innerR, strokePaint);
                    oval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR);
                    canvas.drawArc(oval, 35f, 295f, false, strokePaint);
                    // Right stem + tail of '@'
                    oval.set(cx + innerR, cy - innerR, cx + outerR, cy + innerR * 1.05f);
                    canvas.drawLine(cx + innerR, cy - innerR * 0.85f, cx + innerR, cy + innerR * 0.55f, strokePaint);
                    oval.set(cx + innerR, cy - innerR * 0.15f, cx + outerR, cy + innerR * 1.15f);
                    canvas.drawArc(oval, 0f, 180f, false, strokePaint);
                    canvas.drawLine(cx + outerR, cy, cx + outerR, cy + innerR * 0.5f, strokePaint);
                    break;
                }
                case GLYPH_GHOST: {
                    float gw = s * 0.66f;
                    float gh = s * 0.76f;
                    float left = cx - gw * 0.5f;
                    float right = cx + gw * 0.5f;
                    float top = cy - gh * 0.5f;
                    float bottom = cy + gh * 0.5f;
                    float domeR = gw * 0.5f;
                    path.rewind();
                    oval.set(left, top, right, top + gw);
                    path.arcTo(oval, 180f, 180f, true);
                    path.lineTo(right, bottom);
                    float wave = gw / 3f;
                    path.lineTo(right - wave * 0.5f, bottom - s * 0.09f);
                    path.lineTo(right - wave, bottom);
                    path.lineTo(cx, bottom - s * 0.09f);
                    path.lineTo(left + wave, bottom);
                    path.lineTo(left + wave * 0.5f, bottom - s * 0.09f);
                    path.lineTo(left, bottom);
                    path.close();
                    canvas.drawPath(path, strokePaint);
                    float eyeR = Math.max(1f, s * 0.055f);
                    float eyeY = top + domeR * 0.92f;
                    canvas.drawCircle(cx - gw * 0.18f, eyeY, eyeR, fillPaint);
                    canvas.drawCircle(cx + gw * 0.18f, eyeY, eyeR, fillPaint);
                    break;
                }
                case GLYPH_LIQUID_GLASS: {
                    // Two overlapping rounded glass panes + subtle sparkle
                    float pw = s * 0.48f;
                    float ph = s * 0.48f;
                    oval.set(cx - pw * 0.72f, cy - ph * 0.72f, cx + pw * 0.28f, cy + ph * 0.28f);
                    canvas.drawRoundRect(oval, s * 0.11f, s * 0.11f, strokePaint);
                    oval.set(cx - pw * 0.28f, cy - ph * 0.28f, cx + pw * 0.72f, cy + ph * 0.72f);
                    canvas.drawRoundRect(oval, s * 0.11f, s * 0.11f, strokePaint);
                    break;
                }
                case GLYPH_CHEVRON_RIGHT: {
                    float dx = s * 0.14f;
                    float dy = s * 0.26f;
                    path.rewind();
                    path.moveTo(cx - dx * 0.7f, cy - dy);
                    path.lineTo(cx + dx * 0.9f, cy);
                    path.lineTo(cx - dx * 0.7f, cy + dy);
                    canvas.drawPath(path, strokePaint);
                    break;
                }
                case GLYPH_SEARCH: {
                    float r = s * 0.26f;
                    float ox = cx - s * 0.05f;
                    float oy = cy - s * 0.05f;
                    canvas.drawCircle(ox, oy, r, strokePaint);
                    float startOffset = r * 0.72f;
                    float endOffset = s * 0.34f;
                    canvas.drawLine(ox + startOffset, oy + startOffset, cx + endOffset, cy + endOffset, strokePaint);
                    break;
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            syncColors();
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
