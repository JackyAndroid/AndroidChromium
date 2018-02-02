// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import org.chromium.content_public.browser.WebContentsObserver;

/**
 * This class tracks the per-tab state of reader mode.
 */
public class ReaderModeTabInfo {
    // The WebContentsObserver responsible for updates to the distillation status of the tab.
    private WebContentsObserver mWebContentsObserver;

    // The distillation status of the tab.
    private int mStatus;

    // If the panel was closed due to the close button.
    private boolean mIsDismissed;

    // The URL that distiller is using for this tab. This is used to check if a result comes
    // back from distiller and the user has already loaded a new URL.
    private String mCurrentUrl;

    // The distillability heuristics now use a callback to notify the manager that a page can
    // be distilled. This flag is used to detect if the callback is set for this tab.
    private boolean mIsCallbackSet;

    // Used to flag the the panel was shown and recorded by UMA.
    private boolean mShowPanelRecorded;

    /**
     * @param observer The WebContentsObserver for the tab this object represents.
     */
    public void setWebContentsObserver(WebContentsObserver observer) {
        mWebContentsObserver = observer;
    }

    /**
     * @return The WebContentsObserver for the tab this object represents.
     */
    public WebContentsObserver getWebContentsObserver() {
        return mWebContentsObserver;
    }

    /**
     * @param status The status of reader mode for this object's tab.
     */
    public void setStatus(int status) {
        mStatus = status;
    }

    /**
     * @return The reader mode status for this object's tab.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * @return If the panel has been dismissed for this object's tab.
     */
    public boolean isDismissed() {
        return mIsDismissed;
    }

    /**
     * @param dismissed Set the panel as dismissed for this object's tab.
     */
    public void setIsDismissed(boolean dismissed) {
        mIsDismissed = dismissed;
    }

    /**
     * @param url The URL being processed by reader mode.
     */
    public void setUrl(String url) {
        mCurrentUrl = url;
    }

    /**
     * @return The last URL being processed by reader mode.
     */
    public String getUrl() {
        return mCurrentUrl;
    }

    /**
     * @return If the distillability callback is set for this object's tab.
     */
    public boolean isCallbackSet() {
        return mIsCallbackSet;
    }

    /**
     * @param isSet Set if this object's tab has a distillability callback.
     */
    public void setIsCallbackSet(boolean isSet) {
        mIsCallbackSet = isSet;
    }

    /**
     * @return If the call to show the panel was recorded.
     */
    public boolean isPanelShowRecorded() {
        return mShowPanelRecorded;
    }

    /**
     * @param isRecorded True if the action has been recorded.
     */
    public void setIsPanelShowRecorded(boolean isRecorded) {
        mShowPanelRecorded = isRecorded;
    }

}

