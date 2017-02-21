// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Browser;
import android.provider.MediaStore;
import android.speech.RecognizerResultsIntent;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.externalnav.IntentWithGesturesHandler;
import org.chromium.chrome.browser.omnibox.AutocompleteController;
import org.chromium.chrome.browser.rappor.RapporServiceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegate;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Handles all browser-related Intents.
 */
public class IntentHandler {
    private static final String TAG = "IntentHandler";

    /**
     * Document mode: If true, Chrome is launched into the same Task.
     * Note: used by first-party applications, do not rename.
     */
    public static final String EXTRA_APPEND_TASK = "com.android.chrome.append_task";

    /**
     * Document mode: If true, keep tasks in Recents when a user hits back at the root URL.
     * Note: used by first-party applications, do not rename.
     */
    public static final String EXTRA_PRESERVE_TASK = "com.android.chrome.preserve_task";

    /**
     * Document mode: If true, opens the document in background.
     * Note: used by first-party applications, do not rename.
     */
    public static final String EXTRA_OPEN_IN_BG = "com.android.chrome.open_with_affiliation";

    /**
     * Document mode: Records what caused a document to be created.
     */
    public static final String EXTRA_STARTED_BY = "com.android.chrome.started_by";

    /**
     * Tab ID to use when creating a new Tab.
     */
    public static final String EXTRA_TAB_ID = "com.android.chrome.tab_id";

    /**
     * The tab id of the parent tab, if any.
     */
    public static final String EXTRA_PARENT_TAB_ID = "com.android.chrome.parent_tab_id";

    /**
     * Intent to bring the parent Activity back, if the parent Tab lives in a different Activity.
     */
    public static final String EXTRA_PARENT_INTENT = "com.android.chrome.parent_intent";

    /**
     * ComponentName of the parent Activity. Can be used by an Activity launched on top of another
     * Activity (e.g. BookmarkActivity) to intent back into the Activity it sits on top of.
     */
    public static final String EXTRA_PARENT_COMPONENT =
            "org.chromium.chrome.browser.parent_component";

    /**
     * Transition type is only set internally by a first-party app and has to be signed.
     */
    public static final String EXTRA_PAGE_TRANSITION_TYPE = "com.google.chrome.transition_type";

    /**
     * The original intent of the given intent before it was modified.
     */
    public static final String EXTRA_ORIGINAL_INTENT = "com.android.chrome.original_intent";

    /**
     * An extra to indicate that a particular intent was triggered from the first run experience
     * flow.
     */
    public static final String EXTRA_INVOKED_FROM_FRE = "com.android.chrome.invoked_from_fre";

    /**
     * An extra to indicate that the intent was triggered from a launcher shortcut.
     */
    public static final String EXTRA_INVOKED_FROM_SHORTCUT =
            "com.android.chrome.invoked_from_shortcut";

    /**
     * Intent extra used to identify the sending application.
     */
    private static final String TRUSTED_APPLICATION_CODE_EXTRA = "trusted_application_code_extra";

    /**
     * The scheme for referrer coming from an application.
     */
    public static final String ANDROID_APP_REFERRER_SCHEME = "android-app";

    /**
     * A referrer id used for Chrome to Chrome referrer passing.
     */
    public static final String EXTRA_REFERRER_ID = "org.chromium.chrome.browser.referrer_id";

    /**
     * Key to associate a timestamp with an intent.
     */
    private static final String EXTRA_TIMESTAMP_MS = "org.chromium.chrome.browser.timestamp";

    /**
     * For multi-window, passes the id of the window.
     */
    public static final String EXTRA_WINDOW_ID = "org.chromium.chrome.browser.window_id";

    /**
     * Records package names of other applications in the system that could have handled
     * this intent.
     */
    public static final String EXTRA_EXTERNAL_NAV_PACKAGES = "org.chromium.chrome.browser.eenp";

    /**
     * A hash code for the URL to verify intent data hasn't been modified.
     */
    public static final String EXTRA_DATA_HASH_CODE = "org.chromium.chrome.browser.data_hash";

    /**
     * Fake ComponentName used in constructing TRUSTED_APPLICATION_CODE_EXTRA.
     */
    private static ComponentName sFakeComponentName = null;

    private static final Object LOCK = new Object();

    private static Pair<Integer, String> sPendingReferrer;
    private static int sReferrerId;
    private static String sPendingIncognitoUrl;

