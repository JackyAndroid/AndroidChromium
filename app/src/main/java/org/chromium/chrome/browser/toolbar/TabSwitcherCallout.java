// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TextBubble;

/**
 * Draws a bubble pointing upward at the tab switcher button.
 */
public class TabSwitcherCallout extends TextBubble {
    public static final String PREF_NEED_TO_SHOW_TAB_SWITCHER_CALLOUT =
            "org.chromium.chrome.browser.toolbar.NEED_TO_SHOW_TAB_SWITCHER_CALLOUT";

    private static final int TAB_SWITCHER_CALLOUT_DISMISS_MS = 10000;
    private static final float Y_OVERLAP_PERCENTAGE = 0.33f;

    private final Handler mHandler;
    private final Runnable mDismissRunnable;

    /**
     * Show the TabSwitcherCallout, if necessary.
     * @param context           Context to draw resources from.
     * @param tabSwitcherButton Button that triggers the tab switcher.
     * @return TabSwitcherCallout if one was shown, null otherwise.
     */
    public static TabSwitcherCallout showIfNecessary(Context context, View tabSwitcherButton) {
        if (!isTabSwitcherCalloutNecessary(context)) return null;
        setIsTabSwitcherCalloutNecessary(context, false);

        final TabSwitcherCallout callout = new TabSwitcherCallout(context);
        callout.show(tabSwitcherButton);
        return callout;
    }

    @Override
    protected View createContent(Context context) {
        View content = LayoutInflater.from(context).inflate(R.layout.tab_switcher_callout, null);

        // Dismiss the popup when the "OK" button is clicked.
        View okButton = content.findViewById(R.id.confirm_button);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return content;
    }

    /** @return Whether or not the tab switcher button callout needs to be shown. */
    public static boolean isTabSwitcherCalloutNecessary(Context context) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        return prefs.getBoolean(PREF_NEED_TO_SHOW_TAB_SWITCHER_CALLOUT, false);
    }

    /**
     * Sets whether the tab switcher callout should be shown when the browser starts up.
     */
    public static void setIsTabSwitcherCalloutNecessary(Context context, boolean shouldShow) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        prefs.edit().putBoolean(PREF_NEED_TO_SHOW_TAB_SWITCHER_CALLOUT, shouldShow).apply();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private TabSwitcherCallout(Context context) {
        super(context, Y_OVERLAP_PERCENTAGE);
        setAnimationStyle(R.style.TabSwitcherCalloutAnimation);

        // Dismiss the popup automatically after a delay.
        mDismissRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing()) dismiss();
            }
        };
        mHandler = new Handler();
    }

    @Override
    public void show(View anchorView) {
        super.show(anchorView);
        mHandler.postDelayed(mDismissRunnable, TAB_SWITCHER_CALLOUT_DISMISS_MS);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mHandler.removeCallbacks(mDismissRunnable);
    }
}