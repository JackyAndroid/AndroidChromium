// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalnav;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;

/**
 * A container object for passing navigation parameters to {@link ExternalNavigationHandler}.
 */
public class ExternalNavigationParams {
    /** The URL which we are navigating to. */
    private final String mUrl;

    /** Whether we are currently in an incognito context. */
    private final boolean mIsIncognito;

    /** The referrer URL for the current navigation. */
    private final String mReferrerUrl;

    /** The page transition type for the current navigation. */
    private final int mPageTransition;

    /** Whether the current navigation is a redirect. */
    private final boolean mIsRedirect;

    /** Whether Chrome has to be in foreground for external navigation to occur. */
    private final boolean mApplicationMustBeInForeground;

    /** A redirect handler. */
    private final TabRedirectHandler mRedirectHandler;

    private final Tab mTab;

    /** Whether the intent should force a new tab to open. */
    private final boolean mOpenInNewTab;

    /** Whether this navigation happens in background tab. */
    private final boolean mIsBackgroundTabNavigation;

    /** Whether this navigation happens in main frame. */
    private final boolean mIsMainFrame;

    /** Whether this navigation is launched by user gesture. */
    private final boolean mHasUserGesture;

    /**
     * Whether the current tab should be closed when an URL load was overridden and an
     * intent launched.
     */
    private final boolean mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent;

    private ExternalNavigationParams(String url, boolean isIncognito, String referrerUrl,
            int pageTransition, boolean isRedirect, boolean appMustBeInForeground,
            TabRedirectHandler redirectHandler, Tab tab, boolean openInNewTab,
            boolean isBackgroundTabNavigation, boolean isMainFrame, boolean hasUserGesture,
            boolean shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent) {
        mUrl = url;
        mIsIncognito = isIncognito;
        mPageTransition = pageTransition;
        mReferrerUrl = referrerUrl;
        mIsRedirect = isRedirect;
        mApplicationMustBeInForeground = appMustBeInForeground;
        mRedirectHandler = redirectHandler;
        mTab = tab;
        mOpenInNewTab = openInNewTab;
        mIsBackgroundTabNavigation = isBackgroundTabNavigation;
        mIsMainFrame = isMainFrame;
        mHasUserGesture = hasUserGesture;
        mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent =
                shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent;
    }

    /** @return The URL to potentially open externally. */
    public String getUrl() {
        return mUrl;
    }

    /** @return Whether we are currently in incognito mode. */
    public boolean isIncognito() {
        return mIsIncognito;
    }

    /** @return The referrer URL. */
    public String getReferrerUrl() {
        return mReferrerUrl;
    }

    /** @return The page transition for the current navigation. */
    public int getPageTransition() {
        return mPageTransition;
    }

    /** @return Whether the navigation is part of a redirect. */
    public boolean isRedirect() {
        return mIsRedirect;
    }

    /** @return Whether the application has to be in foreground to open the URL. */
    public boolean isApplicationMustBeInForeground() {
        return mApplicationMustBeInForeground;
    }

    /** @return The redirect handler. */
    public TabRedirectHandler getRedirectHandler() {
        return mRedirectHandler;
    }

    /** @return The current tab. */
    public Tab getTab() {
        return mTab;
    }

    /**
     * @return Whether the external navigation should be opened in a new tab if handled by Chrome
     *         through the intent picker.
     */
    public boolean isOpenInNewTab() {
        return mOpenInNewTab;
    }

    /** @return Whether this navigation happens in background tab. */
    public boolean isBackgroundTabNavigation() {
        return mIsBackgroundTabNavigation;
    }

    /** @return Whether this navigation happens in main frame. */
    public boolean isMainFrame() {
        return mIsMainFrame;
    }

    /** @return Whether this navigation is launched by user gesture. */
    public boolean hasUserGesture() {
        return mHasUserGesture;
    }

    /**
     * @return Whether the current tab should be closed when an URL load was overridden and an
     *         intent launched.
     */
    public boolean shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent() {
        return mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent;
    }

    /** The builder for {@link ExternalNavigationParams} objects. */
    public static class Builder {
        /** The URL which we are navigating to. */
        private String mUrl;

        /** Whether we are currently in an incognito context. */
        private boolean mIsIncognito;

        /** The referrer URL for the current navigation. */
        private String mReferrerUrl;

        /** The page transition type for the current navigation. */
        private int mPageTransition;

        /** Whether the current navigation is a redirect. */
        private boolean mIsRedirect;

        /** Whether Chrome has to be in foreground for external navigation to occur. */
        private boolean mApplicationMustBeInForeground;

        /** A redirect handler. */
        private TabRedirectHandler mRedirectHandler;

        private Tab mTab;

        /** Whether the intent should force a new tab to open. */
        private boolean mOpenInNewTab;

        /** Whether this navigation happens in background tab. */
        private boolean mIsBackgroundTabNavigation;

        /** Whether this navigation happens in main frame. */
        private boolean mIsMainFrame;

        /** Whether this navigation is launched by user gesture. */
        private boolean mHasUserGesture;

        /**
         * Whether the current tab should be closed when an URL load was overridden and an
         * intent launched.
         */
        private boolean mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent;

        public Builder(String url, boolean isIncognito) {
            mUrl = url;
            mIsIncognito = isIncognito;
        }

        public Builder(String url, boolean isIncognito, String referrer, int pageTransition,
                boolean isRedirect) {
            mUrl = url;
            mIsIncognito = isIncognito;
            mReferrerUrl = referrer;
            mPageTransition = pageTransition;
            mIsRedirect = isRedirect;
        }

        /** Specify whether the application must be in foreground to launch an external intent. */
        public Builder setApplicationMustBeInForeground(boolean v) {
            mApplicationMustBeInForeground = v;
            return this;
        }

        /** Sets a tab redirect handler. */
        public Builder setRedirectHandler(TabRedirectHandler handler) {
            mRedirectHandler = handler;
            return this;
        }

        /** Sets the current tab. */
        public Builder setTab(Tab tab) {
            mTab = tab;
            return this;
        }

        /** Sets whether we want to open the intent URL in new tab, if handled by Chrome. */
        public Builder setOpenInNewTab(boolean v) {
            mOpenInNewTab = v;
            return this;
        }

        /** Sets whether this navigation happens in background tab. */
        public Builder setIsBackgroundTabNavigation(boolean v) {
            mIsBackgroundTabNavigation = v;
            return this;
        }

        /** Sets whether this navigation happens in main frame. */
        public Builder setIsMainFrame(boolean v) {
            mIsMainFrame = v;
            return this;
        }

        /** Sets whether this navigation happens in main frame. */
        public Builder setHasUserGesture(boolean v) {
            mHasUserGesture = v;
            return this;
        }

        /** Sets whether the current tab should be closed when an URL load was overridden and an
         * intent launched.
         */
        public Builder setShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent(boolean v) {
            mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent = v;
            return this;
        }

        /** @return A fully constructed {@link ExternalNavigationParams} object. */
        public ExternalNavigationParams build() {
            return new ExternalNavigationParams(mUrl, mIsIncognito, mReferrerUrl, mPageTransition,
                    mIsRedirect, mApplicationMustBeInForeground, mRedirectHandler,
                    mTab, mOpenInNewTab, mIsBackgroundTabNavigation, mIsMainFrame,
                    mHasUserGesture, mShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent);
        }
    }
}
