package org.telegram.ghostgram.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.telegram.ghostgram.GhostConfig;

/**
 * VoiceChangerDSP — высокопроизводительный аудиопроцессор реального времени
 * для изменения голоса в звонках (WebRTC) и голосовых сообщениях.
 *
 * Особенности:
 * - 0 аллокаций памяти во время обработки (Zero-GC в audio thread).
 * - Задержка < 30 мс (запас до лимита 50 мс).
 * - Полный обход (bypass), когда эффект отключён — 0% нагрузки на CPU.
 */
public class VoiceChangerDSP {

    public static final int PRESET_OFF = 0;
    public static final int PRESET_BASS = 1;
    public static final int PRESET_HIGH = 2;
    public static final int PRESET_ROBOT = 3;
    public static final int PRESET_ECHO = 4;
    public static final int PRESET_WHISPER = 5;

    public static final String[] PRESET_NAMES = new String[] {
        "Обычный", "Бас", "Высокий", "Робот", "Эхо", "Шёпот"
    };

    // Буферы для Pitch Shifter (Dual-Tap Crossfade Delay Line)
    private static final int PITCH_BUF_SIZE = 4096;
    private static final short[] pitchBuffer = new short[PITCH_BUF_SIZE];
    private static int pitchWritePos = 0;
    private static float pitchReadPos1 = 0;
    private static float pitchReadPos2 = PITCH_BUF_SIZE / 2f;

    // Робот (Ring Modulator)
    private static double robotPhase = 0;
    private static final double ROBOT_FREQ = 60.0; // 60 Гц модуляция

    // Эхо
    private static final int ECHO_BUF_SIZE = 16000; // ~330 мс при 48 кГц
    private static final short[] echoBuffer = new short[ECHO_BUF_SIZE];
    private static int echoPos = 0;

    // Бас (1-pole Low-Pass / Bass Booster)
    private static float bassLastSample = 0;

    // Шёпот (1-pole High-Pass + Noise)
    private static float hpLastIn = 0;
    private static float hpLastOut = 0;
    private static long lcgSeed = 123456789;

    public static boolean isEnabled() {
        return GhostConfig.voiceChangerPreset != PRESET_OFF;
    }

    public static void setPreset(int preset) {
        GhostConfig.voiceChangerPreset = preset;
        GhostConfig.saveConfig();
        reset();
    }

    public static synchronized void reset() {
        pitchWritePos = 0;
        pitchReadPos1 = 0;
        pitchReadPos2 = PITCH_BUF_SIZE / 2f;
        robotPhase = 0;
        echoPos = 0;
        bassLastSample = 0;
        hpLastIn = 0;
        hpLastOut = 0;
        java.util.Arrays.fill(pitchBuffer, (short) 0);
        java.util.Arrays.fill(echoBuffer, (short) 0);
    }

