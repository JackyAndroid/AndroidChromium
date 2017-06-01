// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.BuildInfo;
import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.sync.PassphraseType;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * Dialog to ask to user to enter their sync passphrase.
 */
public class PassphraseDialogFragment extends DialogFragment implements OnClickListener {

    private static final String TAG = "Sync_UI";

    /**
     * A listener for passphrase events.
     */
    interface Listener {
        /**
         * @return whether passphrase was valid.
         */
        boolean onPassphraseEntered(String passphrase);

        void onPassphraseCanceled();
    }

    private static final int PASSPHRASE_DIALOG_OK = 0;
    private static final int PASSPHRASE_DIALOG_ERROR = 1;
    private static final int PASSPHRASE_DIALOG_CANCEL = 2;
    private static final int PASSPHRASE_DIALOG_RESET_LINK = 3;
    private static final int PASSPHRASE_DIALOG_LIMIT = 4;

    private EditText mPassphraseEditText;
    private TextView mVerifyingTextView;

    private Drawable mOriginalBackground;
    private Drawable mErrorBackground;

    /**
     * Create a new instanceof of {@link PassphraseDialogFragment} and set its arguments.
     */
    public static PassphraseDialogFragment newInstance(Fragment target) {
        assert ProfileSyncService.get() != null;
        PassphraseDialogFragment dialog = new PassphraseDialogFragment();
        if (target != null) {
            dialog.setTargetFragment(target, -1);
        }
        return dialog;
    }

    private void recordPassphraseDialogDismissal(int result) {
        RecordHistogram.recordEnumeratedHistogram(
                "Sync.PassphraseDialogDismissed",
                result,
                PASSPHRASE_DIALOG_LIMIT);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.sync_enter_passphrase, null);

        TextView promptText = (TextView) v.findViewById(R.id.prompt_text);
        promptText.setText(getPromptText());

        TextView resetText = (TextView) v.findViewById(R.id.reset_text);
        resetText.setText(getResetText());
        resetText.setMovementMethod(LinkMovementMethod.getInstance());
        resetText.setVisibility(View.VISIBLE);

        mVerifyingTextView = (TextView) v.findViewById(R.id.verifying);

        mPassphraseEditText = (EditText) v.findViewById(R.id.passphrase);
        mPassphraseEditText.setHint(R.string.sync_enter_custom_passphrase_hint);
        mPassphraseEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    handleSubmit();
                }
                return false;
            }
        });

        // Create a new background Drawable for the passphrase EditText to use when the user has
        // entered an invalid potential password.
        // https://crbug.com/602943 was caused by modifying the Drawable from getBackground()
        // without taking a copy.
        mOriginalBackground = mPassphraseEditText.getBackground();
        mErrorBackground = mOriginalBackground.getConstantState().newDrawable();
        mErrorBackground.mutate().setColorFilter(
                ApiCompatibilityUtils.getColor(getResources(), R.color.input_underline_error_color),
                PorterDuff.Mode.SRC_IN);

        final AlertDialog d = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setView(v)
                .setPositiveButton(R.string.submit, new Dialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        // We override the onclick. This is a hack to not dismiss the dialog after
                        // click of OK and instead dismiss it after confirming the passphrase
                        // is correct.
                    }
                })
                 .setNegativeButton(R.string.cancel, this)
                 .setTitle(R.string.sign_in_google_account)
                 .create();

        d.getDelegate().setHandleNativeActionModesEnabled(false);
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button b = d.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        handleSubmit();
                    }
                });
            }
        });
        return d;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_NEGATIVE) {
            handleCancel();
        }
    }

    @Override
    public void onResume() {
        resetPassphraseBoxColor();
        super.onResume();
    }

    private String getPromptText() {
        ProfileSyncService pss = ProfileSyncService.get();
        String accountName = pss.getCurrentSignedInAccountText() + "\n\n";
        PassphraseType passphraseType = pss.getPassphraseType();
        if (pss.hasExplicitPassphraseTime()) {
            switch (passphraseType) {
                case FROZEN_IMPLICIT_PASSPHRASE:
                    return accountName + pss.getSyncEnterGooglePassphraseBodyWithDateText();
                case CUSTOM_PASSPHRASE:
                    return accountName + pss.getSyncEnterCustomPassphraseBodyWithDateText();
                case IMPLICIT_PASSPHRASE: // Falling through intentionally.
                case KEYSTORE_PASSPHRASE: // Falling through intentionally.
                default:
                    Log.w(TAG, "Found incorrect passphrase type " + passphraseType
                                    + ". Falling back to default string.");
                    return accountName + pss.getSyncEnterCustomPassphraseBodyText();
            }
        }
        return accountName + pss.getSyncEnterCustomPassphraseBodyText();
    }

    private SpannableString getResetText() {
        final Context context = getActivity();
        return SpanApplier.applySpans(
                context.getString(R.string.sync_passphrase_reset_instructions),
                new SpanInfo("<resetlink>", "</resetlink>", new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        recordPassphraseDialogDismissal(PASSPHRASE_DIALOG_RESET_LINK);
                        Uri syncDashboardUrl = Uri.parse(
                                context.getText(R.string.sync_dashboard_url).toString());
                        Intent intent = new Intent(Intent.ACTION_VIEW, syncDashboardUrl);
                        intent.setPackage(BuildInfo.getPackageName(context));
                        IntentUtils.safePutBinderExtra(
                                intent, CustomTabsIntent.EXTRA_SESSION, null);
                        context.startActivity(intent);
                    }
                }));
    }

    /**
     * @return whether the incorrect passphrase text is currently visible.
     */
    private boolean isIncorrectPassphraseVisible() {
        // Check if the verifying TextView is currently showing the incorrect passphrase text.
        String incorrectPassphraseMessage =
                getResources().getString(R.string.sync_passphrase_incorrect);
        String verifyMessage = mVerifyingTextView.getText().toString();
        return verifyMessage.equals(incorrectPassphraseMessage);
    }

    private void handleCancel() {
        int cancelReason = isIncorrectPassphraseVisible()
                ? PASSPHRASE_DIALOG_ERROR
                : PASSPHRASE_DIALOG_CANCEL;
        recordPassphraseDialogDismissal(cancelReason);
        getListener().onPassphraseCanceled();
    }

    private void handleSubmit() {
        resetPassphraseBoxColor();
        mVerifyingTextView.setText(R.string.sync_verifying);

        String passphrase = mPassphraseEditText.getText().toString();
        boolean success = getListener().onPassphraseEntered(passphrase);
        if (success) {
            recordPassphraseDialogDismissal(PASSPHRASE_DIALOG_OK);
        } else {
            invalidPassphrase();
        }
    }

    private Listener getListener() {
        Fragment target = getTargetFragment();
        if (target instanceof Listener) {
            return (Listener) target;
        }
        return (Listener) getActivity();
    }

    /**
     * Notify this fragment that the passphrase the user entered is incorrect.
     */
    private void invalidPassphrase() {
        mVerifyingTextView.setText(R.string.sync_passphrase_incorrect);
        mVerifyingTextView.setTextColor(ApiCompatibilityUtils.getColor(getResources(),
                R.color.input_underline_error_color));

        mPassphraseEditText.setBackground(mErrorBackground);
    }

    private void resetPassphraseBoxColor() {
        mPassphraseEditText.setBackground(mOriginalBackground);
    }
}
