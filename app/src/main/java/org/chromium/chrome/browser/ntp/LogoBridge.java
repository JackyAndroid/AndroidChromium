// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * Provides access to the search provider's logo via the C++ LogoService.
 */
class LogoBridge {

    /**
     * A logo for a search provider (e.g. the Yahoo! logo or Google doodle).
     */
    static class Logo {
        /**
         * The logo image. Non-null.
         */
        public final Bitmap image;

        /**
         * The URL to navigate to when the user clicks on the logo. May be null.
         */
        public final String onClickUrl;

        /**
         * The accessibility text describing the logo. May be null.
         */
        public final String altText;

        /**
         * The URL to download animated GIF logo. If null, there is no animated logo to download.
         */
        public final String animatedLogoUrl;

        Logo(Bitmap image, String onClickUrl, String altText, String animatedLogoUrl) {
            this.image = image;
            this.onClickUrl = onClickUrl;
            this.altText = altText;
            this.animatedLogoUrl = animatedLogoUrl;
        }
    }

    /**
     * Observer for receiving the logo when it's available.
     */
    interface LogoObserver {
        /**
         * Called when the cached or fresh logo is available. This may be called up to two times,
         * once with the cached logo and once with a freshly downloaded logo.
         *
         * @param logo The search provider's logo.
         * @param fromCache Whether the logo was loaded from the cache.
         */
        @CalledByNative("LogoObserver")
        void onLogoAvailable(Logo logo, boolean fromCache);
    }

    /**
     * A callback that is called when the animated logo is successfully downloaded.
     */
    interface AnimatedLogoCallback {

        /**
         * Called when the animated GIF logo is successfully downloaded.
         *
         * @param animatedLogoImage The {@link BaseGifImage} representing the animated logo.
         */
        @CalledByNative("AnimatedLogoCallback")
        void onAnimatedLogoAvailable(BaseGifImage animatedLogoImage);
    }

    private long mNativeLogoBridge;

    /**
     * Creates a LogoBridge for getting the logo of the default search provider.
     *
     * @param profile Profile of the tab that will show the logo.
     */
    LogoBridge(Profile profile) {
        mNativeLogoBridge = nativeInit(profile);
    }

    /**
     * Cleans up the C++ side of this class. After calling this, LogoObservers passed to
     * getCurrentLogo() will no longer receive updates.
     */
    void destroy() {
        assert mNativeLogoBridge != 0;
        nativeDestroy(mNativeLogoBridge);
        mNativeLogoBridge = 0;
    }

    /**
     * Gets the current logo for the default search provider.
     *
     * @param logoObserver The observer to receive the cached and/or fresh logos when they're
     *                     available. logoObserver.onLogoAvailable() may be called synchronously if
     *                     the cached logo is already available.
     */
    void getCurrentLogo(LogoObserver logoObserver) {
        nativeGetCurrentLogo(mNativeLogoBridge, logoObserver);
    }

    /**
     * Downloads an animated GIF logo. The given callback will not be called if the download failed
     * or there is already an ongoing url fetching for the same url.
     *
     * @param callback The callback to be called when the animated logo is successfully downloaded.
     * @param animatedLogoUrl The url from which to download the animated GIF logo.
     */
    void getAnimatedLogo(AnimatedLogoCallback callback, String animatedLogoUrl) {
        nativeGetAnimatedLogo(mNativeLogoBridge, callback, animatedLogoUrl);
    }

    @CalledByNative
    private static Logo createLogo(Bitmap image, String onClickUrl, String altText, String gifUrl) {
        return new Logo(image, onClickUrl, altText, gifUrl);
    }

    @CalledByNative
    private static BaseGifImage createGifImage(byte[] bytes) {
        return new BaseGifImage(bytes);
    }

    private native long nativeInit(Profile profile);
    private native void nativeGetCurrentLogo(long nativeLogoBridge, LogoObserver logoObserver);
    private native void nativeGetAnimatedLogo(long nativeLogoBridge, AnimatedLogoCallback callback,
            String gifUrl);
    private native void nativeDestroy(long nativeLogoBridge);
}
