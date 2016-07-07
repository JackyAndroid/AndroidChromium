// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouter;

import org.chromium.chrome.browser.media.router.cast.MediaSource;

/**
 * Manages the dialog responsible for controlling an existing media route.
 */
public class MediaRouteControllerDialogManager extends BaseMediaRouteDialogManager implements
        OnCancelListener, OnDismissListener {

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

    @Override
    protected DialogFragment openDialogInternal(FragmentManager fm,
            MediaRouteDialogFactory factory) {
        if (fm.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) return null;

        MediaRouteControllerDialogFragment fragment = factory.onCreateControllerDialogFragment();
        fragment.show(fm, DIALOG_FRAGMENT_TAG);
        fm.executePendingTransactions();

        Dialog dialog = fragment.getDialog();
        if (dialog == null) return null;

        dialog.setOnCancelListener(this);
        dialog.setOnDismissListener(this);
        androidMediaRouter().addCallback(mediaSource().buildRouteSelector(), mCallback);

        return fragment;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        delegate().onDialogCancelled();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        androidMediaRouter().removeCallback(mCallback);
        closeDialog();
    }
}
