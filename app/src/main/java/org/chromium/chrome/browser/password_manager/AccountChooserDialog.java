// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.password_manager;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.signin.AccountManagementFragment;
import org.chromium.ui.base.WindowAndroid;

/**
 *  A dialog offers the user the ability to choose credentials for authentication. User is
 *  presented with username along with avatar and full name in case they are available.
 *  Native counterpart should be notified about credentials user have chosen and also if user
 *  haven't chosen anything.
 */
public class AccountChooserDialog
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private final Context mContext;
    private final Credential[] mCredentials;

    /**
     * Title of the dialog, contains Smart Lock branding for the Smart Lock users.
     */
    private final String mTitle;
    private final int mTitleLinkStart;
    private final int mTitleLinkEnd;
    private final String mOrigin;
    private final String mSigninButtonText;
    private ArrayAdapter<Credential> mAdapter;
    private boolean mIsDestroyed;
    private boolean mWasDismissedByNative;

    /**
     * Holds the reference to the credentials which were chosen by the user.
     */
    private Credential mCredential;
    private long mNativeAccountChooserDialog;
    private AlertDialog mDialog;
    /**
     * True, if credentials were selected via "Sign In" button instead of clicking on the credential
     * itself.
     */
    private boolean mSigninButtonClicked;

    private AccountChooserDialog(Context context, long nativeAccountChooserDialog,
            Credential[] credentials, String title, int titleLinkStart, int titleLinkEnd,
            String origin, String signinButtonText) {
        mNativeAccountChooserDialog = nativeAccountChooserDialog;
        mContext = context;
        mCredentials = credentials.clone();
        mTitle = title;
        mTitleLinkStart = titleLinkStart;
        mTitleLinkEnd = titleLinkEnd;
        mOrigin = origin;
        mSigninButtonText = signinButtonText;
        mSigninButtonClicked = false;
    }

    /**
     *  Creates and shows the dialog which allows user to choose credentials for login.
     *  @param credentials Credentials to display in the dialog.
     *  @param title Title message for the dialog, which can contain Smart Lock branding.
     *  @param titleLinkStart Start of a link in case title contains Smart Lock branding.
     *  @param titleLinkEnd End of a link in case title contains Smart Lock branding.
     *  @param origin Address of the web page, where dialog was triggered.
     */
    @CalledByNative
    private static AccountChooserDialog createAndShowAccountChooser(WindowAndroid windowAndroid,
            long nativeAccountChooserDialog, Credential[] credentials, String title,
            int titleLinkStart, int titleLinkEnd, String origin, String signinButtonText) {
        Activity activity = windowAndroid.getActivity().get();
        if (activity == null) return null;
        AccountChooserDialog chooser =
                new AccountChooserDialog(activity, nativeAccountChooserDialog, credentials, title,
                        titleLinkStart, titleLinkEnd, origin, signinButtonText);
        chooser.show();
        return chooser;
    }

    private ArrayAdapter<Credential> generateAccountsArrayAdapter(
            Context context, Credential[] credentials) {
        return new ArrayAdapter<Credential>(context, 0 /* resource */, credentials) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    convertView =
                            inflater.inflate(R.layout.account_chooser_dialog_item, parent, false);
                }
                convertView.setTag(position);

                Credential credential = getItem(position);

                ImageView avatarView = (ImageView) convertView.findViewById(R.id.profile_image);
                Bitmap avatar = credential.getAvatar();
                if (avatar != null) {
                    avatarView.setImageBitmap(avatar);
                } else {
                    avatarView.setImageResource(R.drawable.account_management_no_picture);
                }

                TextView mainNameView = (TextView) convertView.findViewById(R.id.main_name);
                TextView secondaryNameView =
                        (TextView) convertView.findViewById(R.id.secondary_name);
                if (credential.getFederation().isEmpty()) {
                    // Not federated credentials case
                    if (credential.getDisplayName().isEmpty()) {
                        mainNameView.setText(credential.getUsername());
                        secondaryNameView.setVisibility(View.GONE);
                    } else {
                        mainNameView.setText(credential.getDisplayName());
                        secondaryNameView.setText(credential.getUsername());
                        secondaryNameView.setVisibility(View.VISIBLE);
                    }
                } else {
                    mainNameView.setText(credential.getUsername());
                    secondaryNameView.setText(credential.getFederation());
                    secondaryNameView.setVisibility(View.VISIBLE);
                }

                return convertView;
            }
        };
    }

    private void show() {
        View titleView =
                LayoutInflater.from(mContext).inflate(R.layout.account_chooser_dialog_title, null);
        TextView origin = (TextView) titleView.findViewById(R.id.origin);
        origin.setText(mOrigin);
        TextView titleMessageText = (TextView) titleView.findViewById(R.id.title);
        if (mTitleLinkStart != 0 && mTitleLinkEnd != 0) {
            SpannableString spanableTitle = new SpannableString(mTitle);
            spanableTitle.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    nativeOnLinkClicked(mNativeAccountChooserDialog);
                    mDialog.dismiss();
                }
            }, mTitleLinkStart, mTitleLinkEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            titleMessageText.setText(spanableTitle, TextView.BufferType.SPANNABLE);
            titleMessageText.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            titleMessageText.setText(mTitle);
        }
        mAdapter = generateAccountsArrayAdapter(mContext, mCredentials);
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(mContext, R.style.AlertDialogTheme)
                        .setCustomTitle(titleView)
                        .setNegativeButton(R.string.cancel, this)
                        .setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                mCredential = mCredentials[item];
                            }
                        });
        if (!TextUtils.isEmpty(mSigninButtonText)) {
            builder.setPositiveButton(mSigninButtonText, this);
        }
        mDialog = builder.create();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @CalledByNative
    private void imageFetchComplete(int index, Bitmap avatarBitmap) {
        if (mIsDestroyed) return;
        assert index >= 0 && index < mCredentials.length;
        assert mCredentials[index] != null;
        avatarBitmap = AccountManagementFragment.makeRoundUserPicture(avatarBitmap);
        mCredentials[index].setBitmap(avatarBitmap);
        ListView view = mDialog.getListView();
        if (index >= view.getFirstVisiblePosition() && index <= view.getLastVisiblePosition()) {
            // Profile image is in the visible range.
            View credentialView = view.getChildAt(index - view.getFirstVisiblePosition());
            if (credentialView == null) return;
            ImageView avatar = (ImageView) credentialView.findViewById(R.id.profile_image);
            avatar.setImageBitmap(avatarBitmap);
        }
    }

    private void destroy() {
        assert mNativeAccountChooserDialog != 0;
        assert !mIsDestroyed;
        mIsDestroyed = true;
        nativeDestroy(mNativeAccountChooserDialog);
        mNativeAccountChooserDialog = 0;
        mDialog = null;
    }

    @CalledByNative
    private void dismissDialog() {
        assert !mWasDismissedByNative;
        mWasDismissedByNative = true;
        mDialog.dismiss();
    }

    @Override
    public void onClick(DialogInterface dialog, int whichButton) {
        if (whichButton == DialogInterface.BUTTON_POSITIVE) {
            mCredential = mCredentials[0];
            mSigninButtonClicked = true;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (!mWasDismissedByNative) {
            if (mCredential != null) {
                nativeOnCredentialClicked(mNativeAccountChooserDialog, mCredential.getIndex(),
                        mCredential.getType(), mSigninButtonClicked);
            } else {
                nativeCancelDialog(mNativeAccountChooserDialog);
            }
        }
        destroy();
    }

    private native void nativeOnCredentialClicked(long nativeAccountChooserDialogAndroid,
            int credentialId, int credentialType, boolean signinButtonClicked);
    private native void nativeCancelDialog(long nativeAccountChooserDialogAndroid);
    private native void nativeDestroy(long nativeAccountChooserDialogAndroid);
    private native void nativeOnLinkClicked(long nativeAccountChooserDialogAndroid);
}
