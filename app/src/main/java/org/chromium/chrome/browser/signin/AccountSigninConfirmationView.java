// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import org.chromium.chrome.R;

/**
* This view allows the user to confirm signed in account, sync, and service personalization.
*/
public class AccountSigninConfirmationView extends ScrollView {
    private Observer mObserver;
    private boolean mScrolledToBottom = false;

    /**
    * Scrolled to bottom observer.
    */
    public interface Observer {
        /**
        * On scrolled to bottom. This is called only once when showing the view.
        */
        void onScrolledToBottom();
    }

    public AccountSigninConfirmationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width and layout_height are set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        assert MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        View head = findViewById(R.id.signin_confirmation_head);
        RelativeLayout.LayoutParams headLayoutParams =
                (RelativeLayout.LayoutParams) head.getLayoutParams();
        View accountImage = findViewById(R.id.signin_account_image);
        LinearLayout.LayoutParams accountImageLayoutParams =
                (LinearLayout.LayoutParams) accountImage.getLayoutParams();
        if (height > width) {
            // Sets aspect ratio of the head to 16:9.
            headLayoutParams.height = width * 9 / 16;
            accountImageLayoutParams.topMargin = 0;
        } else {
            headLayoutParams.height = LayoutParams.WRAP_CONTENT;

            // Adds top margin.
            accountImageLayoutParams.topMargin =
                    getResources().getDimensionPixelOffset(R.dimen.signin_screen_top_padding);
        }
        head.setLayoutParams(headLayoutParams);
        accountImage.setLayoutParams(accountImageLayoutParams);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        // Disable fading out effect at the top of this ScrollView.
        return 0;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        notifyIfScrolledToBottom(true);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        notifyIfScrolledToBottom(false);
    }

    /**
     * Sets scrolled to bottom observer. See {@link Observer}
     *
     * @param observer The observer.
     */
    public void setScrolledToBottomObserver(Observer observer) {
        mObserver = observer;
    }

    private void notifyIfScrolledToBottom(boolean forceNotify) {
        if (mObserver == null) return;

        if (!forceNotify && mScrolledToBottom) return;

        int distance = (getChildAt(getChildCount() - 1).getBottom() - (getHeight() + getScrollY()));
        if (distance <= findViewById(R.id.signin_settings_control).getPaddingBottom()) {
            mObserver.onScrolledToBottom();
            mScrolledToBottom = true;
        } else {
            mScrolledToBottom = false;
        }
    }
}
