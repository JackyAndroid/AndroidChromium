// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webshare;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.support.annotation.Nullable;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.mojo.system.MojoException;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.url.mojom.Url;
import org.chromium.webshare.mojom.ShareService;

/**
 * Android implementation of the ShareService service defined in
 * third_party/WebKit/public/platform/modules/webshare/webshare.mojom.
 */
public class ShareServiceImpl implements ShareService {
    private final Activity mActivity;
    // We no longer show a warning for incognito mode.
    // TODO(mgiuca): Remove this code (https://crrev.com/420564, https://crbug.com/645007).
    private static final boolean mIsIncognito = false;

    // These numbers are written to histograms. Keep in sync with WebShareMethod enum in
    // histograms.xml, and don't reuse or renumber entries (except for the _COUNT entry).
    private static final int WEBSHARE_METHOD_SHARE = 0;
    // Count is technically 1, but recordEnumeratedHistogram requires a boundary of at least 2
    // (https://crbug.com/645032).
    private static final int WEBSHARE_METHOD_COUNT = 2;

    // These numbers are written to histograms. Keep in sync with WebShareOutcome enum in
    // histograms.xml, and don't reuse or renumber entries (except for the _COUNT entry).
    private static final int WEBSHARE_OUTCOME_SUCCESS = 0;
    private static final int WEBSHARE_OUTCOME_UNKNOWN_FAILURE = 1;
    private static final int WEBSHARE_OUTCOME_CANCELED = 2;
    private static final int WEBSHARE_OUTCOME_COUNT = 3;

    public ShareServiceImpl(@Nullable WebContents webContents) {
        mActivity = activityFromWebContents(webContents);
    }

    @Override
    public void close() {}

    @Override
    public void onConnectionError(MojoException e) {}

    @Override
    public void share(String title, String text, Url url, final ShareResponse callback) {
        RecordHistogram.recordEnumeratedHistogram("WebShare.ApiCount", WEBSHARE_METHOD_SHARE,
                WEBSHARE_METHOD_COUNT);

        if (mActivity == null) {
            RecordHistogram.recordEnumeratedHistogram("WebShare.ShareOutcome",
                    WEBSHARE_OUTCOME_UNKNOWN_FAILURE, WEBSHARE_OUTCOME_COUNT);
            callback.call("Share failed");
            return;
        }

        if (mIsIncognito) {
            // In incognito mode, confirm with the user before sending intent externally.
            showIncognitoWarningDialog(title, text, url, callback);
        } else {
            startShare(title, text, url, callback);
        }
    }

    @Nullable
    private static Activity activityFromWebContents(@Nullable WebContents webContents) {
        if (webContents == null) return null;

        ContentViewCore contentViewCore = ContentViewCore.fromWebContents(webContents);
        if (contentViewCore == null) return null;

        WindowAndroid window = contentViewCore.getWindowAndroid();
        if (window == null) return null;

        return window.getActivity().get();
    }

    private void startShare(String title, String text, Url url, final ShareResponse callback) {
        ShareHelper.TargetChosenCallback innerCallback = new ShareHelper.TargetChosenCallback() {
            public void onTargetChosen(ComponentName chosenComponent) {
                RecordHistogram.recordEnumeratedHistogram("WebShare.ShareOutcome",
                        WEBSHARE_OUTCOME_SUCCESS, WEBSHARE_OUTCOME_COUNT);
                callback.call(null);
            }

            public void onCancel() {
                cancelShare(callback);
            }
        };

        ShareHelper.share(false, false, mActivity, title, text, url.url, null, null, innerCallback);
    }

    private static void cancelShare(ShareResponse callback) {
        RecordHistogram.recordEnumeratedHistogram("WebShare.ShareOutcome",
                WEBSHARE_OUTCOME_CANCELED, WEBSHARE_OUTCOME_COUNT);
        callback.call("Share canceled");
    }

    private void showIncognitoWarningDialog(final String title, final String text, final Url url,
            final ShareResponse callback) {
        ExternalNavigationDelegateImpl.showLeaveIncognitoWarningDialog(mActivity,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startShare(title, text, url, callback);
                    }
                },
                new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancelShare(callback);
                    }
                });
    }
}
