// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.app.Activity;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.feedback.FeedbackCollector;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.base.WindowAndroid;

/**
 * Java implementation of ChildAccountFeedbackReporterAndroid.
 */
public final class ChildAccountFeedbackReporter {
    private static FeedbackReporter sFeedbackReporter;

    public static void reportFeedback(Activity activity, final String description, String url) {
        ThreadUtils.assertOnUiThread();
        if (sFeedbackReporter == null) {
            ChromeApplication application = (ChromeApplication) activity.getApplication();
            sFeedbackReporter = application.createFeedbackReporter();
        }
        FeedbackCollector.create(activity, Profile.getLastUsedProfile(), url,
                new FeedbackCollector.FeedbackResult() {
                    @Override
                    public void onResult(FeedbackCollector collector) {
                        collector.setDescription(description);
                        sFeedbackReporter.reportFeedback(collector);
                    }
                });
    }

    @CalledByNative
    public static void reportFeedbackWithWindow(
            WindowAndroid window, String description, String url) {
        reportFeedback(window.getActivity().get(), description, url);
    }

    private ChildAccountFeedbackReporter() {}
}
