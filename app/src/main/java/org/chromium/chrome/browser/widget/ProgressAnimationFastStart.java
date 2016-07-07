// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

/**
 * Progress bar animation logic that starts fast at the beginning and slows down approaching to the
 * end.
 */
class ProgressAnimationFastStart implements ToolbarProgressBar.AnimationLogic {
    // The speed unit is progress per second where 0 <= progress <= 1.
    private static final float NORMALIZED_INITIAL_SPEED = 1.5f;
    private static final float FINISHING_SPEED = 2.0f;

    private float mProgress;

    @Override
    public void reset() {
        mProgress = 0.0f;
    }

    @Override
    public float updateProgress(float targetProgress, float frameTimeSec, int resolution) {
        assert mProgress <= targetProgress;

        float progressChange;
        if (targetProgress == 1.0f) {
            progressChange = FINISHING_SPEED * frameTimeSec;
        } else {
            progressChange = (targetProgress - mProgress)
                    * (1.0f - (float) Math.exp(-frameTimeSec * NORMALIZED_INITIAL_SPEED));
        }

        mProgress = Math.min(mProgress + progressChange, targetProgress);
        if (targetProgress - mProgress < 0.5f / resolution) mProgress = targetProgress;

        return mProgress;
    }
}
