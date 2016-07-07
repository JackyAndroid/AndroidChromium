// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.app.Activity;

/**
 * Provides a way of reporting feedback to an external feedback service.
 */
public interface ExternalFeedbackReporter {
    /**
     * Records feedback related to child account features.
     *
     * @param activity the activity to take a screenshot of.
     * @param description default description text.
     * @param url the URL to report feedback for.
     */
    void reportFeedback(Activity activity, String description, String url);
}
