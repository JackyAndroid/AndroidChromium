// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LoaderErrors;
import org.chromium.chrome.R;

/**
 * Dialog shown when startup fails.
 * <br>
 * Fragments are required to be public with a public empty constructor, hence the visibility.
 */
public class InvalidStartupDialog extends DialogFragment {
    private static final String TAG = "InvalidStartupDialog";

    private static final String MESSAGE_KEY = "InvalidStartupErrorKey";

    /**
     * Shows the invalid startup dialog for a given error code.
     *
     * @param activity The activity showing the dialog.
     * @param errorCode The error code that triggered the failure.
     */
    @SuppressFBWarnings("DM_EXIT")
    public static void show(Activity activity, int errorCode) {
        int msg;
        switch (errorCode) {
            case LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_LOAD_FAILED:
                msg = R.string.os_version_missing_features;
                break;
            case LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_WRONG_VERSION:
                msg = R.string.incompatible_libraries;
                break;
            default:
                msg = R.string.native_startup_failed;
        }
        final String message = activity.getResources().getString(msg);

        if (!(activity instanceof FragmentActivity)) {
            Log.e(TAG, "Unable to start chrome due to: " + msg);
            System.exit(-1);
            return;
        }

        Bundle dialogArgs = new Bundle();
        dialogArgs.putString(MESSAGE_KEY, message);

        InvalidStartupDialog dialog = new InvalidStartupDialog();
        dialog.setArguments(dialogArgs);
        dialog.show(((FragmentActivity) activity).getSupportFragmentManager(),
                "InvalidStartupDialog");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        String message = arguments.getString(MESSAGE_KEY, "Failed to start");

        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
        builder.setMessage(message)
                .setCancelable(true)
                .setPositiveButton(getResources().getString(android.R.string.ok), null);
        return builder.create();
    }

    @SuppressFBWarnings("DM_EXIT")
    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        System.exit(-1);
    }
}