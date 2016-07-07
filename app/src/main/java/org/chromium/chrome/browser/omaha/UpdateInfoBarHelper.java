// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.content.res.Resources;
import android.os.AsyncTask;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Shows an InfoBar indicating that a new version of Chrome is available.
 */
public class UpdateInfoBarHelper {
    /** Whether OmahaClient has already been checked for an update. */
    private boolean mAlreadyCheckedForUpdates;

    /** Whether to show the InfoBar indicating a new version is available. */
    private boolean mMustShowInfoBar;

    /** URL to direct the user to when Omaha detects a newer version available. */
    private String mUpdateURL;

    /** Checks if the OmahaClient knows about an update. */
    public void checkForUpdateOnBackgroundThread(final ChromeActivity activity) {
        ThreadUtils.assertOnUiThread();

        if (mAlreadyCheckedForUpdates) return;
        mAlreadyCheckedForUpdates = true;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (OmahaClient.isNewerVersionAvailable(activity)) {
                    mUpdateURL = OmahaClient.getMarketURL(activity);
                    mMustShowInfoBar = true;
                } else {
                    mMustShowInfoBar = false;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (activity.isActivityDestroyed()) return;
                showUpdateInfobarIfNecessary(activity);
            }
        }.execute();
    }

    /** Shows the InfoBar if it needs to be shown. */
    public void showUpdateInfobarIfNecessary(ChromeActivity activity) {
        if (mMustShowInfoBar) showUpdateInfoBar(activity);
    }

    /** Shows an InfoBar indicating that a new version of Chrome is available. */
    private void showUpdateInfoBar(ChromeActivity activity) {
        ThreadUtils.assertOnUiThread();

        // Don't show the InfoBar if it doesn't make sense to.
        Tab currentTab = activity.getActivityTab();
        boolean tabIsInvalid = currentTab == null || currentTab.isNativePage();
        boolean mayShowUpdateInfoBar = activity.mayShowUpdateInfoBar();
        boolean urlIsInvalid = mUpdateURL == null;
        if (tabIsInvalid || !mayShowUpdateInfoBar || urlIsInvalid) {
            mMustShowInfoBar = true;
            return;
        }

        // Create and show the InfoBar.
        Resources resources = activity.getResources();
        String message = resources.getString(R.string.update_available_infobar);
        String button = resources.getString(R.string.update_available_infobar_button);
        OmahaUpdateInfobar updateBar =
                new OmahaUpdateInfobar(activity, message, button, mUpdateURL);
        currentTab.getInfoBarContainer().addInfoBar(updateBar);
        mMustShowInfoBar = false;
    }
}
