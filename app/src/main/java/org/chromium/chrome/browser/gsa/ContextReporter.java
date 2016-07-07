// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gsa;

import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.sync.ModelType;
import org.chromium.sync.PassphraseType;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Reports context to GSA for search quality.
 */
public class ContextReporter {
    private static final String TAG = "cr.GSA";

    // Values for UMA histogram.
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_GSA_NOT_AVAILABLE = 1;
    public static final int STATUS_SYNC_NOT_INITIALIZED = 2;
    public static final int STATUS_SYNC_NOT_SYNCING_URLS = 3;
    public static final int STATUS_SYNC_NOT_KEYSTORE_PASSPHRASE = 4;
    public static final int STATUS_SYNC_OTHER = 5;
    public static final int STATUS_SVELTE_DEVICE = 6;
    public static final int STATUS_NO_TAB = 7;
    public static final int STATUS_INCOGNITO = 8;
    public static final int STATUS_INVALID_SCHEME = 9;
    public static final int STATUS_TAB_ID_MISMATCH = 10;
    public static final int STATUS_DUP_TITLE_CHANGE = 11;
    public static final int STATUS_CONNECTION_FAILED = 12;
    public static final int STATUS_SYNC_NOT_READY_AT_REPORT_TIME = 13;
    public static final int STATUS_NOT_SIGNED_IN = 14;
    public static final int STATUS_GSA_ACCOUNT_MISSING = 15;
    public static final int STATUS_GSA_ACCOUNT_MISMATCH = 16;
    public static final int STATUS_RESULT_IS_NULL = 17;
    public static final int STATUS_RESULT_FAILED = 18;
    public static final int STATUS_SUCCESS_WITH_SELECTION = 19;
    // This should always stay last and have the highest number.
    private static final int STATUS_BOUNDARY = 20;

    private final ChromeActivity mActivity;
    private final GSAContextReportDelegate mDelegate;
    private TabModelSelectorTabObserver mSelectorTabObserver;
    private TabModelObserver mModelObserver;
    private ContextualSearchObserver mContextualSearchObserver;
    private boolean mLastContextWasTitleChange;
    private final AtomicBoolean mContextInUse;

    /**
     * Creates a ContextReporter for an Activity.
     * @param activity Chrome Activity which context will be reported.
     * @param controller used to communicate with GSA
     */
    public ContextReporter(ChromeActivity activity, GSAContextReportDelegate controller) {
        mActivity = activity;
        mDelegate = controller;
        mContextInUse = new AtomicBoolean(false);
        Log.d(TAG, "Created a new ContextReporter");
    }

