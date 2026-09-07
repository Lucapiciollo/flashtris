package com.lucapiciollo.filetto;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * Central palette and drawable factory for the "gamer neon" visual language.
 * Kept dependency-free (no Context) so it can be reused from any screen builder
 * in {@link MainActivity}; all sizes are expected in pixels (already converted
 * by the caller, e.g. via MainActivity#dp).
 */
public final class GameTheme {

    // ---- Palette ---------------------------------------------------------
    public static final int BG_NIGHT = Color.parseColor("#0A0E1B");
    public static final int BG_NIGHT_LOW = Color.parseColor("#050710");
    public static final int BG_PANEL = Color.parseColor("#131A33");
    public static final int BG_PANEL_LIGHT = Color.parseColor("#1B2246");
    public static final int BG_CELL = Color.parseColor("#0F1530");

    public static final int CYAN = Color.parseColor("#00E5FF");
    public static final int BLUE_NEON = Color.parseColor("#2979FF");
    public static final int VIOLET = Color.parseColor("#8C4DFF");
    public static final int MAGENTA = Color.parseColor("#FF2FA0");
    public static final int LIME = Color.parseColor("#C6FF00");
    public static final int DANGER = Color.parseColor("#FF3366");

    public static final int TEXT_PRIMARY = Color.parseColor("#F3F5FF");
    public static final int TEXT_SECONDARY = Color.parseColor("#8E97C2");
    public static final int TEXT_MUTED = Color.parseColor("#5A6290");

    public static final int SYMBOL_X = CYAN;
    public static final int SYMBOL_O = MAGENTA;

    private GameTheme() {
    }

    // ---- Backgrounds -------------------------------------------------------

    /** Deep night gradient used as the base background for every screen. */
    public static GradientDrawable screenBackground() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_NIGHT_LOW, BG_NIGHT, Color.parseColor("#0D1330")});
        return gd;
    }

    /** Flat rounded fill, no border. */
    public static GradientDrawable roundedFill(int fillColor, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    /** Rounded fill with a solid neon border. */
    public static GradientDrawable roundedStroke(int fillColor, int strokeColor, float radiusPx, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    /**
     * Simulates a neon glow around a rounded panel/button using a two-layer
     * drawable: a low-alpha "halo" of the accent color behind an inset solid
     * card with a bright stroke. Cheap (no per-frame drawing) and battery-friendly.
     */
    public static Drawable glowPanel(int fillColor, int accentColor, float radiusPx, int strokeWidthPx, int haloPx) {
        GradientDrawable halo = new GradientDrawable();
        halo.setColor(withAlpha(accentColor, 70));
        halo.setCornerRadius(radiusPx + haloPx);

        GradientDrawable card = roundedStroke(fillColor, accentColor, radiusPx, strokeWidthPx);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{halo, card});
        layers.setLayerInset(1, haloPx, haloPx, haloPx, haloPx);
        return layers;
    }

    /** Wraps a drawable with a ripple touch feedback (available since API 21, minSdk 23). */
    public static Drawable withRipple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(rippleColor, 90)), content, null);
    }

    /** Primary CTA style: lime glow, dark text for max contrast/visibility. */
    public static Drawable primaryButtonBackground(float radiusPx) {
        Drawable base = glowPanel(LIME, LIME, radiusPx, 0, dpGuess(radiusPx));
        return withRipple(base, BG_NIGHT);
    }

    /** Secondary CTA style: dark panel, neon cyan outline. */
    public static Drawable secondaryButtonBackground(float radiusPx) {
        Drawable base = roundedStroke(BG_PANEL_LIGHT, CYAN, radiusPx, 3);
        return withRipple(base, CYAN);
    }

    /** Destructive action style: dark panel, danger outline. */
    public static Drawable dangerButtonBackground(float radiusPx) {
        Drawable base = roundedStroke(BG_PANEL_LIGHT, DANGER, radiusPx, 3);
        return withRipple(base, DANGER);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Transparent oval outline, used to build simple radar/scan decorations. */
    public static GradientDrawable ovalStroke(int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.TRANSPARENT);
        gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    /** Solid oval fill, used for the radar core / decorative dots. */
    public static GradientDrawable ovalFill(int fillColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fillColor);
        return gd;
    }

    // Small helper so glow halo scales sensibly without needing a Context for dp conversion.
    private static int dpGuess(float radiusPx) {
        return Math.max(4, Math.round(radiusPx * 0.35f));
    }
}
