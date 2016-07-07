// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

/**
 * Progress bar animation logic that has linear speed.
 */
class ProgressAnimationLinear implements ToolbarProgressBar.AnimationLogic {
    // The speed unit is progress per second where 0 <= progress <= 1.
    private static final float NORMAL_SPEED = 0.4f;
    private static final float FINISHING_SPEED = 2.0f;

    private float mProgress;

    @Override
    public void reset() {
        mProgress = 0.0f;
    }

    @Override
    public float updateProgress(float targetProgress, float frameTimeSec, int resolution) {
        assert mProgress <= targetProgress;

        mProgress = Math.min(targetProgress, mProgress
                + frameTimeSec * (targetProgress == 1.0f ? FINISHING_SPEED : NORMAL_SPEED));
        return mProgress;
    }
}
