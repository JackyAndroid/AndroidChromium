// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.app.Activity;
import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * Manages the add-to-homescreen process.
 */
public class AddToHomescreenManager {

    /** Interface for tracking fetch of add-to-homescreen data. */
    public interface Observer {
        /** Called when the title of the page is available. */
        void onUserTitleAvailable(String title);

        /**
         * Called once native has finished fetching the homescreen shortcut's data (like the Web
         * Manifest) and is ready for {@link AddToHomescreenManager#addShortcut} to be called.
         * @param icon Icon to use in the launcher.
         */
        void onReadyToAdd(Bitmap icon);
    }

    private final Activity mActivity;
    private final Tab mTab;

    private Observer mObserver;
    private long mNativeAddToHomescreenManager;

    public AddToHomescreenManager(Activity activity, Tab tab) {
        mActivity = activity;
        mTab = tab;
    }

    /** Starts add-to-homescreen process. */
    public void start() {
        mNativeAddToHomescreenManager = nativeInitializeAndStart(mTab.getWebContents());
    }

    /** Sets the add-to-homescreen observer tracking the add-to-homescreen data fetch. */
    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    /**
     * Puts the object in a state where it is safe to be destroyed.
     */
    public void destroy() {
        nativeDestroy(mNativeAddToHomescreenManager);

        mObserver = null;
        mNativeAddToHomescreenManager = 0;
    }

    /**
     * Adds a shortcut for the current Tab.
     * @param userRequestedTitle Title of the shortcut displayed on the homescreen.
     */
    public void addShortcut(String userRequestedTitle) {
        nativeAddShortcut(mNativeAddToHomescreenManager, userRequestedTitle);
    }

    public void onDismissed() {
        destroy();
    }

    /**
     * Shows alert to prompt user for name of home screen shortcut.
     */
    @CalledByNative
    public void showDialog() {
        AddToHomescreenDialog dialog = new AddToHomescreenDialog(this);
        dialog.show(mActivity);
        setObserver(dialog);
    }

    @CalledByNative
    private void onUserTitleAvailable(String title) {
        mObserver.onUserTitleAvailable(title);
    }

    @CalledByNative
    private void onReadyToAdd(Bitmap icon) {
        mObserver.onReadyToAdd(icon);
    }

    private native long nativeInitializeAndStart(WebContents webContents);
    private native void nativeAddShortcut(
            long nativeAddToHomescreenManager, String userRequestedTitle);
    private native void nativeDestroy(long nativeAddToHomescreenManager);
}
