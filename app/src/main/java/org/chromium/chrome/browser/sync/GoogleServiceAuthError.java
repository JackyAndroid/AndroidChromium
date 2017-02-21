// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import org.chromium.chrome.R;

/**
 * This class mirrors the native GoogleServiceAuthError class State enum from:
 * google_apis/gaia/google_service_auth_error.h.
 */
public class GoogleServiceAuthError {

    public enum State {
        // The user is authenticated.
        NONE(0, R.string.sync_error_generic),

        // The credentials supplied to GAIA were either invalid, or the locally
        // cached credentials have expired.
        INVALID_GAIA_CREDENTIALS(1, R.string.sync_error_ga),

        // The GAIA user is not authorized to use the service.
        USER_NOT_SIGNED_UP(2, R.string.sync_error_generic),

        // Could not connect to server to verify credentials. This could be in
        // response to either failure to connect to GAIA or failure to connect to
        // the service needing GAIA tokens during authentication.
        CONNECTION_FAILED(3, R.string.sync_error_connection),

        // The user needs to satisfy a CAPTCHA challenge to unlock their account.
        // If no other information is available, this can be resolved by visiting
        // https://www.google.com/accounts/DisplayUnlockCaptcha. Otherwise,
        // captcha() will provide details about the associated challenge.
        CAPTCHA_REQUIRED(4, R.string.sync_error_generic),

        // The user account has been deleted.
        ACCOUNT_DELETED(5, R.string.sync_error_generic),

        // The user account has been disabled.
        ACCOUNT_DISABLED(6, R.string.sync_error_generic),

        // The service is not available; try again later.
        SERVICE_UNAVAILABLE(7, R.string.sync_error_service_unavailable),

        // The password is valid but we need two factor to get a token.
        TWO_FACTOR(8, R.string.sync_error_generic),

        // The requestor of the authentication step cancelled the request
        // prior to completion.
        REQUEST_CANCELED(9, R.string.sync_error_generic),

        // HOSTED accounts are deprecated; left in enumeration to match
        // GoogleServiceAuthError enum in histograms.xml.
        HOSTED_NOT_ALLOWED_DEPRECATED(10, R.string.sync_error_generic);

        private final int mCode;
        private final int mMessage;

        State(int code, int message) {
            mCode = code;
            mMessage = message;
        }

        public static State fromCode(int code) {
            for (State state : State.values()) {
                if (state.mCode == code) {
                    return state;
                }
            }
            throw new IllegalArgumentException("No state for code: " + code);
        }

        public int getMessage() {
            return mMessage;
        }
    }

    private final State mState;

    GoogleServiceAuthError(int code) {
        mState = State.fromCode(code);
    }

    State getState() {
        return mState;
    }
}
