// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Contains functionality needed for Chrome to host WebAPKs.
 */
public class ChromeWebApkHost {
    private static final String TAG = "ChromeWebApkHost";

    private static Boolean sEnabledForTesting;

    public static void init() {
        WebApkValidator.initWithBrowserHostSignature(ChromeWebApkHostSignature.EXPECTED_SIGNATURE);
    }

    public static void initForTesting(boolean enabled) {
        sEnabledForTesting = enabled;
    }

    public static boolean isEnabled() {
        if (sEnabledForTesting != null) return sEnabledForTesting;

        return isEnabledInPrefs();
    }

    @CalledByNative
    private static boolean areWebApkEnabled() {
        return ChromeWebApkHost.isEnabled();
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have
     * to use the cached value because native library may not yet been loaded.
     *
     * @return Whether the feature is enabled.
     */
    private static boolean isEnabledInPrefs() {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(
                    ContextUtils.getApplicationContext()).getCachedWebApkRuntimeEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Show dialog warning user that "installation from unknown sources" is required by the WebAPK
     * experiment if:
     * - The user toggled the --enable-improved-a2hs command line flag via chrome://flags
     * AND
     * - WebAPKs are not disabled via variations kill switch.
     * Must be run prior to {@link cacheEnabledStateForNextLaunch}.
     */
    public static void launchWebApkRequirementsDialogIfNeeded(Context context) {
        // Show dialog on Canary & Dev. Installation via "unknown sources" is disabled via
        // variations on other channels.
        if (!ChromeVersionInfo.isCanaryBuild() && !ChromeVersionInfo.isDevBuild()) return;

        Context applicationContext = ContextUtils.getApplicationContext();
        boolean wasCommandLineFlagEnabled = ChromePreferenceManager.getInstance(applicationContext)
                                                    .getCachedWebApkCommandLineEnabled();
        if (computeEnabled() && !wasCommandLineFlagEnabled
                && !installingFromUnknownSourcesAllowed(applicationContext)) {
            showUnknownSourcesNeededDialog(context);
        }
    }

    /**
     * Once native is loaded we can consult the command-line (set via about:flags) and also finch
     * state to see if we should enable WebAPKs.
     */
    public static void cacheEnabledStateForNextLaunch() {
        ChromePreferenceManager preferenceManager =
                ChromePreferenceManager.getInstance(ContextUtils.getApplicationContext());

        boolean wasCommandLineEnabled = preferenceManager.getCachedWebApkCommandLineEnabled();
        boolean isCommandLineEnabled = isCommandLineFlagSet();
        if (isCommandLineEnabled != wasCommandLineEnabled) {
            // {@link launchWebApkRequirementsDialogIfNeeded()} is skipped the first time Chrome is
            // launched so do caching here instead.
            preferenceManager.setCachedWebApkCommandLineEnabled(isCommandLineEnabled);
        }

        boolean wasEnabled = isEnabledInPrefs();
        boolean isEnabled = computeEnabled();
        if (isEnabled != wasEnabled) {
            Log.d(TAG, "WebApk setting changed (%s => %s)", wasEnabled, isEnabled);
            preferenceManager.setCachedWebApkRuntimeEnabled(isEnabled);
        }
    }

    /** Returns whether the --enable-improved-a2hs command line flag is set */
    private static boolean isCommandLineFlagSet() {
        return CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_WEBAPK);
    }

    /** Returns whether we should enable WebAPKs */
    private static boolean computeEnabled() {
        return isCommandLineFlagSet() && ChromeFeatureList.isEnabled(ChromeFeatureList.WEBAPKS);
    }

    /**
     * Returns whether the user has enabled installing apps from sources other than the Google Play
     * Store.
     */
    private static boolean installingFromUnknownSourcesAllowed(Context context) {
        try {
            return Settings.Secure.getInt(
                           context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS)
                    == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    /**
     * Show dialog warning user that "installation from unknown sources" is required by the WebAPK
     * experiment.
     */
    private static void showUnknownSourcesNeededDialog(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.webapk_unknown_sources_dialog_title);
        builder.setMessage(R.string.webapk_unknown_sources_dialog_message);
        builder.setPositiveButton(R.string.webapk_unknown_sources_settings_button,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Open Android Security settings.
                        Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                        context.startActivity(intent);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
