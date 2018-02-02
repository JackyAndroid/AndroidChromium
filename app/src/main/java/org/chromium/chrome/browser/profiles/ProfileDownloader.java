// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.profiles;

import android.content.Context;
import android.graphics.Bitmap;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.signin.AccountTrackerService;

import java.util.ArrayList;

/**
 * Android wrapper of the ProfileDownloader which provides access from the Java layer.
 * The native ProfileDownloader requires its access to be in the UI thread.
 * See chrome/browser/profiles/profile_downloader.h/cc for more details.
 */
public class ProfileDownloader {
    private static final ObserverList<Observer> sObservers = new ObserverList<Observer>();

    /**
     * Interface for receiving notifications on account information updates.
     */
    public interface Observer {
        /**
         * Notifies that an account data in the profile has been updated.
         * @param accountId An account ID.
         * @param fullName A full name.
         * @param givenName A given name.
         * @param bitmap A user picture.
         */
        void onProfileDownloaded(String accountId, String fullName, String givenName,
                Bitmap bitmap);
    }

    /**
     * Add an observer.
     * @param observer An observer.
     */
    public static void addObserver(Observer observer) {
        sObservers.addObserver(observer);
    }

    /**
     * Remove an observer.
     * @param observer An observer.
     */
    public static void removeObserver(Observer observer) {
        sObservers.removeObserver(observer);
    }

    /**
    * Private class to pend profile download requests when system accounts have not been seeded into
    * AccountTrackerService. It listens onSystemAccountsSeedingComplete to finish pending requests
    * and onSystemAccountsChanged to clear outdated pending requests.
    */
    private static class PendingProfileDownloads
            implements AccountTrackerService.OnSystemAccountsSeededListener {
        private static PendingProfileDownloads sPendingProfileDownloads;

        private final ArrayList<Profile> mProfiles;
        private final ArrayList<String> mAccountIds;
        private final ArrayList<Integer> mImageSidePixels;

        private PendingProfileDownloads() {
            mProfiles = new ArrayList<>();
            mAccountIds = new ArrayList<>();
            mImageSidePixels = new ArrayList<>();
        }

        @SuppressFBWarnings("LI_LAZY_INIT_UPDATE_STATIC")
        public static PendingProfileDownloads get(Context context) {
            ThreadUtils.assertOnUiThread();
            if (sPendingProfileDownloads == null) {
                sPendingProfileDownloads = new PendingProfileDownloads();
                AccountTrackerService.get(context).addSystemAccountsSeededListener(
                        sPendingProfileDownloads);
            }
            return sPendingProfileDownloads;
        }

        public void pendProfileDownload(Profile profile, String accountId, int imageSidePixels) {
            mProfiles.add(profile);
            mAccountIds.add(accountId);
            mImageSidePixels.add(imageSidePixels);
        }

        @Override
        public void onSystemAccountsSeedingComplete() {
            int numberOfPendingRequests = mAccountIds.size();
            while (numberOfPendingRequests > 0) {
                // Pending requests here must be pre-signin request since SigninManager will wait
                // system accounts been seeded into AccountTrackerService before finishing sign in.
                nativeStartFetchingAccountInfoFor(
                        mProfiles.get(0), mAccountIds.get(0), mImageSidePixels.get(0), true);
                mProfiles.remove(0);
                mAccountIds.remove(0);
                mImageSidePixels.remove(0);
                numberOfPendingRequests--;
            }
        }

        @Override
        public void onSystemAccountsChanged() {
            mProfiles.clear();
            mAccountIds.clear();
            mImageSidePixels.clear();
        }
    }

    /**
     * Starts fetching the account information for a given account.
     * @param context context associated with the request
     * @param profile Profile associated with the request
     * @param accountId Account name to fetch the information for
     * @param imageSidePixels Request image side (in pixels)
     */
    public static void startFetchingAccountInfoFor(Context context, Profile profile,
            String accountId, int imageSidePixels, boolean isPreSignin) {
        ThreadUtils.assertOnUiThread();
        if (!AccountTrackerService.get(context).checkAndSeedSystemAccounts()) {
            PendingProfileDownloads.get(context).pendProfileDownload(
                    profile, accountId, imageSidePixels);
            return;
        }
        nativeStartFetchingAccountInfoFor(profile, accountId, imageSidePixels, isPreSignin);
    }

    @CalledByNative
    private static void onProfileDownloadSuccess(String accountId, String fullName,
            String givenName, Bitmap bitmap) {
        ThreadUtils.assertOnUiThread();
        for (Observer observer : sObservers) {
            observer.onProfileDownloaded(accountId, fullName, givenName, bitmap);
        }
    }

    /**
     * @param profile Profile
     * @return The profile full name if cached, or null.
     */
    public static String getCachedFullName(Profile profile) {
        return nativeGetCachedFullNameForPrimaryAccount(profile);
    }

    /**
     * @param profile Profile
     * @return The profile given name if cached, or null.
     */
    public static String getCachedGivenName(Profile profile) {
        return nativeGetCachedGivenNameForPrimaryAccount(profile);
    }

    /**
     * @param profile Profile
     * @return The profile avatar if cached, or null.
     */
    public static Bitmap getCachedAvatar(Profile profile) {
        return nativeGetCachedAvatarForPrimaryAccount(profile);
    }

    // Native methods.
    private static native void nativeStartFetchingAccountInfoFor(
            Profile profile, String accountId, int imageSidePixels, boolean isPreSignin);
    private static native String nativeGetCachedFullNameForPrimaryAccount(Profile profile);
    private static native String nativeGetCachedGivenNameForPrimaryAccount(Profile profile);
    private static native Bitmap nativeGetCachedAvatarForPrimaryAccount(Profile profile);
}
