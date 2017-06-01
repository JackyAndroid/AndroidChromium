// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.chrome.R;

/**
 * HTTP Authentication Dialog
 *
 * This borrows liberally from android.browser.HttpAuthenticationDialog.
 */
public class LoginPrompt implements ChromeHttpAuthHandler.AutofillObserver {
    private final Context mContext;
    private final ChromeHttpAuthHandler mAuthHandler;

    private AlertDialog mDialog;
    private EditText mUsernameView;
    private EditText mPasswordView;

    public LoginPrompt(Context context, ChromeHttpAuthHandler authHandler) {
        mContext = context;
        mAuthHandler = authHandler;
        createDialog();
    }

    private void createDialog() {
        View v = LayoutInflater.from(mContext).inflate(R.layout.http_auth_dialog, null);
        mUsernameView = (EditText) v.findViewById(R.id.username);
        mPasswordView = (EditText) v.findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            }
        });

        TextView explanationView = (TextView) v.findViewById(R.id.explanation);
        explanationView.setText(mAuthHandler.getMessageBody());

        mDialog = new AlertDialog.Builder(mContext, R.style.AlertDialogTheme)
                .setTitle(R.string.login_dialog_title)
                .setView(v)
                .setPositiveButton(R.string.login_dialog_ok_button_label,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mAuthHandler.proceed(getUsername(),  getPassword());
                            }
                        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mAuthHandler.cancel();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mAuthHandler.cancel();
                    }
                })
                .create();
        mDialog.getDelegate().setHandleNativeActionModesEnabled(false);

        // Make the IME appear when the dialog is displayed if applicable.
        mDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    /**
     * Shows the dialog.
     */
    public void show() {
        mDialog.show();
        mUsernameView.requestFocus();
    }

    private String getUsername() {
        return mUsernameView.getText().toString();
    }

    private String getPassword() {
        return mPasswordView.getText().toString();
    }

    @Override
    public void onAutofillDataAvailable(String username, String password) {
        mUsernameView.setText(username);
        mPasswordView.setText(password);
        mUsernameView.selectAll();
    }
}
