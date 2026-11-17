package org.telegram.ghostgram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.EditTextBoldCursor;

import java.util.ArrayList;
import java.util.Random;

/**
 * Ghostgram: анимация ввода/удаления текста и светящийся курсор для поля ввода чата.
 *
 * Подключение (уже выполнено в ChatActivityEnterView):
 *  - поле typingAnimationController + ленивый getTypingAnimationController();
 *  - в createMessageEditText() после messageEditTextContainer.addView(messageEditText, ...)
 *    вызывается getTypingAnimationController() для инициализации;
 *  - в анонимном сабклассе messageEditText.onDraw() после super.onDraw(canvas)
 *    вызывается controller.draw(canvas);
 *  - в ChatActivityEditTextCaption.onSelectionChanged() вызывается
 *    controller.onSelectionChanged(selStart, selEnd);
 *  - в ChatActivityEnterView.onDestroy() вызывается controller.release().
 *
 * Хинт («Сообщение») анимируется хуками setTypingHintAnimationEnabled /
 * setTypingHintHasText в EditTextBoldCursor (140 мс, сдвиг 6dp).
 * Стандартный курсор отключается через setTypingCursorAnimationEnabled(true).
 *
 * Рендеринг аппаратно-ускоренный: свечение курсора и частиц рисуется слоями
 * скруглённых прямоугольников/кругов (Paint.setShadowLayer для фигур в HW Canvas
 * не работает), без аллокаций в draw() — частицы и глифы берутся из пулов.
 */
public final class TypingAnimationController {
    private static final long APPEAR_DURATION = 170;
    private static final long PARTICLE_LIFETIME_MIN = 240;
    private static final long PARTICLE_LIFETIME_RANGE = 41;
    private static final int PARTICLES_PER_GLYPH_MIN = 6;
    private static final int PARTICLES_PER_GLYPH_RANGE = 3;
    private static final int MAX_PARTICLES = 512;
    private static final int MAX_APPEAR_SPANS = 512;
    private static final long CURSOR_IDLE_DELAY = 220;
    private static final double BREATH_PERIOD = 900d;
    private static final float CURSOR_LERP_PER_FRAME = 0.32f;

    private final EditTextBoldCursor editText;
    private final ArrayList<AppearSpan> appearSpans = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    private final ArrayList<Particle> particlePool = new ArrayList<>(MAX_PARTICLES);
    private final ArrayList<DeletedGlyph> deletedGlyphs = new ArrayList<>();
    private final ArrayList<DeletedGlyph> deletedGlyphPool = new ArrayList<>();
    private final TextPaint measurePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final DecelerateInterpolator appearInterpolator = new DecelerateInterpolator(1.8f);
    private final TextWatcher textWatcher;

    private String deletedText;
    private float cursorX;
    private float cursorTop;
    private float cursorBottom;
    private boolean cursorInitialized;
    private long lastFrameTime;
    private long lastInteractionTime;
    private boolean hasText;
    private boolean released;

    public TypingAnimationController(EditTextBoldCursor editText) {
        this.editText = editText;
        particlePaint.setColor(0xffffffff);
        cursorPaint.setColor(0xffffffff);
        editText.setTypingCursorAnimationEnabled(true);
        editText.setTypingHintAnimationEnabled(true);
        hasText = editText.length() > 0;
        editText.setTypingHintHasText(hasText);

        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                if (released) {
                    return;
                }
                releaseDeletedGlyphs();
                deletedText = count > 0 ? text.subSequence(start, start + count).toString() : null;
                if (deletedText != null) {
                    captureDeletedGlyphs(start, deletedText);
                }
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (released) {
                    return;
                }
                long now = SystemClock.uptimeMillis();
                lastInteractionTime = now;
                if (before > count && deletedText != null) {
                    spawnDeletionParticles(now);
                }
                if (count > 0 && text instanceof Spannable) {
                    addAppearSpans((Spannable) text, start, count, now);
                }
                boolean newHasText = text.length() > 0;
                if (hasText != newHasText) {
                    hasText = newHasText;
                    editText.setTypingHintHasText(hasText);
                }
                editText.postInvalidateOnAnimation();
            }

