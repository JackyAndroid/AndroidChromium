// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;

import org.chromium.base.ContextUtils;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.externalnav.ExternalNavigationParams;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;
import org.chromium.webapk.lib.client.WebApkServiceConnectionManager;

/**
 * An Activity is designed for WebAPKs (native Android apps) and displays a webapp in a nearly
 * UI-less Chrome.
 */
public class WebApkActivity extends WebappActivity {
    /** Manages whether to check update for the WebAPK, and starts update check if needed. */
    private WebApkUpdateManager mUpdateManager;
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // We could bring a WebAPK hosted WebappActivity to foreground and navigate it to a
        // different URL. For example, WebAPK "foo" is launched and navigates to
        // "www.foo.com/foo". In Chrome, user clicks a link "www.foo.com/bar" in Google search
        // results. After clicking the link, WebAPK "foo" is brought to foreground, and
        // loads the page of "www.foo.com/bar" at the same time.
        // The extra {@link ShortcutHelper.EXTRA_URL} provides the URL that the WebAPK will
        // navigate to.
        String overrideUrl = intent.getStringExtra(ShortcutHelper.EXTRA_URL);
        if (overrideUrl != null && isInitialized()
                && !overrideUrl.equals(getActivityTab().getUrl())) {
            getActivityTab().loadUrl(
                    new LoadUrlParams(overrideUrl, PageTransition.AUTO_TOPLEVEL));
        }
    }

    @Override
    protected WebappInfo createWebappInfo(Intent intent) {
        return (intent == null) ? WebApkInfo.createEmpty() : WebApkInfo.create(intent);
    }

    @Override
    protected void onStorageIsNull(final int backgroundColor) {
        // Register the WebAPK. It is possible that a WebAPK's meta data was deleted when user
        // cleared Chrome's data. When it is launched again, we know that the WebAPK is still
        // installed, so re-register it.
        WebappRegistry.getInstance().register(
                mWebappInfo.id(), new WebappRegistry.FetchWebappDataStorageCallback() {
                    @Override
                    public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                        updateStorage(storage);
                        // Initialize the time of the last is-update-needed check with the
                        // registration time. This prevents checking for updates on the first run.
                        storage.updateTimeOfLastCheckForUpdatedWebManifest();

                        // The downloading of the splash screen image happens before a WebAPK's
                        // package name is available. If we want to use the image in the first
                        // launch, we need to cache the image, register the WebAPK and store the
                        // image in the SharedPreference when the WebAPK is installed
                        // (before the first launch), and delete the cached image if it's not
                        // installed. Therefore, lots of complexity will be introduced. To simplify
                        // the logic, WebAPKs are registered during the first launch, and don't
                        // retrieve splash screen image but use app icon for initialization.
                        initializeSplashScreenWidgets(backgroundColor, null);
                    }
                });
    }

    @Override
    protected TabDelegateFactory createTabDelegateFactory() {
        return new WebappDelegateFactory(this) {
            @Override
            public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(Tab tab) {
                return new InterceptNavigationDelegateImpl(tab) {
                    @Override
                    public ExternalNavigationParams.Builder buildExternalNavigationParams(
                            NavigationParams navigationParams,
                            TabRedirectHandler tabRedirectHandler, boolean shouldCloseTab) {
                        ExternalNavigationParams.Builder builder =
                                super.buildExternalNavigationParams(
                                        navigationParams, tabRedirectHandler, shouldCloseTab);
                        builder.setWebApkPackageName(getWebApkPackageName());
                        return builder;
                    }
                };
            }

            @Override
            public boolean canShowAppBanners(Tab tab) {
                // Do not show app banners for WebAPKs regardless of the current page URL.
                // A WebAPK can display a page outside of its WebAPK scope if a page within the
                // WebAPK scope navigates via JavaScript while the WebAPK is in the background.
                return false;
            }
        };
    }

    @Override
    public void onStop() {
        super.onStop();
        WebApkServiceConnectionManager.getInstance().disconnect(
                ContextUtils.getApplicationContext(), getWebApkPackageName());
    }

    /**
     * Returns the WebAPK's package name.
     */
    private String getWebApkPackageName() {
        return getWebappInfo().webApkPackageName();
    }

    @Override
    public void onResume() {
        super.onResume();
        // WebAPK hosts Chrome's renderer processes by declaring the Chrome's renderer service in
        // its AndroidManifest.xml. We set {@link ChildProcessCreationParams} for WebAPK's renderer
        // process so the {@link ChildProcessLauncher} knows which application's renderer
        // service to connect.
        initializeChildProcessCreationParams(true);
    }

    @Override
    protected void initializeChildProcessCreationParams() {
        // TODO(hanxi): crbug.com/611842. Investigates whether this function works for multiple
        // windows or with --site-per-process enabled.
        initializeChildProcessCreationParams(true);
    }

    @Override
    public void onDeferredStartup() {
        super.onDeferredStartup();

        mUpdateManager = new WebApkUpdateManager();
        mUpdateManager.updateIfNeeded(getActivityTab(), (WebApkInfo) mWebappInfo);
    }

    @Override
    public void onPause() {
        super.onPause();
        initializeChildProcessCreationParams(false);
    }

    /**
     * Initializes {@link ChildProcessCreationParams} as a WebAPK's renderer process if
     * {@link isForWebApk}} is true; as Chrome's child process otherwise.
     * @param isForWebApk: Whether the {@link ChildProcessCreationParams} is initialized as a
     *                     WebAPK renderer process.
     */
    private void initializeChildProcessCreationParams(boolean isForWebApk) {
        // TODO(hanxi): crbug.com/664530. WebAPKs shouldn't use a global ChildProcessCreationParams.
        ChromeApplication chrome = (ChromeApplication) ContextUtils.getApplicationContext();
        ChildProcessCreationParams params = chrome.getChildProcessCreationParams();
        if (isForWebApk) {
            boolean isExternalService = false;
            params = new ChildProcessCreationParams(getWebappInfo().webApkPackageName(),
                    isExternalService, LibraryProcessType.PROCESS_CHILD);
        }
        ChildProcessCreationParams.set(params);
    }

    @Override
    protected void onDestroyInternal() {
        if (mUpdateManager != null) {
            mUpdateManager.destroy();
        }
        super.onDestroyInternal();
    }
}
