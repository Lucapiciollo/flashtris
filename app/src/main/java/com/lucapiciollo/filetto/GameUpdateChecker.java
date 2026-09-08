package com.lucapiciollo.filetto;

import android.app.Activity;
import android.content.IntentSender;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Checks the Play Store for a newer app version and prompts the user with Google's own
 * full-screen "immediate update" flow. Relies entirely on Play Store infrastructure: no custom
 * backend, no network call of our own. Silently does nothing if the app wasn't installed from
 * Play Store (e.g. sideloaded debug builds) or the device is offline.
 */
public final class GameUpdateChecker {

    private static final String TAG = "FlashTris";
    public static final int REQ_UPDATE = 950;

    private GameUpdateChecker() { }

    /** Call from onCreate(): checks for an update and starts the immediate-update flow if one is available. */
    public static void checkForUpdate(Activity activity) {
        AppUpdateManager manager = AppUpdateManagerFactory.create(activity);
        manager.getAppUpdateInfo()
                .addOnSuccessListener(info -> startIfAvailable(manager, info, activity))
                .addOnFailureListener(e -> Log.i(TAG, "Update check skipped (offline or not installed via Play Store)", e));
    }

    /** Call from onResume(): resumes an immediate update that was interrupted (e.g. app backgrounded mid-update). */
    public static void resumeUpdateIfInProgress(Activity activity) {
        AppUpdateManager manager = AppUpdateManagerFactory.create(activity);
        manager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startIfAvailable(manager, info, activity);
            }
        });
    }

    private static void startIfAvailable(AppUpdateManager manager, AppUpdateInfo info, Activity activity) {
        boolean updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                || info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
        if (!updateAvailable || !info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return;
        try {
            manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQ_UPDATE);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Failed to start update flow", e);
        }
    }
}
