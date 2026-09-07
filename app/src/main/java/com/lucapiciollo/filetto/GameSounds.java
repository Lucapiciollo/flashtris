package com.lucapiciollo.filetto;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/** Lightweight local audio. No assets, network access or background playback. */
final class GameSounds {
    private final SharedPreferences preferences;
    private final Context context;
    private ToneGenerator tones;
    GameSounds(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("flashtris_arcade", Context.MODE_PRIVATE);
        try { tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 65); } catch (RuntimeException ignored) { }
    }
    boolean sounds() { return preferences.getBoolean("sounds", true); }
    boolean vibration() { return preferences.getBoolean("vibration", true); }
    boolean motion() { return preferences.getBoolean("motion", true); }
    void sounds(boolean value) { preferences.edit().putBoolean("sounds", value).apply(); }
    void vibration(boolean value) { preferences.edit().putBoolean("vibration", value).apply(); }
    void motion(boolean value) { preferences.edit().putBoolean("motion", value).apply(); }
    void play(int tone, int duration) {
        if (!sounds() || tones == null) return;
        try { tones.startTone(tone, duration); } catch (RuntimeException ignored) { }
    }
    void tap() { play(ToneGenerator.TONE_PROP_BEEP, 45); }
    void move() { play(ToneGenerator.TONE_PROP_ACK, 90); vibrate(25); }
    void connected() { play(ToneGenerator.TONE_PROP_ACK, 180); }
    void win() { play(ToneGenerator.TONE_PROP_ACK, 380); vibrate(100); }
    void lose() { play(ToneGenerator.TONE_PROP_NACK, 240); }
    void draw() { play(ToneGenerator.TONE_PROP_BEEP2, 180); }
    private void vibrate(long millis) {
        if (!vibration()) return;
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        else vibrator.vibrate(millis);
    }
    void release() { if (tones != null) { tones.release(); tones = null; } }
}
