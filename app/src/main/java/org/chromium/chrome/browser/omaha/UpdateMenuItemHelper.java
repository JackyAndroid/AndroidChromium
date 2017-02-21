// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.appmenu.AppMenu;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.io.File;

/**
 * Contains logic for whether the update menu item should be shown, whether the update toolbar badge
 * should be shown, and UMA logging for the update menu item.
 */
public class UpdateMenuItemHelper {
    private static final String TAG = "UpdateMenuItemHelper";

    // VariationsAssociatedData configs
    private static final String FIELD_TRIAL_NAME = "UpdateMenuItem";
    private static final String ENABLED_VALUE = "true";
    private static final String ENABLE_UPDATE_MENU_ITEM = "enable_update_menu_item";
    private static final String ENABLE_UPDATE_BADGE = "enable_update_badge";
    private static final String SHOW_SUMMARY = "show_summary";
    private static final String USE_NEW_FEATURES_SUMMARY = "use_new_features_summary";
    private static final String CUSTOM_SUMMARY = "custom_summary";

    // UMA constants for logging whether the menu item was clicked.
    private static final int ITEM_NOT_CLICKED = 0;
    private static final int ITEM_CLICKED_INTENT_LAUNCHED = 1;
    private static final int ITEM_CLICKED_INTENT_FAILED = 2;
    private static final int ITEM_CLICKED_BOUNDARY = 3;

    // UMA constants for logging whether Chrome was updated after the menu item was clicked.
    private static final int UPDATED = 0;
    private static final int NOT_UPDATED = 1;
    private static final int UPDATED_BOUNDARY = 2;

    private static UpdateMenuItemHelper sInstance;
    private static Object sGetInstanceLock = new Object();

    // Whether OmahaClient has already been checked for an update.
    private boolean mAlreadyCheckedForUpdates;

    // Whether an update is available.
    private boolean mUpdateAvailable;

    // URL to direct the user to when Omaha detects a newer version available.
    private String mUpdateUrl;

    // Whether the menu item was clicked. This is used to log the click-through rate.
    private boolean mMenuItemClicked;

    // The latest Chrome version available if OmahaClient.isNewerVersionAvailable() returns true.
    private String mLatestVersion;

    /**
     * @return The {@link UpdateMenuItemHelper} instance.
     */
    public static UpdateMenuItemHelper getInstance() {
        synchronized (UpdateMenuItemHelper.sGetInstanceLock) {
            if (sInstance == null) {
                sInstance = new UpdateMenuItemHelper();
                String testMarketUrl = getStringParamValue(ChromeSwitches.MARKET_URL_FOR_TESTING);
                if (!TextUtils.isEmpty(testMarketUrl)) {
                    sInstance.mUpdateUrl = testMarketUrl;
                }
            }
            return sInstance;
        }
    }

