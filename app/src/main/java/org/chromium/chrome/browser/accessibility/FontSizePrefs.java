// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.accessibility;

import android.content.Context;
import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.util.MathUtils;

/**
 * Singleton class for accessing these font size-related preferences:
 *  - User Font Scale Factor: the font scale value that the user sees and can set. This is a value
 *        between 50% and 200% (i.e. 0.5 and 2).
 *  - Font Scale Factor: the font scale factor applied to webpage text during font boosting. This
 *        equals the user font scale factor times the Android system font scale factor, which
 *        reflects the font size indicated in Android settings > Display > Font size.
 *  - Force Enable Zoom: whether force enable zoom is on or off
 *  - User Set Force Enable Zoom: whether the user has manually set the force enable zoom button
 */
public class FontSizePrefs {
    /**
     * The font scale threshold beyond which force enable zoom is automatically turned on. It
     * is chosen such that force enable zoom will be activated when the accessibility large text
     * setting is on (i.e. this value should be the same as or lesser than the font size scale used
     * by accessiblity large text).
     */
    public static final float FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER = 1.3f;

    private static final float EPSILON = 0.001f;

    static final String PREF_USER_SET_FORCE_ENABLE_ZOOM = "user_set_force_enable_zoom";
    static final String PREF_USER_FONT_SCALE_FACTOR = "user_font_scale_factor";

    private static FontSizePrefs sFontSizePrefs;

    private final long mFontSizePrefsAndroidPtr;
    private final Context mApplicationContext;
    private final SharedPreferences mSharedPreferences;
    private final ObserverList<FontSizePrefsObserver> mObserverList;

    private Float mSystemFontScaleForTests = null;

    /**
     * Interface for observing changes in font size-related preferences.
     */
    public interface FontSizePrefsObserver {
        void onFontScaleFactorChanged(float fontScaleFactor, float userFontScaleFactor);
        void onForceEnableZoomChanged(boolean enabled);
    }

    private FontSizePrefs(Context context) {
        mFontSizePrefsAndroidPtr = nativeInit();
        mApplicationContext = context.getApplicationContext();
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
        mObserverList = new ObserverList<FontSizePrefsObserver>();
    }

