// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroid;
import org.chromium.chrome.browser.tab.TopControlsVisibilityDelegate;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.security_state.ConnectionSecurityLevel;

/**
 * A {@link TabDelegateFactory} class to be used in all {@link Tab} instances owned by a
 * {@link FullScreenActivity}.
 */
public class WebappDelegateFactory extends FullScreenDelegateFactory {
    private static class WebappWebContentsDelegateAndroid extends TabWebContentsDelegateAndroid {
        private final WebappActivity mActivity;

        public WebappWebContentsDelegateAndroid(WebappActivity activity, Tab tab) {
            super(tab);
            mActivity = activity;
        }

        @Override
        public void activateContents() {
            String startUrl = mActivity.getWebappInfo().uri().toString();

            // Create an Intent that will be fired toward the WebappLauncherActivity, which in turn
            // will fire an Intent to launch the correct WebappActivity.  On L+ this could probably
            // be changed to call AppTask.moveToFront(), but for backwards compatibility we relaunch
            // it the hard way.
            Intent intent = new Intent();
            intent.setAction(WebappLauncherActivity.ACTION_START_WEBAPP);
            intent.setPackage(mActivity.getPackageName());
            mActivity.getWebappInfo().setWebappIntentExtras(intent);

            intent.putExtra(
                    ShortcutHelper.EXTRA_MAC, ShortcutHelper.getEncodedMac(mActivity, startUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ContextUtils.getApplicationContext().startActivity(intent);
        }
    }

    @VisibleForTesting
    static class TopControlsDelegate extends TopControlsVisibilityDelegate {
        private final WebappActivity mActivity;

        public TopControlsDelegate(WebappActivity activity, Tab tab) {
            super(tab);
            mActivity = activity;
        }

        @Override
        public boolean isShowingTopControlsEnabled() {
            if (!super.isShowingTopControlsEnabled()) return false;

            String webappStartUrl = mActivity.getWebappInfo().uri().toString();
            return shouldShowTopControls(webappStartUrl, mTab.getUrl(), mTab.getSecurityLevel());
        }

        @Override
        public boolean isHidingTopControlsEnabled() {
            return !isShowingTopControlsEnabled();
        }

        /**
         * Returns whether the top controls should be shown when a webapp is navigated to
         * {@link url}.
         * @param webappStartUrl The webapp's URL when it is opened from the home screen.
         * @param url The webapp's current URL
         * @param securityLevel The security level for the webapp's current URL.
         * @return Whether the top controls should be shown for {@link url}.
         */
        public static boolean shouldShowTopControls(
                String webappStartUrl, String url, int securityLevel) {
            // Do not show top controls when URL is not ready yet.
            boolean visible = false;
            if (TextUtils.isEmpty(url)) return false;

            boolean isSameWebsite =
                    UrlUtilities.sameDomainOrHost(webappStartUrl, url, true);
            visible = !isSameWebsite || securityLevel == ConnectionSecurityLevel.DANGEROUS
                    || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING;
            return visible;
        }
    }

    private final WebappActivity mActivity;

    public WebappDelegateFactory(WebappActivity activity) {
        mActivity = activity;
    }

    @Override
    public TabWebContentsDelegateAndroid createWebContentsDelegate(Tab tab) {
        return new WebappWebContentsDelegateAndroid(mActivity, tab);
    }

    @Override
    public TopControlsVisibilityDelegate createTopControlsVisibilityDelegate(Tab tab) {
        return new TopControlsDelegate(mActivity, tab);
    }
}
