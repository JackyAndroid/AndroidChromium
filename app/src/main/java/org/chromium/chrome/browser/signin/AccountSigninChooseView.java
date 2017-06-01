// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;

import java.util.List;

/**
* The view that allows the user to choose the sign in account.
*/
public class AccountSigninChooseView extends ScrollView {
    private final LayoutInflater mInflater;
    private LinearLayout mRootChildView;
    private int mAccountViewStartIndex;
    private int mSelectedAccountPosition;
    private Observer mObserver;

    /**
    * Add new account observer.
    */
    public interface Observer {
        /**
        * On add new account clicked.
        */
        void onAddNewAccount();
    }

    public AccountSigninChooseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRootChildView =
                (LinearLayout) findViewById(R.id.account_signin_choose_view_root_child_view);
        mAccountViewStartIndex = mRootChildView.getChildCount();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width and layout_height are set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        assert MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        View title = findViewById(R.id.signin_title);
        ViewGroup.LayoutParams params = title.getLayoutParams();
        if (height > width) {
            // Sets the title aspect ratio to be 16:9.
            params.height = width * 9 / 16;
            title.setPadding(
                    title.getPaddingLeft(), 0, title.getPaddingRight(), title.getPaddingBottom());
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;

            // Adds top padding.
            title.setPadding(title.getPaddingLeft(),
                    getResources().getDimensionPixelOffset(R.dimen.signin_screen_top_padding),
                    title.getPaddingRight(), title.getPaddingBottom());
        }
        title.setLayoutParams(params);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        // Disable fading out effect at the top of this ScrollView.
        return 0;
    }

    /**
    * Updates candidate accounts to sign in.
    *
    * @param accounts The candidate accounts.
    * @param accountToSelect The index of the default selected account to sign in.
    * @param profileData The ProfileDataCache contains accounts' info.
    */
    public void updateAccounts(
            List<String> accounts, int accountToSelect, ProfileDataCache profileData) {
        mRootChildView.removeViews(
                mAccountViewStartIndex, mRootChildView.getChildCount() - mAccountViewStartIndex);
        if (accounts.isEmpty()) return;

        // Add accounts view.
        for (int i = 0; i < accounts.size(); i++) {
            View view =
                    mInflater.inflate(R.layout.account_signin_account_view, mRootChildView, false);

            // Sets account profile image and name.
            String accountName = accounts.get(i);
            ((ImageView) view.findViewById(R.id.account_image))
                    .setImageBitmap(profileData.getImage(accountName));
            ((TextView) view.findViewById(R.id.account_name)).setText(accountName);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int indexOfClickedAccount =
                            mRootChildView.indexOfChild(v) - mAccountViewStartIndex;
                    if (indexOfClickedAccount == mSelectedAccountPosition) return;
                    mRootChildView.getChildAt(mSelectedAccountPosition + mAccountViewStartIndex)
                            .findViewById(R.id.account_selection_mark)
                            .setVisibility(View.GONE);
                    v.findViewById(R.id.account_selection_mark).setVisibility(View.VISIBLE);
                    mSelectedAccountPosition = indexOfClickedAccount;
                }
            });

            mRootChildView.addView(view);
        }

        // The view at the last position is the "Add account" view.
        View view = mInflater.inflate(R.layout.account_signin_account_view, mRootChildView, false);
        ((ImageView) view.findViewById(R.id.account_image))
                .setImageResource(R.drawable.add_circle_blue);
        ((TextView) view.findViewById(R.id.account_name))
                .setText(getResources().getString(R.string.signin_add_account));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mObserver != null) mObserver.onAddNewAccount();
            }
        });
        mRootChildView.addView(view);

        // Sets the default selected account selection status.
        mRootChildView.getChildAt(accountToSelect + mAccountViewStartIndex)
                .findViewById(R.id.account_selection_mark)
                .setVisibility(View.VISIBLE);
        mSelectedAccountPosition = accountToSelect;
    }

    /**
    * Updates candidate accounts' profile image.
    *
    * @param profileData The ProfileDataCache contains accounts' profile image.
    */
    public void updateAccountProfileImages(ProfileDataCache profileData) {
        // Do not update the last "Add account" view.
        for (int i = mAccountViewStartIndex; i < mRootChildView.getChildCount() - 1; i++) {
            View view = mRootChildView.getChildAt(i);
            String accountEmail =
                    ((TextView) view.findViewById(R.id.account_name)).getText().toString();
            ((ImageView) view.findViewById(R.id.account_image))
                    .setImageBitmap(profileData.getImage(accountEmail));
        }
    }

    /**
    * Sets add new account observer. See {@link Observer}
    *
    * @param observer The observer.
    */
    public void setAddNewAccountObserver(Observer observer) {
        mObserver = observer;
    }

    /**
     * Gets selected account position.
     */
    public int getSelectedAccountPosition() {
        return mSelectedAccountPosition;
    }
}
