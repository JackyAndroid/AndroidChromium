// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.content.Context;

import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionProxyUma;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Each time a tab loads with Lo-Fi this controller saves that tab id and title to the stack of
 * SnackbarManager. It will then let SnackbarManager show a snackbar representing the top entry
 * of the stack.
 * <p/>
 * When the load images button is clicked, it will reload the page without Lo-Fi.
 */
public class LoFiBarPopupController implements SnackbarManager.SnackbarController {
    private static final int DEFAULT_LO_FI_SNACKBAR_SHOW_DURATION_MS = 6000;
    private final SnackbarManager mSnackbarManager;
    private final Context mContext;
    private final boolean mDisabled;
    private Tab mTab;

    /**
     * Creates an instance of a {@link LoFiBarPopupController}.
     * @param context The {@link Context} in which snackbar is shown.
     * @param snackbarManager The manager that helps to show up snackbar.
     */
    public LoFiBarPopupController(Context context, SnackbarManager snackbarManager) {
        mSnackbarManager = snackbarManager;
        mContext = context;
        mDisabled = CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_LOFI_SNACKBAR);
    }

    /**
     * @param tab The tab. Saved to reload the page.
     */
    public void showLoFiBar(Tab tab) {
        if (mDisabled) return;
        mTab = tab;
        mSnackbarManager.showSnackbar(Snackbar.make(
                mContext.getString(R.string.data_reduction_lo_fi_snackbar_message), this)
                .setAction(mContext.getString(R.string.data_reduction_lo_fi_snackbar_action),
                        tab.getId())
                .setDuration(DEFAULT_LO_FI_SNACKBAR_SHOW_DURATION_MS));
        DataReductionProxySettings.getInstance().incrementLoFiSnackbarShown();
        DataReductionProxyUma.dataReductionProxyLoFiUIAction(
                DataReductionProxyUma.ACTION_LOAD_IMAGES_SNACKBAR_SHOWN);
    }

    /**
     * Dismisses the snackbar.
     */
    public void dismissLoFiBar() {
        if (mSnackbarManager.isShowing()) mSnackbarManager.dismissSnackbars(this);
    }

    /**
     * Reloads the page showing all images.
     */
    @Override
    public void onAction(Object actionData) {
        mSnackbarManager.dismissSnackbars(this);
        mTab.reloadDisableLoFi();
        DataReductionProxySettings.getInstance().incrementLoFiUserRequestsForImages();
        DataReductionProxyUma.dataReductionProxyLoFiUIAction(
                DataReductionProxyUma.ACTION_LOAD_IMAGES_SNACKBAR_CLICKED);
    }

    @Override
    public void onDismissNoAction(Object actionData) {}

    @Override
    public void onDismissForEachType(boolean isTimeout) {}
}