// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ScrollView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.FadingShadow;

/**
 * Simple wrapper on top of a ScrollView that will acquire focus when tapped.  Ensures the
 * New Tab page receives focus when clicked.
 */
public class NewTabScrollView extends ScrollView {

    private static final String TAG = "NewTabScrollView";

    /**
     * Listener for scroll changes.
     */
    public interface OnScrollListener {
        /**
         * Triggered when the scroll changes.  See ScrollView#onScrollChanged for more
         * details.
         */
        void onScrollChanged(int l, int t, int oldl, int oldt);
    }

    private GestureDetector mGestureDetector;
    private OnScrollListener mOnScrollListener;

    private NewTabPageLayout mNewTabPageLayout;

    private FadingShadow mFadingShadow;

    /**
     * Constructor needed to inflate from XML.
     */
    public NewTabScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mGestureDetector = new GestureDetector(
                getContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        boolean retVal = super.onSingleTapUp(e);
                        requestFocus();
                        return retVal;
                    }
                });
    }

    /**
     * Enables drawing a shadow at the bottom of the view when the view's content extends beyond
     * the bottom of the view. This is exactly the same as a fading edge, except that the shadow
     * color can have an alpha component, whereas a fading edge color must be opaque.
     *
     * @param shadowColor The color of the shadow, e.g. 0x11000000.
     */
    public void enableBottomShadow(int shadowColor) {
        mFadingShadow = new FadingShadow(shadowColor);
        setFadingEdgeLength(getResources().getDimensionPixelSize(R.dimen.ntp_shadow_height));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View child = getChildAt(0);
        if (child instanceof NewTabPageLayout) mNewTabPageLayout = (NewTabPageLayout) child;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mNewTabPageLayout != null) {
            mNewTabPageLayout.setParentScrollViewportHeight(
                    MeasureSpec.getSize(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Action down would already have been handled in onInterceptTouchEvent
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            mGestureDetector.onTouchEvent(ev);
        }
        try {
            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException ex) {
            // In JB MR0 and earlier, an ACTION_MOVE that is not preceded by an ACTION_DOWN event
            // causes a crash. This can happen under normal circumstances (e.g. going back to the
            // NTP while a finger is down on the screen) and should not crash. The most reliable way
            // to prevent this crash is to catch the exception. http://crbug.com/293822
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                    && ev.getActionMasked() == MotionEvent.ACTION_MOVE
                    && "pointerIndex out of range".equals(ex.getMessage())) {
                Log.d(TAG, "Ignoring pointerIndex out of range exception.");
                return true;
            }
            throw ex;
        }
    }

    /**
     * Sets the listener to be notified of scroll changes.
     * @param listener The listener to be updated on scroll changes.
     */
    public void setOnScrollListener(OnScrollListener listener) {
        mOnScrollListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollListener != null) mOnScrollListener.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    public void focusableViewAvailable(View v) {
        // To avoid odd jumps during NTP animation transitions, we do not attempt to give focus
        // to child views if this scroll view already has focus.
        if (hasFocus()) return;
        super.focusableViewAvailable(v);
    }

    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        // Ignore all key events except arrow keys and spacebar. Otherwise, the ScrollView consumes
        // unwanted events (including the hardware menu button and app-level keyboard shortcuts).
        // http://crbug.com/308322
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_SPACE:
                return super.executeKeyEvent(event);
            default:
                return false;
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Fixes lanscape transitions when unfocusing the URL bar: crbug.com/288546
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return super.onCreateInputConnection(outAttrs);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mFadingShadow != null) {
            setVerticalFadingEdgeEnabled(true);
            float shadowStrength = getBottomFadingEdgeStrength();
            float shadowHeight = getVerticalFadingEdgeLength();
            setVerticalFadingEdgeEnabled(false);
            mFadingShadow.drawShadow(this, canvas, FadingShadow.POSITION_BOTTOM,
                    shadowHeight, shadowStrength);
        }
    }
}
