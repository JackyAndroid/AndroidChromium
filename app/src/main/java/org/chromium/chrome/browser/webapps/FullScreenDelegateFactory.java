// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContextMenuItemDelegate;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroid;

/**
 * A {@link TabDelegateFactory} class to be used in all {@link Tab} instances owned
 * by a {@link FullScreenActivity}.
 */
public class FullScreenDelegateFactory extends TabDelegateFactory {
    private static class FullScreenTabWebContentsDelegateAndroid
            extends TabWebContentsDelegateAndroid {

        public FullScreenTabWebContentsDelegateAndroid(Tab tab, ChromeActivity activity) {
            super(tab, activity);
        }

        @Override
        public void activateContents() {
            if (!(mActivity instanceof WebappActivity)) return;

            WebappInfo webappInfo = ((WebappActivity) mActivity).getWebappInfo();
            String url = webappInfo.uri().toString();

            // Create an Intent that will be fired toward the WebappLauncherActivity, which in turn
            // will fire an Intent to launch the correct WebappActivity.  On L+ this could probably
            // be changed to call AppTask.moveToFront(), but for backwards compatibility we relaunch
            // it the hard way.
            Intent intent = new Intent();
            intent.setAction(WebappLauncherActivity.ACTION_START_WEBAPP);
            intent.setPackage(mActivity.getPackageName());
            webappInfo.setWebappIntentExtras(intent);

            intent.putExtra(ShortcutHelper.EXTRA_MAC, ShortcutHelper.getEncodedMac(mActivity, url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.getApplicationContext().startActivity(intent);
        }
    }

    @Override
    public FullScreenTabWebContentsDelegateAndroid createWebContentsDelegate(
            Tab tab, ChromeActivity activity) {
        return new FullScreenTabWebContentsDelegateAndroid(tab, activity);
    }

    @Override
    public ContextMenuPopulator createContextMenuPopulator(Tab tab, final ChromeActivity activity) {
        return new ChromeContextMenuPopulator(
                new TabContextMenuItemDelegate(tab, activity),
                ChromeContextMenuPopulator.FULLSCREEN_TAB_MODE);
    }
}
