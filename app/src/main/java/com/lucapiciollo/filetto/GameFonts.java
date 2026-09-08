package com.lucapiciollo.filetto;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Loads and caches the bundled "gamer" font family from assets/fonts:
 * Audiowide (bold display wordmark/headline) and Rajdhani (angular HUD-style
 * font used everywhere else, in Bold and Regular weights). Falls back
 * silently to the system default typeface if an asset is ever missing.
 */
public final class GameFonts {

    private static Typeface display;
    private static Typeface bold;
    private static Typeface regular;

    private GameFonts() {
    }

    /** Audiowide: used only for the FLASHTRIS wordmark and the big end-of-match headline. */
    public static Typeface display(Context context) {
        if (display == null) {
            display = safeLoad(context, "fonts/Audiowide-Regular.ttf");
        }
        return display;
    }

    /** Rajdhani Bold: used for titles, buttons, labels, values and board symbols. */
    public static Typeface bold(Context context) {
        if (bold == null) {
            bold = safeLoad(context, "fonts/Rajdhani-Bold.ttf");
        }
        return bold;
    }

    /** Rajdhani Regular: used for body copy (subtitles/captions). */
    public static Typeface regular(Context context) {
        if (regular == null) {
            regular = safeLoad(context, "fonts/Rajdhani-Regular.ttf");
        }
        return regular;
    }

    private static Typeface safeLoad(Context context, String assetPath) {
        try {
            return Typeface.createFromAsset(context.getAssets(), assetPath);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }
}
