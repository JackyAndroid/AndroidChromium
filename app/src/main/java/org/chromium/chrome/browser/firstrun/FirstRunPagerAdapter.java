// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Adapter used to provide First Run pages to the FirstRunActivity ViewPager.
 */
class FirstRunPagerAdapter extends FragmentStatePagerAdapter {
    private final List<Callable<FirstRunPage>> mPages;
    private final Bundle mFreProperties;

    private boolean mStopAtTheFirstPage;

    public FirstRunPagerAdapter(FragmentManager fragmentManager,
            List<Callable<FirstRunPage>> pages, Bundle freProperties) {
        super(fragmentManager);
        assert pages != null;
        assert pages.size() > 0;
        assert freProperties != null;
        mPages = pages;
        mFreProperties = freProperties;
    }

    /**
     * Controls progression beyond the first page.
     * @param stop True if no progression beyond the first page is allowed.
     */
    void setStopAtTheFirstPage(boolean stop) {
        if (stop != mStopAtTheFirstPage) {
            mStopAtTheFirstPage = stop;
            notifyDataSetChanged();
        }
    }

    @Override
    public Fragment getItem(int position) {
        assert position >= 0 && position < mPages.size();
        FirstRunPage result = null;
        try {
            result = mPages.get(position).call();
        } catch (Exception e) {
            // We can always return null and it will be properly handled at the caller level.
        }
        if (result == null) return null;

        Bundle props = new Bundle();
        props.putAll(mFreProperties);
        FirstRunPage.addProperties(props, position, getCount() - 1);
        result.setArguments(props);

        return result;
    }

    @Override
    public int getCount() {
        if (mStopAtTheFirstPage) return 1;
        return mPages.size();
    }

    @Override
    public int getItemPosition(Object object) {
        // We do not keep track of constructed objects, but we want the pages to be recreated
        // on notifyDataSetChanged. Hence, tell the view that it needs to refresh the objects.
        return POSITION_NONE;
    }
}