    /**
     * Returns the singleton FontSizePrefs, constructing it if it doesn't already exist.
     */
    public static FontSizePrefs getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sFontSizePrefs == null) {
            sFontSizePrefs = new FontSizePrefs(context);
        }
        return sFontSizePrefs;
    }

    /**
     * Adds an observer to listen for changes to font scale-related preferences.
     */
    public void addObserver(FontSizePrefsObserver observer) {
        mObserverList.addObserver(observer);
    }

    /**
     * Removes an observer so it will no longer receive updates for changes to font scale-related
     * preferences.
     */
    public void removeObserver(FontSizePrefsObserver observer) {
        mObserverList.removeObserver(observer);
    }

    /**
     * Updates the fontScaleFactor based on the userFontScaleFactor and the system-wide font scale.
     *
     * This should be called during application start-up and whenever the system font size changes.
     */
    public void onSystemFontScaleChanged() {
        float userFontScaleFactor = getUserFontScaleFactor();
        if (userFontScaleFactor != 0f) {
            setFontScaleFactor(userFontScaleFactor * getSystemFontScale());
        }
    }

    /**
     * Sets the userFontScaleFactor. This should be a value between .5 and 2.
     */
    public void setUserFontScaleFactor(float userFontScaleFactor) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putFloat(PREF_USER_FONT_SCALE_FACTOR, userFontScaleFactor);
        sharedPreferencesEditor.apply();
        setFontScaleFactor(userFontScaleFactor * getSystemFontScale());
    }

    /**
     * Returns the userFontScaleFactor. This is the value that should be displayed to the user.
     */
    public float getUserFontScaleFactor() {
        float userFontScaleFactor = mSharedPreferences.getFloat(PREF_USER_FONT_SCALE_FACTOR, 0f);
        if (userFontScaleFactor == 0f) {
            float fontScaleFactor = getFontScaleFactor();

            if (Math.abs(fontScaleFactor - 1f) <= EPSILON) {
                // If the font scale factor is 1, assume that the user hasn't customized their font
                // scale and/or wants the default value
                userFontScaleFactor = 1f;
            } else {
                // Initialize userFontScaleFactor based on fontScaleFactor, since
                // userFontScaleFactor was added long after fontScaleFactor.
                userFontScaleFactor =
                        MathUtils.clamp(fontScaleFactor / getSystemFontScale(), 0.5f, 2f);
            }
            SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            sharedPreferencesEditor.putFloat(PREF_USER_FONT_SCALE_FACTOR, userFontScaleFactor);
            sharedPreferencesEditor.apply();
        }
        return userFontScaleFactor;
    }

    /**
     * Returns the fontScaleFactor. This is the product of the userFontScaleFactor and the system
     * font scale, and is the amount by which webpage text will be scaled during font boosting.
     */
    public float getFontScaleFactor() {
        return nativeGetFontScaleFactor(mFontSizePrefsAndroidPtr);
    }

    /**
     * Sets forceEnableZoom due to a user request (e.g. checking a checkbox). This implicitly sets
     * userSetForceEnableZoom.
     */
    public void setForceEnableZoomFromUser(boolean enabled) {
        setForceEnableZoom(enabled, true);
    }

    /**
     * Returns whether forceEnableZoom is enabled.
     */
    public boolean getForceEnableZoom() {
        return nativeGetForceEnableZoom(mFontSizePrefsAndroidPtr);
    }

    /**
     * Sets a mock value for the system-wide font scale. Use only in tests.
     */
    @VisibleForTesting
    void setSystemFontScaleForTest(float fontScale) {
        mSystemFontScaleForTests = fontScale;
    }

    private float getSystemFontScale() {
        if (mSystemFontScaleForTests != null) return mSystemFontScaleForTests;
        return mApplicationContext.getResources().getConfiguration().fontScale;
    }

    private void setForceEnableZoom(boolean enabled, boolean fromUser) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_USER_SET_FORCE_ENABLE_ZOOM, fromUser);
        sharedPreferencesEditor.apply();
        nativeSetForceEnableZoom(mFontSizePrefsAndroidPtr, enabled);
    }

    private boolean getUserSetForceEnableZoom() {
        return mSharedPreferences.getBoolean(PREF_USER_SET_FORCE_ENABLE_ZOOM, false);
    }

    private void setFontScaleFactor(float fontScaleFactor) {
        float previousFontScaleFactor = getFontScaleFactor();
        nativeSetFontScaleFactor(mFontSizePrefsAndroidPtr, fontScaleFactor);

        if (previousFontScaleFactor < FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER
                && fontScaleFactor >= FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER
                && !getForceEnableZoom()) {
            // If the font scale factor just crossed above the threshold, set force enable zoom even
            // if the user has previously unset it.
            setForceEnableZoom(true, false);
        } else if (previousFontScaleFactor >= FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER
                && fontScaleFactor < FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER
                && !getUserSetForceEnableZoom()) {
            // If the font scale factor just crossed below the threshold and the user didn't set
            // force enable zoom manually, then unset force enable zoom.
            setForceEnableZoom(false, false);
        }
    }

    @CalledByNative
    private void onFontScaleFactorChanged(float fontScaleFactor) {
        float userFontScaleFactor = getUserFontScaleFactor();
        for (FontSizePrefsObserver observer : mObserverList) {
            observer.onFontScaleFactorChanged(fontScaleFactor, userFontScaleFactor);
        }
    }

    @CalledByNative
    private void onForceEnableZoomChanged(boolean enabled) {
        for (FontSizePrefsObserver observer : mObserverList) {
            observer.onForceEnableZoomChanged(enabled);
        }
    }

    private native long nativeInit();
    private native void nativeSetFontScaleFactor(long nativeFontSizePrefsAndroid,
            float fontScaleFactor);
    private native float nativeGetFontScaleFactor(long nativeFontSizePrefsAndroid);
    private native boolean nativeGetForceEnableZoom(long nativeFontSizePrefsAndroid);
    private native void nativeSetForceEnableZoom(long nativeFontSizePrefsAndroid, boolean enabled);
}
