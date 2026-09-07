package com.lucapiciollo.filetto;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Small collection of lightweight, dependency-free view animation helpers
 * used to keep the "gamer neon" feel consistent across every screen without
 * relying on any external animation library.
 */
public final class GameAnimations {

    private GameAnimations() {
    }

    /** Adds a quick scale-down/scale-up feedback (0.96 -> 1.0) on touch, without swallowing the click. */
    public static void pressFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Fade + slide-up entrance used when a new screen is shown. */
    public static void fadeSlideIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(28f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /** Continuous soft pulse (scale) used for "searching / waiting" radar-like feedback. Returns the animator so it can be stopped later. */
    public static Animator startPulse(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.18f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.18f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.55f, 1f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(1100);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        return set;
    }

    /** Continuous alpha pulse used to highlight whose turn it is. */
    public static Animator startAlphaPulse(View view) {
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.5f, 1f);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setDuration(900);
        alpha.start();
        return alpha;
    }

    public static void stop(Animator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    /** "Pop" entrance used when a symbol is placed on the board. */
    public static void popIn(View view) {
        view.setScaleX(0.2f);
        view.setScaleY(0.2f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    /** Simple celebratory bounce for the end-game screen. */
    public static void celebrate(View view) {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1.12f, 1f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1.12f, 1f);
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY);
        anim.setDuration(550);
        anim.setInterpolator(new OvershootInterpolator());
        anim.start();
    }
}
