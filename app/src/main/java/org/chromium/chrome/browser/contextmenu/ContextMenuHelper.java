// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.app.Activity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * A helper class that handles generating context menus for {@link ContentViewCore}s.
 */
public class ContextMenuHelper implements OnCreateContextMenuListener, OnMenuItemClickListener {
    private long mNativeContextMenuHelper;

    private ContextMenuPopulator mPopulator;
    private ContextMenuParams mCurrentContextMenuParams;

    private ContextMenuHelper(long nativeContextMenuHelper) {
        mNativeContextMenuHelper = nativeContextMenuHelper;
    }

    @CalledByNative
    private static ContextMenuHelper create(long nativeContextMenuHelper) {
        return new ContextMenuHelper(nativeContextMenuHelper);
    }

    @CalledByNative
    private void destroy() {
        mNativeContextMenuHelper = 0;
    }

    /**
     * @param populator A {@link ContextMenuPopulator} that is responsible for managing and showing
     *                  context menus.
     */
    @CalledByNative
    private void setPopulator(ContextMenuPopulator populator) {
        mPopulator = populator;
    }

    /**
     * Starts showing a context menu for {@code view} based on {@code params}.
     * @param contentViewCore The {@link ContentViewCore} to show the menu to.
     * @param params          The {@link ContextMenuParams} that indicate what menu items to show.
     */
    @CalledByNative
    private void showContextMenu(ContentViewCore contentViewCore, ContextMenuParams params) {
        final View view = contentViewCore.getContainerView();

        if (view == null
                || view.getVisibility() != View.VISIBLE
                || view.getParent() == null) {
            return;
        }

        mCurrentContextMenuParams = params;

        view.setOnCreateContextMenuListener(this);
        if (view.showContextMenu()) {
            WebContents webContents = contentViewCore.getWebContents();
            RecordHistogram.recordBooleanHistogram(
                    "ContextMenu.Shown", webContents != null);
        }
    }

    /**
     * Starts a download based on the current {@link ContextMenuParams}.
     * @param isLink Whether or not the download target is a link.
     */
    public void startContextMenuDownload(boolean isLink, boolean isDataReductionProxyEnabled) {
        if (mNativeContextMenuHelper != 0) {
            nativeOnStartDownload(mNativeContextMenuHelper, isLink, isDataReductionProxyEnabled);
        }
    }

    /**
     * Trigger an image search for the current image that triggered the context menu.
     */
    public void searchForImage() {
        if (mNativeContextMenuHelper == 0) return;
        nativeSearchForImage(mNativeContextMenuHelper);
    }

    /**
     * Share the image that triggered the current context menu.
     */
    public void shareImage() {
        if (mNativeContextMenuHelper == 0) return;
        nativeShareImage(mNativeContextMenuHelper);
    }

    @CalledByNative
    private void onShareImageReceived(
            WindowAndroid windowAndroid, byte[] jpegImageData) {
        Activity activity = windowAndroid.getActivity().get();
        if (activity == null) return;

        ShareHelper.shareImage(activity, jpegImageData);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        assert mPopulator != null;
        mPopulator.buildContextMenu(menu, v.getContext(), mCurrentContextMenuParams);

        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setOnMenuItemClickListener(this);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return mPopulator.onItemSelected(this, mCurrentContextMenuParams, item.getItemId());
    }

    /**
     * @return The {@link ContextMenuPopulator} responsible for populating the context menu.
     */
    @VisibleForTesting
    public ContextMenuPopulator getPopulator() {
        return mPopulator;
    }

    private native void nativeOnStartDownload(
            long nativeContextMenuHelper, boolean isLink, boolean isDataReductionProxyEnabled);
    private native void nativeSearchForImage(long nativeContextMenuHelper);
    private native void nativeShareImage(long nativeContextMenuHelper);
}