    /**
     * Starts reporting context.
     */
    public void enable() {
        Tab currentTab = mActivity.getActivityTab();
        reportUsageOfCurrentContextIfPossible(currentTab, false, null);

        TabModelSelector selector = mActivity.getTabModelSelector();
        assert selector != null;

        if (mSelectorTabObserver == null) {
            mSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
                @Override
                public void onTitleUpdated(Tab tab) {
                    // Report usage declaring this as a title change.
                    reportUsageOfCurrentContextIfPossible(tab, true, null);
                }

                @Override
                public void onUrlUpdated(Tab tab) {
                    reportUsageOfCurrentContextIfPossible(tab, false, null);
                }
            };
        }
        if (mModelObserver == null) {
            assert !selector.getModels().isEmpty();
            mModelObserver = new EmptyTabModelObserver() {
                @Override
                public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                    reportUsageOfCurrentContextIfPossible(tab, false, null);
                }
            };
            for (TabModel model : selector.getModels()) {
                model.addObserver(mModelObserver);
            }
        }
        if (mContextualSearchObserver == null && mActivity.getContextualSearchManager() != null) {
            mContextualSearchObserver = new ContextualSearchObserver() {
                @Override
                public void onShowContextualSearch(GSAContextDisplaySelection contextSelection) {
                    if (contextSelection != null) reportDisplaySelection(contextSelection);
                }

                @Override
                public void onHideContextualSearch() {
                    reportDisplaySelection(null);
                }
            };
            mActivity.getContextualSearchManager().addObserver(mContextualSearchObserver);
        }
    }

    /**
     * Stops reporting context. Called when the app goes to the background.
     */
    public void disable() {
        reportUsageEndedIfNecessary();

        if (mSelectorTabObserver != null) {
            mSelectorTabObserver.destroy();
            mSelectorTabObserver = null;
        }
        if (mModelObserver != null) {
            for (TabModel model : mActivity.getTabModelSelector().getModels()) {
                model.removeObserver(mModelObserver);
            }
            mModelObserver = null;
        }
        if (mContextualSearchObserver != null && mActivity.getContextualSearchManager() != null) {
            mActivity.getContextualSearchManager().removeObserver(mContextualSearchObserver);
            mContextualSearchObserver = null;
        }
    }

    /**
     * Reports that the given display selection has been established for the current tab.
     * @param displaySelection The information about the selection being displayed.
     */
    private void reportDisplaySelection(@Nullable GSAContextDisplaySelection displaySelection) {
        Tab currentTab = mActivity.getActivityTab();
        reportUsageOfCurrentContextIfPossible(currentTab, false, displaySelection);
    }

    private void reportUsageEndedIfNecessary() {
        if (mContextInUse.compareAndSet(true, false)) mDelegate.reportContextUsageEnded();
    }

    private void reportUsageOfCurrentContextIfPossible(
            Tab tab, boolean isTitleChange, @Nullable GSAContextDisplaySelection displaySelection) {
        Tab currentTab = mActivity.getActivityTab();
        if (currentTab == null || currentTab.isIncognito()) {
            if (currentTab == null) {
                reportStatus(STATUS_NO_TAB);
                Log.d(TAG, "Not reporting, tab is null");
            } else {
                reportStatus(STATUS_INCOGNITO);
                Log.d(TAG, "Not reporting, tab is incognito");
            }
            reportUsageEndedIfNecessary();
            return;
        }

        String currentUrl = currentTab.getUrl();
        if (TextUtils.isEmpty(currentUrl) || !(currentUrl.startsWith(UrlConstants.HTTP_SCHEME)
                || currentUrl.startsWith(UrlConstants.HTTPS_SCHEME))) {
            reportStatus(STATUS_INVALID_SCHEME);
            Log.d(TAG, "Not reporting, URL scheme is invalid");
            reportUsageEndedIfNecessary();
            return;
        }

        // Check whether this is a context change we would like to report.
        if (currentTab.getId() != tab.getId()) {
            reportStatus(STATUS_TAB_ID_MISMATCH);
            Log.d(TAG, "Not reporting, tab ID doesn't match");
            return;
        }
        if (isTitleChange && mLastContextWasTitleChange) {
            reportStatus(STATUS_DUP_TITLE_CHANGE);
            Log.d(TAG, "Not reporting, repeated title update");
            return;
        }

        reportUsageEndedIfNecessary();

        mDelegate.reportContext(currentTab.getUrl(), currentTab.getTitle(), displaySelection);
        mLastContextWasTitleChange = isTitleChange;
        mContextInUse.set(true);
    }

    /**
     * Records the given status via UMA.
     * Use one of the STATUS_* constants above.
     */
    public static void reportStatus(int status) {
        RecordHistogram.recordEnumeratedHistogram(
                "Search.IcingContextReportingStatus", status, STATUS_BOUNDARY);
    }

    /**
     * Records an appropriate status via UMA given the current sync status.
     */
    public static void reportSyncStatus(ProfileSyncService syncService) {
        if (!syncService.isBackendInitialized()) {
            reportStatus(STATUS_SYNC_NOT_INITIALIZED);
        } else if (!syncService.getActiveDataTypes().contains(ModelType.TYPED_URLS)) {
            reportStatus(STATUS_SYNC_NOT_SYNCING_URLS);
        } else if (!syncService.getPassphraseType().equals(PassphraseType.KEYSTORE_PASSPHRASE)) {
            reportStatus(STATUS_SYNC_NOT_KEYSTORE_PASSPHRASE);
        } else {
            reportStatus(STATUS_SYNC_OTHER);
        }
    }
}
