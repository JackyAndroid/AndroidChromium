// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.content.Context;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouter;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.media.remote.ChromeMediaRouteDialogFactory;
import org.chromium.chrome.browser.media.router.cast.MediaSource;

import javax.annotation.Nullable;

/**
 * Shared code for {@link MediaRouteDialogManager} implementations.
 */
public abstract class BaseMediaRouteDialogManager implements MediaRouteDialogManager {

    private final MediaSource mMediaSource;
    private final MediaRouter mAndroidMediaRouter;
    private final MediaRouteDialogDelegate mDelegate;

    private DialogFragment mDialogFragment;

    @Override
    public void openDialog() {
        if (mAndroidMediaRouter == null) return;

        FragmentActivity currentActivity =
                (FragmentActivity) ApplicationStatus.getLastTrackedFocusedActivity();
        if (currentActivity == null) return;

        FragmentManager fm = currentActivity.getSupportFragmentManager();
        if (fm == null) return;

        MediaRouteDialogFactory factory = new ChromeMediaRouteDialogFactory();
        mDialogFragment = openDialogInternal(fm, factory);
    }

    @Override
    public void closeDialog() {
        if (mDialogFragment == null) return;

        mDialogFragment.dismiss();
        mDialogFragment = null;
    }

    @Override
    public boolean isShowingDialog() {
        return mDialogFragment != null && mDialogFragment.isVisible();
    }

    protected BaseMediaRouteDialogManager(MediaSource source, Context applicationContext,
            MediaRouteDialogDelegate delegate) {
        mMediaSource = source;
        mAndroidMediaRouter = ChromeMediaRouter.getAndroidMediaRouter(applicationContext);
        mDelegate = delegate;
    }

    /**
     * Initializes and shows the {@link DialogFragment} instance corresponding to the dialog type
     * needed.
     *
     * @param fm {@link FragmentManager} to use to show the dialog.
     * @param factory {@link MediaRouteDialogFactory} to use to create the dialog.
     * @return null if the initialization fails, otherwise the initialized dialog fragment.
     */
    @Nullable
    protected abstract DialogFragment openDialogInternal(
            FragmentManager fm, MediaRouteDialogFactory factory);

    protected MediaRouteDialogDelegate delegate() {
        return mDelegate;
    }

    protected MediaRouter androidMediaRouter() {
        return mAndroidMediaRouter;
    }

    protected MediaSource mediaSource() {
        return mMediaSource;
    }
}
