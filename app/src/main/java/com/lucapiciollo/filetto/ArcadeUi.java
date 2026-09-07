package com.lucapiciollo.filetto;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable native arcade presentation. No game or networking state lives here. */
final class ArcadeUi {
    private final Activity activity;
    ArcadeUi(Activity activity) { this.activity = activity; }
    int dp(float n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1.5f), stroke);
        return d;
    }
    GradientDrawable gradient(int a, int b, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a,b});
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1.5f), stroke);
        return d;
    }
    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        return t;
    }
    TextView heading(String value) { return text(value, 29, ArcadeColors.TEXT, true); }
    TextView label(String value) { return text(value, 14, ArcadeColors.MUTED, false); }
    Button button(String value, int color, boolean filled) {
        Button b = new Button(activity);
        b.setText(value); b.setAllCaps(false); b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setTextColor(filled ? ArcadeColors.BACKGROUND : color);
        b.setBackground(filled ? gradient(color, mix(color, Color.WHITE, .17f), color, 18) : background(ArcadeColors.PANEL, color, 18));
        b.setMinHeight(dp(58)); b.setMinimumHeight(dp(58));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setElevation(dp(3));
        return b;
    }
    LinearLayout column() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER_HORIZONTAL);
        return l;
    }
    LinearLayout row() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    LinearLayout card(int stroke) {
        LinearLayout l = column();
        l.setPadding(dp(16),dp(16),dp(16),dp(16));
        l.setBackground(gradient(ArcadeColors.PANEL,0xff10152d,stroke,22));
        return l;
    }
    LinearLayout.LayoutParams full(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top); return p;
    }
    LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0,-2,1); }
    View gap(int height) { View v = new View(activity); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(height))); return v; }
    void add(LinearLayout parent, View child, int top) { parent.addView(child,full(top)); }
    void enter(View v, boolean enabled) {
        if (!enabled) return;
        v.setAlpha(0f); v.setTranslationY(dp(12));
        v.animate().alpha(1f).translationY(0).setDuration(260).start();
    }
    void pop(View v, boolean enabled) {
        if (!enabled) return;
        v.setScaleX(.88f); v.setScaleY(.88f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }
    static int mix(int a,int b,float f) {
        return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),(int)(Color.green(a)*(1-f)+Color.green(b)*f),(int)(Color.blue(a)*(1-f)+Color.blue(b)*f));
    }
}
