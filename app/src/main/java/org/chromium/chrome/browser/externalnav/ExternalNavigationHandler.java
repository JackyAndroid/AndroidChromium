// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalnav;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Browser;
import android.text.TextUtils;
import android.webkit.WebView;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.ui.base.PageTransition;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Logic related to the URL overriding/intercepting functionality.
 * This feature allows Chrome to convert certain navigations to Android Intents allowing
 * applications like Youtube to direct users clicking on a http(s) link to their native app.
 */
public class ExternalNavigationHandler {
    private static final String TAG = "UrlHandler";
    private static final String SCHEME_WTAI = "wtai://wp/";
    private static final String SCHEME_WTAI_MC = "wtai://wp/mc;";
    private static final String SCHEME_SMS = "sms";

    @VisibleForTesting
    static final String EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url";

    // Supervisor package name
    private static final Object SUPERVISOR_PKG = "com.google.android.instantapps.supervisor";

    // An extra that may be specified on an intent:// URL that contains an encoded value for the
    // referrer field passed to the market:// URL in the case where the app is not present.
    @VisibleForTesting
    static final String EXTRA_MARKET_REFERRER = "market_referrer";

    private final ExternalNavigationDelegate mDelegate;

    /**
     * Result types for checking if we should override URL loading.
     * NOTE: this enum is used in UMA, do not reorder values. Changes should be append only.
     */
    public enum OverrideUrlLoadingResult {
        /* We should override the URL loading and launch an intent. */
        OVERRIDE_WITH_EXTERNAL_INTENT,
        /* We should override the URL loading and clobber the current tab. */
        OVERRIDE_WITH_CLOBBERING_TAB,
        /* We should override the URL loading.  The desired action will be determined
         * asynchronously (e.g. by requiring user confirmation). */
        OVERRIDE_WITH_ASYNC_ACTION,
        /* We shouldn't override the URL loading. */
        NO_OVERRIDE,
    }

    /**
     * A constructor for UrlHandler.
     *
     * @param tab The tab that initiated the external intent.
     */
    public ExternalNavigationHandler(Tab tab) {
        this(new ExternalNavigationDelegateImpl(tab));
    }

    /**
     * Constructs a new instance of {@link ExternalNavigationHandler}, using the injected
     * {@link ExternalNavigationDelegate}.
     */
    public ExternalNavigationHandler(ExternalNavigationDelegate delegate) {
        mDelegate = delegate;
    }