    private static final String PACKAGE_GSA = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_GMAIL = "com.google.android.gm";
    private static final String PACKAGE_PLUS = "com.google.android.apps.plus";
    private static final String PACKAGE_HANGOUTS = "com.google.android.talk";
    private static final String PACKAGE_MESSENGER = "com.google.android.apps.messaging";
    private static final String PACKAGE_LINE = "jp.naver.line.android";
    private static final String PACKAGE_WHATSAPP = "com.whatsapp";
    private static final String FACEBOOK_LINK_PREFIX = "http://m.facebook.com/l.php?";
    private static final String TWITTER_LINK_PREFIX = "http://t.co/";
    private static final String NEWS_LINK_PREFIX = "http://news.google.com/news/url?";

    /**
     * Represents popular external applications that can load a page in Chrome via intent.
     */
    public static enum ExternalAppId {
        OTHER,
        GMAIL,
        FACEBOOK,
        PLUS,
        TWITTER,
        CHROME,
        HANGOUTS,
        MESSENGER,
        NEWS,
        LINE,
        WHATSAPP,
        GSA,
        INDEX_BOUNDARY
    }

    private static ComponentName getFakeComponentName(String packageName) {
        synchronized (LOCK) {
            if (sFakeComponentName == null) {
                sFakeComponentName = new ComponentName(packageName, "FakeClass");
            }
        }

        return sFakeComponentName;
    }

    /** Intent extra to open an incognito tab. */
    public static final String EXTRA_OPEN_NEW_INCOGNITO_TAB =
            "com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB";

    /** Schemes used by web pages to start up Chrome without an explicit Intent. */
    public static final String GOOGLECHROME_SCHEME = "googlechrome";
    public static final String GOOGLECHROME_NAVIGATE_PREFIX =
            GOOGLECHROME_SCHEME + "://navigate?url=";

    /**
     * The class name to be specified in the ComponentName for Intents that are creating a new
     * tab (regardless of whether the user is in document or tabbed mode).
     */
    // TODO(tedchoc): Remove this and directly reference the Launcher activity when that becomes
    //                publicly available.
    private static final String TAB_ACTIVITY_COMPONENT_CLASS_NAME =
            "com.google.android.apps.chrome.Main";

    private static boolean sTestIntentsEnabled;

    private final IntentHandlerDelegate mDelegate;
    private final String mPackageName;
    private KeyguardManager mKeyguardManager;

    public static enum TabOpenType {
        OPEN_NEW_TAB,
        // Tab is reused only if the URLs perfectly match.
        REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB,
        // Tab is reused only if there's an existing tab opened by the same app ID.
        REUSE_APP_ID_MATCHING_TAB_ELSE_NEW_TAB,
        CLOBBER_CURRENT_TAB,
        BRING_TAB_TO_FRONT,
        // Opens a new incognito tab.
        OPEN_NEW_INCOGNITO_TAB,
    }

    /**
     * A delegate interface for users of IntentHandler.
     */
    public static interface IntentHandlerDelegate {
        /**
         * Processes a URL VIEW Intent.
         */
        void processUrlViewIntent(String url, String referer, String headers,
                TabOpenType tabOpenType, String externalAppId, int tabIdToBringToFront,
                boolean hasUserGesture, Intent intent);

        void processWebSearchIntent(String query);
    }

    /** Sets whether or not test intents are enabled. */
    @VisibleForTesting
    public static void setTestIntentsEnabled(boolean enabled) {
        sTestIntentsEnabled = enabled;
    }

    public IntentHandler(IntentHandlerDelegate delegate, String packageName) {
        mDelegate = delegate;
        mPackageName = packageName;
    }

    /**
     * Determines what App was used to fire this Intent.
     * @param packageName Package name of this application.
     * @param intent Intent that was used to launch Chrome.
     * @return ExternalAppId representing the app.
     */
    public static ExternalAppId determineExternalIntentSource(String packageName, Intent intent) {
        String appId = IntentUtils.safeGetStringExtra(intent, Browser.EXTRA_APPLICATION_ID);
        ExternalAppId externalId = ExternalAppId.OTHER;
        if (appId == null) {
            String url = getUrlFromIntent(intent);
            if (url != null && url.startsWith(TWITTER_LINK_PREFIX)) {
                externalId = ExternalAppId.TWITTER;
            } else if (url != null && url.startsWith(FACEBOOK_LINK_PREFIX)) {
                externalId = ExternalAppId.FACEBOOK;
            } else if (url != null && url.startsWith(NEWS_LINK_PREFIX)) {
                externalId = ExternalAppId.NEWS;
            }
        } else {
            if (appId.equals(PACKAGE_PLUS)) {
                externalId = ExternalAppId.PLUS;
            } else if (appId.equals(PACKAGE_GMAIL)) {
                externalId = ExternalAppId.GMAIL;
            } else if (appId.equals(PACKAGE_HANGOUTS)) {
                externalId = ExternalAppId.HANGOUTS;
            } else if (appId.equals(PACKAGE_MESSENGER)) {
                externalId = ExternalAppId.MESSENGER;
            } else if (appId.equals(PACKAGE_LINE)) {
                externalId = ExternalAppId.LINE;
            } else if (appId.equals(PACKAGE_WHATSAPP)) {
                externalId = ExternalAppId.WHATSAPP;
            } else if (appId.equals(PACKAGE_GSA)) {
                externalId = ExternalAppId.GSA;
            } else if (appId.equals(packageName)) {
                externalId = ExternalAppId.CHROME;
            }
        }
        return externalId;
    }

