// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalnav;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.os.TransactionTooLargeException;
import android.provider.Browser;
import android.provider.Telephony;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeTabbedActivity2;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler.OverrideUrlLoadingResult;
import org.chromium.chrome.browser.instantapps.AuthenticatedProxyActivity;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.webapps.WebappActivity;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;
import org.chromium.webapk.lib.client.WebApkValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * The main implementation of the {@link ExternalNavigationDelegate}.
 */
public class ExternalNavigationDelegateImpl implements ExternalNavigationDelegate {
    private static final String TAG = "ExternalNavigationDelegateImpl";
    private static final String PDF_VIEWER = "com.google.android.apps.docs";
    private static final String PDF_MIME = "application/pdf";
    private static final String PDF_SUFFIX = ".pdf";
    private static final String PDF_EXTENSION = "pdf";

    protected final Context mApplicationContext;
    private final Tab mTab;

    public ExternalNavigationDelegateImpl(Tab tab) {
        mTab = tab;
        mApplicationContext = tab.getWindowAndroid().getApplicationContext();
    }

    /**
     * Get a {@link Context} linked to this delegate with preference to {@link Activity}.
     * The tab this delegate associates with can swap the {@link Activity} it is hosted in and
     * during the swap, there might not be an available {@link Activity}.
     * @return The activity {@link Context} if it can be reached.
     *         Application {@link Context} if not.
     */
    protected final Context getAvailableContext() {
        if (mTab.getWindowAndroid() == null) return mApplicationContext;
        Context activityContext = WindowAndroid.activityFromContext(
                mTab.getWindowAndroid().getContext().get());
        if (activityContext == null) return mApplicationContext;
        return activityContext;
    }

    /**
     * If the intent is for a pdf, resolves intent handlers to find the platform pdf viewer if
     * it is available and force is for the provided |intent| so that the user doesn't need to
     * choose it from Intent picker.
     *
     * @param context Context of the app.
     * @param intent Intent to open.
     */
    public static void forcePdfViewerAsIntentHandlerIfNeeded(Context context, Intent intent) {
        if (intent == null || !isPdfIntent(intent)) return;
        resolveIntent(context, intent, true /* allowSelfOpen (ignored) */);
    }

