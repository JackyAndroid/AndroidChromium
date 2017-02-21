// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.os.Build;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabTaskDescriptionHelper;

/**
 * Simple wrapper around the CustomTabActivity to be used when launching each CustomTab in a
 * separate task.
 */
public class SeparateTaskCustomTabActivity extends CustomTabActivity {
    private ActivityTabTaskDescriptionHelper mTaskDescriptionHelper;

    @Override
    public void finishNativeInitialization() {
        super.finishNativeInitialization();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTaskDescriptionHelper = new ActivityTabTaskDescriptionHelper(this,
                    ApiCompatibilityUtils.getColor(getResources(), R.color.default_primary_color));
        }
    }

    @Override
    protected void onDestroyInternal() {
        super.onDestroyInternal();

        if (mTaskDescriptionHelper != null) mTaskDescriptionHelper.destroy();
    }

    @Override
    protected void handleFinishAndClose() {
        ApiCompatibilityUtils.finishAndRemoveTask(this);
    }
}
