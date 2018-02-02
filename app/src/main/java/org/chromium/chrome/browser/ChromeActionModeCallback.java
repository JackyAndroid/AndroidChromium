// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content.R;
import org.chromium.content_public.browser.ActionModeCallbackHelper;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;

/**
 * A class that handles selection action mode for an associated {@link Tab}.
 */
public class ChromeActionModeCallback implements ActionMode.Callback {
    private final Context mContext;
    private final Tab mTab;
    private final ActionModeCallbackHelper mHelper;

    public ChromeActionModeCallback(Context context, Tab tab, ActionModeCallbackHelper helper) {
        mContext = context;
        mTab = tab;
        mHelper = helper;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        notifyContextualActionBarVisibilityChanged(true);
        mHelper.onCreateActionMode(mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        notifyContextualActionBarVisibilityChanged(true);
        return mHelper.onPrepareActionMode(mode, menu);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (!mHelper.isActionModeValid()) return true;

        if (item.getItemId() == R.id.select_action_menu_web_search) {
            search();
            mHelper.finishActionMode();
        } else {
            return mHelper.onActionItemClicked(mode, item);
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mHelper.onDestroyActionMode();
        notifyContextualActionBarVisibilityChanged(false);
    }

    private void notifyContextualActionBarVisibilityChanged(boolean show) {
        if (!mHelper.supportsFloatingActionMode()) {
            mTab.notifyContextualActionBarVisibilityChanged(show);
        }
    }

    private void search() {
        RecordUserAction.record("MobileActionMode.WebSearch");
        if (mTab.getTabModelSelector() == null) return;

        String query = mHelper.sanitizeQuery(mHelper.getSelectedText(),
                ActionModeCallbackHelper.MAX_SEARCH_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        String url = TemplateUrlService.getInstance().getUrlForSearchQuery(query);
        String headers = GeolocationHeader.getGeoHeader(mContext.getApplicationContext(),
                url, mTab.isIncognito());

        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setVerbatimHeaders(headers);
        loadUrlParams.setTransitionType(PageTransition.GENERATED);
        mTab.getTabModelSelector().openNewTab(loadUrlParams,
                TabLaunchType.FROM_LONGPRESS_FOREGROUND, mTab, mTab.isIncognito());
    }
}
