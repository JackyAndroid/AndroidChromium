// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.SharedPreferences;
import android.os.Build;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.components.location.LocationUtils;

/**
 * This class provides the basic interface to the Physical Web feature.
 */
public class PhysicalWeb {
    public static final int OPTIN_NOTIFY_MAX_TRIES = 1;
    private static final String PREF_PHYSICAL_WEB_NOTIFY_COUNT = "physical_web_notify_count";
    private static final String PREF_IGNORE_OTHER_CLIENTS = "physical_web_ignore_other_clients";
    private static final String FEATURE_NAME = "PhysicalWeb";
    private static final String IGNORE_OTHER_CLIENTS_FEATURE_NAME = "PhysicalWebIgnoreOtherClients";
    private static final int MIN_ANDROID_VERSION = 18;

    /**
     * Evaluate whether the environment is one in which the Physical Web should
     * be enabled.
     * @return true if the PhysicalWeb should be enabled
     */
    public static boolean featureIsEnabled() {
        return ChromeFeatureList.isEnabled(FEATURE_NAME)
                && Build.VERSION.SDK_INT >= MIN_ANDROID_VERSION;
    }

    /**
     * Checks whether the Physical Web preference is switched to On.
     *
     * @return boolean {@code true} if the preference is On.
     */
    public static boolean isPhysicalWebPreferenceEnabled() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebEnabled();
    }

    /**
     * Checks whether the Physical Web onboard flow is active and the user has
     * not yet elected to either enable or decline the feature.
     *
     * @return boolean {@code true} if onboarding is complete.
     */
    public static boolean isOnboarding() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebOnboarding();
    }

    /**
     * Start the Physical Web feature.
     * At the moment, this only enables URL discovery over BLE.
     */
    public static void startPhysicalWeb() {
        // Only subscribe to Nearby if we have the location permission.
        LocationUtils locationUtils = LocationUtils.getInstance();
        if (locationUtils.hasAndroidLocationPermission()
                && locationUtils.isSystemLocationSettingEnabled()) {
            new NearbyBackgroundSubscription(NearbySubscription.SUBSCRIBE, new Runnable() {
                @Override
                public void run() {
                    // We need to clear the list of nearby URLs so that they can be repopulated by
                    // the new subscription, but we don't know whether we are already subscribed, so
                    // we need to pass a callback so that we can clear as soon as we are
                    // resubscribed.
                    UrlManager.getInstance().clearNearbyUrls();
                }
            }).run();
        }
    }

    /**
     * Stop the Physical Web feature.
     */
    public static void stopPhysicalWeb() {
        new NearbyBackgroundSubscription(NearbySubscription.UNSUBSCRIBE, new Runnable() {
            @Override
            public void run() {
                // This isn't absolutely necessary, but it's nice to clean up all our shared prefs.
                UrlManager.getInstance().clearAllUrls();
            }
        }).run();
    }

    /**
     * Returns true if we should fire notifications regardless of the existence of other Physical
     * Web clients.
     * This method is for use when the native library is not available.
     */
    public static boolean shouldIgnoreOtherClients() {
        return ContextUtils.getAppSharedPreferences().getBoolean(PREF_IGNORE_OTHER_CLIENTS, false);
    }

    /**
     * Increments a value tracking how many times we've shown the Physical Web
     * opt-in notification.
     */
    public static void recordOptInNotification() {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        int value = sharedPreferences.getInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, 0);
        sharedPreferences.edit().putInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, value + 1).apply();
    }

    /**
     * Gets the current count of how many times a high-priority opt-in notification
     * has been shown.
     * @return an integer representing the high-priority notifification display count.
     */
    public static int getOptInNotifyCount() {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        return sharedPreferences.getInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, 0);
    }

    /**
     * Perform various Physical Web operations that should happen on startup.
     */
    public static void onChromeStart() {
        // The PhysicalWebUma calls in this method should be called only when the native library is
        // loaded.  This is always the case on chrome startup.
        if (featureIsEnabled() && (isPhysicalWebPreferenceEnabled() || isOnboarding())) {
            boolean ignoreOtherClients =
                    ChromeFeatureList.isEnabled(IGNORE_OTHER_CLIENTS_FEATURE_NAME);
            ContextUtils.getAppSharedPreferences().edit()
                    .putBoolean(PREF_IGNORE_OTHER_CLIENTS, ignoreOtherClients)
                    .apply();
            startPhysicalWeb();
            PhysicalWebUma.uploadDeferredMetrics();
        } else {
            stopPhysicalWeb();
        }
    }
}