    /**
     * Determines whether the URL needs to be sent as an intent to the system,
     * and sends it, if appropriate.
     * @return Whether the URL generated an intent, caused a navigation in
     *         current tab, or wasn't handled at all.
     */
    public OverrideUrlLoadingResult shouldOverrideUrlLoading(ExternalNavigationParams params) {
        Intent intent;
        // Perform generic parsing of the URI to turn it into an Intent.
        try {
            intent = Intent.parseUri(params.getUrl(), Intent.URI_INTENT_SCHEME);
        } catch (Exception ex) {
            Log.w(TAG, "Bad URI %s", params.getUrl(), ex);
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        boolean hasBrowserFallbackUrl = false;
        String browserFallbackUrl =
                IntentUtils.safeGetStringExtra(intent, EXTRA_BROWSER_FALLBACK_URL);
        if (browserFallbackUrl != null
                && UrlUtilities.isValidForIntentFallbackNavigation(browserFallbackUrl)) {
            hasBrowserFallbackUrl = true;
        } else {
            browserFallbackUrl = null;
        }

        long time = SystemClock.elapsedRealtime();
        OverrideUrlLoadingResult result = shouldOverrideUrlLoadingInternal(
                params, intent, hasBrowserFallbackUrl, browserFallbackUrl);
        RecordHistogram.recordTimesHistogram("Android.StrictMode.OverrideUrlLoadingTime",
                SystemClock.elapsedRealtime() - time, TimeUnit.MILLISECONDS);

        if (result == OverrideUrlLoadingResult.NO_OVERRIDE && hasBrowserFallbackUrl
                && (params.getRedirectHandler() == null
                        // For instance, if this is a chained fallback URL, we ignore it.
                        || !params.getRedirectHandler().shouldNotOverrideUrlLoading())) {
            return clobberCurrentTabWithFallbackUrl(browserFallbackUrl, params);
        }
        return result;
    }

    private boolean resolversSubsetOf(List<ResolveInfo> infos, List<ResolveInfo> container) {
        HashSet<ComponentName> containerSet = new HashSet<>();
        for (ResolveInfo info : container) {
            containerSet.add(
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
        }
        for (ResolveInfo info : infos) {
            if (!containerSet.contains(new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name))) {
                return false;
            }
        }
        return true;
    }

    private OverrideUrlLoadingResult shouldOverrideUrlLoadingInternal(
            ExternalNavigationParams params, Intent intent, boolean hasBrowserFallbackUrl,
            String browserFallbackUrl) {
        // http://crbug.com/441284 : Disallow firing external intent while Chrome is in the
        // background.
        if (params.isApplicationMustBeInForeground() && !mDelegate.isChromeAppInForeground()) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }
        // http://crbug.com/464669 : Disallow firing external intent from background tab.
        if (params.isBackgroundTabNavigation()) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // http://crbug.com/605302 : Allow Chrome to handle all pdf file downloads.
        if (mDelegate.isPdfDownload(params.getUrl())) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // pageTransition is a combination of an enumeration (core value) and bitmask.
        int pageTransitionCore = params.getPageTransition() & PageTransition.CORE_MASK;
        boolean isLink = pageTransitionCore == PageTransition.LINK;
        boolean isFormSubmit = pageTransitionCore == PageTransition.FORM_SUBMIT;
        boolean isFromIntent = (params.getPageTransition() & PageTransition.FROM_API) != 0;
        boolean isForwardBackNavigation =
                (params.getPageTransition() & PageTransition.FORWARD_BACK) != 0;
        boolean isExternalProtocol = !UrlUtilities.isAcceptedScheme(params.getUrl());

        // http://crbug.com/169549 : If you type in a URL that then redirects in server side to an
        // link that cannot be rendered by the browser, we want to show the intent picker.
        boolean isTyped = pageTransitionCore == PageTransition.TYPED;
        boolean typedRedirectToExternalProtocol = isTyped && params.isRedirect()
                && isExternalProtocol;

        // We do not want to show the intent picker for core types typed, bookmarks, auto toplevel,
        // generated, keyword, keyword generated. See below for exception to typed URL and
        // redirects:
        // - http://crbug.com/143118 : URL intercepting should not be invoked on navigations
        //   initiated by the user in the omnibox / NTP.
        // - http://crbug.com/159153 : Don't override http or https URLs from the NTP or bookmarks.
        // - http://crbug.com/162106: Intent picker should not be presented on returning to a page.
        //   This should be covered by not showing the picker if the core type is reload.

        // http://crbug.com/164194 . A navigation forwards or backwards should never trigger
        // the intent picker.
        if (isForwardBackNavigation) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // If accessing a file URL, ensure that the user has granted the necessary file access
        // to Chrome.  This check should happen for reloads, navigations, etc..., which is why
        // it occurs before the subsequent blocks.
        if (params.getUrl().startsWith("file:")
                && mDelegate.shouldRequestFileAccess(params.getUrl(), params.getTab())) {
            mDelegate.startFileIntent(
                    intent, params.getReferrerUrl(), params.getTab(),
                    params.shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent());
            return OverrideUrlLoadingResult.OVERRIDE_WITH_ASYNC_ACTION;
        }

        // http://crbug.com/149218: We want to show the intent picker for ordinary links, providing
        // the link is not an incoming intent from another application, unless it's a redirect (see
        // below).
        boolean linkNotFromIntent = isLink && !isFromIntent;

        boolean isOnEffectiveIntentRedirect = params.getRedirectHandler() == null ? false
                : params.getRedirectHandler().isOnEffectiveIntentRedirectChain();

        // http://crbug.com/170925: We need to show the intent picker when we receive an intent from
        // another app that 30x redirects to a YouTube/Google Maps/Play Store/Google+ URL etc.
        boolean incomingIntentRedirect = (isLink && isFromIntent && params.isRedirect())
                || isOnEffectiveIntentRedirect;


        // http://crbug/331571 : Do not override a navigation started from user typing.
        // http://crbug/424029 : Need to stay in Chrome for an intent heading explicitly to Chrome.
        if (params.getRedirectHandler() != null) {
            TabRedirectHandler handler = params.getRedirectHandler();
            if (handler.shouldStayInChrome(isExternalProtocol)
                    || handler.shouldNotOverrideUrlLoading()) {
                // http://crbug.com/659301: Handle redirects to Instant Apps out of Custom Tabs.
                if (handler.isFromCustomTabIntent()
                        && !isExternalProtocol
                        && incomingIntentRedirect
                        && !handler.shouldNavigationTypeStayInChrome()
                        && mDelegate.maybeLaunchInstantApp(params.getTab(), params.getUrl(),
                                params.getReferrerUrl(), true)) {
                    return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
                }
                return OverrideUrlLoadingResult.NO_OVERRIDE;
            }
        }

        // http://crbug.com/647569 : Stay in a PWA window for a URL within the same scope.
        if (mDelegate.isWithinCurrentWebappScope(params.getUrl())) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // http://crbug.com/181186: We need to show the intent picker when we receive a redirect
        // following a form submit.
        boolean isRedirectFromFormSubmit = isFormSubmit && params.isRedirect();

        if (!typedRedirectToExternalProtocol) {
            if (!linkNotFromIntent && !incomingIntentRedirect && !isRedirectFromFormSubmit) {
                return OverrideUrlLoadingResult.NO_OVERRIDE;
            }
            if (params.getRedirectHandler() != null
                    && params.getRedirectHandler().isNavigationFromUserTyping()) {
                return OverrideUrlLoadingResult.NO_OVERRIDE;
            }
        }

        // Don't override navigation from a chrome:* url to http or https. For example,
        // when clicking a link in bookmarks or most visited. When navigating from such a
        // page, there is clear intent to complete the navigation in Chrome.
        if (params.getReferrerUrl() != null && params.getReferrerUrl().startsWith(
                UrlConstants.CHROME_SCHEME) && (params.getUrl().startsWith(UrlConstants.HTTP_SCHEME)
                        || params.getUrl().startsWith(UrlConstants.HTTPS_SCHEME))) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        if (params.getUrl().startsWith(SCHEME_WTAI_MC)) {
            // wtai://wp/mc;number
            // number=string(phone-number)
            mDelegate.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(WebView.SCHEME_TEL
                            + params.getUrl().substring(SCHEME_WTAI_MC.length()))), false);
            return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
        }

        if (params.getUrl().startsWith(SCHEME_WTAI)) {
            // TODO: handle other WTAI schemes.
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // The "about:", "chrome:", and "chrome-native:" schemes are internal to the browser;
        // don't want these to be dispatched to other apps.
        if (params.getUrl().startsWith("about:")
                || params.getUrl().startsWith("chrome:")
                || params.getUrl().startsWith("chrome-native:")) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // The "content:" scheme is disabled in Clank. Do not try to start an activity.
        if (params.getUrl().startsWith("content:")) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // Special case - It makes no sense to use an external application for a YouTube
        // pairing code URL, since these match the current tab with a device (Chromecast
        // or similar) it is supposed to be controlling. Using a different application
        // that isn't expecting this (in particular YouTube) doesn't work.
        if (params.getUrl().matches(".*youtube\\.com.*[?&]pairingCode=.*")) {
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // TODO(changwan): check if we need to handle URL even when external intent is off.
        if (CommandLine.getInstance().hasSwitch(
                ChromeSwitches.DISABLE_EXTERNAL_INTENT_REQUESTS)) {
            Log.w(TAG, "External intent handling is disabled by a command-line flag.");
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        // Sanitize the Intent, ensuring web pages can not bypass browser
        // security (only access to BROWSABLE activities).
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        Intent selector = intent.getSelector();
        if (selector != null) {
            selector.addCategory(Intent.CATEGORY_BROWSABLE);
            selector.setComponent(null);
        }

        List<ResolveInfo> resolvingInfos = mDelegate.queryIntentActivities(intent);
        boolean canResolveActivity = resolvingInfos.size() > 0;
        // check whether the intent can be resolved. If not, we will see
        // whether we can download it from the Market.
        if (!canResolveActivity) {
            if (hasBrowserFallbackUrl) {
                return clobberCurrentTabWithFallbackUrl(browserFallbackUrl, params);
            }

            String packagename = intent.getPackage();
            if (packagename != null) {
                String marketReferrer =
                        IntentUtils.safeGetStringExtra(intent, EXTRA_MARKET_REFERRER);
                if (TextUtils.isEmpty(marketReferrer)) {
                    marketReferrer = mDelegate.getPackageName();
                }
                try {
                    Uri marketUri = new Uri.Builder()
                            .scheme("market")
                            .authority("details")
                            .appendQueryParameter("id", packagename)
                            .appendQueryParameter("referrer", Uri.decode(marketReferrer))
                            .build();
                    intent = new Intent(Intent.ACTION_VIEW, marketUri);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setPackage("com.android.vending");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (params.getReferrerUrl() != null) {
                        intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(params.getReferrerUrl()));
                    }
                    mDelegate.startActivity(intent, false);
                    return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
                } catch (ActivityNotFoundException ex) {
                    // ignore the error on devices that does not have
                    // play market installed.
                    return OverrideUrlLoadingResult.NO_OVERRIDE;
                }
            }

            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }

        if (hasBrowserFallbackUrl) {
            intent.removeExtra(EXTRA_BROWSER_FALLBACK_URL);
        }

        if (intent.getPackage() == null) {
            final Uri uri = intent.getData();
            if (uri != null && SCHEME_SMS.equals(uri.getScheme())) {
                intent.setPackage(getDefaultSmsPackageName(resolvingInfos));
            }
        }

        // Set the Browser application ID to us in case the user chooses Chrome
        // as the app.  This will make sure the link is opened in the same tab
        // instead of making a new one.
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mDelegate.getPackageName());
        if (params.isOpenInNewTab()) intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mDelegate.maybeSetWindowId(intent);
        mDelegate.maybeRecordAppHandlersInIntent(intent, resolvingInfos);

        if (params.getReferrerUrl() != null) {
            IntentHandler.setPendingReferrer(intent, params.getReferrerUrl());
        }

        if (params.isIncognito()) {
            // In incognito mode, links that can be handled within the browser should just do so,
            // without asking the user.
            if (!isExternalProtocol) {
                return OverrideUrlLoadingResult.NO_OVERRIDE;
            }

            IntentHandler.setPendingIncognitoUrl(intent);
        }

        // Make sure webkit can handle it internally before checking for specialized
        // handlers. If webkit can't handle it internally, we need to call
        // startActivityIfNeeded or startActivity.
        if (!isExternalProtocol) {
            if (!mDelegate.isSpecializedHandlerAvailable(resolvingInfos)) {
                if (params.webApkPackageName() != null) {
                    intent.setPackage(mDelegate.getPackageName());
                    mDelegate.startActivity(intent, false);
                    return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
                }

                if (incomingIntentRedirect && mDelegate.maybeLaunchInstantApp(
                        params.getTab(), params.getUrl(), params.getReferrerUrl(), true)) {
                    return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
                } else if (linkNotFromIntent && !params.isIncognito()
                        && mDelegate.maybeLaunchInstantApp(params.getTab(), params.getUrl(),
                                params.getReferrerUrl(), false)) {
                    return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
                }

                return OverrideUrlLoadingResult.NO_OVERRIDE;
            }

            if (params.getReferrerUrl() != null && (isLink || isFormSubmit)) {
                // Current URL has at least one specialized handler available. For navigations
                // within the same host, keep the navigation inside the browser unless the set of
                // available apps to handle the new navigation is different. http://crbug.com/463138
                URI currentUri;
                URI previousUri;
                try {
                    currentUri = new URI(params.getUrl());
                    previousUri = new URI(params.getReferrerUrl());
                } catch (Exception e) {
                    currentUri = null;
                    previousUri = null;
                }

                if (currentUri != null && previousUri != null
                        && TextUtils.equals(currentUri.getHost(), previousUri.getHost())) {
                    Intent previousIntent;
                    try {
                        previousIntent = Intent.parseUri(
                                params.getReferrerUrl(), Intent.URI_INTENT_SCHEME);
                    } catch (Exception e) {
                        previousIntent = null;
                    }

                    if (previousIntent != null
                            && resolversSubsetOf(resolvingInfos,
                                       mDelegate.queryIntentActivities(previousIntent))) {
                        return OverrideUrlLoadingResult.NO_OVERRIDE;
                    }
                }
            }
        }

        boolean isDirectInstantAppsIntent = isExternalProtocol
                && SUPERVISOR_PKG.equals(intent.getPackage());
        boolean shouldProxyForInstantApps = isDirectInstantAppsIntent
                && mDelegate.isSerpReferrer(params.getReferrerUrl(), params.getTab());
        if (shouldProxyForInstantApps) {
            intent.putExtra(InstantAppsHandler.IS_GOOGLE_SEARCH_REFERRER, true);
        } else if (isDirectInstantAppsIntent) {
            // For security reasons, we disable all intent:// URLs to Instant Apps that are
            // not coming from SERP.
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        } else {
            // Make sure this extra is not sent unless we've done the verification.
            intent.removeExtra(InstantAppsHandler.IS_GOOGLE_SEARCH_REFERRER);
        }

        try {
            if (params.isIncognito() && !mDelegate.willChromeHandleIntent(intent)) {
                // This intent may leave Chrome.  Warn the user that incognito does not carry over
                // to apps out side of Chrome.
                mDelegate.startIncognitoIntent(intent, params.getReferrerUrl(),
                        hasBrowserFallbackUrl ? browserFallbackUrl : null, params.getTab(),
                        params.shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent(),
                        shouldProxyForInstantApps);
                return OverrideUrlLoadingResult.OVERRIDE_WITH_ASYNC_ACTION;
            }

            // Some third-party app launched Chrome with an intent, and the URL got redirected. The
            // user has explicitly chosen Chrome over other intent handlers, so stay in Chrome
            // unless there was a new intent handler after redirection or Chrome cannot handle it
            // any more.
            // Custom tabs are an exception to this rule, since at no point, the user sees an intent
            // picker and "picking Chrome" is handled inside the support library.
            if (params.getRedirectHandler() != null && incomingIntentRedirect) {
                if (!isExternalProtocol && !params.getRedirectHandler().isFromCustomTabIntent()
                        && !params.getRedirectHandler().hasNewResolver(intent)) {
                    return OverrideUrlLoadingResult.NO_OVERRIDE;
                }
            }

            // The intent can be used to launch Chrome itself, record the user
            // gesture here so that it can be used later.
            if (params.hasUserGesture()) {
                IntentWithGesturesHandler.getInstance().onNewIntentWithGesture(intent);
            }

            if (ChromeWebApkHost.isEnabled()) {
                // If the only specialized intent handler is a WebAPK, set the intent's package to
                // launch the WebAPK without showing the intent picker.
                String targetWebApkPackageName = mDelegate.findWebApkPackageName(resolvingInfos);

                // We can't rely on this falling through to startActivityIfNeeded and behaving
                // correctly for WebAPKs. This is because the target of the intent is the WebApk's
                // main activity but that's just a bouncer which will redirect to WebApkActivity in
                // chrome. To avoid bouncing indefinitely, don't override the navigation if we are
                // currently showing the WebApk |params.webApkPackageName()| that we will redirect
                // to.
                if (targetWebApkPackageName != null
                        && targetWebApkPackageName.equals(params.webApkPackageName())) {
                    return OverrideUrlLoadingResult.NO_OVERRIDE;
                }

                if (targetWebApkPackageName != null
                        && mDelegate.countSpecializedHandlers(resolvingInfos) == 1) {
                    intent.setPackage(targetWebApkPackageName);
                }
            }

            if (mDelegate.startActivityIfNeeded(intent, shouldProxyForInstantApps)) {
                return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
            }

            return OverrideUrlLoadingResult.NO_OVERRIDE;
        } catch (ActivityNotFoundException ex) {
            // Ignore the error. If no application can handle the URL,
            // assume the browser can handle it.
        }

        return OverrideUrlLoadingResult.NO_OVERRIDE;
    }

    /**
     * Clobber the current tab with fallback URL.
     *
     * @param browserFallbackUrl The fallback URL.
     * @param params The external navigation params.
     * @return {@link OverrideUrlLoadingResult} if the tab was clobbered, or we launched an
     *         intent.
     */
    private OverrideUrlLoadingResult clobberCurrentTabWithFallbackUrl(
            String browserFallbackUrl, ExternalNavigationParams params) {
        if (!params.isMainFrame()) {
            // For subframes, we don't support fallback url for now.
            // http://crbug.com/364522.
            return OverrideUrlLoadingResult.NO_OVERRIDE;
        }
        // NOTE: any further redirection from fall-back URL should not override URL loading.
        // Otherwise, it can be used in chain for fingerprinting multiple app installation
        // status in one shot. In order to prevent this scenario, we notify redirection
        // handler that redirection from the current navigation should stay in Chrome.
        if (params.getRedirectHandler() != null) {
            params.getRedirectHandler().setShouldNotOverrideUrlLoadingUntilNewUrlLoading();
        }
        return mDelegate.clobberCurrentTab(
                browserFallbackUrl, params.getReferrerUrl(), params.getTab());
    }

    /**
     * @return Whether the |url| could be handled by an external application on the system.
     */
    public boolean canExternalAppHandleUrl(String url) {
        if (url.startsWith(SCHEME_WTAI_MC)) return true;
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            return intent.getPackage() != null
                    || mDelegate.queryIntentActivities(intent).size() > 0;
        } catch (Exception ex) {
            // Ignore the error.
            Log.w(TAG, "Bad URI %s", url, ex);
        }
        return false;
    }

    /**
     * Dispatch SMS intents to the default SMS application if applicable.
     * Most SMS apps refuse to send SMS if not set as default SMS application.
     *
     * @param resolvingComponentNames The list of ComponentName that resolves the current intent.
     */
    private String getDefaultSmsPackageName(List<ResolveInfo> resolvingComponentNames) {
        String defaultSmsPackageName = mDelegate.getDefaultSmsPackageName();
        if (defaultSmsPackageName == null) return null;
        // Makes sure that the default SMS app actually resolves the intent.
        for (ResolveInfo resolveInfo : resolvingComponentNames) {
            if (defaultSmsPackageName.equals(resolveInfo.activityInfo.packageName)) {
                return defaultSmsPackageName;
            }
        }
        return null;
    }
}
