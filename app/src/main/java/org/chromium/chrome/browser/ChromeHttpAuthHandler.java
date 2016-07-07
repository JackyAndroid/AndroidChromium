// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.ui.base.WindowAndroid;

/**
 * Represents an HTTP authentication request to be handled by the UI.
 * The request can be fulfilled or canceled using setAuth() or cancelAuth().
 * This class also provides strings for building a login form.
 *
 * Note: this class supercedes android.webkit.HttpAuthHandler, but does not
 * extend HttpAuthHandler due to the private access of HttpAuthHandler's
 * constructor.
 */
public class ChromeHttpAuthHandler {
    private final long mNativeChromeHttpAuthHandler;
    private AutofillObserver mAutofillObserver;
    private String mAutofillUsername;
    private String mAutofillPassword;

    private ChromeHttpAuthHandler(long nativeChromeHttpAuthHandler) {
        assert nativeChromeHttpAuthHandler != 0;
        mNativeChromeHttpAuthHandler = nativeChromeHttpAuthHandler;
    }

    @CalledByNative
    private static ChromeHttpAuthHandler create(long nativeChromeHttpAuthHandler) {
        return new ChromeHttpAuthHandler(nativeChromeHttpAuthHandler);
    }

    // ---------------------------------------------
    // HttpAuthHandler methods
    // ---------------------------------------------

    // Note on legacy useHttpAuthUsernamePassword() method:
    // For reference, the existing WebView (when using the chromium stack) returns true here
    // iff this is the first auth challenge attempt for this connection.
    // (see WebUrlLoaderClient::authRequired call to didReceiveAuthenticationChallenge)
    // In ChromeHttpAuthHandler this mechanism is superseded by the
    // AutofillObserver.onAutofillDataAvailable mechanism below, however the legacy WebView
    // implementation will need to handle the API mismatch between the legacy
    // WebView.getHttpAuthUsernamePassword synchronous call and the credentials arriving
    // asynchronously in onAutofillDataAvailable.

    /**
     * Cancel the authorization request.
     */
    public void cancel() {
        nativeCancelAuth(mNativeChromeHttpAuthHandler);
    }

    /**
     * Proceed with the authorization with the given credentials.
     */
    public void proceed(String username, String password) {
        nativeSetAuth(mNativeChromeHttpAuthHandler, username, password);
    }

    public String getMessageBody() {
        return nativeGetMessageBody(mNativeChromeHttpAuthHandler);
    }

    @CalledByNative
    private void showDialog(WindowAndroid windowAndroid) {
        if (windowAndroid == null) {
            cancel();
        }
        Activity activity = windowAndroid.getActivity().get();
        if (activity == null) {
            cancel();
        }
        LoginPrompt authDialog = new LoginPrompt(activity, this);
        setAutofillObserver(authDialog);
        authDialog.show();
    }

    // ---------------------------------------------
    // Autofill-related
    // ---------------------------------------------

    /**
     * This is a public interface that will act as a hook for providing login data using
     * autofill. When the observer is set, {@link ChromeHttpAuthhandler}'s
     * onAutofillDataAvailable callback can be used by the observer to fill out necessary
     * login information.
     */
    public static interface AutofillObserver {
        public void onAutofillDataAvailable(String username, String password);
    }

    /**
     * Register for onAutofillDataAvailable callbacks.  |observer| can be null,
     * in which case no callback is made.
     */
    private void setAutofillObserver(AutofillObserver observer) {
        mAutofillObserver = observer;
        // In case the autofill data arrives before the observer is set.
        if (mAutofillUsername != null && mAutofillPassword != null) {
            mAutofillObserver.onAutofillDataAvailable(mAutofillUsername, mAutofillPassword);
        }
    }

    @CalledByNative
    private void onAutofillDataAvailable(String username, String password) {
        mAutofillUsername = username;
        mAutofillPassword = password;
        if (mAutofillObserver != null) {
            mAutofillObserver.onAutofillDataAvailable(username, password);
        }
    }

    // ---------------------------------------------
    // Native side calls
    // ---------------------------------------------

    private native void nativeSetAuth(long nativeChromeHttpAuthHandler,
            String username, String password);
    private native void nativeCancelAuth(long nativeChromeHttpAuthHandler);
    private native String nativeGetMessageBody(long nativeChromeHttpAuthHandler);
}
