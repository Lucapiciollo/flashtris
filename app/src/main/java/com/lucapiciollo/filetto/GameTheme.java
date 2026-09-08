package com.lucapiciollo.filetto;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * Central palette and drawable factory for the "deep forest" soft-UI (neumorphic)
 * visual language. Kept dependency-free (no Context) so it can be reused from any
 * screen builder in {@link MainActivity}; all sizes are expected in pixels (already
 * converted by the caller, e.g. via MainActivity#dp).
 * <p>
 * Depth is simulated with diagonal (top-left to bottom-right) gradients rather than
 * flat fills: {@link #roundedFill} / {@link #roundedStroke} lighten the top-left edge
 * and darken the bottom-right edge to read as a soft "raised bump", while
 * {@link #insetFill} / {@link #insetStroke} do the reverse to read as "pressed in"
 * (used for the game board cells).
 */
public final class GameTheme {

    // ---- Palette (Deep Forest / Mint) ------------------------------------
    public static final int BG_NIGHT = Color.parseColor("#0E1B14");
    public static final int BG_NIGHT_LOW = Color.parseColor("#0A140F");
    public static final int BG_PANEL = Color.parseColor("#16261E");
    public static final int BG_PANEL_LIGHT = Color.parseColor("#1C2F24");
    public static final int BG_CELL = Color.parseColor("#14231C");

    public static final int CYAN = Color.parseColor("#0FDB8F");
    public static final int BLUE_NEON = Color.parseColor("#17C79A");
    public static final int VIOLET = Color.parseColor("#E0954D");
    public static final int MAGENTA = Color.parseColor("#FFD166");
    public static final int LIME = Color.parseColor("#9BE15D");
    public static final int DANGER = Color.parseColor("#FF6152");

    public static final int TEXT_PRIMARY = Color.parseColor("#F1F7F3");
    public static final int TEXT_SECONDARY = Color.parseColor("#8FAE9C");
    public static final int TEXT_MUTED = Color.parseColor("#55705F");

    public static final int SYMBOL_X = CYAN;
    public static final int SYMBOL_O = MAGENTA;

    private GameTheme() {
    }

    // ---- Backgrounds -------------------------------------------------------

    /** Deep forest gradient used as the base background for every screen. */
    public static GradientDrawable screenBackground() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_NIGHT_LOW, BG_NIGHT, Color.parseColor("#0D1F17")});
        return gd;
    }

    /** Raised (bump) rounded fill: subtle light top-left, subtle shadow bottom-right. */
    public static GradientDrawable roundedFill(int fillColor, float radiusPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(fillColor, 0.12f), fillColor, darken(fillColor, 0.35f)});
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    /** Raised (bump) rounded fill with a solid border. */
    public static GradientDrawable roundedStroke(int fillColor, int strokeColor, float radiusPx, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(fillColor, 0.10f), fillColor, darken(fillColor, 0.30f)});
        gd.setCornerRadius(radiusPx);
        gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    /**
     * Recessed (pressed-in) rounded fill — reverse gradient of {@link #roundedFill}:
     * dark top-left (light blocked by the recess wall), light bottom-right (reflected
     * light). Used for the game board cells so they read as "slots" you tap into.
     */
    public static GradientDrawable insetFill(int fillColor, float radiusPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{darken(fillColor, 0.45f), fillColor, lighten(fillColor, 0.08f)});
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    /** Recessed (pressed-in) rounded fill with a solid border. */
    public static GradientDrawable insetStroke(int fillColor, int strokeColor, float radiusPx, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{darken(fillColor, 0.45f), fillColor, lighten(fillColor, 0.08f)});
        gd.setCornerRadius(radiusPx);
        gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    /** Blends a color towards white by {@code factor} (0..1). */
    public static int lighten(int color, float factor) {
        int r = Color.red(color) + Math.round((255 - Color.red(color)) * factor);
        int g = Color.green(color) + Math.round((255 - Color.green(color)) * factor);
        int b = Color.blue(color) + Math.round((255 - Color.blue(color)) * factor);
        return Color.rgb(clampChannel(r), clampChannel(g), clampChannel(b));
    }

    /** Blends a color towards black by {@code factor} (0..1). */
    public static int darken(int color, float factor) {
        int r = Math.round(Color.red(color) * (1 - factor));
        int g = Math.round(Color.green(color) * (1 - factor));
        int b = Math.round(Color.blue(color) * (1 - factor));
        return Color.rgb(clampChannel(r), clampChannel(g), clampChannel(b));
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
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
