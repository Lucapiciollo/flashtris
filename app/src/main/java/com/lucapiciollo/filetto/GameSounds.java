package com.lucapiciollo.filetto;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Lightweight local sound + haptic feedback manager.
 * Uses {@link ToneGenerator} (no audio assets needed) and the system
 * {@link Vibrator}. Preferences are persisted locally via SharedPreferences,
 * no external dependency, no backend.
 */
public class GameSounds {

    private static final String TAG = "FlashTris";
    private static final String PREFS_NAME = "flashtris_settings";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_VIBRATION = "vibration_enabled";

    private final SharedPreferences prefs;
    private final Vibrator vibrator;
    private ToneGenerator toneGenerator;

    public GameSounds(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (RuntimeException e) {
            Log.w(TAG, "ToneGenerator unavailable", e);
            toneGenerator = null;
        }
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION, true);
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    public void tap() {
        tone(ToneGenerator.TONE_PROP_BEEP, 35);
    }

    public void moveX() {
        tone(ToneGenerator.TONE_DTMF_1, 70);
    }

    public void moveO() {
        tone(ToneGenerator.TONE_DTMF_4, 70);
    }

    public void connected() {
        tone(ToneGenerator.TONE_PROP_ACK, 150);
        vibrate(new long[]{0, 40});
    }

    public void win() {
        tone(ToneGenerator.TONE_PROP_BEEP2, 400);
        vibrate(new long[]{0, 70, 60, 70, 60, 120});
    }

    public void lose() {
        tone(ToneGenerator.TONE_CDMA_LOW_L, 350);
        vibrate(new long[]{0, 150});
    }

    public void draw() {
        tone(ToneGenerator.TONE_PROP_NACK, 250);
        vibrate(new long[]{0, 60});
    }

    private void tone(int type, int durationMs) {
        if (!isSoundEnabled() || toneGenerator == null) return;
        try {
            toneGenerator.startTone(type, durationMs);
        } catch (Exception e) {
            Log.w(TAG, "startTone failed", e);
        }
    }

    @SuppressWarnings("deprecation") // Vibrator#vibrate(long[], int) is the only API available below API 26 (minSdk 23).
    private void vibrate(long[] pattern) {
        if (!isVibrationEnabled() || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Log.w(TAG, "vibrate failed", e);
        }
    }

    /** Must be called from the owning Activity's onDestroy to free native resources. */
    public void release() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
