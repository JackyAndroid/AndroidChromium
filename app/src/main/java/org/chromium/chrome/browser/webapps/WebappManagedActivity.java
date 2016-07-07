// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

/**
 * Type of WebappActivity that has the ability to swap out the webapp it is currently showing for a
 * new one. This is necessary on Android versions older than L because the framework had no way of
 * allowing multiple instances of an Activity to be launched and show up as different tasks.
 * Anything extending this class must be named WebappActivity0, WebappActivity1, etc.
 */
public abstract class WebappManagedActivity extends WebappActivity {
    private final int mActivityIndex;

    public WebappManagedActivity() {
        mActivityIndex = getActivityIndex();
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();

        if (!isFinishing()) {
            markActivityUsed();
        }
    }

    @Override
    protected String getId() {
        return String.valueOf(mActivityIndex);
    }

    /**
     * Marks that this WebappActivity is recently used to prevent other webapps from using it.
     */
    private void markActivityUsed() {
        ActivityAssigner.instance(this).markActivityUsed(mActivityIndex, getWebappInfo().id());
    }

    /**
     * Pulls out the index of the WebappActivity subclass that is being used.
     * e.g. WebappActivity0.getActivityIndex() will return 0.
     * @return The index corresponding to this WebappActivity.
     */
    private int getActivityIndex() {
        // Cull out the activity index from the class name.
        String baseClassName = WebappActivity.class.getSimpleName();
        String className = this.getClass().getSimpleName();
        assert className.matches("^" + baseClassName + "[0-9]+$");
        String indexString = className.substring(baseClassName.length());
        return Integer.parseInt(indexString);
    }
}