    private void recordExternalIntentSourceUMA(Intent intent) {
        ExternalAppId externalId = determineExternalIntentSource(mPackageName, intent);
        RecordHistogram.recordEnumeratedHistogram("MobileIntent.PageLoadDueToExternalApp",
                externalId.ordinal(), ExternalAppId.INDEX_BOUNDARY.ordinal());
        if (externalId == ExternalAppId.OTHER) {
            String appId = IntentUtils.safeGetStringExtra(intent, Browser.EXTRA_APPLICATION_ID);
            if (!TextUtils.isEmpty(appId)) {
                RapporServiceBridge.sampleString("Android.PageLoadDueToExternalApp", appId);
            }
        }
    }

    /**
     * Records an action when a user chose to handle a URL in Chrome that could have been handled
     * by an application installed on the phone. Also records the name of that application.
     * This doesn't include generic URL handlers, such as browsers.
     */
    private void recordAppHandlersForIntent(Intent intent) {
        List<String> packages = IntentUtils.safeGetStringArrayListExtra(intent,
                IntentHandler.EXTRA_EXTERNAL_NAV_PACKAGES);
        if (packages != null && packages.size() > 0) {
            RecordUserAction.record("MobileExternalNavigationReceived");
            for (String name : packages) {
                RapporServiceBridge.sampleString("Android.ExternalNavigationNotChosen", name);
            }
        }
    }

    /**
     * Handles an Intent after the ChromeTabbedActivity decides that it shouldn't ignore the
     * Intent.
     *
     * @return Whether the Intent was successfully handled.
     */
    boolean onNewIntent(Context context, Intent intent) {
        assert intentHasValidUrl(intent);
        String url = getUrlFromIntent(intent);
        boolean hasUserGesture =
                IntentWithGesturesHandler.getInstance().getUserGestureAndClear(intent);
        TabOpenType tabOpenType = getTabOpenType(intent);
        int tabIdToBringToFront = IntentUtils.safeGetIntExtra(
                intent, TabOpenType.BRING_TAB_TO_FRONT.name(), Tab.INVALID_TAB_ID);
        if (url == null && tabIdToBringToFront == Tab.INVALID_TAB_ID
                && tabOpenType != TabOpenType.OPEN_NEW_INCOGNITO_TAB) {
            return handleWebSearchIntent(intent);
        }

        String referrerUrl = getReferrerUrlIncludingExtraHeaders(intent, context);
        String extraHeaders = getExtraHeadersFromIntent(intent);

        // TODO(joth): Presumably this should check the action too.
        mDelegate.processUrlViewIntent(url, referrerUrl, extraHeaders, tabOpenType,
                IntentUtils.safeGetStringExtra(intent, Browser.EXTRA_APPLICATION_ID),
                tabIdToBringToFront, hasUserGesture, intent);
        recordExternalIntentSourceUMA(intent);
        recordAppHandlersForIntent(intent);
        return true;
    }

    /**
     * Extracts referrer Uri from intent, if supplied.
     * @param intent The intent to use.
     * @return The referrer Uri.
     */
    private static Uri getReferrer(Intent intent) {
        Uri referrer = IntentUtils.safeGetParcelableExtra(intent, Intent.EXTRA_REFERRER);
        if (referrer != null) {
            return referrer;
        }
        String referrerName = IntentUtils.safeGetStringExtra(intent, Intent.EXTRA_REFERRER_NAME);
        if (referrerName != null) {
            return Uri.parse(referrerName);
        }
        return null;
    }

