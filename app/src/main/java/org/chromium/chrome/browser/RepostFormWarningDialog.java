// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * Form resubmission warning dialog. Presents the cancel/continue choice and fires one of two
 * callbacks accordingly.
 */
public class RepostFormWarningDialog extends DialogFragment {
    // Warning dialog currently being shown, stored for testing.
    private static Dialog sCurrentDialog;

    private final Runnable mCancelCallback;
    private final Runnable mContinueCallback;

    /** Empty constructor required for DialogFragments. */
    public RepostFormWarningDialog() {
        mCancelCallback = null;
        mContinueCallback = null;
    }

    public RepostFormWarningDialog(Runnable cancelCallback, Runnable continueCallback) {
        mCancelCallback = cancelCallback;
        mContinueCallback = continueCallback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If there is savedInstanceState, then the dialog is being recreated by android
        // and will lack the necessary callbacks.  Dismiss immediately as the tab will
        // need to be recreated anyway.
        if (savedInstanceState != null) {
            dismiss();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setMessage(R.string.http_post_warning);

        if (savedInstanceState == null) {
            assert mCancelCallback != null;
            assert mContinueCallback != null;
            builder.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            mCancelCallback.run();
                        }
                    });
            builder.setPositiveButton(R.string.http_post_warning_resend,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            mContinueCallback.run();
                        }
                    });
        }

        assert getCurrentDialog() == null;
        Dialog dialog = builder.create();
        setCurrentDialog(dialog);

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        setCurrentDialog(null);
    }

    /**
     * Sets the currently displayed dialog in sCurrentDialog. This is required by findbugs, which
     * allows static fields only to be set from static methods.
     */
    private static void setCurrentDialog(Dialog dialog) {
        sCurrentDialog = dialog;
    }

    /**
     * @return dialog currently being displayed.
     */
    public static Dialog getCurrentDialog() {
        return sCurrentDialog;
    }
}