            @Override
            public void afterTextChanged(Editable text) {
                deletedText = null;
                releaseDeletedGlyphs();
            }
        };
        editText.addTextChangedListener(textWatcher);
    }

    /** Освобождение ресурсов: вызывать при уничтожении экрана (ChatActivityEnterView.onDestroy). */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        editText.removeTextChangedListener(textWatcher);
        Editable text = editText.getText();
        if (text != null) {
            for (int i = 0; i < appearSpans.size(); i++) {
                text.removeSpan(appearSpans.get(i));
            }
        }
        appearSpans.clear();
        particles.clear();
        releaseDeletedGlyphs();
        editText.setTypingCursorAnimationEnabled(false);
        editText.setTypingHintAnimationEnabled(false);
        editText.invalidate();
    }

    private void releaseDeletedGlyphs() {
        for (int i = 0; i < deletedGlyphs.size(); i++) {
            DeletedGlyph glyph = deletedGlyphs.get(i);
            glyph.x = glyph.y = 0;
            if (deletedGlyphPool.size() < 256) {
                deletedGlyphPool.add(glyph);
            }
        }
        deletedGlyphs.clear();
    }

    private DeletedGlyph obtainDeletedGlyph(float x, float y) {
        DeletedGlyph glyph = deletedGlyphPool.isEmpty() ? new DeletedGlyph() : deletedGlyphPool.remove(deletedGlyphPool.size() - 1);
        glyph.x = x;
        glyph.y = y;
        return glyph;
    }

    /** Координаты удаляемых глифов снимаются ДО изменения текста, по текущему Layout. */
    private void captureDeletedGlyphs(int start, String text) {
        Layout layout = editText.getLayout();
        if (layout == null) {
            return;
        }
        measurePaint.set(editText.getPaint());
        for (int offset = start; offset < start + text.length();) {
            int localOffset = offset - start;
            int next = start + nextGrapheme(text, localOffset, text.length());
            int cp = Character.codePointAt(text, localOffset);
            char value = text.charAt(localOffset);
            if (value != '\n' && value != '\r' && cp != 0x200D) {
                int line = layout.getLineForOffset(offset);
                float x1 = layout.getPrimaryHorizontal(offset);
                float x2 = layout.getPrimaryHorizontal(next);
                float y = layout.getLineBaseline(line) - (layout.getLineBottom(line) - layout.getLineTop(line)) * 0.42f;
                float glyphWidth = measurePaint.measureText(text, localOffset, localOffset + (next - offset));
                float center = (x1 + x2) * 0.5f;
                if (Math.abs(x2 - x1) < 1f) {
                    center += glyphWidth * 0.5f;
                }
                deletedGlyphs.add(obtainDeletedGlyph(center, y));
            }
            offset = next;
        }
    }

    private void spawnDeletionParticles(long now) {
        for (int i = 0; i < deletedGlyphs.size(); i++) {
            DeletedGlyph glyph = deletedGlyphs.get(i);
            int count = PARTICLES_PER_GLYPH_MIN + random.nextInt(PARTICLES_PER_GLYPH_RANGE);
            for (int j = 0; j < count && particles.size() < MAX_PARTICLES; j++) {
                Particle particle = particlePool.isEmpty() ? new Particle() : particlePool.remove(particlePool.size() - 1);
                particle.x = glyph.x;
                particle.y = glyph.y;
                particle.vx = dp(-35f + random.nextFloat() * 90f);
                particle.vy = dp(-45f + random.nextFloat() * 90f);
                particle.radius = dp(0.75f + random.nextFloat() * 0.25f);
                particle.lifetime = PARTICLE_LIFETIME_MIN + random.nextInt((int) PARTICLE_LIFETIME_RANGE);
                particle.startTime = now;
                particles.add(particle);
            }
        }
    }

    private void addAppearSpans(Spannable text, int start, int count, long now) {
        int end = Math.min(text.length(), start + count);
        for (int offset = start; offset < end && appearSpans.size() < MAX_APPEAR_SPANS;) {
            int next = nextGrapheme(text, offset, end);
            if (next <= offset) {
                break;
            }
            ReplacementSpan[] replacements = text.getSpans(offset, next, ReplacementSpan.class);
            boolean replaced = replacements != null && replacements.length > 0;
            if (!replaced && text.charAt(offset) != '\n' && text.charAt(offset) != '\r') {
                AppearSpan span = new AppearSpan(this, now);
                text.setSpan(span, offset, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                appearSpans.add(span);
            }
            offset = next;
        }
    }

    /** Границы графема: суррогатные пары, combining marks, variation selectors,
     *  emoji modifiers, ZWJ-последовательности, пары regional indicators, keycap. */
    private static int nextGrapheme(CharSequence text, int offset, int end) {
        int cp = Character.codePointAt(text, offset);
        int next = offset + Character.charCount(cp);
        boolean joinNext = false;
        int regionalCount = isRegionalIndicator(cp) ? 1 : 0;
        while (next < end) {
            int following = Character.codePointAt(text, next);
            int type = Character.getType(following);
            boolean extending = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || isVariationSelector(following)
                    || isEmojiModifier(following) || following == 0x20E3;
            if (extending || joinNext || following == 0x200D || isRegionalIndicator(following) && regionalCount == 1) {
                next += Character.charCount(following);
                if (following == 0x200D) {
                    joinNext = true;
                } else {
                    joinNext = false;
                    if (isRegionalIndicator(following)) {
                        regionalCount++;
                    }
                }
            } else {
                break;
            }
        }
        return Math.min(next, end);
    }

    private static boolean isVariationSelector(int cp) {
        return cp >= 0xFE00 && cp <= 0xFE0F || cp >= 0xE0100 && cp <= 0xE01EF;
    }

    private static boolean isEmojiModifier(int cp) {
        return cp >= 0x1F3FB && cp <= 0x1F3FF;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    public void onSelectionChanged(int start, int end) {
        if (released) {
            return;
        }
        lastInteractionTime = SystemClock.uptimeMillis();
        editText.postInvalidateOnAnimation();
    }

    /** Вызывать из messageEditText.onDraw() после super.onDraw(canvas). Аллокаций нет. */
    public void draw(Canvas canvas) {
        if (released) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameTime == 0 ? 1f / 60f : Math.min(0.05f, Math.max(0f, (now - lastFrameTime) / 1000f));
        boolean needsFrame = false;

        canvas.save();
        // Та же система координат, что и у нативного курсора EditTextBoldCursor:
        // layout-координаты + padding/gravity offset; скролл применяется на уровне View.
        int voffset = 0;
        if ((editText.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = editText.getTotalPaddingTop() - editText.getExtendedPaddingTop();
        }
        canvas.translate(editText.getPaddingLeft(), editText.getExtendedPaddingTop() + voffset);

        if (!particles.isEmpty()) {
            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle particle = particles.get(i);
                long age = now - particle.startTime;
                if (age >= particle.lifetime) {
                    particles.remove(i);
                    if (particlePool.size() < MAX_PARTICLES) {
                        particlePool.add(particle);
                    }
                    continue;
                }
                particle.x += particle.vx * dt;
                particle.y += particle.vy * dt;
                particle.vy += dp(28f) * dt;
                float progress = age / (float) particle.lifetime;
                float fade = 1f - progress;
                particlePaint.setAlpha((int) (70 * fade));
                canvas.drawCircle(particle.x, particle.y, particle.radius * 2.5f, particlePaint);
                particlePaint.setAlpha((int) (255 * fade));
                canvas.drawCircle(particle.x, particle.y, particle.radius * fade, particlePaint);
            }
            needsFrame |= !particles.isEmpty();
        }

        Layout layout = editText.getLayout();
        if (editText.isFocused() && layout != null && editText.getSelectionStart() == editText.getSelectionEnd()) {
            drawCursor(canvas, layout, now, dt);
            needsFrame = true;
        } else {
            cursorInitialized = false;
        }

        Editable text = editText.getText();
        for (int i = appearSpans.size() - 1; i >= 0; i--) {
            AppearSpan span = appearSpans.get(i);
            if (now - span.startTime >= APPEAR_DURATION || text == null || text.getSpanStart(span) < 0) {
                appearSpans.remove(i);
                editText.post(span);
            } else {
                needsFrame = true;
            }
        }
        canvas.restore();
        lastFrameTime = now;
        if (needsFrame) {
            editText.postInvalidateOnAnimation();
        }
    }

    private void drawCursor(Canvas canvas, Layout layout, long now, float dt) {
        int offset = Math.max(0, Math.min(editText.getSelectionStart(), layout.getText().length()));
        int line = layout.getLineForOffset(offset);
        float targetX = layout.getPrimaryHorizontal(offset);
        float targetTop = layout.getLineTop(line);
        float targetBottom = layout.getLineBottom(line);
        if (!cursorInitialized) {
            cursorX = targetX;
            cursorTop = targetTop;
            cursorBottom = targetBottom;
            cursorInitialized = true;
        } else {
            // Эквивалент currentX += (targetX - currentX) * 0.32f при 60 FPS,
            // нормализованный по deltaTime для 90/120 Гц.
            float k = 1f - (float) Math.pow(1f - CURSOR_LERP_PER_FRAME, dt * 60d);
            cursorX += (targetX - cursorX) * k;
            cursorTop += (targetTop - cursorTop) * k;
            cursorBottom += (targetBottom - cursorBottom) * k;
        }
        float alpha = 1f;
        if (lastInteractionTime == 0) {
            lastInteractionTime = now;
        }
        long idle = now - lastInteractionTime;
        if (idle > CURSOR_IDLE_DELAY) {
            float breath = 0.5f + 0.5f * (float) Math.sin(idle * 2d * Math.PI / BREATH_PERIOD);
            alpha = (90f + 165f * breath) / 255f;
        }
        float width = dp(2);
        float height = Math.max(dp(12), cursorBottom - cursorTop);
        float top = cursorTop + (cursorBottom - cursorTop - height) * 0.5f;
        float bottom = top + height;
        float radius = dp(1);
        // Мягкое свечение слоями (hardware-safe замена setShadowLayer).
        rect.set(cursorX - width * 0.5f - dp(3), top - dp(2), cursorX + width * 0.5f + dp(3), bottom + dp(2));
        cursorPaint.setAlpha((int) (45 * alpha));
        canvas.drawRoundRect(rect, dp(4), dp(4), cursorPaint);
        rect.set(cursorX - width * 0.5f - dp(1), top, cursorX + width * 0.5f + dp(1), bottom);
        cursorPaint.setAlpha((int) (105 * alpha));
        canvas.drawRoundRect(rect, dp(2), dp(2), cursorPaint);
        rect.set(cursorX - width * 0.5f, top, cursorX + width * 0.5f, bottom);
        cursorPaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(rect, radius, radius, cursorPaint);
    }

    private float dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private static final class DeletedGlyph {
        float x;
        float y;
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float radius;
        long lifetime;
        long startTime;
    }

    private static final class AppearSpan extends ReplacementSpan implements Runnable {
        private final TypingAnimationController controller;
        private final long startTime;
        private boolean cleanupPosted;

        AppearSpan(TypingAnimationController controller, long startTime) {
            this.controller = controller;
            this.startTime = startTime;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int baseline, int bottom, Paint paint) {
            float progress = Math.min(1f, Math.max(0f, (SystemClock.uptimeMillis() - startTime) / (float) APPEAR_DURATION));
            float eased = controller.appearInterpolator.getInterpolation(progress);
            float scale = 1.28f + (1f - 1.28f) * eased;
            float offsetY = -controller.dp(3) * (1f - eased);
            int oldAlpha = paint.getAlpha();
            paint.setAlpha((int) (oldAlpha * eased));
            float width = paint.measureText(text, start, end);
            canvas.save();
            canvas.translate(0, offsetY);
            canvas.scale(scale, scale, x + width * 0.5f, baseline - (paint.descent() - paint.ascent()) * 0.25f);
            canvas.drawText(text, start, end, x, baseline, paint);
            canvas.restore();
            paint.setAlpha(oldAlpha);
            if (progress < 1f) {
                controller.editText.postInvalidateOnAnimation();
            } else if (!cleanupPosted) {
                cleanupPosted = true;
                controller.editText.post(this);
            }
        }

        @Override
        public void run() {
            if (controller.released) {
                return;
            }
            Editable text = controller.editText.getText();
            if (text != null && text.getSpanStart(this) >= 0) {
                text.removeSpan(this);
            }
        }
    }
}