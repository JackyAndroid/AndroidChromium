// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.chrome.browser.webapps.WebappDataStorage;
import org.chromium.chrome.browser.webapps.WebappRegistry;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;
import org.chromium.content_public.common.ResourceRequestBody;
import org.chromium.ui.base.PageTransition;
import org.chromium.webapk.lib.client.WebApkNavigationClient;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Tab Launcher to be used to launch new tabs from background Android Services,
 * when it is not known whether an activity is available. It will send an intent to launch the
 * activity.
 *
 * URLs within the scope of a recently launched standalone-capable web app on the Android home
 * screen are launched in the standalone web app frame.
 */
public class ServiceTabLauncher {
    // Name of the extra containing the Id of a tab launch request id.
    public static final String LAUNCH_REQUEST_ID_EXTRA =
            "org.chromium.chrome.browser.ServiceTabLauncher.LAUNCH_REQUEST_ID";

    /**
     * Launches the browser activity and launches a tab for |url|.
     *
     * @param context The context using which the URL is being loaded.
     * @param requestId Id of the request for launching this tab.
     * @param incognito Whether the tab should be launched in incognito mode.
     * @param url The URL which should be launched in a tab.
     * @param disposition The disposition requested by the navigation source.
     * @param referrerUrl URL of the referrer which is opening the page.
     * @param referrerPolicy The referrer policy to consider when applying the referrer.
     * @param extraHeaders Extra headers to apply when requesting the tab's URL.
     * @param postData Post-data to include in the tab URL's request body.
     */
    @CalledByNative
    public static void launchTab(final Context context, final int requestId,
            final boolean incognito, final String url, final int disposition,
            final String referrerUrl, final int referrerPolicy, final String extraHeaders,
            final ResourceRequestBody postData) {
        final TabDelegate tabDelegate = new TabDelegate(incognito);

        // 1. Launch WebAPK if one matches the target URL.
        if (ChromeWebApkHost.isEnabled()) {
            String webApkPackageName = WebApkValidator.queryWebApkPackage(context, url);
            if (webApkPackageName != null) {
                Intent intent =
                        WebApkNavigationClient.createLaunchWebApkIntent(webApkPackageName, url);
                if (intent != null) {
                    intent.putExtra(ShortcutHelper.EXTRA_SOURCE, ShortcutSource.NOTIFICATION);
                    context.startActivity(intent);
                    return;
                }
            }
        }

        // 2. Launch WebappActivity if one matches the target URL and was opened recently.
        // Otherwise, open the URL in a tab.
        final WebappDataStorage storage =
                WebappRegistry.getInstance().getWebappDataStorageForUrl(url);

        // If we do not find a WebappDataStorage corresponding to this URL, or if it hasn't
        // been opened recently enough, open the URL in a tab.
        if (storage == null || !storage.wasLaunchedRecently()) {
            LoadUrlParams loadUrlParams = new LoadUrlParams(url, PageTransition.LINK);
            loadUrlParams.setPostData(postData);
            loadUrlParams.setVerbatimHeaders(extraHeaders);
            loadUrlParams.setReferrer(new Referrer(referrerUrl, referrerPolicy));

            AsyncTabCreationParams asyncParams = new AsyncTabCreationParams(loadUrlParams,
                    requestId);
            tabDelegate.createNewTab(asyncParams, TabLaunchType.FROM_CHROME_UI,
                    Tab.INVALID_TAB_ID);
        } else {
            // The URL is within the scope of a recently launched standalone-capable web app
            // on the home screen, so open it a standalone web app frame. An AsyncTask is
            // used because WebappDataStorage.createWebappLaunchIntent contains a Bitmap
            // decode operation and should not be run on the UI thread.
            //
            // This currently assumes that the only source is notifications; any future use
            // which adds a different source will need to change this.
            new AsyncTask<Void, Void, Intent>() {
                @Override
                protected final Intent doInBackground(Void... nothing) {
                    return storage.createWebappLaunchIntent();
                }

                @Override
                protected final void onPostExecute(Intent intent) {
                    // Replace the web app URL with the URL from the notification. This is
                    // within the webapp's scope, so it is valid.
                    intent.putExtra(ShortcutHelper.EXTRA_URL, url);
                    intent.putExtra(ShortcutHelper.EXTRA_SOURCE,
                            ShortcutSource.NOTIFICATION);
                    tabDelegate.createNewStandaloneFrame(intent);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * To be called by the activity when the WebContents for |requestId| has been created, or has
     * been recycled from previous use. The |webContents| must not yet have started provisional
     * load for the main frame.
     *
     * @param requestId Id of the tab launching request which has been fulfilled.
     * @param webContents The WebContents instance associated with this request.
     */
    public static void onWebContentsForRequestAvailable(int requestId, WebContents webContents) {
        nativeOnWebContentsForRequestAvailable(requestId, webContents);
    }

    private static native void nativeOnWebContentsForRequestAvailable(
            int requestId, WebContents webContents);
}
