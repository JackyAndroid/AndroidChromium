// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.TransactionTooLargeException;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContextMenuItemDelegate;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroid;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

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
        private boolean mHasActivityStarted;

        /**
         * Constructs a new instance of {@link CustomTabNavigationDelegate}.
         */
        public CustomTabNavigationDelegate(ChromeActivity activity) {
            super(activity);
        }

        @Override
        public void startActivity(Intent intent) {
            super.startActivity(intent);
            mHasActivityStarted = true;
        }

        @Override
        public boolean startActivityIfNeeded(Intent intent) {
            boolean isExternalProtocol = !UrlUtilities.isAcceptedScheme(intent.getDataString());
            boolean hasDefaultHandler = hasDefaultHandler(intent);
            try {
                // For a url chrome can handle and there is no default set, handle it ourselves.
                if (!hasDefaultHandler && !isExternalProtocol) return false;
                // If android fails to find a handler, handle it ourselves.
                if (!getActivity().startActivityIfNeeded(intent, -1)) return false;

                mHasActivityStarted = true;
                return true;
            } catch (RuntimeException e) {
                logTransactionTooLargeOrRethrow(e, intent);
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
                ResolveInfo info = getActivity().getPackageManager().resolveActivity(intent, 0);
                if (info != null) {
                    final String chromePackage = getActivity().getPackageName();
                    // If a default handler is found and it is not chrome itself, fire the intent.
                    if (info.match != 0 && !chromePackage.equals(info.activityInfo.packageName)) {
                        return true;
                    }
                }
            } catch (RuntimeException e) {
                logTransactionTooLargeOrRethrow(e, intent);
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

        private static void logTransactionTooLargeOrRethrow(RuntimeException e, Intent intent) {
            // See http://crbug.com/369574.
            if (e.getCause() instanceof TransactionTooLargeException) {
                Log.e(TAG, "Could not resolve Activity for intent " + intent.toString(), e);
            } else {
                throw e;
            }
        }
    }

    private static class CustomTabWebContentsDelegate extends TabWebContentsDelegateAndroid {
        private String mTargetUrl;

        /**
         * See {@link TabWebContentsDelegateAndroid}.
         */
        public CustomTabWebContentsDelegate(Tab tab, CustomTabActivity activity) {
            super(tab, activity);
        }

        @Override
        public boolean shouldResumeRequestsForCreatedWindow() {
            return true;
        }

        @Override
        public void webContentsCreated(WebContents sourceWebContents, long openerRenderFrameId,
                String frameName, String targetUrl, WebContents newWebContents) {
            super.webContentsCreated(
                    sourceWebContents, openerRenderFrameId, frameName, targetUrl,
                    newWebContents);
            mTargetUrl = targetUrl;
        }

        @Override
        public boolean addNewContents(WebContents sourceWebContents, WebContents webContents,
                int disposition, Rect initialPosition, boolean userGesture) {
            assert mTargetUrl != null;
            ((CustomTabActivity) mActivity).loadUrlInCurrentTab(
                    new LoadUrlParams(mTargetUrl), SystemClock.elapsedRealtime());
            mTargetUrl = null;
            return false;
        }

        @Override
        protected void bringActivityToForeground() {
            // No-op here. If client's task is in background Chrome is unable to foreground it.
        }
    }

    private final Application mApplication;
    private final IBinder mSession;
    private CustomTabNavigationDelegate mNavigationDelegate;
    private ExternalNavigationHandler mNavigationHandler;
    private CustomTabObserver mTabObserver;

    public CustomTabDelegateFactory(Application application, IBinder session) {
        super();
        mApplication = application;
        mSession = session;
    }

    @Override
    public TabWebContentsDelegateAndroid createWebContentsDelegate(Tab tab,
            ChromeActivity activity) {
        assert activity instanceof CustomTabActivity;
        return new CustomTabWebContentsDelegate(tab, (CustomTabActivity) activity);
    }

    @Override
    public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(Tab tab,
            ChromeActivity activity) {
        mNavigationDelegate = new CustomTabNavigationDelegate(activity);
        mNavigationHandler = new ExternalNavigationHandler(mNavigationDelegate);
        return new InterceptNavigationDelegateImpl(mNavigationHandler, activity, tab);
    }

    @Override
    public ContextMenuPopulator createContextMenuPopulator(Tab tab, ChromeActivity activity) {
        return new ChromeContextMenuPopulator(
                new TabContextMenuItemDelegate(tab, activity),
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
    CustomTabNavigationDelegate getExternalNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public AppBannerManager createAppBannerManager(Tab tab) {
        return null;
    }
}
