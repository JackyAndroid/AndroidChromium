// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.FirstRunFlowSequencer;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.webapps.FullScreenActivity;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;

/**
 * An activity that shows a webpage with a title above it. No navigation controls or menu is
 * provided. This is useful for showing a webpage without disrupting with the user's current
 * task or their list of tabs, e.g. for showing Terms of Service during first run.
 */
public class EmbedContentViewActivity extends FullScreenActivity {
    private static final String TAG = "EmbedContentViewActivity";

    /** An intent extra that will determine what URL is to be loaded initially. */
    protected static final String URL_INTENT_EXTRA = "url";
    /** An intent extra that will determine what title is to be set for the activity. */
    protected static final String TITLE_INTENT_EXTRA = "title";

    /**
     * Starts an EmbedContentViewActivity that shows title and URL for the given
     * resource IDs.
     */
    public static void show(Context context, int titleResId, int urlResId) {
        if (context == null) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(context, EmbedContentViewActivity.class.getName());
        if (context instanceof Activity) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            // Required to handle the case when this is triggered from tests.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(TITLE_INTENT_EXTRA, titleResId);
        intent.putExtra(URL_INTENT_EXTRA, urlResId);
        context.startActivity(intent);
    }

    @Override
    public void finishNativeInitialization() {
        super.finishNativeInitialization();
        getSupportActionBar().setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);

        final int titleResId = getIntent().getIntExtra(TITLE_INTENT_EXTRA, 0);
        if (titleResId != 0) {
            setTitle(getString(titleResId));
        }

        final int urlResId = getIntent().getIntExtra(URL_INTENT_EXTRA, 0);
        if (urlResId != 0) {
            final String url = getString(urlResId);
            getActivityTab().loadUrl(new LoadUrlParams(url, PageTransition.AUTO_TOPLEVEL));
        }
    }

    @Override
    protected final ChromeFullscreenManager createFullscreenManager(View controlContainer) {
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean retVal = super.onCreateOptionsMenu(menu);
        if (!FirstRunStatus.getFirstRunFlowComplete(this)) return retVal;
        menu.add(Menu.NONE, R.id.menu_id_open_in_chrome, Menu.NONE, R.string.menu_open_in_chrome);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Handles up navigation
            finish();
            return true;
        } else if (itemId == R.id.menu_id_open_in_chrome && getActivityTab() != null) {
            Intent chromeIntent = new Intent(
                    Intent.ACTION_VIEW, Uri.parse(getActivityTab().getUrl()));
            chromeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            IntentHandler.startChromeLauncherActivityForTrustedIntent(
                    chromeIntent, getApplicationContext());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean isContextualSearchAllowed() {
        return false;
    }

    @Override
    protected void setStatusBarColor(Tab tab, int color) {
        // Intentionally do nothing as EmbedContentViewActivity does not set status bar color.
    }

    @Override
    protected TabDelegate createTabDelegate(boolean incognito) {
        return new TabDelegate(incognito) {
            @Override
            protected boolean isAllowedToLaunchDocumentActivity(Context context) {
                // Catch a corner case where the user can bypass the ToS.  crbug.com/516645
                return FirstRunFlowSequencer.checkIfFirstRunIsNecessary(context, false) == null;
            }
        };
    }
}