    /**
     * Retrieve the best activity for the given intent. If a default activity is provided,
     * choose the default one. Otherwise, return the Intent picker if there are more than one
     * capable activities. If the intent is pdf type, return the platform pdf viewer if
     * it is available so user don't need to choose it from Intent picker.
     *
     * Note this function is slow on Android versions less than Lollipop.
     *
     * @param context Context of the app.
     * @param intent Intent to open.
     * @param allowSelfOpen Whether chrome itself is allowed to open the intent.
     * @return true if the intent can be resolved, or false otherwise.
     */
    public static boolean resolveIntent(Context context, Intent intent, boolean allowSelfOpen) {
        try {
            boolean activityResolved = false;
            ResolveInfo info = context.getPackageManager().resolveActivity(intent, 0);
            if (info != null) {
                final String packageName = context.getPackageName();
                if (info.match != 0) {
                    // There is a default activity for this intent, use that.
                    if (allowSelfOpen || !packageName.equals(info.activityInfo.packageName)) {
                        activityResolved = true;
                    }
                } else {
                    List<ResolveInfo> handlers = context.getPackageManager().queryIntentActivities(
                            intent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (handlers != null && !handlers.isEmpty()) {
                        activityResolved = true;
                        boolean canSelfOpen = false;
                        boolean hasPdfViewer = false;
                        for (ResolveInfo resolveInfo : handlers) {
                            String pName = resolveInfo.activityInfo.packageName;
                            if (packageName.equals(pName)) {
                                canSelfOpen = true;
                            } else if (PDF_VIEWER.equals(pName)) {
                                if (isPdfIntent(intent)) {
                                    intent.setClassName(pName, resolveInfo.activityInfo.name);
                                    Uri referrer = new Uri.Builder().scheme(
                                            IntentHandler.ANDROID_APP_REFERRER_SCHEME).authority(
                                                    packageName).build();
                                    intent.putExtra(Intent.EXTRA_REFERRER, referrer);
                                    hasPdfViewer = true;
                                    break;
                                }
                            }
                        }
                        if ((canSelfOpen && !allowSelfOpen) && !hasPdfViewer) {
                            activityResolved = false;
                        }
                    }
                }
            }
            return activityResolved;
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
        return false;
    }

    private static boolean isPdfIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        String filename = intent.getData().getLastPathSegment();
        return (filename != null && filename.endsWith(PDF_SUFFIX))
                || PDF_MIME.equals(intent.getType());
    }

    /**
     * Retrieve information about the Activity that will handle the given Intent.
     *
     * Note this function is slow on Android versions less than Lollipop.
     *
     * @param intent Intent to resolve.
     * @return       ResolveInfo of the Activity that will handle the Intent, or null if it failed.
     */
    public static ResolveInfo resolveActivity(Intent intent) {
        // This function is expensive on KK and below and should not be called from main thread.
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                || !ThreadUtils.runningOnUiThread();
        try {
            Context context = ContextUtils.getApplicationContext();
            PackageManager pm = context.getPackageManager();
            return pm.resolveActivity(intent, 0);
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
        return null;
    }

    /**
     * Determines whether Chrome will be handling the given Intent.
     *
     * Note this function is slow on Android versions less than Lollipop.
     *
     * @param context           Context that will be firing the Intent.
     * @param intent            Intent that will be fired.
     * @param matchDefaultOnly  See {@link PackageManager#MATCH_DEFAULT_ONLY}.
     * @return                  True if Chrome will definitely handle the intent, false otherwise.
     */
    public static boolean willChromeHandleIntent(
            Context context, Intent intent, boolean matchDefaultOnly) {
        try {
            // Early-out if the intent targets Chrome.
            if (intent.getComponent() != null
                    && context.getPackageName().equals(intent.getComponent().getPackageName())) {
                return true;
            }

            // Fall back to the more expensive querying of Android when the intent doesn't target
            // Chrome.
            ResolveInfo info = context.getPackageManager().resolveActivity(
                    intent, matchDefaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0);
            return info != null
                    && info.activityInfo.packageName.equals(context.getPackageName());
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
            return false;
        }
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent) {
        // White-list for Samsung. See http://crbug.com/613977 for more context.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return mApplicationContext.getPackageManager().queryIntentActivities(intent,
                    PackageManager.GET_RESOLVED_FILTER);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    @Override
    public boolean willChromeHandleIntent(Intent intent) {
        return willChromeHandleIntent(mApplicationContext, intent, false);
    }

    @Override
    public boolean isSpecializedHandlerAvailable(List<ResolveInfo> infos) {
        return countSpecializedHandlers(infos) > 0;
    }

    @Override
    public boolean isWithinCurrentWebappScope(String url) {
        Context context = getAvailableContext();
        if (context instanceof WebappActivity) {
            String scope = ((WebappActivity) context).getWebappScope();
            return url.startsWith(scope);
        }
        return false;
    }

    @Override
    public int countSpecializedHandlers(List<ResolveInfo> infos) {
        return getSpecializedHandlersWithFilter(infos, null).size();
    }

    @VisibleForTesting
    static ArrayList<String> getSpecializedHandlersWithFilter(
            List<ResolveInfo> infos, String filterPackageName) {
        ArrayList<String> result = new ArrayList<>();
        if (infos == null) {
            return result;
        }

        int count = 0;
        for (ResolveInfo info : infos) {
            IntentFilter filter = info.filter;
            if (filter == null) {
                // Error on the side of classifying ResolveInfo as generic.
                continue;
            }
            if (filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0) {
                // Don't count generic handlers.
                continue;
            }

            if (!TextUtils.isEmpty(filterPackageName)
                    && (info.activityInfo == null
                               || !info.activityInfo.packageName.equals(filterPackageName))) {
                continue;
            }

            result.add(info.activityInfo != null ? info.activityInfo.packageName : "");
        }
        return result;
    }

    /**
     * Check whether the given package is a specialized handler for the given intent
     *
     * @param context {@link Context} to use for getting the {@link PackageManager}.
     * @param packageName Package name to check against. Can be null or empty.
     * @param intent The intent to resolve for.
     * @return Whether the given package is a specialized handler for the given intent. If there is
     *         no package name given checks whether there is any specialized handler.
     */
    public static boolean isPackageSpecializedHandler(
            Context context, String packageName, Intent intent) {
        try {
            List<ResolveInfo> handlers = context.getPackageManager().queryIntentActivities(
                    intent, PackageManager.GET_RESOLVED_FILTER);
            return getSpecializedHandlersWithFilter(handlers, packageName).size() > 0;
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
        return false;
    }

    @Override
    public String findWebApkPackageName(List<ResolveInfo> infos) {
        return WebApkValidator.findWebApkPackage(mApplicationContext, infos);
    }

    @Override
    public String getPackageName() {
        return mApplicationContext.getPackageName();
    }

    @Override
    public void startActivity(Intent intent, boolean proxy) {
        try {
            forcePdfViewerAsIntentHandlerIfNeeded(mApplicationContext, intent);
            if (proxy) {
                dispatchAuthenticatedIntent(intent);
            } else {
                Context context = getAvailableContext();
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            recordExternalNavigationDispatched(intent);
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
    }

    @Override
    public boolean startActivityIfNeeded(Intent intent, boolean proxy) {
        boolean activityWasLaunched;
        // Only touches disk on Kitkat. See http://crbug.com/617725 for more context.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        StrictMode.allowThreadDiskReads();
        try {
            forcePdfViewerAsIntentHandlerIfNeeded(mApplicationContext, intent);
            if (proxy) {
                dispatchAuthenticatedIntent(intent);
                activityWasLaunched = true;
            } else {
                Context context = getAvailableContext();
                if (context instanceof Activity) {
                    activityWasLaunched = ((Activity) context).startActivityIfNeeded(intent, -1);
                } else {
                    activityWasLaunched = false;
                }
            }
            if (activityWasLaunched) recordExternalNavigationDispatched(intent);
            return activityWasLaunched;
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void recordExternalNavigationDispatched(Intent intent) {
        ArrayList<String> specializedHandlers = intent.getStringArrayListExtra(
                IntentHandler.EXTRA_EXTERNAL_NAV_PACKAGES);
        if (specializedHandlers != null && specializedHandlers.size() > 0) {
            RecordUserAction.record("MobileExternalNavigationDispatched");
        }
    }

    /**
     * Shows an alert dialog prompting the user to leave incognito mode.
     *
     * @param activity The {@link Activity} to launch the dialog from.
     * @param onAccept Will be called when the user chooses to leave incognito.
     * @param onCancel Will be called when the user declines to leave incognito.
     */
    public static void showLeaveIncognitoWarningDialog(Activity activity,
            final OnClickListener onAccept, final OnCancelListener onCancel) {
        new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
            .setTitle(R.string.external_app_leave_incognito_warning_title)
            .setMessage(R.string.external_app_leave_incognito_warning)
            .setPositiveButton(R.string.ok, onAccept)
            .setNegativeButton(R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onCancel.onCancel(dialog);
                    }
                })
            .setOnCancelListener(onCancel)
            .show();
    }

    @Override
    public void startIncognitoIntent(final Intent intent, final String referrerUrl,
            final String fallbackUrl, final Tab tab, final boolean needsToCloseTab,
            final boolean proxy) {
        Context context = tab.getWindowAndroid().getContext().get();
        if (!(context instanceof Activity)) return;

        showLeaveIncognitoWarningDialog((Activity) context,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(intent, proxy);
                        if (tab != null && !tab.isClosing() && tab.isInitialized()
                                && needsToCloseTab) {
                            closeTab(tab);
                        }
                    }
                },
                new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        loadIntent(intent, referrerUrl, fallbackUrl, tab, needsToCloseTab, true);
                    }
                });
    }

    @Override
    public boolean shouldRequestFileAccess(String url, Tab tab) {
        // If the tab is null, then do not attempt to prompt for access.
        if (tab == null) return false;

        // If the url points inside of Chromium's data directory, no permissions are necessary.
        // This is required to prevent permission prompt when uses wants to access offline pages.
        if (url.startsWith("file://" + PathUtils.getDataDirectory())) {
            return false;
        }

        return !tab.getWindowAndroid().hasPermission(permission.WRITE_EXTERNAL_STORAGE)
                && tab.getWindowAndroid().canRequestPermission(permission.WRITE_EXTERNAL_STORAGE);
    }

    @Override
    public void startFileIntent(final Intent intent, final String referrerUrl, final Tab tab,
            final boolean needsToCloseTab) {
        PermissionCallback permissionCallback = new PermissionCallback() {
            @Override
            public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadIntent(intent, referrerUrl, null, tab, needsToCloseTab, tab.isIncognito());
                } else {
                    // TODO(tedchoc): Show an indication to the user that the navigation failed
                    //                instead of silently dropping it on the floor.
                    if (needsToCloseTab) {
                        // If the access was not granted, then close the tab if necessary.
                        closeTab(tab);
                    }
                }
            }
        };
        tab.getWindowAndroid().requestPermissions(
                new String[] {permission.WRITE_EXTERNAL_STORAGE}, permissionCallback);
    }

    private void loadIntent(Intent intent, String referrerUrl, String fallbackUrl, Tab tab,
            boolean needsToCloseTab, boolean launchIncogntio) {
        boolean needsToStartIntent = false;
        if (tab == null || tab.isClosing() || !tab.isInitialized()) {
            needsToStartIntent = true;
            needsToCloseTab = false;
        } else if (needsToCloseTab) {
            needsToStartIntent = true;
        }

        String url = fallbackUrl != null ? fallbackUrl : intent.getDataString();
        if (!UrlUtilities.isAcceptedScheme(url)) {
            if (needsToCloseTab) closeTab(tab);
            return;
        }

        if (needsToStartIntent) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
            if (launchIncogntio) intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setClassName(getPackageName(), ChromeLauncherActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            IntentHandler.addTrustedIntentExtras(intent, mApplicationContext);
            startActivity(intent, false);

            if (needsToCloseTab) closeTab(tab);
            return;
        }

        LoadUrlParams loadUrlParams = new LoadUrlParams(url, PageTransition.AUTO_TOPLEVEL);
        if (!TextUtils.isEmpty(referrerUrl)) {
            Referrer referrer = new Referrer(referrerUrl, Referrer.REFERRER_POLICY_ALWAYS);
            loadUrlParams.setReferrer(referrer);
        }
        tab.loadUrl(loadUrlParams);
    }

    @Override
    public OverrideUrlLoadingResult clobberCurrentTab(
            String url, String referrerUrl, Tab tab) {
        int transitionType = PageTransition.LINK;
        LoadUrlParams loadUrlParams = new LoadUrlParams(url, transitionType);
        if (!TextUtils.isEmpty(referrerUrl)) {
            Referrer referrer = new Referrer(referrerUrl, Referrer.REFERRER_POLICY_ALWAYS);
            loadUrlParams.setReferrer(referrer);
        }
        if (tab != null) {
            tab.loadUrl(loadUrlParams);
            return OverrideUrlLoadingResult.OVERRIDE_WITH_CLOBBERING_TAB;
        } else {
            assert false : "clobberCurrentTab was called with an empty tab.";
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setPackage(getPackageName());
            startActivity(intent, false);
            return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
        }
    }

    @Override
    public boolean isChromeAppInForeground() {
        return ApplicationStatus.getStateForApplication()
                == ApplicationState.HAS_RUNNING_ACTIVITIES;
    }

    @Override
    public void maybeSetWindowId(Intent intent) {
        Context context = getAvailableContext();
        if (!(context instanceof ChromeTabbedActivity2)) return;
        intent.putExtra(IntentHandler.EXTRA_WINDOW_ID, 2);
    }

    @Override
    public String getDefaultSmsPackageName() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null;
        return Telephony.Sms.getDefaultSmsPackage(mApplicationContext);
    }

    private static void logTransactionTooLargeOrRethrow(RuntimeException e, Intent intent) {
        // See http://crbug.com/369574.
        if (e.getCause() instanceof TransactionTooLargeException) {
            Log.e(TAG, "Could not resolve Activity for intent " + intent.toString(), e);
        } else {
            throw e;
        }
    }

    private void closeTab(Tab tab) {
        Context context = tab.getWindowAndroid().getContext().get();
        if (context instanceof ChromeActivity) {
            ((ChromeActivity) context).getTabModelSelector().closeTab(tab);
        }
    }

    @Override
    public boolean isPdfDownload(String url) {
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (TextUtils.isEmpty(fileExtension)) return false;

        return PDF_EXTENSION.equals(fileExtension);
    }

    @Override
    public void maybeRecordAppHandlersInIntent(Intent intent, List<ResolveInfo> infos) {
        intent.putExtra(IntentHandler.EXTRA_EXTERNAL_NAV_PACKAGES,
                getSpecializedHandlersWithFilter(infos, null));
    }

    @Override
    public boolean isSerpReferrer(String referrerUrl, Tab tab) {
        if (tab == null || tab.getWebContents() == null) {
            return false;
        }

        NavigationController nController = tab.getWebContents().getNavigationController();
        int index = nController.getLastCommittedEntryIndex();
        if (index == -1) return false;

        NavigationEntry entry = nController.getEntryAtIndex(index);
        if (entry == null) return false;

        return UrlUtilities.nativeIsGoogleSearchUrl(entry.getUrl());
    }

    @Override
    public boolean maybeLaunchInstantApp(Tab tab, String url, String referrerUrl,
            boolean isIncomingRedirect) {
        if (tab == null || tab.getWebContents() == null) return false;

        InstantAppsHandler handler = InstantAppsHandler.getInstance();
        Intent intent = tab.getTabRedirectHandler() != null
                ? tab.getTabRedirectHandler().getInitialIntent() : null;
        // TODO(mariakhomenko): consider also handling NDEF_DISCOVER action redirects.
        if (isIncomingRedirect && intent != null && intent.getAction() == Intent.ACTION_VIEW) {
            // Set the URL the redirect was resolved to for checking the existence of the
            // instant app inside handleIncomingIntent().
            Intent resolvedIntent = new Intent(intent);
            resolvedIntent.setData(Uri.parse(url));
            return handler.handleIncomingIntent(getAvailableContext(), resolvedIntent,
                    ChromeLauncherActivity.isCustomTabIntent(resolvedIntent));
        } else if (!isIncomingRedirect) {
            // Check if the navigation is coming from SERP and skip instant app handling.
            if (isSerpReferrer(referrerUrl, tab)) return false;
            return handler.handleNavigation(
                    getAvailableContext(), url,
                    TextUtils.isEmpty(referrerUrl) ? null : Uri.parse(referrerUrl),
                    tab.getWebContents());
        }
        return false;
    }

    /**
     * Dispatches the intent through a proxy activity, so that startActivityForResult can be used
     * and the intent recipient can verify the caller.
     * @param intent The bare intent we were going to send.
     */
    protected void dispatchAuthenticatedIntent(Intent intent) {
        Intent proxyIntent = new Intent(Intent.ACTION_MAIN);
        proxyIntent.setClass(getAvailableContext(), AuthenticatedProxyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        proxyIntent.putExtra(AuthenticatedProxyActivity.AUTHENTICATED_INTENT_EXTRA, intent);
        getAvailableContext().startActivity(proxyIntent);
    }
}
