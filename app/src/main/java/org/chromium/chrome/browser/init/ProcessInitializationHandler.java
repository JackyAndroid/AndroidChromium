// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.google.ipc.invalidation.external.client.android.service.AndroidLogger;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivitySessionTracker;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.DeferredStartupHandler;
import org.chromium.chrome.browser.DevToolsServer;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.download.DownloadController;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.firstrun.ForcedSigninProcessor;
import org.chromium.chrome.browser.identity.UniqueIdentificationGeneratorFactory;
import org.chromium.chrome.browser.identity.UuidBasedUniqueIdentificationGenerator;
import org.chromium.chrome.browser.invalidation.UniqueIdInvalidationClientNameGenerator;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.rlz.RevenueStats;
import org.chromium.chrome.browser.services.AccountsChangedReceiver;
import org.chromium.chrome.browser.services.GoogleServicesManager;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.content.common.ContentSwitches;
import org.chromium.printing.PrintDocumentAdapterWrapper;
import org.chromium.printing.PrintingControllerImpl;
import org.chromium.ui.UiUtils;

/**
 * Handles the initialization dependences of the browser process.  This is meant to handle the
 * initialization that is not tied to any particular Activity, and the logic that should only be
 * triggered a single time for the lifetime of the browser process.
 */
public class ProcessInitializationHandler {

    private static final String SESSIONS_UUID_PREF_KEY = "chromium.sync.sessions.id";
    private static final String DEV_TOOLS_SERVER_SOCKET_PREFIX = "chrome";

    private static Class<? extends ProcessInitializationHandler> sHandlerClassOverride;
    private static ProcessInitializationHandler sInstance;

    private boolean mInitializedPreNative;
    private boolean mInitializedPostNative;
    private boolean mInitializedDeferredStartupTasks;
    private DevToolsServer mDevToolsServer;

    /**
     * Overrides the type of ProcessInitializationHandler to be created.
     * <p>
     * This must be called before {@link #getInstance()} is triggered.
     *
     * @param classOverride The ProcessInitializationHandler class type to be created instead of
     *                      the default.
     */
    public static void setProcessInitializationHandlerType(
            Class<? extends ProcessInitializationHandler> classOverride) {
        if (sInstance != null) {
            throw new IllegalStateException("Browser delegate override set after initialized");
        }
        sHandlerClassOverride = classOverride;
    }

