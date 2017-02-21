// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnercustomizations;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;

/**
 * Provides information regarding homepage enabled states and URI.
 *
 * This class serves as a single homepage logic gateway.
 */
public class HomepageManager {

    /**
     * An interface to use for getting homepage related updates.
     */
    public interface HomepageStateListener {
        /**
         * Called when the homepage is enabled or disabled or the homepage URL changes.
         */
        void onHomepageStateUpdated();
    }

    private static final String PREF_HOMEPAGE_ENABLED = "homepage";
    private static final String PREF_HOMEPAGE_CUSTOM_URI = "homepage_custom_uri";
    private static final String PREF_HOMEPAGE_USE_DEFAULT_URI = "homepage_partner_enabled";

    private static HomepageManager sInstance;

    private final SharedPreferences mSharedPreferences;
    private final ObserverList<HomepageStateListener> mHomepageStateListeners;

    private HomepageManager(Context context) {
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
        mHomepageStateListeners = new ObserverList<HomepageManager.HomepageStateListener>();
    }

    /**
     * Returns the singleton instance of HomepageManager, creating it if needed.
     * @param context Any old Context.
     */
    public static HomepageManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HomepageManager(context);
        }
        return sInstance;
    }

    /**
     * Adds a HomepageStateListener to receive updates when the homepage state changes.
     */
    public void addListener(HomepageStateListener listener) {
        mHomepageStateListeners.addObserver(listener);
    }

    /**
     * Removes the given listener from the state listener list.
     * @param listener The listener to remove.
     */
    public void removeListener(HomepageStateListener listener) {
        mHomepageStateListeners.removeObserver(listener);
    }

    /**
     * Notify any listeners about a homepage state change.
     */
    public void notifyHomepageUpdated() {
        for (HomepageStateListener listener : mHomepageStateListeners) {
            listener.onHomepageStateUpdated();
        }
    }

    /**
     * @return Whether or not homepage is enabled.
     */
    public static boolean isHomepageEnabled(Context context) {
        return PartnerBrowserCustomizations.isHomepageProviderAvailableAndEnabled()
                && getInstance(context).getPrefHomepageEnabled();
    }

    /**
     * @return Whether or not homepage setting should be shown.
     */
    public static boolean shouldShowHomepageSetting() {
        return PartnerBrowserCustomizations.isHomepageProviderAvailableAndEnabled();
    }

    /**
     * @return Homepage URI string, if it's enabled. Null otherwise or uninitialized.
     */
    public static String getHomepageUri(Context context) {
        if (!isHomepageEnabled(context)) return null;

        HomepageManager manager = getInstance(context);
        String homepageUri = manager.getPrefHomepageUseDefaultUri()
                ? PartnerBrowserCustomizations.getHomePageUrl()
                : manager.getPrefHomepageCustomUri();
        return TextUtils.isEmpty(homepageUri) ? null : homepageUri;
    }

    /**
     * Returns the user preference for whether the homepage is enabled. This doesn't take into
     * account whether the device supports having a homepage.
     *
     * @see #isHomepageEnabled
     */
    public boolean getPrefHomepageEnabled() {
        return mSharedPreferences.getBoolean(PREF_HOMEPAGE_ENABLED, true);
    }

    /**
     * Sets the user preference for whether the homepage is enabled.
     */
    public void setPrefHomepageEnabled(boolean enabled) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_HOMEPAGE_ENABLED, enabled);
        sharedPreferencesEditor.apply();
        notifyHomepageUpdated();
    }

    /**
     * @return User specified homepage custom URI string.
     */
    public String getPrefHomepageCustomUri() {
        return mSharedPreferences.getString(PREF_HOMEPAGE_CUSTOM_URI, "");
    }

    /**
     * Sets custom homepage URI
     */
    public void setPrefHomepageCustomUri(String customUri) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putString(PREF_HOMEPAGE_CUSTOM_URI, customUri);
        sharedPreferencesEditor.apply();
    }

    /**
     * @return Whether the homepage URL is the default value.
     */
    public boolean getPrefHomepageUseDefaultUri() {
        return mSharedPreferences.getBoolean(PREF_HOMEPAGE_USE_DEFAULT_URI, true);
    }

    /**
     * Sets whether the homepage URL is the default value.
     */
    public void setPrefHomepageUseDefaultUri(boolean useDefaultUri) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_HOMEPAGE_USE_DEFAULT_URI, useDefaultUri);
        sharedPreferencesEditor.apply();
    }
}
