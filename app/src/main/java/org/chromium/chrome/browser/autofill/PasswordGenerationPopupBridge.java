// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.PopupWindow;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.DropdownPopupWindow;
import org.chromium.ui.R;
import org.chromium.ui.base.WindowAndroid;

/**
 * JNI call glue for password generation between native and Java objects.
 */
@JNINamespace("autofill")
public class PasswordGenerationPopupBridge implements AdapterView.OnItemClickListener,
        PopupWindow.OnDismissListener, PasswordGenerationAdapter.Delegate {
    private final long mNativePasswordGenerationPopupViewAndroid;
    private final Context mContext;
    private final DropdownPopupWindow mPopup;
    private final View mAnchorView;
    /**
     * A convenience method for the constructor to be invoked from the native counterpart.
     * @param anchorView View anchored for popup.
     * @param nativePopup The pointer to the native counterpart.
     * @param windowAndroid The browser window.
     */
    @CalledByNative
    private static PasswordGenerationPopupBridge create(View anchorView, long nativePopup,
            WindowAndroid windowAndroid) {
        return new PasswordGenerationPopupBridge(anchorView, nativePopup, windowAndroid);
    }

    /**
     * Builds the bridge between native and Java objects.
     * @param anchorView View anchored for popup.
     * @param nativePopup The pointer to the native counterpart.
     * @param windowAndroid The browser window.
     */
    public PasswordGenerationPopupBridge(View anchorView, long nativePopup,
            WindowAndroid windowAndroid) {
        mNativePasswordGenerationPopupViewAndroid = nativePopup;
        mContext = windowAndroid.getActivity().get();
        mAnchorView = anchorView;
        // mContext could've been garbage collected.
        if (mContext == null) {
            mPopup = null;
            // Prevent destroying the native counterpart when it's about to derefence its own
            // members in UpdateBoundsAndRedrawPopup().
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    onDismiss();
                }
            });
        } else {
            mPopup = new DropdownPopupWindow(mContext, anchorView);
            mPopup.setOnItemClickListener(this);
            mPopup.setOnDismissListener(this);
            mPopup.disableHideOnOutsideTap();
            mPopup.setContentDescriptionForAccessibility(
                    mContext.getString(R.string.password_generation_popup_content_description));
        }
    }

    /**
     * Handles clicks on popup list elements. Only suggestion element is enabled. Clicking on the
     * suggestion element notifies the native counterpart that the suggested password was selected.
     * @param parent The parent view where the click happened.
     * @param view The view that was provided by the adapter that was clicked.
     * @param position The position of the view in the adapter.
     * @param id The row id of the clicked element.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        nativePasswordSelected(mNativePasswordGenerationPopupViewAndroid);
    }

    private native void nativePasswordSelected(long nativePasswordGenerationPopupViewAndroid);

    /**
     * Handles dismissing the popup window. The native counterpart is notified to destroy the
     * controller.
     */
    @Override
    public void onDismiss() {
        nativeDismissed(mNativePasswordGenerationPopupViewAndroid);
    }

    private native void nativeDismissed(long nativePasswordGenerationPopupViewAndroid);

    /**
     * Shows a password generation popup with specified data. Should be called after
     * setAnchorRect().
     * @param isRtl True if the popup should be RTL.
     * @param passwordDisplayed Whether the generated password should be displayed.
     * @param password The auto-generated password to suggest.
     * @param suggestionTitle The translated title of the suggestion part of the popup.
     * @param explanationText The translated text that explains the popup.
     * @param explanationTextLinkRangeStart The start of the range in the explanation text that
     * should be a link to the saved passwords.
     * @param explanationTextLinkRangeEnd The end of the range in the explanation text that should
     * be a link to the saved passwords.
     */
    @CalledByNative
    private void show(boolean isRtl, boolean passwordDisplayed, String password,
            String suggestionTitle, String explanationText, int explanationTextLinkRangeStart,
            int explanationTextLinkRangeEnd) {
        if (mPopup != null) {
            float anchorWidth = mAnchorView.getLayoutParams().width;
            assert anchorWidth > 0;
            PasswordGenerationAdapter adapter = new PasswordGenerationAdapter(mContext, this,
                    passwordDisplayed, password, suggestionTitle, explanationText,
                    explanationTextLinkRangeStart, explanationTextLinkRangeEnd, anchorWidth);
            mPopup.setAdapter(adapter);
            mPopup.setRtl(isRtl);
            mPopup.show();
        }
    }

    /**
     * Called from adapter when the "saved passwords" link is clicked.
     */
    @Override
    public void onSavedPasswordsLinkClicked() {
        nativeSavedPasswordsLinkClicked(mNativePasswordGenerationPopupViewAndroid);
    }

    private native void nativeSavedPasswordsLinkClicked(
            long nativePasswordGenerationPopupViewAndroid);

    /**
     * Hides the password generation popup.
     */
    @CalledByNative
    private void hide() {
        if (mPopup != null) mPopup.dismiss();
    }
}