    /**
     * Extracts referrer URL string. The extra is used if we received it from a first party app or
     * if the referrer_extra is specified as android-app://package style URL.
     * @param intent The intent from which to extract the URL.
     * @param context The activity that received the intent.
     * @return The URL string or null if none should be used.
     */
    private static String getReferrerUrl(Intent intent, Context context) {
        Uri referrerExtra = getReferrer(intent);
        if (referrerExtra == null) return null;
        String referrerUrl = IntentHandler.getPendingReferrerUrl(
                IntentUtils.safeGetIntExtra(intent, EXTRA_REFERRER_ID, 0));
        if (!TextUtils.isEmpty(referrerUrl)) {
            return referrerUrl;
        } else if (isValidReferrerHeader(referrerExtra.toString())) {
            return referrerExtra.toString();
        } else if (IntentHandler.isIntentChromeOrFirstParty(intent, context)) {
            return referrerExtra.toString();
        }
        return null;
    }

    /**
     * Gets the referrer, looking in the Intent extra and in the extra headers extra.
     *
     * The referrer extra takes priority over the "extra headers" one.
     *
     * @param intent The Intent containing the extras.
     * @param context The application context.
     * @return The referrer, or null.
     */
    public static String getReferrerUrlIncludingExtraHeaders(Intent intent, Context context) {
        String referrerUrl = getReferrerUrl(intent, context);
        if (referrerUrl != null) return referrerUrl;

        Bundle bundleExtraHeaders = IntentUtils.safeGetBundleExtra(intent, Browser.EXTRA_HEADERS);
        if (bundleExtraHeaders == null) return null;
        for (String key : bundleExtraHeaders.keySet()) {
            String value = bundleExtraHeaders.getString(key);
            if ("referer".equals(key.toLowerCase(Locale.US)) && isValidReferrerHeader(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Add referrer and extra headers to a {@link LoadUrlParams}, if we managed to parse them from
     * the intent.
     * @param params The {@link LoadUrlParams} to add referrer and headers.
     * @param intent The intent we use to parse the extras.
     */
    public static void addReferrerAndHeaders(LoadUrlParams params, Intent intent, Context context) {
        String referrer = getReferrerUrlIncludingExtraHeaders(intent, context);
        if (referrer != null) {
            params.setReferrer(new Referrer(referrer, Referrer.REFERRER_POLICY_DEFAULT));
        }
        String headers = getExtraHeadersFromIntent(intent);
        if (headers != null) params.setVerbatimHeaders(headers);
    }

    /**
     * @return Whether that the given referrer is of the format that Chrome allows external
     * apps to specify.
     */
    private static boolean isValidReferrerHeader(String referrer) {
        return referrer != null
                && referrer.toLowerCase(Locale.US).startsWith(ANDROID_APP_REFERRER_SCHEME + "://");
    }

    /**
     * Constructs a valid referrer using the given authority.
     * @param authority The authority to use.
     * @return Referrer with default policy that uses the valid android app scheme.
     */
    public static Referrer constructValidReferrerForAuthority(String authority) {
        return new Referrer(new Uri.Builder().scheme(ANDROID_APP_REFERRER_SCHEME)
                .authority(authority).build().toString(), Referrer.REFERRER_POLICY_DEFAULT);
    }

    /**
     * Extracts the URL from voice search result intent.
     * @return URL if it was found, null otherwise.
     */
    static String getUrlFromVoiceSearchResult(Intent intent) {
        if (!RecognizerResultsIntent.ACTION_VOICE_SEARCH_RESULTS.equals(intent.getAction())) {
            return null;
        }
        ArrayList<String> results = IntentUtils.safeGetStringArrayListExtra(
                intent, RecognizerResultsIntent.EXTRA_VOICE_SEARCH_RESULT_STRINGS);

        // Allow specifying a single voice result via the command line during testing (as the
        // 'am' command does not allow specifying an array of strings).
        if (results == null && sTestIntentsEnabled) {
            String testResult = IntentUtils.safeGetStringExtra(
                    intent, RecognizerResultsIntent.EXTRA_VOICE_SEARCH_RESULT_STRINGS);
            if (testResult != null) {
                results = new ArrayList<String>();
                results.add(testResult);
            }
        }
        if (results == null || results.size() == 0) return null;
        String query = results.get(0);
        String url = AutocompleteController.nativeQualifyPartialURLQuery(query);
        if (url == null) {
            List<String> urls = IntentUtils.safeGetStringArrayListExtra(
                    intent, RecognizerResultsIntent.EXTRA_VOICE_SEARCH_RESULT_URLS);
            if (urls != null && urls.size() > 0) {
                url = urls.get(0);
            } else {
                url = TemplateUrlService.getInstance().getUrlForVoiceSearchQuery(query);
            }
        }
        return url;
    }

    public boolean handleWebSearchIntent(Intent intent) {
        if (intent == null) return false;

        String query = null;
        final String action = intent.getAction();
        if (Intent.ACTION_SEARCH.equals(action)
                || MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(action)) {
            query = IntentUtils.safeGetStringExtra(intent, SearchManager.QUERY);
        }

        if (query == null || TextUtils.isEmpty(query)) return false;

        mDelegate.processWebSearchIntent(query);
        return true;
    }

    private static PendingIntent getAuthenticationToken(Context appContext) {
        Intent fakeIntent = new Intent();
        fakeIntent.setComponent(getFakeComponentName(appContext.getPackageName()));
        return PendingIntent.getActivity(appContext, 0, fakeIntent, 0);
    }

    /**
     * Start activity for the given trusted Intent.
     *
     * To make sure the intent is not dropped by Chrome, we send along an authentication token to
     * identify ourselves as a trusted sender. The method {@link #shouldIgnoreIntent} validates the
     * token.
     */
    public static void startActivityForTrustedIntent(Intent intent, Context context) {
        startActivityForTrustedIntentInternal(intent, context, null);
    }

    /**
     * Start the activity that handles launching tabs in Chrome given the trusted intent.
     *
     * This allows specifying URLs that chrome:// handles internally, but does not expose in
     * intent-filters for global use.
     *
     * To make sure the intent is not dropped by Chrome, we send along an authentication token to
     * identify ourselves as a trusted sender. The method {@link #shouldIgnoreIntent} validates the
     * token.
     */
    public static void startChromeLauncherActivityForTrustedIntent(Intent intent, Context context) {
        // Specify the exact component that will handle creating a new tab.  This allows specifying
        // URLs that are not exposed in the intent filters (i.e. chrome://).
        startActivityForTrustedIntentInternal(intent, context, new ComponentName(
                context.getPackageName(), TAB_ACTIVITY_COMPONENT_CLASS_NAME));
    }

    private static void startActivityForTrustedIntentInternal(
            Intent intent, Context context, ComponentName componentName) {
        // The caller might want to re-use the Intent, so we'll use a copy.
        Intent copiedIntent = new Intent(intent);

        if (componentName != null) {
            assert copiedIntent.getComponent() == null;
            // Specify the exact component that will handle creating a new tab.  This allows
            // specifying URLs that are not exposed in the intent filters (i.e. chrome://).
            copiedIntent.setComponent(componentName);
        }

        addTrustedIntentExtras(copiedIntent, context);

        // Make sure we use the application context.
        Context appContext = context.getApplicationContext();
        appContext.startActivity(copiedIntent);
    }

    /**
     * Sets TRUSTED_APPLICATION_CODE_EXTRA on the provided intent to identify it as coming from
     * a trusted source.
     */
    public static void addTrustedIntentExtras(Intent intent, Context context) {
        if (ExternalNavigationDelegateImpl.willChromeHandleIntent(context, intent, true)) {
            // The PendingIntent functions as an authentication token --- it could only have come
            // from us. Stash it in the real Intent as an extra. shouldIgnoreIntent will retrieve it
            // and check it with isIntentChromeInternal.
            intent.putExtra(TRUSTED_APPLICATION_CODE_EXTRA,
                    getAuthenticationToken(context.getApplicationContext()));
            // It is crucial that we never leak the authentication token to other packages, because
            // then the other package could be used to impersonate us/do things as us. Therefore,
            // scope the real Intent to our package.
            intent.setPackage(context.getApplicationContext().getPackageName());
        }
    }

    /**
     * Returns a String (or null) containing the extra headers sent by the intent, if any.
     *
     * This methods skips the referrer header.
     *
     * @param intent The intent containing the bundle extra with the HTTP headers.
     */
    public static String getExtraHeadersFromIntent(Intent intent) {
        Bundle bundleExtraHeaders = IntentUtils.safeGetBundleExtra(intent, Browser.EXTRA_HEADERS);
        if (bundleExtraHeaders == null) return null;
        StringBuilder extraHeaders = new StringBuilder();
        Iterator<String> keys = bundleExtraHeaders.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = bundleExtraHeaders.getString(key);
            if ("referer".equals(key.toLowerCase(Locale.US))) continue;
            if (extraHeaders.length() != 0) extraHeaders.append("\n");
            extraHeaders.append(key);
            extraHeaders.append(": ");
            extraHeaders.append(value);
        }
        return extraHeaders.length() == 0 ? null : extraHeaders.toString();
    }

    /**
     * Adds a timestamp to an intent, as returned by {@link SystemClock#elapsedRealtime()}.
     *
     * To track page load time, this needs to be called as close as possible to
     * the entry point (in {@link Activity#onCreate()} for instance).
     */
    public static void addTimestampToIntent(Intent intent) {
        intent.putExtra(EXTRA_TIMESTAMP_MS, SystemClock.elapsedRealtime());
    }

    /**
     * @return the timestamp associated with an intent, or -1.
     */
    public static long getTimestampFromIntent(Intent intent) {
        return intent.getLongExtra(EXTRA_TIMESTAMP_MS, -1);
    }

    /**
     * Returns true if the app should ignore a given intent.
     *
     * @param context Android Context.
     * @param intent Intent to check.
     * @return true if the intent should be ignored.
     */
    public boolean shouldIgnoreIntent(Context context, Intent intent) {
        // Although not documented to, many/most methods that retrieve values from an Intent may
        // throw. Because we can't control what packages might send to us, we should catch any
        // Throwable and then fail closed (safe). This is ugly, but resolves top crashers in the
        // wild.
        try {
            // Ignore all invalid URLs, regardless of what the intent was.
            if (!intentHasValidUrl(intent)) {
                return true;
            }

            // Determine if this intent came from a trustworthy source (either Chrome or Google
            // first party applications).
            boolean isInternal = isIntentChromeOrFirstParty(intent, context);

            // "Open new incognito tab" is currently limited to Chrome or first parties.
            if (!isInternal
                    && IntentUtils.safeGetBooleanExtra(
                            intent, EXTRA_OPEN_NEW_INCOGNITO_TAB, false)
                    && (getPendingIncognitoUrl() == null
                            || !getPendingIncognitoUrl().equals(intent.getDataString()))) {
                return true;
            }

            // Now if we have an empty URL and the intent was ACTION_MAIN,
            // we are pretty sure it is the launcher calling us to show up.
            // We can safely ignore the screen state.
            String url = getUrlFromIntent(intent);
            if (url == null && Intent.ACTION_MAIN.equals(intent.getAction())) {
                return false;
            }

            // Ignore all intents that specify a Chrome internal scheme if they did not come from
            // a trustworthy source.
            String scheme = getSanitizedUrlScheme(url);
            if (!isInternal && scheme != null
                    && (intent.hasCategory(Intent.CATEGORY_BROWSABLE)
                               || intent.hasCategory(Intent.CATEGORY_DEFAULT)
                               || intent.getCategories() == null)) {
                String lowerCaseScheme = scheme.toLowerCase(Locale.US);
                if ("chrome".equals(lowerCaseScheme) || "chrome-native".equals(lowerCaseScheme)
                        || "about".equals(lowerCaseScheme)) {
                    // Allow certain "safe" internal URLs to be launched by external
                    // applications.
                    String lowerCaseUrl = url.toLowerCase(Locale.US);
                    if ("about:blank".equals(lowerCaseUrl)
                            || "about://blank".equals(lowerCaseUrl)) {
                        return false;
                    }

                    Log.w(TAG, "Ignoring internal Chrome URL from untrustworthy source.");
                    return true;
                }
            }

            // We must check for screen state at this point.
            // These might be slow.
            boolean internalOrVisible = isInternal || isIntentUserVisible(context);
            return !internalOrVisible;
        } catch (Throwable t) {
            return true;
        }
    }

    @VisibleForTesting
    boolean intentHasValidUrl(Intent intent) {
        String url = getUrlFromIntent(intent);

        // Always drop insecure urls.
        if (url != null && isJavascriptSchemeOrInvalidUrl(url)) {
            return false;
        }
        return true;
    }

    /**
     * Fetch the authentication token (a PendingIntent) created by startActivityForTrustedIntent,
     * if any. If anything goes wrong trying to retrieve the token (examples include
     * BadParcelableException or ClassNotFoundException), fail closed.
     */
    private static PendingIntent fetchAuthenticationTokenFromIntent(Intent intent) {
        return (PendingIntent) IntentUtils.safeGetParcelableExtra(
                intent, TRUSTED_APPLICATION_CODE_EXTRA);
    }

    private static boolean isChromeToken(PendingIntent token, Context context) {
        // Fetch what should be a matching token.
        Context appContext = context.getApplicationContext();
        PendingIntent pending = getAuthenticationToken(appContext);
        return pending.equals(token);
    }

    /**
     * @param intent An Intent to be checked.
     * @param context A context.
     * @return Whether an intent originates from Chrome.
     */
    public static boolean wasIntentSenderChrome(Intent intent, Context context) {
        if (intent == null) return false;

        PendingIntent token = fetchAuthenticationTokenFromIntent(intent);
        if (token == null) return false;

        // Do not ignore a valid URL Intent if the sender is Chrome. (If the PendingIntents are
        // equal, we know that the sender was us.)
        return isChromeToken(token, context);
    }

    /**
     * @param intent An Intent to be checked.
     * @param context A context.
     * @return Whether an intent originates from Chrome or a first-party app.
     */
    public static boolean isIntentChromeOrFirstParty(Intent intent, Context context) {
        if (intent == null) return false;

        PendingIntent token = fetchAuthenticationTokenFromIntent(intent);
        if (token == null) return false;

        // Do not ignore a valid URL Intent if the sender is Chrome. (If the PendingIntents are
        // equal, we know that the sender was us.)
        if (isChromeToken(token, context)) {
            return true;
        }
        if (ExternalAuthUtils.getInstance().isGoogleSigned(
                    context, ApiCompatibilityUtils.getCreatorPackage(token))) {
            return true;
        }
        return false;
    }

    private boolean isIntentUserVisible(Context context) {
        // Only process Intents if the screen is on and the device is unlocked;
        // i.e. the user will see what is going on.
        if (mKeyguardManager == null) {
            mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        }
        if (!ApiCompatibilityUtils.isInteractive(context)) return false;
        return !ApiCompatibilityUtils.isDeviceProvisioned(context)
                || !mKeyguardManager.inKeyguardRestrictedInputMode();
    }

    /*
     * The default behavior here is to open in a new tab.  If this is changed, ensure
     * intents with action NDEF_DISCOVERED (links beamed over NFC) are handled properly.
     */
    private TabOpenType getTabOpenType(Intent intent) {
        if (IntentUtils.safeGetBooleanExtra(
                    intent, ShortcutHelper.REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, false)) {
            return TabOpenType.REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB;
        }

        if (IntentUtils.safeGetBooleanExtra(intent, EXTRA_OPEN_NEW_INCOGNITO_TAB, false)) {
            return TabOpenType.OPEN_NEW_INCOGNITO_TAB;
        }

        if (IntentUtils.safeGetIntExtra(intent, TabOpenType.BRING_TAB_TO_FRONT.name(),
                    Tab.INVALID_TAB_ID) != Tab.INVALID_TAB_ID) {
            return TabOpenType.BRING_TAB_TO_FRONT;
        }

        String appId = IntentUtils.safeGetStringExtra(intent, Browser.EXTRA_APPLICATION_ID);
        // Due to users complaints, we are NOT reusing tabs for apps that do not specify an appId.
        if (appId == null
                || IntentUtils.safeGetBooleanExtra(intent, Browser.EXTRA_CREATE_NEW_TAB, false)) {
            return TabOpenType.OPEN_NEW_TAB;
        }

        // Intents from chrome open in the same tab by default, all others only clobber
        // tabs created by the same app.
        return mPackageName.equals(appId) ? TabOpenType.CLOBBER_CURRENT_TAB
                                          : TabOpenType.REUSE_APP_ID_MATCHING_TAB_ELSE_NEW_TAB;
    }

    private boolean isInvalidScheme(String scheme) {
        return scheme != null && (scheme.toLowerCase(Locale.US).equals("javascript")
                                         || scheme.toLowerCase(Locale.US).equals("jar"));
    }

    /**
     * Parses the scheme out of the URL if possible, trimming and getting rid of unsafe characters.
     * This is useful for determining if a URL has a sneaky, unsafe scheme, e.g. "java  script" or
     * "j$a$r". See: http://crbug.com/248398
     * @return The sanitized URL scheme or null if no scheme is specified.
     */
    private String getSanitizedUrlScheme(String url) {
        if (url == null) {
            return null;
        }

        int colonIdx = url.indexOf(":");
        if (colonIdx < 0) {
            // No scheme specified for the url
            return null;
        }

        String scheme = url.substring(0, colonIdx).toLowerCase(Locale.US).trim();

        // Check for the presence of and get rid of all non-alphanumeric characters in the scheme,
        // except dash, plus and period. Those are the only valid scheme chars:
        // https://tools.ietf.org/html/rfc3986#section-3.1
        boolean nonAlphaNum = false;
        for (char ch : scheme.toCharArray()) {
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '+' && ch != '.') {
                nonAlphaNum = true;
                break;
            }
        }

        if (nonAlphaNum) {
            scheme = scheme.replaceAll("[^a-z0-9.+-]", "");
        }
        return scheme;
    }

    private boolean isJavascriptSchemeOrInvalidUrl(String url) {
        String urlScheme = getSanitizedUrlScheme(url);
        return isInvalidScheme(urlScheme);
    }

    /**
     * Retrieve the URL from the Intent, which may be in multiple locations.
     * @param intent Intent to examine.
     * @return URL from the Intent, or null if a valid URL couldn't be found.
     */
    public static String getUrlFromIntent(Intent intent) {
        if (intent == null) return null;

        String url = getUrlFromVoiceSearchResult(intent);
        if (url == null) url = ActivityDelegate.getInitialUrlForDocument(intent);
        if (url == null) url = getUrlForCustomTab(intent);
        if (url == null) url = intent.getDataString();
        if (url == null) return null;

        url = url.trim();
        if (isGoogleChromeScheme(url)) {
            url = getUrlFromGoogleChromeSchemeUrl(url);
        }
        return TextUtils.isEmpty(url) ? null : url;
    }

    private static String getUrlForCustomTab(Intent intent) {
        if (intent == null || intent.getData() == null) return null;
        Uri data = intent.getData();
        return TextUtils.equals(data.getScheme(), UrlConstants.CUSTOM_TAB_SCHEME)
                ? data.getQuery() : null;
    }

    /**
     * Adjusts the URL to account for the googlechrome:// scheme.
     * Currently, its only use is to handle navigations.
     * @param url URL to be processed
     * @return The string with the scheme and prefixes chopped off, if a valid prefix was used.
     *         Otherwise returns null.
     */
    public static String getUrlFromGoogleChromeSchemeUrl(String url) {
        if (url.toLowerCase(Locale.US).startsWith(GOOGLECHROME_NAVIGATE_PREFIX)) {
            return url.substring(GOOGLECHROME_NAVIGATE_PREFIX.length());
        }

        return null;
    }

    /**
     * @param url URL to be tested
     * @return Whether the given URL adheres to the googlechrome:// scheme definition.
     */
    public static boolean isGoogleChromeScheme(String url) {
        if (url == null) return false;
        String urlScheme = Uri.parse(url).getScheme();
        return urlScheme != null && urlScheme.equals(GOOGLECHROME_SCHEME);
    }

    // TODO(mariakhomenko): pending referrer and pending incognito intent could potentially
    // not work correctly in multi-window. Store per-window information instead.

    /**
     * Records a pending referrer URL that we may be sending to ourselves through an intent.
     * @param intent The intent to which we add a referrer.
     * @param url The referrer URL.
     */
    public static void setPendingReferrer(Intent intent, String url) {
        intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(url));
        intent.putExtra(IntentHandler.EXTRA_REFERRER_ID, ++sReferrerId);
        sPendingReferrer = new Pair<Integer, String>(sReferrerId, url);
    }

    /**
     * Clears any pending referrer data.
     */
    public static void clearPendingReferrer() {
        sPendingReferrer = null;
    }

    /**
     * Retrieves pending referrer URL based on the given id.
     * @param id The referrer id.
     * @return The URL for the referrer or null if none found.
     */
    public static String getPendingReferrerUrl(int id) {
        if (sPendingReferrer != null && (sPendingReferrer.first == id)) {
            return sPendingReferrer.second;
        }
        return null;
    }

    /**
     * Keeps track of pending incognito URL to be loaded and ensures we allow to load it if it
     * comes back to us. This is a method for dispatching incognito URL intents from Chrome that
     * may or may not end up in Chrome.
     * @param intent The intent that will be sent.
     */
    public static void setPendingIncognitoUrl(Intent intent) {
        if (intent.getData() != null) {
            intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
            sPendingIncognitoUrl = intent.getDataString();
        }
    }

    /**
     * Clears the pending incognito URL.
     */
    public static void clearPendingIncognitoUrl() {
        sPendingIncognitoUrl = null;
    }

    /**
     * @return Pending incognito URL that is allowed to be loaded without system token.
     */
    public static String getPendingIncognitoUrl() {
        return sPendingIncognitoUrl;
    }

    /**
     * Some applications may request to load the URL with a particular transition type.
     * @param context The application context.
     * @param intent Intent causing the URL load, may be null.
     * @param defaultTransition The transition to return if none specified in the intent.
     * @return The transition type to use for loading the URL.
     */
    public static int getTransitionTypeFromIntent(Context context, Intent intent,
            int defaultTransition) {
        if (intent == null) return defaultTransition;
        int transitionType = IntentUtils.safeGetIntExtra(
                intent, IntentHandler.EXTRA_PAGE_TRANSITION_TYPE, PageTransition.LINK);
        if (transitionType == PageTransition.TYPED) {
            return transitionType;
        } else if (transitionType != PageTransition.LINK
                && isIntentChromeOrFirstParty(intent, context)) {
            // 1st party applications may specify any transition type.
            return transitionType;
        }
        return defaultTransition;
    }
}
