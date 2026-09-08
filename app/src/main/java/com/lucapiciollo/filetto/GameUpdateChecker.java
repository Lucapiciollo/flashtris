package com.lucapiciollo.filetto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.IntentSender;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Checks the Play Store for a newer app version and prompts the user to update. Relies entirely
 * on Play Store infrastructure: no custom backend, no network call of our own. Silently does
 * nothing if the app wasn't installed from Play Store (e.g. sideloaded debug builds) or the
 * device is offline.
 *
 * Tries an IMMEDIATE (full-screen, blocking) update first; falls back to a FLEXIBLE
 * (background download + "restart to apply" prompt) update if IMMEDIATE isn't allowed for the
 * current rollout, so users still get prompted instead of nothing happening at all.
 */
public final class GameUpdateChecker {

    private static final String TAG = "FlashTris";
    public static final int REQ_UPDATE = 950;

    private static AppUpdateManager appUpdateManager;
    private static InstallStateUpdatedListener installListener;

    private GameUpdateChecker() { }

    /** Call from onCreate(): checks for an update and starts an update flow if one is available. */
    public static void checkForUpdate(Activity activity) {
        AppUpdateManager manager = manager(activity);
        manager.getAppUpdateInfo()
                .addOnSuccessListener(info -> startIfAvailable(manager, info, activity))
                .addOnFailureListener(e -> Log.i(TAG, "Update check skipped (offline or not installed via Play Store)", e));
    }

    /** Call from onResume(): resumes an update that was interrupted, or prompts to restart if a flexible update already finished downloading. */
    public static void resumeUpdateIfInProgress(Activity activity) {
        AppUpdateManager manager = manager(activity);
        manager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startIfAvailable(manager, info, activity);
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                promptCompleteUpdate(manager, activity);
            }
        });
    }

    /** Call from onDestroy(): drops the install-state listener so it doesn't keep the Activity alive. */
    public static void unregister() {
        if (appUpdateManager != null && installListener != null) {
            appUpdateManager.unregisterListener(installListener);
        }
        installListener = null;
    }

    private static AppUpdateManager manager(Activity activity) {
        if (appUpdateManager == null) {
            appUpdateManager = AppUpdateManagerFactory.create(activity.getApplicationContext());
        }
        return appUpdateManager;
    }

    private static void startIfAvailable(AppUpdateManager manager, AppUpdateInfo info, Activity activity) {
        boolean updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                || info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
        if (!updateAvailable) return;
        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            try {
                manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQ_UPDATE);
            } catch (IntentSender.SendIntentException e) {
                Log.w(TAG, "Failed to start immediate update flow", e);
            }
        } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            startFlexibleUpdate(manager, info, activity);
        } else {
            Log.i(TAG, "Update available but neither immediate nor flexible flow is allowed right now");
        }
    }

    private static void startFlexibleUpdate(AppUpdateManager manager, AppUpdateInfo info, Activity activity) {
        if (installListener != null) manager.unregisterListener(installListener);
        installListener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) promptCompleteUpdate(manager, activity);
        };
        manager.registerListener(installListener);
        try {
            manager.startUpdateFlowForResult(info, AppUpdateType.FLEXIBLE, activity, REQ_UPDATE);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Failed to start flexible update flow", e);
        }
    }

    private static void promptCompleteUpdate(AppUpdateManager manager, Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Aggiornamento pronto")
                .setMessage("È stato scaricato un aggiornamento di FlashTris. Riavvia l'app per applicarlo.")
                .setPositiveButton("Riavvia ora", (dialog, which) -> manager.completeUpdate())
                .setNegativeButton("Più tardi", null)
                .setCancelable(true)
                .show();
    }
}
