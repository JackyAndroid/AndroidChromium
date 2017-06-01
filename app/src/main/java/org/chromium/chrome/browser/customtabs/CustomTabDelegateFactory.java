// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.chrome.browser.fullscreen.BrowserStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.fullscreen.ComposedBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContextMenuItemDelegate;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroid;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;

/**
 * A {@link TabDelegateFactory} class to be used in all {@link Tab} owned
 * by a {@link CustomTabActivity}.
 */
public class CustomTabDelegateFactory extends TabDelegateFactory {
    /**
     * A custom external navigation delegate that forbids the intent picker from showing up.
     */
    static class CustomTabNavigationDelegate extends ExternalNavigationDelegateImpl {
        private static final String TAG = "customtabs";
        private final String mClientPackageName;
        private boolean mHasActivityStarted;

        /**
         * Constructs a new instance of {@link CustomTabNavigationDelegate}.
         */
        public CustomTabNavigationDelegate(Tab tab, String clientPackageName) {
            super(tab);
            mClientPackageName = clientPackageName;
        }

        @Override
        public void startActivity(Intent intent, boolean proxy) {
            super.startActivity(intent, proxy);
            mHasActivityStarted = true;
        }

        @Override
        public boolean startActivityIfNeeded(Intent intent, boolean proxy) {
            boolean isExternalProtocol = !UrlUtilities.isAcceptedScheme(intent.toUri(0));
            boolean hasDefaultHandler = hasDefaultHandler(intent);
            try {
                // For a URL chrome can handle and there is no default set, handle it ourselves.
                if (!hasDefaultHandler) {
                    if (!TextUtils.isEmpty(mClientPackageName) && isPackageSpecializedHandler(
                            mApplicationContext, mClientPackageName, intent)) {
                        intent.setPackage(mClientPackageName);
                    } else if (!isExternalProtocol) {
                        return false;
                    }
                }

                if (proxy) {
                    dispatchAuthenticatedIntent(intent);
                    mHasActivityStarted = true;
                    return true;
                } else {
                    // If android fails to find a handler, handle it ourselves.
                    Context context = getAvailableContext();
                    if (context instanceof Activity
                            && ((Activity) context).startActivityIfNeeded(intent, -1)) {
                        mHasActivityStarted = true;
                        return true;
                    }
                }
                return false;
            } catch (RuntimeException e) {
                IntentUtils.logTransactionTooLargeOrRethrow(e, intent);
                return false;
            }
        }

        /**
         * Resolve the default external handler of an intent.
         * @return Whether the default external handler is found: if chrome turns out to be the
         *         default handler, this method will return false.
         */
        private boolean hasDefaultHandler(Intent intent) {
            try {
                ResolveInfo info =
                        mApplicationContext.getPackageManager().resolveActivity(intent, 0);
                if (info != null) {
                    final String chromePackage = mApplicationContext.getPackageName();
                    // If a default handler is found and it is not chrome itself, fire the intent.
                    if (info.match != 0 && !chromePackage.equals(info.activityInfo.packageName)) {
                        return true;
                    }
                }
            } catch (RuntimeException e) {
                IntentUtils.logTransactionTooLargeOrRethrow(e, intent);
            }
            return false;
        }

        /**
         * @return Whether an external activity has started to handle a url. For testing only.
         */
        @VisibleForTesting
        public boolean hasExternalActivityStarted() {
            return mHasActivityStarted;
        }
    }

    private static class CustomTabWebContentsDelegate extends TabWebContentsDelegateAndroid {
        /**
         * See {@link TabWebContentsDelegateAndroid}.
         */
        public CustomTabWebContentsDelegate(Tab tab) {
            super(tab);
        }

        @Override
        public boolean shouldResumeRequestsForCreatedWindow() {
            return true;
        }

        @Override
        protected void bringActivityToForeground() {
            // No-op here. If client's task is in background Chrome is unable to foreground it.
        }
    }

    private final boolean mShouldHideBrowserControls;
    private final boolean mIsOpenedByChrome;
    private final BrowserStateBrowserControlsVisibilityDelegate mBrowserStateVisibilityDelegate;

    private ExternalNavigationDelegateImpl mNavigationDelegate;
    private ExternalNavigationHandler mNavigationHandler;

    /**
     * @param shouldHideBrowserControls Whether or not the browser controls may auto-hide.
     * @param isOpenedByChrome Whether the CustomTab was originally opened by Chrome.
     * @param visibilityDelegate The delegate that handles browser control visibility associated
     *                           with browser actions (as opposed to tab state).
     */
    public CustomTabDelegateFactory(boolean shouldHideBrowserControls, boolean isOpenedByChrome,
            BrowserStateBrowserControlsVisibilityDelegate visibilityDelegate) {
        mShouldHideBrowserControls = shouldHideBrowserControls;
        mIsOpenedByChrome = isOpenedByChrome;
        mBrowserStateVisibilityDelegate = visibilityDelegate;
    }

    @Override
    public BrowserControlsVisibilityDelegate createBrowserControlsVisibilityDelegate(Tab tab) {
        return new ComposedBrowserControlsVisibilityDelegate(
                new TabStateBrowserControlsVisibilityDelegate(tab) {
                    @Override
                    public boolean isHidingBrowserControlsEnabled() {
                        return mShouldHideBrowserControls && super.isHidingBrowserControlsEnabled();
                    }
                },
                mBrowserStateVisibilityDelegate);
    }

    @Override
    public TabWebContentsDelegateAndroid createWebContentsDelegate(Tab tab) {
        return new CustomTabWebContentsDelegate(tab);
    }

    @Override
    public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(Tab tab) {
        if (mIsOpenedByChrome) {
            mNavigationDelegate = new ExternalNavigationDelegateImpl(tab);
        } else {
            mNavigationDelegate = new CustomTabNavigationDelegate(tab, tab.getAppAssociatedWith());
        }
        mNavigationHandler = new ExternalNavigationHandler(mNavigationDelegate);
        return new InterceptNavigationDelegateImpl(mNavigationHandler, tab);
    }

    @Override
    public ContextMenuPopulator createContextMenuPopulator(Tab tab) {
        return new ChromeContextMenuPopulator(new TabContextMenuItemDelegate(tab),
                ChromeContextMenuPopulator.CUSTOM_TAB_MODE);
    }

    /**
     * @return The {@link ExternalNavigationHandler} in this tab. For test purpose only.
     */
    @VisibleForTesting
    ExternalNavigationHandler getExternalNavigationHandler() {
        return mNavigationHandler;
    }

    /**
     * @return The {@link CustomTabNavigationDelegate} in this tab. For test purpose only.
     */
    @VisibleForTesting
    ExternalNavigationDelegateImpl getExternalNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public boolean canShowAppBanners(Tab tab) {
        return false;
    }
}