    /**
     * Checks if the {@link OmahaClient} knows about an update.
     * @param activity The current {@link ChromeActivity}.
     */
    public void checkForUpdateOnBackgroundThread(final ChromeActivity activity) {
        if (!getBooleanParam(ENABLE_UPDATE_MENU_ITEM)
                && !getBooleanParam(ChromeSwitches.FORCE_SHOW_UPDATE_MENU_ITEM)
                && !getBooleanParam(ChromeSwitches.FORCE_SHOW_UPDATE_MENU_BADGE)) {
            return;
        }

        ThreadUtils.assertOnUiThread();

        if (mAlreadyCheckedForUpdates) {
            if (activity.isActivityDestroyed()) return;
            activity.onCheckForUpdate(mUpdateAvailable);
            return;
        }

        mAlreadyCheckedForUpdates = true;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (OmahaClient.isNewerVersionAvailable(activity)) {
                    mUpdateUrl = OmahaClient.getMarketURL(activity);
                    mLatestVersion = OmahaClient.getLatestVersionNumberString(activity);
                    mUpdateAvailable = true;
                    recordInternalStorageSize();
                } else {
                    mUpdateAvailable = false;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (activity.isActivityDestroyed()) return;
                activity.onCheckForUpdate(mUpdateAvailable);
                recordUpdateHistogram();
            }
        }.execute();
    }

    /**
     * Logs whether an update was performed if the update menu item was clicked.
     * Should be called from ChromeActivity#onStart().
     */
    public void onStart() {
        if (mAlreadyCheckedForUpdates) {
            recordUpdateHistogram();
        }
    }

    /**
     * @param activity The current {@link ChromeActivity}.
     * @return Whether the update menu item should be shown.
     */
    public boolean shouldShowMenuItem(ChromeActivity activity) {
        if (getBooleanParam(ChromeSwitches.FORCE_SHOW_UPDATE_MENU_ITEM)) {
            return true;
        }

        if (!getBooleanParam(ENABLE_UPDATE_MENU_ITEM)) {
            return false;
        }

        return updateAvailable(activity);
    }

    /**
     * @param context The current {@link Context}.
     * @return The string to use for summary text or the empty string if no summary should be shown.
     */
    public String getMenuItemSummaryText(Context context) {
        if (!getBooleanParam(SHOW_SUMMARY) && !getBooleanParam(USE_NEW_FEATURES_SUMMARY)
                && !getBooleanParam(CUSTOM_SUMMARY)) {
            return "";
        }

        String customSummary = getStringParamValue(CUSTOM_SUMMARY);
        if (!TextUtils.isEmpty(customSummary)) {
            return customSummary;
        }

        if (getBooleanParam(USE_NEW_FEATURES_SUMMARY)) {
            return context.getResources().getString(R.string.menu_update_summary_new_features);
        }

        return context.getResources().getString(R.string.menu_update_summary_default);
    }

    /**
     * @param activity The current {@link ChromeActivity}.
     * @return Whether the update badge should be shown in the toolbar.
     */
    public boolean shouldShowToolbarBadge(ChromeActivity activity) {
        if (getBooleanParam(ChromeSwitches.FORCE_SHOW_UPDATE_MENU_BADGE)) {
            return true;
        }

        // The badge is hidden if the update menu item has been clicked until there is an
        // even newer version of Chrome available.
        String latestVersionWhenClicked =
                PrefServiceBridge.getInstance().getLatestVersionWhenClickedUpdateMenuItem();
        if (!getBooleanParam(ENABLE_UPDATE_BADGE)
                || TextUtils.equals(latestVersionWhenClicked, mLatestVersion)) {
            return false;
        }

        return updateAvailable(activity);
    }

    /**
     * Handles a click on the update menu item.
     * @param activity The current {@link ChromeActivity}.
     */
    public void onMenuItemClicked(ChromeActivity activity) {
        if (mUpdateUrl == null) return;

        // If the update menu item is showing because it was forced on through about://flags
        // then mLatestVersion may be null.
        if (mLatestVersion != null) {
            PrefServiceBridge.getInstance().setLatestVersionWhenClickedUpdateMenuItem(
                    mLatestVersion);
        }

        // Fire an intent to open the URL.
        try {
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUpdateUrl));
            activity.startActivity(launchIntent);
            recordItemClickedHistogram(ITEM_CLICKED_INTENT_LAUNCHED);
            PrefServiceBridge.getInstance().setClickedUpdateMenuItem(true);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch Activity for: %s", mUpdateUrl);
            recordItemClickedHistogram(ITEM_CLICKED_INTENT_FAILED);
        }
    }

    /**
     * Should be called before the AppMenu is dismissed if the update menu item was clicked.
     */
    public void setMenuItemClicked() {
        mMenuItemClicked = true;
    }

    /**
     * Called when the {@link AppMenu} is dimissed. Logs a histogram immediately if the update menu
     * item was not clicked. If it was clicked, logging is delayed until #onMenuItemClicked().
     */
    public void onMenuDismissed() {
        if (!mMenuItemClicked) {
            recordItemClickedHistogram(ITEM_NOT_CLICKED);
        }
        mMenuItemClicked = false;
    }

    /**
     * Creates an {@link AnimatorSet} for showing the update badge that is displayed on top
     * of the app menu button.
     *
     * @param menuButton The {@link View} containing the app menu button.
     * @param menuBadge The {@link View} containing the update badge.
     * @return An {@link AnimatorSet} to run when showing the update badge.
     */
    public static AnimatorSet createShowUpdateBadgeAnimation(final View menuButton,
            final View menuBadge) {
        // Create badge ObjectAnimators.
        ObjectAnimator badgeFadeAnimator = ObjectAnimator.ofFloat(menuBadge, View.ALPHA, 1.f);
        badgeFadeAnimator.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);

        int pixelTranslation = menuBadge.getResources().getDimensionPixelSize(
                R.dimen.menu_badge_translation_y_distance);
        ObjectAnimator badgeTranslateYAnimator = ObjectAnimator.ofFloat(menuBadge,
                View.TRANSLATION_Y, pixelTranslation, 0.f);
        badgeTranslateYAnimator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);

        // Create menu button ObjectAnimator.
        ObjectAnimator menuButtonFadeAnimator = ObjectAnimator.ofFloat(menuButton, View.ALPHA, 0.f);
        menuButtonFadeAnimator.setInterpolator(new LinearInterpolator());

        // Create AnimatorSet and listeners.
        AnimatorSet set = new AnimatorSet();
        set.playTogether(badgeFadeAnimator, badgeTranslateYAnimator, menuButtonFadeAnimator);
        set.setDuration(350);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Make sure the menu button is visible again.
                menuButton.setAlpha(1.f);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // Jump to the end state if the animation is canceled.
                menuBadge.setAlpha(1.f);
                menuBadge.setTranslationY(0.f);
                menuButton.setAlpha(1.f);
            }
        });

        return set;
    }

    /**
     * Creates an {@link AnimatorSet} for hiding the update badge that is displayed on top
     * of the app menu button.
     *
     * @param menuButton The {@link View} containing the app menu button.
     * @param menuBadge The {@link View} containing the update badge.
     * @return An {@link AnimatorSet} to run when hiding the update badge.
     */
    public static AnimatorSet createHideUpdateBadgeAnimation(final View menuButton,
            final View menuBadge) {
        // Create badge ObjectAnimator.
        ObjectAnimator badgeFadeAnimator = ObjectAnimator.ofFloat(menuBadge, View.ALPHA, 0.f);
        badgeFadeAnimator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);

        // Create menu button ObjectAnimator.
        ObjectAnimator menuButtonFadeAnimator = ObjectAnimator.ofFloat(menuButton, View.ALPHA, 1.f);
        menuButtonFadeAnimator.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);

        // Create AnimatorSet and listeners.
        AnimatorSet set = new AnimatorSet();
        set.playTogether(badgeFadeAnimator, menuButtonFadeAnimator);
        set.setDuration(200);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                menuBadge.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // Jump to the end state if the animation is canceled.
                menuButton.setAlpha(1.f);
                menuBadge.setVisibility(View.GONE);
            }
        });

        return set;
    }

    private boolean updateAvailable(ChromeActivity activity) {
        if (!mAlreadyCheckedForUpdates) {
            checkForUpdateOnBackgroundThread(activity);
            return false;
        }

        return mUpdateAvailable;
    }

    private void recordItemClickedHistogram(int action) {
        RecordHistogram.recordEnumeratedHistogram("GoogleUpdate.MenuItem.ActionTakenOnMenuOpen",
                action, ITEM_CLICKED_BOUNDARY);
    }

    private void recordUpdateHistogram() {
        if (PrefServiceBridge.getInstance().getClickedUpdateMenuItem()) {
            RecordHistogram.recordEnumeratedHistogram(
                    "GoogleUpdate.MenuItem.ActionTakenAfterItemClicked",
                    mUpdateAvailable ? NOT_UPDATED : UPDATED, UPDATED_BOUNDARY);
            PrefServiceBridge.getInstance().setClickedUpdateMenuItem(false);
        }
    }

    /**
     * Gets a boolean VariationsAssociatedData parameter, assuming the <paramName>="true" format.
     * Also checks for a command-line switch with the same name, for easy local testing.
     * @param paramName The name of the parameter (or command-line switch) to get a value for.
     * @return Whether the param is defined with a value "true", if there's a command-line
     *         flag present with any value.
     */
    private static boolean getBooleanParam(String paramName) {
        if (CommandLine.getInstance().hasSwitch(paramName)) {
            return true;
        }
        return TextUtils.equals(ENABLED_VALUE,
                VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName));
    }

    /**
     * Gets a String VariationsAssociatedData parameter. Also checks for a command-line switch with
     * the same name, for easy local testing.
     * @param paramName The name of the parameter (or command-line switch) to get a value for.
     * @return The command-line flag value if present, or the param is value if present.
     */
    private static String getStringParamValue(String paramName) {
        String value = CommandLine.getInstance().getSwitchValue(paramName);
        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName);
        }
        return value;
    }

    private void recordInternalStorageSize() {
        assert !ThreadUtils.runningOnUiThread();

        File path = Environment.getDataDirectory();
        StatFs statFs = new StatFs(path.getAbsolutePath());
        long size;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            size = getSize(statFs);
        } else {
            size = getSizeUpdatedApi(statFs);
        }
        RecordHistogram.recordLinearCountHistogram(
                "GoogleUpdate.InfoBar.InternalStorageSizeAvailable", (int) size, 1, 200, 100);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static long getSizeUpdatedApi(StatFs statFs) {
        return statFs.getAvailableBytes() / (1024 * 1024);
    }

    @SuppressWarnings("deprecation")
    private static long getSize(StatFs statFs) {
        int blockSize = statFs.getBlockSize();
        int availableBlocks = statFs.getAvailableBlocks();
        return (blockSize * availableBlocks) / (1024 * 1024);
    }
}