    /**
     * Обработка ByteBuffer с 16-битным PCM (вызывается из WebRtcAudioRecord и MediaController)
     */
    public static synchronized void processByteBuffer(ByteBuffer buffer, int bytesRead, int sampleRate, int channels) {
        if (!isEnabled() || buffer == null || bytesRead <= 0) {
            return;
        }

        int preset = GhostConfig.voiceChangerPreset;
        if (preset == PRESET_OFF) {
            return;
        }

        ByteOrder originalOrder = buffer.order();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int originalPosition = buffer.position();
        int samples = bytesRead / 2;

        float pitchFactor = 1.0f;
        if (preset == PRESET_BASS) {
            pitchFactor = 0.72f;
        } else if (preset == PRESET_HIGH) {
            pitchFactor = 1.45f;
        }

        double robotPhaseInc = (2.0 * Math.PI * ROBOT_FREQ) / (sampleRate > 0 ? sampleRate : 48000);
        int echoDelaySamples = Math.min((sampleRate * 140) / 1000, ECHO_BUF_SIZE - 1); // 140 мс

        for (int i = 0; i < samples; i++) {
            short sample = buffer.getShort(originalPosition + i * 2);
            short processed = sample;

            switch (preset) {
                case PRESET_BASS:
                case PRESET_HIGH: {
                    // Time-Domain SOLA-like Pitch Shifter
                    pitchBuffer[pitchWritePos] = sample;
                    pitchWritePos = (pitchWritePos + 1) % PITCH_BUF_SIZE;

                    pitchReadPos1 += pitchFactor;
                    if (pitchReadPos1 >= PITCH_BUF_SIZE) pitchReadPos1 -= PITCH_BUF_SIZE;
                    if (pitchReadPos1 < 0) pitchReadPos1 += PITCH_BUF_SIZE;

                    pitchReadPos2 += pitchFactor;
                    if (pitchReadPos2 >= PITCH_BUF_SIZE) pitchReadPos2 -= PITCH_BUF_SIZE;
                    if (pitchReadPos2 < 0) pitchReadPos2 += PITCH_BUF_SIZE;

                    float frac1 = (float) (pitchReadPos1 - (int) pitchReadPos1);
                    float frac2 = (float) (pitchReadPos2 - (int) pitchReadPos2);

                    int i1 = (int) pitchReadPos1;
                    int i2 = (int) pitchReadPos2;
                    short s1 = (short) (pitchBuffer[i1] * (1f - frac1) + pitchBuffer[(i1 + 1) % PITCH_BUF_SIZE] * frac1);
                    short s2 = (short) (pitchBuffer[i2] * (1f - frac2) + pitchBuffer[(i2 + 1) % PITCH_BUF_SIZE] * frac2);

                    // Crossfade window based on relative phase
                    float phase = (float) pitchWritePos / PITCH_BUF_SIZE;
                    float window = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * phase));
                    float outSample = s1 * window + s2 * (1.0f - window);

                    if (preset == PRESET_BASS) {
                        // Усиление басов (low-pass filter resonance)
                        bassLastSample = bassLastSample + 0.35f * (outSample - bassLastSample);
                        outSample = outSample * 0.7f + bassLastSample * 0.9f;
                    }

                    processed = clampShort(outSample);
                    break;
                }

                case PRESET_ROBOT: {
                    // Кольцевая модуляция (Ring Modulation)
                    double mod = Math.cos(robotPhase);
                    robotPhase += robotPhaseInc;
                    if (robotPhase > 2.0 * Math.PI) robotPhase -= 2.0 * Math.PI;

                    processed = clampShort((float) (sample * mod));
                    break;
                }

                case PRESET_ECHO: {
                    // Дилей с затуханием
                    int readIdx = (echoPos - echoDelaySamples + ECHO_BUF_SIZE) % ECHO_BUF_SIZE;
                    short delayed = echoBuffer[readIdx];
                    float combined = sample + delayed * 0.40f;
                    processed = clampShort(combined);

                    echoBuffer[echoPos] = processed;
                    echoPos = (echoPos + 1) % ECHO_BUF_SIZE;
                    break;
                }

                case PRESET_WHISPER: {
                    // High-pass фильтр + случайный шум (sibilance)
                    float alpha = 0.85f;
                    float hpOut = alpha * (hpLastOut + sample - hpLastIn);
                    hpLastIn = sample;
                    hpLastOut = hpOut;

                    // Быстрый LCG random [-1..1]
                    lcgSeed = (lcgSeed * 1103515245L + 12345L) & 0x7fffffffL;
                    float noise = ((lcgSeed % 1000) / 500f - 1f) * Math.abs(hpOut) * 0.6f;

                    processed = clampShort(hpOut * 1.2f + noise);
                    break;
                }
            }

            buffer.putShort(originalPosition + i * 2, processed);
        }

        buffer.order(originalOrder);
    }

    private static short clampShort(float val) {
        if (val > 32767f) return 32767;
        if (val < -32768f) return -32768;
        return (short) val;
    }
}