    /**
     * @return The ProcessInitializationHandler for use during the lifetime of the browser process.
     */
    public static ProcessInitializationHandler getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            if (sHandlerClassOverride != null) {
                try {
                    sInstance = sHandlerClassOverride.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    assert false : "Unable to instantiate ProcessInitializationHandler override.";
                }
            }
            if (sInstance == null) sInstance = new ProcessInitializationHandler();
        }
        return sInstance;
    }

    /**
     * Constructor exposed to allow overriding.
     */
    protected ProcessInitializationHandler() {}

    /**
     * Initializes the any dependencies that must occur before native library has been loaded.
     * <p>
     * Adding anything expensive to this must be avoided as it would delay the Chrome startup path.
     * <p>
     * All entry points that do not rely on {@link ChromeBrowserInitializer} must call this on
     * startup.
     */
    public final void initializePreNative() {
        ThreadUtils.assertOnUiThread();
        if (mInitializedPreNative) return;
        mInitializedPreNative = true;
        handlePreNativeInitialization();
    }

    /**
     * Performs the shared class initialization.
     */
    protected void handlePreNativeInitialization() {
        ChromeApplication application = (ChromeApplication) ContextUtils.getApplicationContext();

        UiUtils.setKeyboardShowingDelegate(new UiUtils.KeyboardShowingDelegate() {
            @Override
            public boolean disableKeyboardCheck(Context context, View view) {
                Activity activity = null;
                if (context instanceof Activity) {
                    activity = (Activity) context;
                } else if (view != null && view.getContext() instanceof Activity) {
                    activity = (Activity) view.getContext();
                }

                // For multiwindow mode we do not track keyboard visibility.
                return activity != null
                        && MultiWindowUtils.getInstance().isLegacyMultiWindow(activity);
            }
        });

        // Initialize the AccountManagerHelper with the correct AccountManagerDelegate. Must be done
        // only once and before AccountMangerHelper.get(...) is called to avoid using the
        // default AccountManagerDelegate.
        AccountManagerHelper.initializeAccountManagerHelper(
                application, application.createAccountManagerDelegate());

        // Set the unique identification generator for invalidations.  The
        // invalidations system can start and attempt to fetch the client ID
        // very early.  We need this generator to be ready before that happens.
        UniqueIdInvalidationClientNameGenerator.doInitializeAndInstallGenerator(application);

        // Set minimum Tango log level. This sets an in-memory static field, and needs to be
        // set in the ApplicationContext instead of an activity, since Tango can be woken up
        // by the system directly though messages from GCM.
        AndroidLogger.setMinimumAndroidLogLevel(Log.WARN);

        // Set up the identification generator for sync. The ID is actually generated
        // in the SyncController constructor.
        UniqueIdentificationGeneratorFactory.registerGenerator(SyncController.GENERATOR_ID,
                new UuidBasedUniqueIdentificationGenerator(
                        application, SESSIONS_UUID_PREF_KEY), false);
    }

    /**
     * Initializes any dependencies that must occur after the native library has been loaded.
     */
    public final void initializePostNative() {
        ThreadUtils.assertOnUiThread();
        if (mInitializedPostNative) return;
        mInitializedPostNative = true;
        handlePostNativeInitialization();
    }

    /**
     * Performs the post native initialization.
     */
    protected void handlePostNativeInitialization() {
        final ChromeApplication application =
                (ChromeApplication) ContextUtils.getApplicationContext();

        DataReductionProxySettings.reconcileDataReductionProxyEnabledState(application);
        ChromeActivitySessionTracker.getInstance().initializeWithNative();
        ChromeApplication.removeSessionCookies();
        AppBannerManager.setAppDetailsDelegate(application.createAppDetailsDelegate());
        ChromeLifetimeController.initialize();

        PrefServiceBridge.getInstance().migratePreferences(application);
    }

    /**
     * Initializes the deferred startup tasks that should only be triggered once per browser process
     * lifetime.
     */
    public final void initializeDeferredStartupTasks() {
        ThreadUtils.assertOnUiThread();
        if (mInitializedDeferredStartupTasks) return;
        mInitializedDeferredStartupTasks = true;
        handleDeferredStartupTasksInitialization();
    }

    /**
     * Performs the deferred startup task initialization.
     */
    protected void handleDeferredStartupTasksInitialization() {
        final ChromeApplication application =
                (ChromeApplication) ContextUtils.getApplicationContext();

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                ForcedSigninProcessor.start(application);
                AccountsChangedReceiver.addObserver(
                        new AccountsChangedReceiver.AccountsChangedObserver() {
                            @Override
                            public void onAccountsChanged(Context context, Intent intent) {
                                ThreadUtils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ForcedSigninProcessor.start(application);
                                    }
                                });
                            }
                        });
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                GoogleServicesManager.get(application).onMainActivityStart();
                RevenueStats.getInstance();
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                mDevToolsServer = new DevToolsServer(DEV_TOOLS_SERVER_SOCKET_PREFIX);
                mDevToolsServer.setRemoteDebuggingEnabled(
                        true, DevToolsServer.Security.ALLOW_DEBUG_PERMISSION);
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                // Add process check to diagnose http://crbug.com/606309. Remove this after the bug
                // is fixed.
                assert !CommandLine.getInstance().hasSwitch(ContentSwitches.SWITCH_PROCESS_TYPE);
                if (!CommandLine.getInstance().hasSwitch(ContentSwitches.SWITCH_PROCESS_TYPE)) {
                    DownloadController.setDownloadNotificationService(
                            DownloadManagerService.getDownloadManagerService(application));
                }

                if (ApiCompatibilityUtils.isPrintingSupported()) {
                    String errorText = application.getResources().getString(
                            R.string.error_printing_failed);
                    PrintingControllerImpl.create(new PrintDocumentAdapterWrapper(), errorText);
                }
            }
        });
    }
}
