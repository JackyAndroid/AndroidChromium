// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.help;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.text.TextUtils;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.feedback.FeedbackCollector;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.UrlUtilities;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Launches an activity that displays a relevant support page and has an option to provide feedback.
 */
public class HelpAndFeedback {
    protected static final String FALLBACK_SUPPORT_URL =
            "https://support.google.com/chrome/topic/6069782";

    private static HelpAndFeedback sInstance;

    /**
     * Returns the singleton instance of HelpAndFeedback, creating it if needed.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static HelpAndFeedback getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = ((ChromeApplication) context.getApplicationContext())
                    .createHelpAndFeedback();
        }
        return sInstance;
    }

    /**
     * Starts an activity showing a help page for the specified context ID.
     *
     * @param activity The activity to use for starting the help activity and to take a
     *                 screenshot of.
     * @param helpContext One of the CONTEXT_* constants. This should describe the user's current
     *                    context and will be used to show a more relevant help page.
     * @param collector the {@link FeedbackCollector} to use for extra data. Must not be null.
     */
    protected void show(
            Activity activity, String helpContext, @Nonnull FeedbackCollector collector) {
        launchFallbackSupportUri(activity);
    }

    /**
     * Starts an activity showing a help page for the specified context ID.
     *
     * @param activity The activity to use for starting the help activity and to take a
     *                 screenshot of.
     * @param helpContext One of the CONTEXT_* constants. This should describe the user's current
     *                    context and will be used to show a more relevant help page.
     * @param profile the current profile.
     * @param url the current URL. May be null.
     */
    public void show(final Activity activity, final String helpContext, Profile profile,
            @Nullable String url) {
        FeedbackCollector.create(activity, profile, url, new FeedbackCollector.FeedbackResult() {
            @Override
            public void onResult(FeedbackCollector collector) {
                show(activity, helpContext, collector);
            }
        });
    }

    /**
     * Get help context ID from URL.
     *
     * @param url The URL to be checked.
     * @param isIncognito Whether we are in incognito mode or not.
     * @return Help context ID that matches the URL and incognito mode.
     */
    public static String getHelpContextIdFromUrl(Context context, String url, boolean isIncognito) {
        if (TextUtils.isEmpty(url)) {
            return context.getString(R.string.help_context_general);
        } else if (url.startsWith(UrlConstants.BOOKMARKS_URL)) {
            return context.getString(R.string.help_context_bookmarks);
        } else if (url.equals(UrlConstants.HISTORY_URL)) {
            return context.getString(R.string.help_context_history);
        // Note: For www.google.com the following function returns false.
        } else if (UrlUtilities.nativeIsGoogleSearchUrl(url)) {
            return context.getString(R.string.help_context_search_results);
        // For incognito NTP, we want to show incognito help.
        } else if (isIncognito) {
            return context.getString(R.string.help_context_incognito);
        } else if (url.equals(UrlConstants.NTP_URL)) {
            return context.getString(R.string.help_context_new_tab);
        }
        return context.getString(R.string.help_context_webpage);
    }

    protected static void launchFallbackSupportUri(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(FALLBACK_SUPPORT_URL));
        // Let Chrome know that this intent is from Chrome, so that it does not close the app when
        // the user presses 'back' button.
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
        intent.setPackage(context.getPackageName());
        context.startActivity(intent);
    }
}