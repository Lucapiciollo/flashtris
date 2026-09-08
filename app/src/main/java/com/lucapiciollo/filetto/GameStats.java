package com.lucapiciollo.filetto;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local-only persistent player profile and match statistics.
 * Backed by {@link SharedPreferences}: no server, no accounts, nothing ever leaves the device.
 */
public final class GameStats {

    public enum Outcome { WIN, LOSS, DRAW }

    public static final String[] AVATARS = {"🦊", "🐯", "🐼", "🐸", "🐙", "🤖", "👾", "🦄", "🐲", "🥷"};

    private static final String PREFS = "flashtris_stats";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_WINS_ONLINE = "wins_online";
    private static final String KEY_LOSSES_ONLINE = "losses_online";
    private static final String KEY_DRAWS_ONLINE = "draws_online";
    private static final String KEY_WINS_CPU = "wins_cpu";
    private static final String KEY_LOSSES_CPU = "losses_cpu";
    private static final String KEY_DRAWS_CPU = "draws_cpu";
    private static final String KEY_STREAK = "streak_current";
    private static final String KEY_BEST_STREAK = "streak_best";

    private final SharedPreferences prefs;

    public GameStats(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getNickname(String fallback) {
        return prefs.getString(KEY_NICKNAME, fallback);
    }

    public void setNickname(String nickname) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public String getAvatar() {
        return prefs.getString(KEY_AVATAR, AVATARS[0]);
    }

    public void setAvatar(String avatar) {
        prefs.edit().putString(KEY_AVATAR, avatar).apply();
    }

    public int getWins(boolean vsCpu) {
        return prefs.getInt(vsCpu ? KEY_WINS_CPU : KEY_WINS_ONLINE, 0);
    }

    public int getLosses(boolean vsCpu) {
        return prefs.getInt(vsCpu ? KEY_LOSSES_CPU : KEY_LOSSES_ONLINE, 0);
    }

    public int getDraws(boolean vsCpu) {
        return prefs.getInt(vsCpu ? KEY_DRAWS_CPU : KEY_DRAWS_ONLINE, 0);
    }

    public int getCurrentStreak() {
        return prefs.getInt(KEY_STREAK, 0);
    }

    public int getBestStreak() {
        return prefs.getInt(KEY_BEST_STREAK, 0);
    }

    /** Records the outcome of a just-finished match and updates the win streak. Call exactly once per match. */
    public void recordResult(boolean vsCpu, Outcome outcome) {
        SharedPreferences.Editor editor = prefs.edit();
        switch (outcome) {
            case WIN:
                editor.putInt(vsCpu ? KEY_WINS_CPU : KEY_WINS_ONLINE, getWins(vsCpu) + 1);
                int streak = getCurrentStreak() + 1;
                editor.putInt(KEY_STREAK, streak);
                if (streak > getBestStreak()) editor.putInt(KEY_BEST_STREAK, streak);
                break;
            case LOSS:
                editor.putInt(vsCpu ? KEY_LOSSES_CPU : KEY_LOSSES_ONLINE, getLosses(vsCpu) + 1);
                editor.putInt(KEY_STREAK, 0);
                break;
            case DRAW:
                editor.putInt(vsCpu ? KEY_DRAWS_CPU : KEY_DRAWS_ONLINE, getDraws(vsCpu) + 1);
                editor.putInt(KEY_STREAK, 0);
                break;
        }
        editor.apply();
    }
}
