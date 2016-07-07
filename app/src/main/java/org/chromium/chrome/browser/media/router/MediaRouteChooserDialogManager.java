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
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;

import org.chromium.chrome.browser.media.router.cast.MediaSink;
import org.chromium.chrome.browser.media.router.cast.MediaSource;

/**
 * Manages the dialog responsible for selecting a {@link MediaSink}.
 */
public class MediaRouteChooserDialogManager extends BaseMediaRouteDialogManager implements
        OnCancelListener, OnDismissListener {

    private static final String DIALOG_FRAGMENT_TAG =
            "android.support.v7.mediarouter:MediaRouteChooserDialogFragment";

    private boolean mCancelled;

    public MediaRouteChooserDialogManager(MediaSource source, Context applicationContext,
            MediaRouteDialogDelegate delegate) {
        super(source, applicationContext, delegate);
    }

    @Override
    protected DialogFragment openDialogInternal(FragmentManager fm,
            MediaRouteDialogFactory factory) {
        if (fm.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) return null;

        MediaRouteChooserDialogFragment fragment = factory.onCreateChooserDialogFragment();
        fragment.setRouteSelector(mediaSource().buildRouteSelector());
        fragment.show(fm, DIALOG_FRAGMENT_TAG);
        fm.executePendingTransactions();

        Dialog dialog = fragment.getDialog();
        if (dialog == null) return null;

        dialog.setOnCancelListener(this);
        dialog.setOnDismissListener(this);

        return fragment;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        mCancelled = true;

        delegate().onDialogCancelled();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        closeDialog();

        if (mCancelled) return;

        MediaSink newSink = MediaSink.fromRoute(androidMediaRouter().getSelectedRoute());
        delegate().onSinkSelected(newSink);
    }

}
