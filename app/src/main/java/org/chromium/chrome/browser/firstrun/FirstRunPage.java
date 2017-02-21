// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

/**
 * A first run page shown in the First Run ViewPager.
 */
public class FirstRunPage extends Fragment {
    private Bundle mProperties = new Bundle();

    /**
     * @return Whether this page should be skipped on the FRE creation.
     * @param appContext An application context.
     */
    public boolean shouldSkipPageOnCreate(Context appContext) {
        return false;
    }

    // Fragment:
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mProperties = savedInstanceState;
        } else if (getArguments() != null) {
            mProperties = getArguments();
        } else {
            mProperties = new Bundle();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(mProperties);
    }

    /**
     * @return Whether the back button press was handled by this page.
     */
    public boolean interceptBackPressed() {
        return false;
    }

    /**
     * @return Passed arguments if any, or saved instance state if any, or an empty bundle.
     */
    protected Bundle getProperties() {
        return mProperties;
    }

    /**
     * @return The interface to the host.
     */
    protected FirstRunPageDelegate getPageDelegate() {
        return (FirstRunPageDelegate) getActivity();
    }

    /**
     * Advances to the next FRE page.
     */
    protected void advanceToNextPage() {
        getPageDelegate().advanceToNextPage();
    }
}