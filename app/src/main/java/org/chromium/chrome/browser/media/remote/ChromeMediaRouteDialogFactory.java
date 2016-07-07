// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.MediaRouteChooserDialog;
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.app.MediaRouteControllerDialog;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The Chrome implementation of the dialog factory so custom behavior can
 * be injected for the disconnect button.
 */
public class ChromeMediaRouteDialogFactory extends MediaRouteDialogFactory {

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

    @Override
    public MediaRouteControllerDialogFragment onCreateControllerDialogFragment() {
        return new MediaRouteControllerDialogFragment() {
            final SystemVisibilitySaver mVisibilitySaver = new SystemVisibilitySaver();

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
        };
    }

    @Override
    public MediaRouteChooserDialogFragment onCreateChooserDialogFragment() {
        return new MediaRouteChooserDialogFragment() {
            final SystemVisibilitySaver mVisibilitySaver = new SystemVisibilitySaver();

            @Override
            public MediaRouteChooserDialog onCreateChooserDialog(
                    Context context, Bundle savedInstanceState) {
                mVisibilitySaver.saveSystemVisibility(getActivity());
                return new MediaRouteChooserDialog(context);
            }

            @Override
            public void onStop() {
                super.onStop();
                mVisibilitySaver.restoreSystemVisibility(getActivity());
            }
        };
    }
}
