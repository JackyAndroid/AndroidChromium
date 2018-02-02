// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.MediaRouteControllerDialog;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.remote.MediaRouteController.MediaStateListener;

/**
 * The Chrome implementation of the dialog factory so custom behavior can
 * be injected for the disconnect button.
 */
public class MediaRouteControllerDialogFactory extends MediaRouteDialogFactory {
    private static final String TAG = "MRCtrlDlg";

    private final MediaStateListener mPlayer;

    MediaRouteControllerDialogFactory(MediaStateListener player) {
        mPlayer = player;
    }

    private static class SystemVisibilitySaver {
        private int mSystemVisibility;
        private boolean mRestoreSystemVisibility;

        void saveSystemVisibility(Activity activity) {
            // If we are in fullscreen we may have also have hidden the system UI. This
            // is overridden when we display the dialog. Save the system UI visibility
            // state so we can restore it.
            FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
            mSystemVisibility = decor.getSystemUiVisibility();
            mRestoreSystemVisibility = (
                    (mSystemVisibility & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0);
        }

        void restoreSystemVisibility(Activity activity) {
            if (mRestoreSystemVisibility) {
                FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
                // In some cases we come out of fullscreen before closing this dialog. In these
                // cases we don't want to restore the system UI visibility state.
                int systemVisibility = decor.getSystemUiVisibility();
                if ((systemVisibility & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0) {
                    decor.setSystemUiVisibility(mSystemVisibility);
                }
            }
        }
    }

    /**
     * A dialog fragment for controlling a media route that saves system visibility for
     * handling fullscreen state of Chrome correctly. Needs to be a named public static class,
     * see https://crbug.com/618993.
     */
    public static final class Fragment extends MediaRouteControllerDialogFragment {
        final Handler mHandler = new Handler();
        final SystemVisibilitySaver mVisibilitySaver = new SystemVisibilitySaver();
        final MediaStateListener mPlayer;

        // The class has to be a public static class with a zero-argument constructor.
        // Since we can't pass any callbacks to the fragment easily, just close the dialog.
        // See https://crbug.com/618993.
        public Fragment() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Fragment.this.dismiss();
                }
            });
            mPlayer = null;
        }

        @SuppressLint("ValidFragment")
        Fragment(MediaStateListener player) {
            mPlayer = player;
        }

        @Override
        public Dialog onCreateDialog(Bundle saved) {
            mVisibilitySaver.saveSystemVisibility(getActivity());
            return new MediaRouteControllerDialog(getActivity());
        }

        @Override
        public void onStop() {
            super.onStop();
            mVisibilitySaver.restoreSystemVisibility(getActivity());
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            Log.d(TAG, "onDismiss " + mPlayer);
            super.onDismiss(dialog);
            if (mPlayer != null) mPlayer.onRouteDialogCancelled();
        }
    }

    @Override
    public MediaRouteControllerDialogFragment onCreateControllerDialogFragment() {
        return new Fragment(mPlayer);
    }
}
