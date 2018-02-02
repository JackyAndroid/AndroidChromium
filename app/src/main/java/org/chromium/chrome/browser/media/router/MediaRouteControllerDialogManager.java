// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;

import org.chromium.chrome.browser.media.router.cast.MediaSource;

/**
 * Manages the dialog responsible for controlling an existing media route.
 */
public class MediaRouteControllerDialogManager extends BaseMediaRouteDialogManager {

    private static final String DIALOG_FRAGMENT_TAG =
            "android.support.v7.mediarouter:MediaRouteControllerDialogFragment";

    private final String mMediaRouteId;

    private final MediaRouter.Callback mCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            delegate().onRouteClosed(mMediaRouteId);
        }
    };

    public MediaRouteControllerDialogManager(MediaSource source, String mediaRouteId,
            Context applicationContext,
            MediaRouteDialogDelegate delegate) {
        super(source, applicationContext, delegate);
        mMediaRouteId = mediaRouteId;
    }

    /**
     * Fragment implementation for MediaRouteControllerDialogManager.
     */
    public static class Fragment extends MediaRouteControllerDialogFragment {
        private final Handler mHandler = new Handler();
        private final SystemVisibilitySaver mVisibilitySaver = new SystemVisibilitySaver();
        private BaseMediaRouteDialogManager mManager = null;
        private MediaRouter.Callback mCallback = null;

        public Fragment() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Fragment.this.dismiss();
                }
            });
        }

        public Fragment(BaseMediaRouteDialogManager manager, MediaRouter.Callback callback) {
            mManager = manager;
            mCallback = callback;
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

            mManager.delegate().onDialogCancelled();
            mManager.androidMediaRouter().removeCallback(mCallback);
            mManager.mDialogFragment = null;
        }
    };

    @Override
    protected DialogFragment openDialogInternal(FragmentManager fm) {
        if (fm.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) return null;

        Fragment fragment = new Fragment(this, mCallback);
        MediaRouteSelector selector = mediaSource().buildRouteSelector();
        if (selector == null) return null;

        androidMediaRouter().addCallback(selector, mCallback);

        fragment.show(fm, DIALOG_FRAGMENT_TAG);
        fm.executePendingTransactions();

        return fragment;
    }
}
