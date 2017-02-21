// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.blimp;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.blimp_public.BlimpClientContext;
import org.chromium.blimp_public.BlimpClientContextDelegate;
import org.chromium.chrome.browser.ApplicationLifetime;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.AccountSigninActivity;
import org.chromium.chrome.browser.signin.SigninAccessPoint;

/**
 * The ChromeBlimpClientContextDelegate for //chrome which provides the necessary functionality
 * required to run Blimp. This is the Java counterparty to the C++
 * ChromeBlimpClientContextDelegateAndroid.
 *
 * There can only be a single delegate for any given BlimpClientContext. To create one and attach
 * it, call {@link ChromeBlimpClientContextDelegate#createAndSetDelegateForContext(Profile)}.
 * When the delegate should be deleted, a call to {@link #destroy} is required.
 */
public class ChromeBlimpClientContextDelegate implements BlimpClientContextDelegate {
    /**
     * {@link BlimpClientContext} associated with this delegate.
     */
    private BlimpClientContext mBlimpClientContext;

    /**
     * Creates a new ChromeBlimpClientContextDelegate that is owned by the caller. It automatically
     * attaches itself as the sole delegate for the BlimpClientContext attached to the given
     * profile. When the delegate should be deleted, the caller of this method must call
     * {@link #destroy()} to ensure that the native counterparts are cleaned up. The call to
     * {@link #destroy()} also removes the delegate from the BlimpClientContext by clearing the
     * pointer for the delegate by calling BlimpClientContext::SetDelegate(nullptr).
     *
     * @param profile The profile to use to look for the BlimpClientContext.
     * @return The newly created delegate, owned by the caller.
     */
    public static ChromeBlimpClientContextDelegate createAndSetDelegateForContext(Profile profile) {
        return new ChromeBlimpClientContextDelegate(profile);
    }

    /**
     * @return {@link BlimpClientContext} object this delegate belongs to.
     */
    public BlimpClientContext getBlimpClientContext() {
        return mBlimpClientContext;
    }

    @Override
    public void restartBrowser() {
        ApplicationLifetime.terminate(true);
    }

    @Override
    public void startUserSignInFlow(Context context) {
        // TODO(xingliu): Figure out if Blimp should have its own SigninAccessPoint.
        AccountSigninActivity.startAccountSigninActivity(context, SigninAccessPoint.SETTINGS);
    }

    /**
     * The pointer to the ChromeBlimpClientContextDelegateAndroid JNI bridge.
     */
    private long mNativeChromeBlimpClientContextDelegateAndroid;

    private ChromeBlimpClientContextDelegate(Profile profile) {
        // Create native delegate object.
        mNativeChromeBlimpClientContextDelegateAndroid = nativeInit(profile);

        BlimpClientContext context =
                BlimpClientContextFactory.getBlimpClientContextForProfile(profile);
        mBlimpClientContext = context;

        // Set ourselves as the Java delegate object.
        mBlimpClientContext.setDelegate(this);

        // Connect to engine on start up.
        if (mBlimpClientContext.isBlimpEnabled()) {
            mBlimpClientContext.connect();
        }
    }

    @CalledByNative
    private void clearNativePtr() {
        mNativeChromeBlimpClientContextDelegateAndroid = 0;
    }

    public void destroy() {
        assert mNativeChromeBlimpClientContextDelegateAndroid != 0;

        nativeDestroy(mNativeChromeBlimpClientContextDelegateAndroid);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeChromeBlimpClientContextDelegateAndroid);
}
