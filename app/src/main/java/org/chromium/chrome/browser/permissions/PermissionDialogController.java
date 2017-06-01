// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.permissions;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.IntDef;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedList;
import java.util.List;

/**
 * Singleton instance which controls the display of modal permission dialogs. This class is lazily
 * initiated when getInstance() is first called. Implemented like this as modal permission dialogs
 * are disabled by default.
 *
 * Unlike permission infobars, which stack on top of each other, only one permission dialog may be
 * visible on the screen at once. Any additional request for a modal permissions dialog is queued,
 * and will be displayed once the user responds to the current dialog.
 */
public class PermissionDialogController implements AndroidPermissionRequester.RequestDelegate {
    private static final int NOT_DECIDED = 0;
    private static final int ACCEPTED = 1;
    private static final int CANCELED = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NOT_DECIDED, ACCEPTED, CANCELED})
    private @interface Decision {}

    private AlertDialog mDialog;
    private SwitchCompat mSwitchView;
    private PermissionDialogDelegate mDialogDelegate;
    private List<PermissionDialogDelegate> mRequestQueue;

    /** Whether a decision has been made for the current dialog. */
    @Decision private int mDecision;

    // Static holder to ensure safe initialization of the singleton instance.
    private static class Holder {
        private static final PermissionDialogController sInstance =
                new PermissionDialogController();
    }

    public static PermissionDialogController getInstance() {
        return Holder.sInstance;
    }

    private PermissionDialogController() {
        mRequestQueue = new LinkedList<>();
        mDecision = NOT_DECIDED;
    }

    /**
     * Called by native code to create a modal permission dialog. The PermissionDialogController
     * will decide whether the dialog needs to be queued (because another dialog is on the screen)
     * or whether it can be shown immediately.
     */
    @CalledByNative
    private static void createDialog(PermissionDialogDelegate delegate) {
        PermissionDialogController.getInstance().queueDialog(delegate);
    }

    /**
     * Queues a modal permission dialog for display. If there are currently no dialogs on screen, it
     * will be displayed immediately. Otherwise, it will be displayed as soon as the user responds
     * to the current dialog.
     * @param context  The context to use to get the dialog layout.
     * @param delegate The wrapper for the native-side permission delegate.
     */
    private void queueDialog(PermissionDialogDelegate delegate) {
        mRequestQueue.add(delegate);
        scheduleDisplay();
    }

    private void scheduleDisplay() {
        if (mDialog == null && !mRequestQueue.isEmpty()) showDialog();
    }

    @VisibleForTesting
    public AlertDialog getCurrentDialogForTesting() {
        return mDialog;
    }

    @Override
    public void onAndroidPermissionAccepted() {
        mDialogDelegate.onAccept(mSwitchView.isChecked());
        destroyDelegate();
        scheduleDisplay();
    }

    @Override
    public void onAndroidPermissionCanceled() {
        mDialogDelegate.onDismiss();
        destroyDelegate();
        scheduleDisplay();
    }

    /**
     * Shows the dialog asking the user for a web API permission.
     */
    public void showDialog() {
        if (mRequestQueue.isEmpty()) return;

        mDecision = NOT_DECIDED;
        mDialogDelegate = mRequestQueue.remove(0);
        Activity activity = mDialogDelegate.getTab().getWindowAndroid().getActivity().get();

        // It's possible for the activity to be null if we reach here just after the user
        // backgrounds the browser and cleanup has happened. In that case, we can't show a prompt,
        // so act as though the user dismissed it.
        if (activity == null) {
            mDialogDelegate.onDismiss();
            destroyDelegate();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.permission_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);

        mDialog = builder.create();
        mDialog.getDelegate().setHandleNativeActionModesEnabled(false);
        mDialog.setCanceledOnTouchOutside(false);

        TextView messageTextView = (TextView) view.findViewById(R.id.text);
        messageTextView.setText(prepareMainMessageString(mDialogDelegate));
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.announceForAccessibility(mDialogDelegate.getMessageText());
        messageTextView.setCompoundDrawablesWithIntrinsicBounds(
                mDialogDelegate.getDrawableId(), 0, 0, 0);
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());

        mSwitchView = (SwitchCompat) view.findViewById(R.id.permission_dialog_persist_toggle);
        mSwitchView.setChecked(true);
        TextView toggleTextView =
                (TextView) view.findViewById(R.id.permission_dialog_persist_message);
        if (mDialogDelegate.shouldShowPersistenceToggle()) {
            mSwitchView.setVisibility(View.VISIBLE);
            String toggleMessage =
                    mDialog.getContext().getString(R.string.permission_prompt_persist_text);
            toggleTextView.setText(toggleMessage);
            toggleTextView.setVisibility(View.VISIBLE);
            toggleTextView.announceForAccessibility(toggleMessage);

        } else {
            mSwitchView.setVisibility(View.GONE);
            toggleTextView.setVisibility(View.GONE);
        }

        mDialog.setView(view);

        // Set the buttons to call the appropriate delegate methods. When the dialog is dismissed,
        // the delegate's native pointers are freed, and the next queued dialog (if any) is
        // displayed.
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE, mDialogDelegate.getPrimaryButtonText(),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mDecision = ACCEPTED;
                    }
                });

        mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, mDialogDelegate.getSecondaryButtonText(),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mDecision = CANCELED;
                    }
                });

        // Called when the dialog is dismissed. Interacting with either button in the dialog will
        // call this handler after the primary/secondary handler.
        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mDialog = null;
                if (mDecision == ACCEPTED) {
                    // Request Android permissions if necessary. This will call back into either
                    // onAndroidPermissionAccepted or onAndroidPermissionCanceled, which will
                    // schedule the next permission dialog. If it returns false, no system level
                    // permissions need to be requested, so just run the accept callback.
                    if (!AndroidPermissionRequester.requestAndroidPermissions(
                                mDialogDelegate.getTab(), mDialogDelegate.getContentSettingsTypes(),
                                PermissionDialogController.this)) {
                        onAndroidPermissionAccepted();
                    }
                } else {
                    // Otherwise, run the necessary delegate callback immediately and schedule the
                    // next dialog.
                    if (mDecision == CANCELED) {
                        mDialogDelegate.onCancel(mSwitchView.isChecked());
                    } else {
                        mDialogDelegate.onDismiss();
                    }
                    destroyDelegate();
                    scheduleDisplay();
                }
            }
        });

        mDialog.show();
    }

    private CharSequence prepareMainMessageString(final PermissionDialogDelegate delegate) {
        SpannableStringBuilder fullString = new SpannableStringBuilder();

        String messageText = delegate.getMessageText();
        String linkText = delegate.getLinkText();
        if (!TextUtils.isEmpty(messageText)) fullString.append(messageText);

        // If the linkText exists, then wrap it in a clickable span and concatenate it with the main
        // dialog message.
        if (!TextUtils.isEmpty(linkText)) {
            if (fullString.length() > 0) fullString.append(" ");
            int spanStart = fullString.length();

            fullString.append(linkText);
            fullString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    mDecision = NOT_DECIDED;
                    delegate.onLinkClicked();
                    if (mDialog != null) mDialog.dismiss();
                }
            }, spanStart, fullString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return fullString;
    }

    private void destroyDelegate() {
        mDialogDelegate.destroy();
        mDialogDelegate = null;
    }
}
