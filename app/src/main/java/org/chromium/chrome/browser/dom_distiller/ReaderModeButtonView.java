// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.banners.SwipableOverlayView;
import org.chromium.content.browser.ContentViewCore;

/**
 * Reader Mode "infobar"-style button at the bottom of the screen.
 */
public class ReaderModeButtonView extends SwipableOverlayView {
    private ReaderModeButtonViewDelegate mReaderModeButtonViewDelegate;

    /**
     * Called when the user swipes the button away or clicks it.
     */
    public interface ReaderModeButtonViewDelegate {
        /* Called when the user clicks on the button. */
        void onClick();
        /* Called when the user swipes-away the button. */
        void onSwipeAway();
    }

    /**
     * Creates a ReaderModeButtonView and adds it to the given ContentViewCore.
     *
     * @param contentViewCore    ContentViewCore for the ReaderModeButtonView.
     * @param buttonViewDelegate A delegate for onClick/onDismiss events.
     */
    public static ReaderModeButtonView create(ContentViewCore contentViewCore,
                                              ReaderModeButtonViewDelegate buttonViewDelegate) {
        if (contentViewCore == null) return null;
        if (contentViewCore.getWebContents() == null) return null;
        Context context = contentViewCore.getContext();
        if (context == null) return null;

        ReaderModeButtonView view =
                (ReaderModeButtonView) LayoutInflater.from(context)
                        .inflate(R.layout.reader_mode_view, null);
        view.initialize(buttonViewDelegate);
        view.setContentViewCore(contentViewCore);
        view.addToParentView(contentViewCore.getContainerView());
        return view;
    }

    /**
     * Creates a ReaderModeButtonView.
     *
     * @param context Context for acquiring resources.
     * @param attrs   Attributes from the XML layout inflation.
     */
    public ReaderModeButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void initialize(ReaderModeButtonViewDelegate buttonViewDelegate) {
        mReaderModeButtonViewDelegate = buttonViewDelegate;
        findViewById(R.id.main_close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mReaderModeButtonViewDelegate.onSwipeAway();
            }
        });
        this.setClickable(true);
    }

    @Override
    protected void onViewClicked() {
        mReaderModeButtonViewDelegate.onClick();
    }

    @Override
    protected void onViewPressed(MotionEvent event) {
    }

    @Override
    protected void onViewSwipedAway() {
        mReaderModeButtonViewDelegate.onSwipeAway();
    }
}
