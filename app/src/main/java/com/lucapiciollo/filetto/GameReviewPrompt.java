package com.lucapiciollo.filetto;

import android.app.Activity;
import android.util.Log;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/**
 * Triggers Google Play's native in-app review popup (stars + comment, shown without leaving the
 * app) after a positive moment such as a match win. No custom backend involved: Play Core itself
 * decides internally whether to actually display the dialog (it's quota-limited by Google, so
 * it's safe/expected to call this every time and let Google throttle it).
 */
public final class GameReviewPrompt {

    private static final String TAG = "FlashTris";

    private GameReviewPrompt() { }

    public static void maybeRequestReview(Activity activity) {
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnCompleteListener(request -> {
            if (!request.isSuccessful()) {
                Log.i(TAG, "In-app review not available (offline or not installed via Play Store)");
                return;
            }
            ReviewInfo reviewInfo = request.getResult();
            manager.launchReviewFlow(activity, reviewInfo);
        });
    }
}
