// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;

/**
 * Defines the host interface for First Run Experience pages.
 */
public interface FirstRunPageDelegate {
    /**
     * @return A {@link ProfileDataCache} for Android user accounts.
     */
    ProfileDataCache getProfileDataCache();

    /**
     * Advances the First Run Experience to the next page.
     * Successfully finishes FRE if the current page is the last page.
     */
    void advanceToNextPage();

    /**
     * Asks to re-instantiate the current page.
     * Useful to restore the "clean" state of the UI elements.
     */
    void recreateCurrentPage();

    /**
     * Unsuccessfully aborts the First Run Experience.
     * This usually means that the application will be closed.
     */
    void abortFirstRunExperience();

    /**
     * Successfully completes the First Run Experience.
     * All results will be packaged and sent over to the main activity.
     */
    void completeFirstRunExperience();

    /**
     * Notifies that the sign-in dialog is shown.
     */
    void onSigninDialogShown();

    /**
     * Notifies that the user refused to sign in (e.g. "NO, THANKS").
     */
    void refuseSignIn();

    /**
     * Notifies that the user accepted to be signed in.
     * @param accountName An account to be signed in to.
     */
    void acceptSignIn(String accountName);

    /**
     * Notifies that the user asked to show Sync Settings once the sign in
     * process is complete.
     */
    void askToOpenSyncSettings();

    /**
     * @return Whether the user has accepted Chrome Terms of Service.
     */
    boolean didAcceptTermsOfService();

    /**
     * @return Whether the "upload crash dump" setting is set to "NEVER".
     */
    boolean isNeverUploadCrashDump();

    /**
     * Notifies all interested parties that the user has accepted Chrome Terms of Service.
     * @param allowCrashUpload True if the user allows to upload crash dumps and collect stats.
     */
    void acceptTermsOfService(boolean allowCrashUpload);

    /**
     * Opens the Android account adder UI.
     * @param fragment A fragment that needs the account adder UI.
     */
    void openAccountAdder(Fragment fragment);

    /**
     * Show an EmbedContentViewActivity with a given title and a URL.
     * @param title Resource id for the title of the EmbedContentViewActivity.
     * @param url Resource id for the URL of the EmbedContentViewActivity.
     */
    void showEmbedContentViewActivity(int title, int url);
}