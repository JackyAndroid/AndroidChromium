// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Handler for tracking and updating the assist status for a given activity.
 */
public class AssistStatusHandler {
    private final Activity mActivity;
    private final TabModelSelectorObserver mSelectorObserver;

    private Boolean mAssistSupported;
    private TabModelSelector mTabModelSelector;
    private Method mSetAssistBlockedMethod;

    /**
     * Builds an assist status handler for the specified activity.
     * @param activity The activity whose assist status should be updated.
     */
    public AssistStatusHandler(Activity activity) {
        mActivity = activity;

        mSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onChange() {
                updateAssistState();
            }
        };
    }

    /**
     * Set the selector that the assist handler should track for tab updates.
     * @param selector The selector broadcasting tab updates for the activity associated with
     *                 this handler.
     */
    public void setTabModelSelector(TabModelSelector selector) {
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mSelectorObserver);
        }

        mTabModelSelector = selector;
        if (mTabModelSelector != null) {
            mTabModelSelector.addObserver(mSelectorObserver);
        }

        updateAssistState();
    }

    /**
     * Destroy the handler and removes any remaining dependencies.
     */
    public void destroy() {
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mSelectorObserver);
            mTabModelSelector = null;
        }
    }

    /**
     * @return Whether assist is currently supported based on the Activity state.
     */
    public boolean isAssistSupported() {
        return mTabModelSelector == null || !mTabModelSelector.isIncognitoSelected();
    }

    /**
     * Trigger an update of the assist state.
     */
    public final void updateAssistState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        boolean isAssistSupported = isAssistSupported();
        if (mAssistSupported == null || mAssistSupported != isAssistSupported) {
            if (mSetAssistBlockedMethod == null) {
                try {
                    mSetAssistBlockedMethod =
                            View.class.getMethod("setAssistBlocked", boolean.class);
                } catch (NoSuchMethodException e) {
                    return;
                }
            }
            View rootContent = mActivity.findViewById(android.R.id.content);
            try {
                mSetAssistBlockedMethod.invoke(rootContent, !isAssistSupported);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                return;
            }
        }
        mAssistSupported = isAssistSupported;
    }
}