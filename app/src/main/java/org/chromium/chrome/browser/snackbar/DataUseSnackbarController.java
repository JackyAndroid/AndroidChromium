// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.content.Context;

import org.chromium.chrome.browser.EmbedContentViewActivity;
import org.chromium.chrome.browser.datausage.DataUseTabUIManager;
import org.chromium.chrome.browser.datausage.DataUseTabUIManager.DataUsageUIAction;
import org.chromium.chrome.browser.datausage.DataUseUIMessage;

/**
 * The controller for two data use snackbars:
 *
 * 1. When Chrome starts tracking data use in a Tab, it shows a snackbar informing the user that
 * data use tracking has started.
 *
 * 2. When Chrome stops tracking data use in a Tab, it shows a snackbar informing the user that
 * data use tracking has ended.
 */
public class DataUseSnackbarController implements SnackbarManager.SnackbarController {
    /** Snackbar types */
    private static final int STARTED_SNACKBAR = 0;
    private static final int ENDED_SNACKBAR = 1;

    private final SnackbarManager mSnackbarManager;
    private final Context mContext;

    /**
     * Creates an instance of a {@link DataUseSnackbarController}.
     * @param context The {@link Context} in which snackbar is shown.
     * @param snackbarManager The manager that helps to show up snackbar.
     */
    public DataUseSnackbarController(Context context, SnackbarManager snackbarManager) {
        mSnackbarManager = snackbarManager;
        mContext = context;
    }

    /**
     * Shows the data use tracking started snackbar. This should be called only after checking if
     * the UI elements are not disabled to be shown.
     */
    public void showDataUseTrackingStartedBar() {
        assert DataUseTabUIManager.shouldShowDataUseStartedUI();
        mSnackbarManager.showSnackbar(Snackbar
                .make(DataUseTabUIManager.getDataUseUIString(
                        DataUseUIMessage.DATA_USE_TRACKING_STARTED_SNACKBAR_MESSAGE), this,
                        Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DATA_USE_STARTED)
                .setAction(
                        DataUseTabUIManager.getDataUseUIString(
                                DataUseUIMessage.DATA_USE_TRACKING_SNACKBAR_ACTION),
                        STARTED_SNACKBAR));
        DataUseTabUIManager.recordDataUseUIAction(DataUsageUIAction.STARTED_SNACKBAR_SHOWN);
    }

    /**
     * Shows the data use tracking ended snackbar. This should be called only after checking if the
     * UI elements are not disabled to be shown.
     */
    public void showDataUseTrackingEndedBar() {
        assert DataUseTabUIManager.shouldShowDataUseEndedUI();
        assert DataUseTabUIManager.shouldShowDataUseEndedSnackbar(mContext);
        mSnackbarManager.showSnackbar(
                Snackbar.make(DataUseTabUIManager.getDataUseUIString(
                                      DataUseUIMessage.DATA_USE_TRACKING_ENDED_SNACKBAR_MESSAGE),
                                this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DATA_USE_ENDED)
                        .setAction(DataUseTabUIManager.getDataUseUIString(
                                           DataUseUIMessage.DATA_USE_TRACKING_SNACKBAR_ACTION),
                                ENDED_SNACKBAR));
        DataUseTabUIManager.recordDataUseUIAction(DataUsageUIAction.ENDED_SNACKBAR_SHOWN);
    }

    /**
     * Dismisses the snackbar.
     */
    public void dismissDataUseBar() {
        if (mSnackbarManager.isShowing()) mSnackbarManager.dismissSnackbars(this);
    }

    /**
     * Loads the "Learn more" page.
     */
    @Override
    public void onAction(Object actionData) {
        EmbedContentViewActivity.show(mContext,
                DataUseTabUIManager.getDataUseUIString(DataUseUIMessage.DATA_USE_LEARN_MORE_TITLE),
                DataUseTabUIManager.getDataUseUIString(
                        DataUseUIMessage.DATA_USE_LEARN_MORE_LINK_URL));

        if (actionData == null) return;
        int snackbarType = (int) actionData;
        switch (snackbarType) {
            case STARTED_SNACKBAR:
                DataUseTabUIManager.recordDataUseUIAction(
                        DataUsageUIAction.STARTED_SNACKBAR_MORE_CLICKED);
                break;
            case ENDED_SNACKBAR:
                DataUseTabUIManager.recordDataUseUIAction(
                        DataUsageUIAction.ENDED_SNACKBAR_MORE_CLICKED);
                break;
            default:
                assert false;
                break;
        }
    }

    @Override
    public void onDismissNoAction(Object actionData) {}
}
