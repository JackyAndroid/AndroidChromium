// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.locale;

/**
 * A Handler for changes in a given special locale. This is a JNI bridge and it owns the native
 * object. Make sure to call Destroy() after this object is not used anymore.
 */
public class SpecialLocaleHandler {
    private final String mLocaleId;
    private long mNativeSpecialLocaleHandler;
    private boolean mAddedToService;

    /**
     * Creates a {@link SpecialLocaleHandler} that handles changes for the given locale.
     * @param localeId Country id of the locale. Should be 2 characters long.
     */
    public SpecialLocaleHandler(String localeId) {
        assert localeId.length() == 2;
        mLocaleId = localeId;
        mNativeSpecialLocaleHandler = nativeInit(localeId);
    }

    /**
     * This *must* be called after the {@link SpecialLocaleHandler} is not used anymore.
     */
    public void destroy() {
        assert mNativeSpecialLocaleHandler != 0;
        nativeDestroy(mNativeSpecialLocaleHandler);
        mNativeSpecialLocaleHandler = 0;
    }

    /**
     * Loads the template urls for this locale, and adds it to template url service. If the device
     * was initialized in the given special locale, no-op here.
     * @return Whether loading is needed.
     */
    public boolean loadTemplateUrls() {
        assert mNativeSpecialLocaleHandler != 0;
        // If the locale is the same as the one set at install time, there is no need to load the
        // search engines, as they are already cached in the template url service.
        mAddedToService = nativeLoadTemplateUrls(mNativeSpecialLocaleHandler);
        return mAddedToService;
    }

    /**
     * Removes the template urls that was added by {@link #loadTemplateUrls()}. No-op if
     * {@link #loadTemplateUrls()} returned false.
     */
    public void removeTemplateUrls() {
        assert mNativeSpecialLocaleHandler != 0;
        if (mAddedToService) nativeRemoveTemplateUrls(mNativeSpecialLocaleHandler);
    }

    /**
     * Overrides the default search provider in special locale.
     */
    public void overrideDefaultSearchProvider() {
        assert mNativeSpecialLocaleHandler != 0;
        nativeOverrideDefaultSearchProvider(mNativeSpecialLocaleHandler);
    }

    /**
     * Sets the default search provider back to Google.
     */
    public void setGoogleAsDefaultSearch() {
        assert mNativeSpecialLocaleHandler != 0;
        nativeSetGoogleAsDefaultSearch(mNativeSpecialLocaleHandler);
    }

    private static native long nativeInit(String localeId);
    private static native void nativeDestroy(long nativeSpecialLocaleHandler);
    private static native boolean nativeLoadTemplateUrls(long nativeSpecialLocaleHandler);
    private static native void nativeRemoveTemplateUrls(long nativeSpecialLocaleHandler);
    private static native void nativeOverrideDefaultSearchProvider(long nativeSpecialLocaleHandler);
    private static native void nativeSetGoogleAsDefaultSearch(long nativeSpecialLocaleHandler);
}
