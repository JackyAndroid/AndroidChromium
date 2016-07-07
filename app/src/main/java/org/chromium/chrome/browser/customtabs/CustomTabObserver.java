// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Application;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.customtabs.CustomTabsCallback;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.concurrent.TimeUnit;

/**
 * A {@link TabObserver} that also handles custom tabs specific logging and messaging.
 */
class CustomTabObserver extends EmptyTabObserver {
    private CustomTabsConnection mCustomTabsConnection;
    private IBinder mSession;
    private long mIntentReceivedTimestamp;
    private long mPageLoadStartedTimestamp;

    private static final int STATE_RESET = 0;
    private static final int STATE_WAITING_LOAD_START = 1;
    private static final int STATE_WAITING_LOAD_FINISH = 2;
    private int mCurrentState;

    public CustomTabObserver(Application application, IBinder session) {
        mCustomTabsConnection = CustomTabsConnection.getInstance(application);
        mSession = session;
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
        mCustomTabsConnection.registerLaunch(mSession, params.getUrl());
    }

    @Override
    public void onPageLoadStarted(Tab tab, String url) {
        if (mCurrentState == STATE_WAITING_LOAD_START) {
            mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
            mCurrentState = STATE_WAITING_LOAD_FINISH;
        } else if (mCurrentState == STATE_WAITING_LOAD_FINISH) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_ABORTED);
            mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
        }
        mCustomTabsConnection.notifyNavigationEvent(
                mSession, CustomTabsCallback.NAVIGATION_STARTED);
    }

    @Override
    public void onShown(Tab tab) {
        mCustomTabsConnection.notifyNavigationEvent(
                mSession, CustomTabsCallback.TAB_SHOWN);
    }

    @Override
    public void onPageLoadFinished(Tab tab) {
        long pageLoadFinishedTimestamp = SystemClock.elapsedRealtime();
        mCustomTabsConnection.notifyNavigationEvent(
                mSession, CustomTabsCallback.NAVIGATION_FINISHED);
        // Both histograms (commit and PLT) are reported here, to make sure
        // that they are always recorded together, and that we only record
        // commits for successful navigations.
        if (mCurrentState == STATE_WAITING_LOAD_FINISH && mIntentReceivedTimestamp > 0) {
            long timeToPageLoadStartedMs = mPageLoadStartedTimestamp - mIntentReceivedTimestamp;
            long timeToPageLoadFinishedMs =
                    pageLoadFinishedTimestamp - mIntentReceivedTimestamp;
            // Same bounds and bucket count as "Startup.FirstCommitNavigationTime"
            RecordHistogram.recordCustomTimesHistogram(
                    "CustomTabs.IntentToFirstCommitNavigationTime", timeToPageLoadStartedMs,
                    1, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS, 225);
            // Same bounds and bucket count as PLT histograms.
            RecordHistogram.recordCustomTimesHistogram("CustomTabs.IntentToPageLoadedTime",
                    timeToPageLoadFinishedMs, 10, TimeUnit.MINUTES.toMillis(10),
                    TimeUnit.MILLISECONDS, 100);
        }
        resetPageLoadTracking();
    }

    @Override
    public void onDidAttachInterstitialPage(Tab tab) {
        if (tab.getSecurityLevel() != ConnectionSecurityLevel.SECURITY_ERROR) return;
        resetPageLoadTracking();
        mCustomTabsConnection.notifyNavigationEvent(
                mSession, CustomTabsCallback.NAVIGATION_FAILED);
    }

    @Override
    public void onPageLoadFailed(Tab tab, int errorCode) {
        resetPageLoadTracking();
        mCustomTabsConnection.notifyNavigationEvent(
                mSession, CustomTabsCallback.NAVIGATION_FAILED);
    }

    private void resetPageLoadTracking() {
        mCurrentState = STATE_RESET;
        mIntentReceivedTimestamp = -1;
    }
}