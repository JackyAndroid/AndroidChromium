// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;

import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.prerender.ExternalPrerenderHandler;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.content_public.browser.ContentBitmapCallback;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.concurrent.TimeUnit;

/**
 * A {@link TabObserver} that also handles custom tabs specific logging and messaging.
 */
class CustomTabObserver extends EmptyTabObserver {
    private final CustomTabsConnection mCustomTabsConnection;
    private final CustomTabsSessionToken mSession;
    private final boolean mOpenedByChrome;
    private float mScaleForNavigationInfo = 1f;

    private long mIntentReceivedTimestamp;
    private long mPageLoadStartedTimestamp;

    private boolean mScreenshotTakenForCurrentNavigation;

    private static final int STATE_RESET = 0;
    private static final int STATE_WAITING_LOAD_START = 1;
    private static final int STATE_WAITING_LOAD_FINISH = 2;
    private int mCurrentState;

    public CustomTabObserver(
            Application application, CustomTabsSessionToken session, boolean openedByChrome) {
        if (openedByChrome) {
            mCustomTabsConnection = null;
        } else {
            mCustomTabsConnection = CustomTabsConnection.getInstance(application);
        }
        mSession = session;
        if (!openedByChrome && mCustomTabsConnection.shouldSendNavigationInfoForSession(mSession)) {
            float desiredWidth = application.getResources().getDimensionPixelSize(
                    R.dimen.custom_tabs_screenshot_width);
            float desiredHeight = application.getResources().getDimensionPixelSize(
                    R.dimen.custom_tabs_screenshot_height);
            Rect bounds = ExternalPrerenderHandler.estimateContentSize(application, false);
            mScaleForNavigationInfo = (bounds.width() == 0 || bounds.height() == 0) ? 1f :
                    Math.min(desiredWidth / bounds.width(), desiredHeight / bounds.height());
        }
        mOpenedByChrome = openedByChrome;
        resetPageLoadTracking();
    }

    /**
     * Tracks the next page load, with timestamp as the origin of time.
     */
    public void trackNextPageLoadFromTimestamp(long timestamp) {
        mIntentReceivedTimestamp = timestamp;
        mCurrentState = STATE_WAITING_LOAD_START;
    }

    @Override
    public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.registerLaunch(mSession, params.getUrl());
        }
    }

    @Override
    public void onPageLoadStarted(Tab tab, String url) {
        if (mCurrentState == STATE_WAITING_LOAD_START) {
            mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
            mCurrentState = STATE_WAITING_LOAD_FINISH;
        } else if (mCurrentState == STATE_WAITING_LOAD_FINISH) {
            if (mCustomTabsConnection != null) {
                mCustomTabsConnection.notifyNavigationEvent(
                        mSession, CustomTabsCallback.NAVIGATION_ABORTED);
                mCustomTabsConnection.sendNavigationInfo(
                        mSession, tab.getUrl(), tab.getTitle(), null);
            }
            mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
        }
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.setSendNavigationInfoForSession(mSession, false);
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_STARTED);
            mScreenshotTakenForCurrentNavigation = false;
        }
    }

    @Override
    public void onShown(Tab tab) {
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.TAB_SHOWN);
        }
    }

    @Override
    public void onHidden(Tab tab) {
        if (!mScreenshotTakenForCurrentNavigation) captureNavigationInfo(tab);
    }

    @Override
    public void onPageLoadFinished(Tab tab) {
        long pageLoadFinishedTimestamp = SystemClock.elapsedRealtime();
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FINISHED);
        }
        // Both histograms (commit and PLT) are reported here, to make sure
        // that they are always recorded together, and that we only record
        // commits for successful navigations.
        if (mCurrentState == STATE_WAITING_LOAD_FINISH && mIntentReceivedTimestamp > 0) {
            long timeToPageLoadStartedMs = mPageLoadStartedTimestamp - mIntentReceivedTimestamp;
            long timeToPageLoadFinishedMs =
                    pageLoadFinishedTimestamp - mIntentReceivedTimestamp;

            String histogramPrefix = mOpenedByChrome ? "ChromeGeneratedCustomTab" : "CustomTabs";
            RecordHistogram.recordCustomTimesHistogram(
                    histogramPrefix + ".IntentToFirstCommitNavigationTime2.ZoomedOut",
                    timeToPageLoadStartedMs,
                    50, TimeUnit.MINUTES.toMillis(10), TimeUnit.MILLISECONDS, 50);
            RecordHistogram.recordCustomTimesHistogram(
                    histogramPrefix + ".IntentToFirstCommitNavigationTime2.ZoomedIn",
                    timeToPageLoadStartedMs, 200, 1000, TimeUnit.MILLISECONDS, 100);
            // Same bounds and bucket count as PLT histograms.
            RecordHistogram.recordCustomTimesHistogram(histogramPrefix + ".IntentToPageLoadedTime",
                    timeToPageLoadFinishedMs, 10, TimeUnit.MINUTES.toMillis(10),
                    TimeUnit.MILLISECONDS, 100);
        }
        resetPageLoadTracking();
        captureNavigationInfo(tab);
    }

    @Override
    public void onDidAttachInterstitialPage(Tab tab) {
        if (tab.getSecurityLevel() != ConnectionSecurityLevel.DANGEROUS) return;
        resetPageLoadTracking();
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FAILED);
        }
    }

    @Override
    public void onPageLoadFailed(Tab tab, int errorCode) {
        resetPageLoadTracking();
        if (mCustomTabsConnection != null) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FAILED);
        }
    }

    private void resetPageLoadTracking() {
        mCurrentState = STATE_RESET;
        mIntentReceivedTimestamp = -1;
    }

    private void captureNavigationInfo(final Tab tab) {
        if (mCustomTabsConnection == null) return;
        if (!mCustomTabsConnection.shouldSendNavigationInfoForSession(mSession)) return;

        final ContentBitmapCallback callback = new ContentBitmapCallback() {
            @Override
            public void onFinishGetBitmap(Bitmap bitmap, int response) {
                if (TextUtils.isEmpty(tab.getTitle()) && bitmap == null) return;
                mCustomTabsConnection.sendNavigationInfo(
                        mSession, tab.getUrl(), tab.getTitle(), bitmap);
            }
        };
        // Delay screenshot capture since the page might be doing post load tasks. And this also
        // gives time to get rid of any redirects and avoid capturing screenshots for those.
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                if (!tab.isHidden() && mCurrentState != STATE_RESET) return;
                if (tab.getWebContents() == null) return;
                tab.getWebContents().getContentBitmapAsync(
                        Bitmap.Config.ARGB_8888, mScaleForNavigationInfo, new Rect(), callback);
                mScreenshotTakenForCurrentNavigation = true;
            }
        }, 1000);
    }
}
