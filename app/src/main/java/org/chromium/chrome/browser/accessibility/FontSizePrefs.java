// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.accessibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.NativeCall;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton wrapper class for native FontSizePrefs. Provides support for preferences for Font
 * Scale Factor, Force Enable Zoom, and User Set Force Enable Zoom. User Set Force Enable Zoom
 * tracks whether the user  has manually set the force enable zoom button, while Force Enable Zoom
 * tracks whether force enable zoom is on or off. Font Scale Factor reflects the global font scale.
 */
public class FontSizePrefs implements OnSharedPreferenceChangeListener {
    public static final String PREF_FORCE_ENABLE_ZOOM = "force_enable_zoom";
    public static final String PREF_TEXT_SCALE = "text_scale";
    public static final String PREF_USER_SET_FORCE_ENABLE_ZOOM = "user_set_force_enable_zoom";

    private static FontSizePrefs sFontSizePrefs;
    private final long mFontSizePrefsAndroidPtr;
    private final SharedPreferences mSharedPreferences;
    private final Map<Observer, FontSizePrefsObserverWrapper> mObserverMap;

    /**
     * Observer interface for observing changes in FontScaleFactor, ForceEnableZoom and
     * UserSetForceEnableZoom.
     */
    public interface Observer {
        void onChangeFontSize(float newFontSize);
        void onChangeForceEnableZoom(boolean enabled);
        void onChangeUserSetForceEnableZoom(boolean enabled);
    }

    /**
     * Wrapper for FontSizePrefsObserverAndroid.
     */
    private static class FontSizePrefsObserverWrapper {
        private final Observer mFontSizePrefsObserver;
        private final long mNativeFontSizePrefsObserverWrapperPtr;

        public FontSizePrefsObserverWrapper(Observer observer) {
            mNativeFontSizePrefsObserverWrapperPtr = nativeInitObserverAndroid();
            mFontSizePrefsObserver = observer;
        }

        public long getNativePtr() {
            return mNativeFontSizePrefsObserverWrapperPtr;
        }

        public void destroy() {
            nativeDestroyObserverAndroid(mNativeFontSizePrefsObserverWrapperPtr);
        }

        @CalledByNative("FontSizePrefsObserverWrapper")
        public void onChangeFontSize(float newFontSize) {
            mFontSizePrefsObserver.onChangeFontSize(newFontSize);
        }

        @CalledByNative("FontSizePrefsObserverWrapper")
        public void onChangeForceEnableZoom(boolean enabled) {
            mFontSizePrefsObserver.onChangeForceEnableZoom(enabled);
        }

        public void onChangeUserSetForceEnableZoom(boolean enabled) {
            mFontSizePrefsObserver.onChangeUserSetForceEnableZoom(enabled);
        }

        @NativeCall("FontSizePrefsObserverWrapper")
        private native long nativeInitObserverAndroid();

        @NativeCall("FontSizePrefsObserverWrapper")
        private native void nativeDestroyObserverAndroid(long nativeFontSizePrefsObserverAndroid);
    }

    private FontSizePrefs(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mFontSizePrefsAndroidPtr = nativeInit();
        mObserverMap = new HashMap<Observer, FontSizePrefsObserverWrapper>();
    }

    /**
     * Returns the FontSizePrefs corresponding to the inputted Profile. If no FontSizePrefs existed,
     * this method will create one.
     */
    public static FontSizePrefs getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sFontSizePrefs == null) {
            sFontSizePrefs = new FontSizePrefs(context);
        }
        return sFontSizePrefs;
    }

    /**
     * Adds the observer to listen for Font Scale and Force Enable Zoom preferences.
     * @return true if the observerMap was changed as a result of the call.
     */
    public boolean addObserver(Observer obs) {
        if (mObserverMap.containsKey(obs)) return false;
        FontSizePrefsObserverWrapper wrappedObserver =
                new FontSizePrefsObserverWrapper(obs);
        nativeAddObserver(mFontSizePrefsAndroidPtr, wrappedObserver.getNativePtr());
        mObserverMap.put(obs, wrappedObserver);
        return true;
    }

    /**
     * Removes the observer and unregisters it from Font Scale and Force Enable Zoom changes.
     * @return true if an observer was removed as a result of the call.
     */
    public boolean removeObserver(Observer obs) {
        FontSizePrefsObserverWrapper wrappedObserver = mObserverMap.remove(obs);
        if (wrappedObserver == null) return false;
        nativeRemoveObserver(mFontSizePrefsAndroidPtr, wrappedObserver.getNativePtr());
        wrappedObserver.destroy();
        return true;
    }

    /**
     * Sets UserSetForceEnableZoom. This is the only one of three preferences stored through
     * SharedPreferences.
     */
    public void setUserSetForceEnableZoom(boolean enabled) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_USER_SET_FORCE_ENABLE_ZOOM, enabled);
        sharedPreferencesEditor.apply();
    }

    /**
     * Returns true if user has manually set ForceEnableZoom and false otherwise.
     */
    public boolean getUserSetForceEnableZoom() {
        return mSharedPreferences.getBoolean(PREF_USER_SET_FORCE_ENABLE_ZOOM,
                false);
    }

    public void setFontScaleFactor(float fontScaleFactor) {
        nativeSetFontScaleFactor(mFontSizePrefsAndroidPtr, fontScaleFactor);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_USER_SET_FORCE_ENABLE_ZOOM.equals(key)) {
            for (FontSizePrefsObserverWrapper obsWrapper : mObserverMap.values()) {
                obsWrapper.onChangeUserSetForceEnableZoom(getUserSetForceEnableZoom());
            }
        }
    }

    public float getFontScaleFactor() {
        return nativeGetFontScaleFactor(mFontSizePrefsAndroidPtr);
    }

    public void setForceEnableZoom(boolean enabled) {
        nativeSetForceEnableZoom(mFontSizePrefsAndroidPtr, enabled);
    }

    public boolean getForceEnableZoom() {
        return nativeGetForceEnableZoom(mFontSizePrefsAndroidPtr);
    }

    private native void nativeAddObserver(long nativeFontSizePrefsAndroid,
            long nativeObserverPtr);

    private native void nativeRemoveObserver(long nativeFontSizePrefsAndroid,
            long nativeObserverPtr);

    private native long nativeInit();

    private native void nativeSetFontScaleFactor(long nativeFontSizePrefsAndroid, float font);

    private native float nativeGetFontScaleFactor(long nativeFontSizePrefsAndroid);

    private native boolean nativeGetForceEnableZoom(long nativeFontSizePrefsAndroid);

    private native void nativeSetForceEnableZoom(long nativeFontSizePrefsAndroid, boolean enabled);
}
