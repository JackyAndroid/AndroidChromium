// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar.smartlockautosignin;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;

/**
 * A controller that triggers an auto sign-in snackbar. Auto sign-in snackbar is
 * triggered on a request credentials call of a Credential Manager API.
 */
public class AutoSigninSnackbarController
        implements SnackbarManager.SnackbarController {

    private final SnackbarManager mSnackbarManager;
    private final TabObserver mTabObserver;
    private final Tab mTab;

    /**
     * Displays Auto sign-in snackbar, which communicates to the users that they
     * were signed in to the web site.
     */
    @CalledByNative
    private static void showSnackbar(Tab tab, String text) {
        SnackbarManager snackbarManager = tab.getSnackbarManager();
        if (snackbarManager == null) return;
        AutoSigninSnackbarController snackbarController =
                new AutoSigninSnackbarController(snackbarManager, tab);
        Snackbar snackbar = Snackbar.make(text, snackbarController, Snackbar.TYPE_NOTIFICATION,
                Snackbar.UMA_AUTO_LOGIN);
        Resources resources = tab.getWindowAndroid().getActivity().get().getResources();
        int backgroundColor = ApiCompatibilityUtils.getColor(resources, R.color.light_active_color);
        Bitmap icon = BitmapFactory.decodeResource(
                resources, R.drawable.account_management_no_picture);
        snackbar.setSingleLine(false).setBackgroundColor(backgroundColor).setProfileImage(icon);
        snackbarManager.showSnackbar(snackbar);
    }

    /**
     * Creates an instance of a {@link AutoSigninSnackbarController}.
     * @param snackbarManager The manager that helps to show up snackbar.
     */
    private AutoSigninSnackbarController(SnackbarManager snackbarManager, Tab tab) {
        mTab = tab;
        mSnackbarManager = snackbarManager;
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onHidden(Tab tab) {
                AutoSigninSnackbarController.this.dismissAutoSigninSnackbar();
            }

            @Override
            public void onDestroyed(Tab tab) {
                AutoSigninSnackbarController.this.dismissAutoSigninSnackbar();
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                AutoSigninSnackbarController.this.dismissAutoSigninSnackbar();
            }
        };
        mTab.addObserver(mTabObserver);
    }

    /**
     * Dismisses the snackbar.
     */
    public void dismissAutoSigninSnackbar() {
        if (mSnackbarManager.isShowing()) {
            mSnackbarManager.dismissSnackbars(this);
        }
    }

    @Override
    public void onAction(Object actionData) {}

    @Override
    public void onDismissNoAction(Object actionData) {
        mTab.removeObserver(mTabObserver);
    }
}
