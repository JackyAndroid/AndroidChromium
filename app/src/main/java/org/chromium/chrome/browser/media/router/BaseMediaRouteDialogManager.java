// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.media.MediaRouter;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.media.router.cast.MediaSource;

import javax.annotation.Nullable;

/**
 * Shared code for {@link MediaRouteDialogManager} implementations.
 */
public abstract class BaseMediaRouteDialogManager implements MediaRouteDialogManager {

    /**
     * A helper class to handle the system visibility change caused by the dialog showing up.
     * Call saveSystemVisibility() in onCreateDialog() of the DialogFragment and later
     * restoreSystemVisibility() in onStop().
     * TODO(avayvod): refactor this to avoid a redundant copy in ChromeMediaRouteDialogFactory.
     */
    protected static class SystemVisibilitySaver {
        private int mSystemVisibilityToRestore;
        private boolean mWasFullscreenBeforeShowing;

        void saveSystemVisibility(Activity activity) {
            // If we are in fullscreen we may have also have hidden the system UI. This
            // is overridden when we display the dialog. Save the system UI visibility
            // state so we can restore it.
            FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
            mSystemVisibilityToRestore = decor.getSystemUiVisibility();
            mWasFullscreenBeforeShowing = (
                    (mSystemVisibilityToRestore & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0);
        }

        void restoreSystemVisibility(Activity activity) {
            if (!mWasFullscreenBeforeShowing) return;

            FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
            // In some cases we come out of fullscreen before closing this dialog. In these
            // cases we don't want to restore the system UI visibility state.
            boolean isStillFullscreen =
                    (decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) == 0;
            if (!isStillFullscreen) return;

            decor.setSystemUiVisibility(mSystemVisibilityToRestore);
        }
    }

    private final MediaSource mMediaSource;
    private final MediaRouter mAndroidMediaRouter;
    private final MediaRouteDialogDelegate mDelegate;

    protected DialogFragment mDialogFragment;

    @Override
    public void openDialog() {
        if (mAndroidMediaRouter == null) {
            mDelegate.onDialogCancelled();
            return;
        }

        FragmentActivity currentActivity =
                (FragmentActivity) ApplicationStatus.getLastTrackedFocusedActivity();
        if (currentActivity == null)  {
            mDelegate.onDialogCancelled();
            return;
        }

        FragmentManager fm = currentActivity.getSupportFragmentManager();
        if (fm == null)  {
            mDelegate.onDialogCancelled();
            return;
        }

        mDialogFragment = openDialogInternal(fm);
        if (mDialogFragment == null)  {
            mDelegate.onDialogCancelled();
            return;
        }
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
     * Base class for media router dialog fragment. Classes inheriting from
     * BaseMediaRouteDialogManager may extend the implementation but must redirect the empty
     * constructor to this implementation to handle the case of the Fragment being created via
     * this method.
     */
    public static class Fragment extends MediaRouteChooserDialogFragment {
        private final Handler mHandler = new Handler();
        private final SystemVisibilitySaver mVisibilitySaver = new SystemVisibilitySaver();
        protected BaseMediaRouteDialogManager mManager = null;

        public Fragment() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Fragment.this.dismiss();
                }
            });
        }

        public Fragment(BaseMediaRouteDialogManager manager) {
            mManager = manager;
        }

        @Override
        public void onStart() {
            mVisibilitySaver.saveSystemVisibility(getActivity());
            super.onStart();
        }

        @Override
        public void onStop() {
            super.onStop();
            mVisibilitySaver.restoreSystemVisibility(getActivity());
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (mManager == null) return;

            mManager.mDialogFragment = null;
        }
    }

    /**
     * Initializes and shows the {@link DialogFragment} instance corresponding to the dialog type
     * needed.
     *
     * @param fm {@link FragmentManager} to use to show the dialog.
     * @return null if the initialization fails, otherwise the initialized dialog fragment.
     */
    @Nullable
    protected abstract DialogFragment openDialogInternal(FragmentManager fm);

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
