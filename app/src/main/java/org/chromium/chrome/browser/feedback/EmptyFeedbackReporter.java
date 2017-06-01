// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

/**
 * No-op implementation of {@link FeedbackReporter}.
 */
public class EmptyFeedbackReporter implements FeedbackReporter {
    @Override
    public void reportFeedback(FeedbackCollector collector) {}
}
