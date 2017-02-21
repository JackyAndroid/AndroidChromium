// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IntDef;
import android.view.View;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.ui.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Exposes general configuration info about the NTP UI.
 */
public class UiConfig {
    /** The different supported UI setups. Observers can register to be notified of changes.*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DISPLAY_STYLE_UNDEFINED, DISPLAY_STYLE_NARROW, DISPLAY_STYLE_REGULAR,
            DISPLAY_STYLE_WIDE})
    public @interface DisplayStyle {}
    public static final int DISPLAY_STYLE_UNDEFINED = -1;
    public static final int DISPLAY_STYLE_NARROW = 0;
    public static final int DISPLAY_STYLE_REGULAR = 1;
    public static final int DISPLAY_STYLE_WIDE = 2;

    private static final int REGULAR_CARD_MIN_WIDTH_DP = 360;
    private static final int WIDE_CARD_MIN_WIDTH_DP = 600;

    private static final String TAG = "Ntp";
    private static final boolean DEBUG = false;

    @DisplayStyle
    private int mCurrentDisplayStyle;

    private final List<DisplayStyleObserver> mObservers = new ArrayList<>();
    private final Context mContext;

    /**
     * @param referenceView the View we observe to deduce the configuration from.
     */
    public UiConfig(View referenceView) {
        mContext = referenceView.getContext();
        mCurrentDisplayStyle = computeDisplayStyleForCurrentConfig();

        referenceView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                updateDisplayStyle();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {}
        });
    }

    /**
     * Registers a {@link DisplayStyleObserver}. It will be notified right away with the current
     * display style.
     */
    public void addObserver(DisplayStyleObserver observer) {
        mObservers.add(observer);
        observer.onDisplayStyleChanged(mCurrentDisplayStyle);
    }

    /**
     * Refresh the display style, notify observers of changes.
     */
    public void updateDisplayStyle() {
        updateDisplayStyle(computeDisplayStyleForCurrentConfig());
    }

    /**
     * Sets the display style, notifying observers of changes. Should only be used in testing.
     */
    @VisibleForTesting
    public void setDisplayStyleForTesting(@DisplayStyle int displayStyle) {
        updateDisplayStyle(displayStyle);
    }

    private void updateDisplayStyle(@DisplayStyle int displayStyle) {
        if (displayStyle == mCurrentDisplayStyle) return;

        mCurrentDisplayStyle = displayStyle;
        for (DisplayStyleObserver observer : mObservers) {
            observer.onDisplayStyleChanged(displayStyle);
        }
    }

    /**
     * Returns the currently used display style.
     */
    @DisplayStyle
    public int getCurrentDisplayStyle() {
        return mCurrentDisplayStyle;
    }

    @DisplayStyle
    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private int computeDisplayStyleForCurrentConfig() {
        int widthDp = mContext.getResources().getConfiguration().screenWidthDp;

        String debugString;

        @DisplayStyle
        int newDisplayStyle;
        if (widthDp < REGULAR_CARD_MIN_WIDTH_DP) {
            newDisplayStyle = DISPLAY_STYLE_NARROW;
            if (DEBUG) debugString = String.format("DISPLAY_STYLE_NARROW (w=%ddp)", widthDp);
        } else if (widthDp >= WIDE_CARD_MIN_WIDTH_DP) {
            newDisplayStyle = DISPLAY_STYLE_WIDE;
            if (DEBUG) debugString = String.format("DISPLAY_STYLE_WIDE (w=%ddp)", widthDp);
        } else {
            newDisplayStyle = DISPLAY_STYLE_REGULAR;
            if (DEBUG) debugString = String.format("DISPLAY_STYLE_REGULAR (w=%ddp)", widthDp);
        }

        if (DEBUG) {
            Log.d(TAG, debugString);
            Toast.makeText(mContext, debugString, Toast.LENGTH_SHORT).show();
        }

        return newDisplayStyle;
    }
}
