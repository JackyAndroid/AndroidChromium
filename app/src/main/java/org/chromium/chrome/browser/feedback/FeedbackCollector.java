// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A class which collects generic information about Chrome which is useful for all types of
 * feedback, and provides it as a {@link Bundle}.
 *
 * Creating a {@link FeedbackCollector} initiates asynchronous operations for gathering feedback
 * data, which may or not finish before the bundle is requested by calling {@link #getBundle()}.
 *
 * Interacting with the {@link FeedbackCollector} is only allowed on the main thread.
 */
public class FeedbackCollector
        implements ConnectivityTask.ConnectivityResult, ScreenshotTask.ScreenshotTaskCallback {
    /**
     * A user visible string describing the current URL.
     */
    @VisibleForTesting
    static final String URL_KEY = "URL";

    /**
     * The timeout (ms) for gathering data asynchronously.
     * This timeout is ignored for taking screenshots.
     */
    private static final int TIMEOUT_MS = 500;

    /**
     * The timeout (ms) for gathering connection data.
     * This may be more than the main timeout as taking the screenshot can take more time than
     * {@link #TIMEOUT_MS}.
     */
    private static final int CONNECTIVITY_CHECK_TIMEOUT_MS = 5000;

    private final Map<String, String> mData;
    private final Profile mProfile;
    private final String mUrl;
    private final FeedbackResult mCallback;
    private final long mCollectionStartTime;
    // Not final because created during init. Should be used as a final member.
    protected ConnectivityTask mConnectivityTask;

    /**
     * An optional description for the feedback report.
     */
    private String mDescription;

    /**
     * An optional screenshot for the feedback report.
     */
    private Bitmap mScreenshot;

    /**
     * A flag indicating whether gathering connection data has finished.
     */
    private boolean mConnectivityTaskFinished;

    /**
     * A flag indicating whether taking a screenshot has finished.
     */
    private boolean mScreenshotTaskFinished;

    /**
     * A flag indicating whether the result has already been posted. This is used to ensure that
     * the result is not posted again if a timeout happens.
     */
    private boolean mResultPosted;

    /**
     * A callback for when the gathering of feedback data has finished. This may be called either
     * when all data has been collected, or after a timeout.
     */
    public interface FeedbackResult {
        /**
         * Called when feedback data collection result is ready.
         * @param collector the {@link FeedbackCollector} to retrieve the data from.
         */
        void onResult(FeedbackCollector collector);
    }

    /**
     * Creates a {@link FeedbackCollector} and starts asynchronous operations to gather extra data.
     * @param profile the current Profile.
     * @param url The URL of the current tab to include in the feedback the user sends, if any.
     *            This parameter may be null.
     * @param callback The callback which is invoked when feedback gathering is finished.
     * @return the created {@link FeedbackCollector}.
     */
    public static FeedbackCollector create(
            Activity activity, Profile profile, @Nullable String url, FeedbackResult callback) {
        ThreadUtils.assertOnUiThread();
        return new FeedbackCollector(activity, profile, url, callback);
    }

    @VisibleForTesting
    FeedbackCollector(Activity activity, Profile profile, String url, FeedbackResult callback) {
        mData = new HashMap<>();
        mProfile = profile;
        mUrl = url;
        mCallback = callback;
        mCollectionStartTime = SystemClock.elapsedRealtime();
        init(activity);
    }

    @VisibleForTesting
    void init(Activity activity) {
        postTimeoutTask();
        mConnectivityTask = ConnectivityTask.create(mProfile, CONNECTIVITY_CHECK_TIMEOUT_MS, this);
        ScreenshotTask.create(activity, this);
    }

    /**
     * {@link ConnectivityTask.ConnectivityResult} implementation.
     */
    @Override
    public void onResult(ConnectivityTask.FeedbackData feedbackData) {
        ThreadUtils.assertOnUiThread();
        mConnectivityTaskFinished = true;
        Map<String, String> connectivityMap = feedbackData.toMap();
        mData.putAll(connectivityMap);
        maybePostResult();
    }

    /**
     * {@link ScreenshotTask.ScreenshotTaskCallback} implementation.
     */
    @Override
    public void onGotBitmap(@Nullable Bitmap bitmap, boolean success) {
        ThreadUtils.assertOnUiThread();
        mScreenshotTaskFinished = true;
        if (success) mScreenshot = bitmap;
        maybePostResult();
    }

    private void postTimeoutTask() {
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                maybePostResult();
            }
        }, TIMEOUT_MS);
    }

    @VisibleForTesting
    void maybePostResult() {
        ThreadUtils.assertOnUiThread();
        if (mCallback == null) return;
        if (mResultPosted) return;
        // Always wait for screenshot.
        if (!mScreenshotTaskFinished) return;
        if (!mConnectivityTaskFinished && !hasTimedOut()) return;
        mResultPosted = true;
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCallback.onResult(FeedbackCollector.this);
            }
        });
    }

    @VisibleForTesting
    boolean hasTimedOut() {
        return SystemClock.elapsedRealtime() - mCollectionStartTime > TIMEOUT_MS;
    }

    /**
     * Adds a key-value pair of data to be included in the feedback report. This data
     * is user visible and should only contain single-line forms of data, not long Strings.
     * @param key the user visible key.
     * @param value the user visible value.
     */
    public void add(String key, String value) {
        ThreadUtils.assertOnUiThread();
        mData.put(key, value);
    }

    /**
     * Sets the default description to invoke feedback with.
     * @param description the user visible description.
     */
    public void setDescription(String description) {
        ThreadUtils.assertOnUiThread();
        mDescription = description;
    }

    /**
     * @return the default description to invoke feedback with.
     */
    @VisibleForTesting
    public String getDescription() {
        ThreadUtils.assertOnUiThread();
        return mDescription;
    }

    /**
     * Sets the screenshot to use for the feedback report.
     * @param screenshot the user visible screenshot.
     */
    @VisibleForTesting
    public void setScreenshot(Bitmap screenshot) {
        ThreadUtils.assertOnUiThread();
        mScreenshot = screenshot;
    }

    /**
     * @return the screenshot to use for the feedback report.
     */
    @VisibleForTesting
    public Bitmap getScreenshot() {
        ThreadUtils.assertOnUiThread();
        return mScreenshot;
    }

    /**
     * @return the collected data as a {@link Bundle}.
     */
    @VisibleForTesting
    public Bundle getBundle() {
        ThreadUtils.assertOnUiThread();
        addUrl();
        addConnectivityData();
        addDataReductionProxyData();
        return asBundle();
    }

    private void addUrl() {
        if (!TextUtils.isEmpty(mUrl)) {
            mData.put(URL_KEY, mUrl);
        }
    }

    private void addConnectivityData() {
        if (mConnectivityTaskFinished) return;
        Map<String, String> connectivityMap = mConnectivityTask.get().toMap();
        mData.putAll(connectivityMap);
    }

    private void addDataReductionProxyData() {
        if (mProfile.isOffTheRecord()) return;
        Map<String, String> dataReductionProxyMap =
                DataReductionProxySettings.getInstance().toFeedbackMap();
        mData.putAll(dataReductionProxyMap);
    }

    private Bundle asBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : mData.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        return bundle;
    }
}
