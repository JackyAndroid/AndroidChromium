// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalnav;

import android.Manifest.permission;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.TransactionTooLargeException;
import android.provider.Browser;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler.OverrideUrlLoadingResult;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

import java.util.List;

/**
 * The main implementation of the {@link ExternalNavigationDelegate}.
 */
public class ExternalNavigationDelegateImpl implements ExternalNavigationDelegate {
    private static final String TAG = "ExternalNavigationDelegateImpl";
    private static final String PDF_VIEWER = "com.google.android.apps.docs";
    private static final String PDF_MIME = "application/pdf";
    private static final String PDF_SUFFIX = ".pdf";
    private final ChromeActivity mActivity;

    public ExternalNavigationDelegateImpl(ChromeActivity activity) {
        mActivity = activity;
    }

    /**
     * @return The activity that this delegate is associated with.
     */
    protected final Activity getActivity() {
        return mActivity;
    }

    /**
     * Retrieve the best activity for the given intent. If a default activity is provided,
     * choose the default one. Otherwise, return the Intent picker if there are more than one
     * capable activities. If the intent is pdf type, return the platform pdf viewer if
     * it is available so user don't need to choose it from Intent picker.
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
                                String filename = intent.getData().getLastPathSegment();
                                if ((filename != null && filename.endsWith(PDF_SUFFIX))
                                        || PDF_MIME.equals(intent.getType())) {
                                    intent.setClassName(pName, resolveInfo.activityInfo.name);
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

    /**
     * Retrieve information about the Activity that will handle the given Intent.
     * @param intent Intent to resolve.
     * @return       ResolveInfo of the Activity that will handle the Intent, or null if it failed.
     */
    public static ResolveInfo resolveActivity(Intent intent) {
        try {
            Context context = ApplicationStatus.getApplicationContext();
            PackageManager pm = context.getPackageManager();
            return pm.resolveActivity(intent, 0);
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
        return null;
    }

    /**
     * Determines whether Chrome will be handling the given Intent.
     * @param context           Context that will be firing the Intent.
     * @param intent            Intent that will be fired.
     * @param matchDefaultOnly  See {@link PackageManager#MATCH_DEFAULT_ONLY}.
     * @return                  True if Chrome will definitely handle the intent, false otherwise.
     */
    public static boolean willChromeHandleIntent(
            Context context, Intent intent, boolean matchDefaultOnly) {
        try {
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
    public List<ComponentName> queryIntentActivities(Intent intent) {
        return IntentUtils.getIntentHandlers(mActivity, intent);
    }

    @Override
    public boolean canResolveActivity(Intent intent) {
        try {
            return mActivity.getPackageManager().resolveActivity(intent, 0) != null;
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
            return false;
        }
    }

    @Override
    public boolean willChromeHandleIntent(Intent intent) {
        return willChromeHandleIntent(mActivity, intent, false);
    }

    @Override
    public boolean isSpecializedHandlerAvailable(Intent intent) {
        try {
            PackageManager pm = mActivity.getPackageManager();
            List<ResolveInfo> handlers = pm.queryIntentActivities(
                    intent,
                    PackageManager.GET_RESOLVED_FILTER);
            if (handlers == null || handlers.size() == 0) {
                return false;
            }
            for (ResolveInfo resolveInfo : handlers) {
                IntentFilter filter = resolveInfo.filter;
                if (filter == null) {
                    // No intent filter matches this intent?
                    // Error on the side of staying in the browser, ignore
                    continue;
                }
                if (filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0) {
                    // Generic handler, skip
                    continue;
                }
                return true;
            }
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
        return false;
    }

    @Override
    public String getPackageName() {
        return mActivity.getPackageName();
    }

    @Override
    public void startActivity(Intent intent) {
        try {
            resolveIntent(mActivity, intent, true);
            mActivity.startActivity(intent);
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
        }
    }

    @Override
    public boolean startActivityIfNeeded(Intent intent) {
        try {
            resolveIntent(mActivity, intent, true);
            return mActivity.startActivityIfNeeded(intent, -1);
        } catch (RuntimeException e) {
            logTransactionTooLargeOrRethrow(e, intent);
            return false;
        }
    }

    @Override
    public void startIncognitoIntent(final Intent intent, final String referrerUrl,
            final String fallbackUrl, final Tab tab, final boolean needsToCloseTab) {
        new AlertDialog.Builder(mActivity, R.style.AlertDialogTheme)
            .setTitle(R.string.external_app_leave_incognito_warning_title)
            .setMessage(R.string.external_app_leave_incognito_warning)
            .setPositiveButton(R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(intent);
                        if (tab != null && !tab.isClosing() && tab.isInitialized()
                                && needsToCloseTab) {
                            closeTab(tab);
                        }
                    }
                })
            .setNegativeButton(R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadIntent(intent, referrerUrl, fallbackUrl, tab, needsToCloseTab, true);
                    }
                })
            .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        loadIntent(intent, referrerUrl, fallbackUrl, tab, needsToCloseTab, true);
                    }
                })
            .show();
    }

    @Override
    public boolean shouldRequestFileAccess(Tab tab) {
        // If the tab is null, then do not attempt to prompt for access.
        if (tab == null) return false;

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
            IntentHandler.addTrustedIntentExtras(intent, mActivity);
            startActivity(intent);

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
            startActivity(intent);
            return OverrideUrlLoadingResult.OVERRIDE_WITH_EXTERNAL_INTENT;
        }
    }

    @Override
    public boolean isChromeAppInForeground() {
        return ApplicationStatus.getStateForApplication()
                == ApplicationState.HAS_RUNNING_ACTIVITIES;
    }

    @Override
    public boolean isDocumentMode() {
        return FeatureUtilities.isDocumentMode(mActivity);
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
        mActivity.getTabModelSelector().closeTab(tab);
    }
}
