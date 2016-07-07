// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.password_manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
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
        extends DialogFragment implements DialogInterface.OnClickListener {
    private  Context mContext;
    private  Credential[] mCredentials;
    private  ImageView[] mAvatarViews;

    /**
     * Title of the dialog, contains Smart Lock branding for the Smart Lock users.
     */
    private  String mTitle;
    private  int mTitleLinkStart;
    private  int mTitleLinkEnd;
    private ArrayAdapter<Credential> mAdapter;

    /**
     * Holds the reference to the credentials which were chosen by the user.
     */
    private Credential mCredential;
    private long mNativeAccountChooserDialog;
    private AlertDialog mDialog;

    public AccountChooserDialog() {
    }

    private AccountChooserDialog(Context context, long nativeAccountChooserDialog,
            Credential[] credentials, String title, int titleLinkStart, int titleLinkEnd) {
        mNativeAccountChooserDialog = nativeAccountChooserDialog;
        mContext = context;
        mCredentials = credentials.clone();
        mAvatarViews = new ImageView[mCredentials.length];
        mTitle = title;
        mTitleLinkStart = titleLinkStart;
        mTitleLinkEnd = titleLinkEnd;
    }

    /**
     *  Creates and shows the dialog which allows user to choose credentials for login.
     *  @param credentials Credentials to display in the dialog.
     *  @param title Title message for the dialog, which can contain Smart Lock branding.
     *  @param titleLinkStart Start of a link in case title contains Smart Lock branding.
     *  @param titleLinkEnd End of a link in case title contains Smart Lock branding.
     */
    @CalledByNative
    private static AccountChooserDialog createAccountChooser(WindowAndroid windowAndroid,
            long nativeAccountChooserDialog, Credential[] credentials, String title,
            int titleLinkStart, int titleLinkEnd) {
        Activity activity = windowAndroid.getActivity().get();
        if (activity == null) return null;
        AccountChooserDialog chooser = new AccountChooserDialog(activity,
                nativeAccountChooserDialog, credentials, title, titleLinkStart, titleLinkEnd);
        chooser.show(activity.getFragmentManager(), null);
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
                } else {
                    int oldPosition = (int) convertView.getTag();
                    mAvatarViews[oldPosition] = null;
                }
                convertView.setTag(position);

                Credential credential = getItem(position);

                ImageView avatarView = (ImageView) convertView.findViewById(R.id.profile_image);
                mAvatarViews[position] = avatarView;
                Bitmap avatar = credential.getAvatar();
                if (avatar != null) {
                    avatarView.setImageBitmap(avatar);
                } else {
                    avatarView.setImageResource(R.drawable.account_management_no_picture);
                }

                TextView usernameView = (TextView) convertView.findViewById(R.id.username);
                usernameView.setText(credential.getUsername());

                TextView smallTextView = (TextView) convertView.findViewById(R.id.display_name);
                String smallText = credential.getFederation().isEmpty()
                        ? credential.getDisplayName()
                        : credential.getFederation();
                smallTextView.setText(smallText);

                return convertView;
            }
        };
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View titleView =
                LayoutInflater.from(mContext).inflate(R.layout.account_chooser_dialog_title, null);
        TextView titleMessageText = (TextView) titleView.findViewById(R.id.title);
        // TODO(melandory): add support for showing site origin in the title.
        if (mTitleLinkStart != 0 && mTitleLinkEnd != 0) {
            SpannableString spanableTitle = new SpannableString(mTitle);
            spanableTitle.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    nativeOnLinkClicked(mNativeAccountChooserDialog);
                }
            }, mTitleLinkStart, mTitleLinkEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            titleMessageText.setText(spanableTitle, TextView.BufferType.SPANNABLE);
            titleMessageText.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            titleMessageText.setText(mTitle);
        }
        mAdapter = generateAccountsArrayAdapter(mContext, mCredentials);
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setCustomTitle(titleView)
                .setNegativeButton(R.string.no_thanks, this)
                .setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        mCredential = mCredentials[item];
                    }
                });
        mDialog = builder.create();
        return mDialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) dismiss();
    }

    @Override
    public void onClick(DialogInterface dialog, int whichButton) {}

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mCredential != null) {
            nativeOnCredentialClicked(
                    mNativeAccountChooserDialog, mCredential.getIndex(), mCredential.getType());
        } else {
            nativeCancelDialog(mNativeAccountChooserDialog);
        }
        destroy();
        mDialog = null;
    }

    @CalledByNative
    private void imageFetchComplete(int index, Bitmap avatarBitmap) {
        avatarBitmap = AccountManagementFragment.makeRoundUserPicture(avatarBitmap);
        if (index >= 0 && index < mCredentials.length && mCredentials[index] != null) {
            mCredentials[index].setBitmap(avatarBitmap);
        }
        if (index >= 0 && index < mAvatarViews.length && mAvatarViews[index] != null) {
            mAvatarViews[index].setImageBitmap(avatarBitmap);
        }
    }

    private void destroy() {
        assert mNativeAccountChooserDialog != 0;
        nativeDestroy(mNativeAccountChooserDialog);
        mNativeAccountChooserDialog = 0;
    }

    private native void nativeOnCredentialClicked(
            long nativeAccountChooserDialogAndroid, int credentialId, int credentialType);
    private native void nativeCancelDialog(long nativeAccountChooserDialogAndroid);
    private native void nativeDestroy(long nativeAccountChooserDialogAndroid);
    private native void nativeOnLinkClicked(long nativeAccountChooserDialogAndroid);
}
