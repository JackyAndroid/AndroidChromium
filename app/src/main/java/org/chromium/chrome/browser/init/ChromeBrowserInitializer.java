// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;

import com.squareup.leakcanary.LeakCanary;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.BaseSwitches;
import org.chromium.base.CommandLine;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.PathUtils;
import org.chromium.base.ResourceExtractor;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.annotations.RemovableInRelease;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeStrictMode;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.FileProviderHelper;
import org.chromium.chrome.browser.crash.MinidumpDirectoryObserver;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.services.GoogleServicesManager;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelImpl;
import org.chromium.chrome.browser.webapps.ActivityAssigner;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.content.browser.DeviceUtils;
import org.chromium.content.browser.SpeechRecognition;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.policy.CombinedPolicyProvider;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.LinkedList;
import java.util.Locale;

/**
 * Application level delegate that handles start up tasks.
 * {@link AsyncInitializationActivity} classes should override the {@link BrowserParts}
 * interface for any additional initialization tasks for the initialization to work as intended.
 */
public class ChromeBrowserInitializer {
    private static final String TAG = "BrowserInitializer";
    private static ChromeBrowserInitializer sChromeBrowserInitiliazer;

    private final Handler mHandler;
    private final ChromeApplication mApplication;
    private final Locale mInitialLocale = Locale.getDefault();

    private boolean mPreInflationStartupComplete;
    private boolean mPostInflationStartupComplete;
    private boolean mNativeInitializationComplete;

    private MinidumpDirectoryObserver mMinidumpDirectoryObserver;

    // Public to allow use in ChromeBackupAgent
    public static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "chrome";

    /**
     * A callback to be executed when there is a new version available in Play Store.
     */
    public interface OnNewVersionAvailableCallback extends Runnable {
        /**
         * Set the update url to get the new version available.
         * @param updateUrl The url to be used.
         */
        void setUpdateUrl(String updateUrl);
    }

    /**
     * This class is an application specific object that orchestrates the app initialization.
     * @param context The context to get the application context from.
     * @return The singleton instance of {@link ChromeBrowserInitializer}.
     */
    public static ChromeBrowserInitializer getInstance(Context context) {
        if (sChromeBrowserInitiliazer == null) {
            sChromeBrowserInitiliazer = new ChromeBrowserInitializer(context);
        }
        return sChromeBrowserInitiliazer;
    }

    private ChromeBrowserInitializer(Context context) {
        mApplication = (ChromeApplication) context.getApplicationContext();
        mHandler = new Handler(Looper.getMainLooper());
        initLeakCanary();
    }

    @RemovableInRelease
    private void initLeakCanary() {
        // Watch that Activity objects are not retained after their onDestroy() has been called.
        // This is a no-op in release builds.
        LeakCanary.install(mApplication);
    }

    /**
     * Initializes the Chrome browser process synchronously.
     *
     * @throws ProcessInitException if there is a problem with the native library.
     */
    public void handleSynchronousStartup() throws ProcessInitException {
        assert ThreadUtils.runningOnUiThread() : "Tried to start the browser on the wrong thread";

        BrowserParts parts = new EmptyBrowserParts();
        handlePreNativeStartup(parts);
        handlePostNativeStartup(false, parts);
    }

    /**
     * Execute startup tasks that can be done without native libraries. See {@link BrowserParts} for
     * a list of calls to be implemented.
     * @param parts The delegate for the {@link ChromeBrowserInitializer} to communicate
     *              initialization tasks.
     */
    public void handlePreNativeStartup(final BrowserParts parts) {
        assert ThreadUtils.runningOnUiThread() : "Tried to start the browser on the wrong thread";

        ProcessInitializationHandler.getInstance().initializePreNative();
        preInflationStartup();
        parts.preInflationStartup();
        if (parts.isActivityFinishing()) return;

        preInflationStartupDone();
        parts.setContentViewAndLoadLibrary();
        postInflationStartup();
        parts.postInflationStartup();
    }

    /**
     * This is needed for device class manager which depends on commandline args that are
     * initialized in preInflationStartup()
     */
    private void preInflationStartupDone() {
        // Domain reliability uses significant enough memory that we should disable it on low memory
        // devices for now.
        // TODO(zbowling): remove this after domain reliability is refactored. (crbug.com/495342)
        if (DeviceClassManager.disableDomainReliability()) {
            CommandLine.getInstance().appendSwitch(ChromeSwitches.DISABLE_DOMAIN_RELIABILITY);
        }
    }

    /**
     * Pre-load shared prefs to avoid being blocked on the disk access async task in the future.
     * Running in an AsyncTask as pre-loading itself may cause I/O.
     */
    private void warmUpSharedPrefs() {
        if (Build.VERSION.CODENAME.equals("N") || Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    ContextUtils.getAppSharedPreferences();
                    DocumentTabModelImpl.warmUpSharedPrefs(mApplication);
                    ActivityAssigner.warmUpSharedPrefs(mApplication);
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            ContextUtils.getAppSharedPreferences();
            DocumentTabModelImpl.warmUpSharedPrefs(mApplication);
            ActivityAssigner.warmUpSharedPrefs(mApplication);
        }
    }

    private void preInflationStartup() {
        ThreadUtils.assertOnUiThread();
        if (mPreInflationStartupComplete) return;
        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX, mApplication);

        // Ensure critical files are available, so they aren't blocked on the file-system
        // behind long-running accesses in next phase.
        // Don't do any large file access here!
        ContentApplication.initCommandLine(mApplication);
        waitForDebuggerIfNeeded();
        ChromeStrictMode.configureStrictMode();
        ChromeWebApkHost.init();

        warmUpSharedPrefs();

        DeviceUtils.addDeviceSpecificUserAgentSwitch(mApplication);
        ApplicationStatus.registerStateListenerForAllActivities(
                createActivityStateListener());

        mPreInflationStartupComplete = true;
    }

    private void postInflationStartup() {
        ThreadUtils.assertOnUiThread();
        if (mPostInflationStartupComplete) return;

        // Check to see if we need to extract any new resources from the APK. This could
        // be on first run when we need to extract all the .pak files we need, or after
        // the user has switched locale, in which case we want new locale resources.
        ResourceExtractor.get(mApplication).startExtractingResources();

        mPostInflationStartupComplete = true;
    }

    /**
     * Execute startup tasks that require native libraries to be loaded. See {@link BrowserParts}
     * for a list of calls to be implemented.
     * @param isAsync Whether this call should synchronously wait for the browser process to be
     *                fully initialized before returning to the caller.
     * @param delegate The delegate for the {@link ChromeBrowserInitializer} to communicate
     *                 initialization tasks.
     */
    public void handlePostNativeStartup(final boolean isAsync, final BrowserParts delegate)
            throws ProcessInitException {
        assert ThreadUtils.runningOnUiThread() : "Tried to start the browser on the wrong thread";

        final LinkedList<Runnable> initQueue = new LinkedList<>();

        abstract class NativeInitTask implements Runnable {
            @Override
            public final void run() {
                // Run the current task then put a request for the next one onto the
                // back of the UI message queue. This lets Chrome handle input events
                // between tasks.
                initFunction();
                if (!initQueue.isEmpty()) {
                    Runnable nextTask = initQueue.pop();
                    if (isAsync) {
                        mHandler.post(nextTask);
                    } else {
                        nextTask.run();
                    }
                }
            }
            public abstract void initFunction();
        }

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                ProcessInitializationHandler.getInstance().initializePostNative();
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                initNetworkChangeNotifier(mApplication.getApplicationContext());
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                // This is not broken down as a separate task, since this:
                // 1. Should happen as early as possible
                // 2. Only submits asynchronous work
                // 3. Is thus very cheap (profiled at 0.18ms on a Nexus 5 with Lollipop)
                // It should also be in a separate task (and after) initNetworkChangeNotifier, as
                // this posts a task to the UI thread that would interfere with preconneciton
                // otherwise. By preconnecting afterwards, we make sure that this task has run.
                delegate.maybePreconnect();

                onStartNativeInitialization();
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                if (delegate.isActivityDestroyed()) return;
                delegate.initializeCompositor();
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                if (delegate.isActivityDestroyed()) return;
                delegate.initializeState();
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                onFinishNativeInitialization();
            }
        });

        initQueue.add(new NativeInitTask() {
            @Override
            public void initFunction() {
                if (delegate.isActivityDestroyed()) return;
                delegate.finishNativeInitialization();
            }
        });

        // See crbug.com/593250. This can be removed after N SDK is released, crbug.com/592722.
        ChildProcessCreationParams creationParams = mApplication.getChildProcessCreationParams();
        // WebAPK uses this code path to initialize Chrome's native code, and the
        // ChildProcessCreationParams has been set in {@link WebApkActivity}. We have to prevent
        // resetting with a wrong parameter here. TODO(hanxi): Remove the entire if block after
        // N SDK is released, since it breaks WebAPKs on N+.
        if (creationParams != null && ChildProcessCreationParams.get() != null) {
            ChildProcessCreationParams.set(creationParams);
        }
        if (isAsync) {
            // We want to start this queue once the C++ startup tasks have run; allow the
            // C++ startup to run asynchonously, and set it up to start the Java queue once
            // it has finished.
            startChromeBrowserProcessesAsync(
                    delegate.shouldStartGpuProcess(),
                    new BrowserStartupController.StartupCallback() {
                        @Override
                        public void onFailure() {
                            delegate.onStartupFailure();
                        }

                        @Override
                        public void onSuccess(boolean success) {
                            mHandler.post(initQueue.pop());
                        }
                    });
        } else {
            startChromeBrowserProcessesSync();
            initQueue.pop().run();
            assert initQueue.isEmpty();
        }
    }

    private void startChromeBrowserProcessesAsync(
            boolean startGpuProcess,
            BrowserStartupController.StartupCallback callback) throws ProcessInitException {
        try {
            TraceEvent.begin("ChromeBrowserInitializer.startChromeBrowserProcessesAsync");
            BrowserStartupController.get(mApplication, LibraryProcessType.PROCESS_BROWSER)
                    .startBrowserProcessesAsync(startGpuProcess, callback);
        } finally {
            TraceEvent.end("ChromeBrowserInitializer.startChromeBrowserProcessesAsync");
        }
    }

    private void startChromeBrowserProcessesSync() throws ProcessInitException {
        try {
            TraceEvent.begin("ChromeBrowserInitializer.startChromeBrowserProcessesSync");
            ThreadUtils.assertOnUiThread();
            mApplication.initCommandLine();
            LibraryLoader libraryLoader = LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER);
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            libraryLoader.ensureInitialized();
            StrictMode.setThreadPolicy(oldPolicy);
            libraryLoader.asyncPrefetchLibrariesToMemory();
            BrowserStartupController.get(mApplication, LibraryProcessType.PROCESS_BROWSER)
                    .startBrowserProcessesSync(false);
            GoogleServicesManager.get(mApplication);
        } finally {
            TraceEvent.end("ChromeBrowserInitializer.startChromeBrowserProcessesSync");
        }
    }

    public static void initNetworkChangeNotifier(Context context) {
        ThreadUtils.assertOnUiThread();
        TraceEvent.begin("NetworkChangeNotifier.init");
        // Enable auto-detection of network connectivity state changes.
        NetworkChangeNotifier.init(context);
        NetworkChangeNotifier.setAutoDetectConnectivityState(true);
        TraceEvent.end("NetworkChangeNotifier.init");
    }

    private void onStartNativeInitialization() {
        ThreadUtils.assertOnUiThread();
        if (mNativeInitializationComplete) return;
        // The policies are used by browser startup, so we need to register the policy providers
        // before starting the browser process.
        mApplication.registerPolicyProviders(CombinedPolicyProvider.get());

        SpeechRecognition.initialize(mApplication);
    }

    private void onFinishNativeInitialization() {
        if (mNativeInitializationComplete) return;

        mNativeInitializationComplete = true;
        ContentUriUtils.setFileProviderUtil(new FileProviderHelper());

        // Start the file observer to watch the minidump directory.
        new AsyncTask<Void, Void, MinidumpDirectoryObserver>() {
            @Override
            protected MinidumpDirectoryObserver doInBackground(Void... params) {
                return new MinidumpDirectoryObserver();
            }

            @Override
            protected void onPostExecute(MinidumpDirectoryObserver minidumpDirectoryObserver) {
                mMinidumpDirectoryObserver = minidumpDirectoryObserver;
                mMinidumpDirectoryObserver.startWatching();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void waitForDebuggerIfNeeded() {
        if (CommandLine.getInstance().hasSwitch(BaseSwitches.WAIT_FOR_JAVA_DEBUGGER)) {
            Log.e(TAG, "Waiting for Java debugger to connect...");
            android.os.Debug.waitForDebugger();
            Log.e(TAG, "Java debugger connected. Resuming execution.");
        }
    }

    private ActivityStateListener createActivityStateListener() {
        return new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.CREATED || newState == ActivityState.DESTROYED) {
                    // Android destroys Activities at some point after a locale change, but doesn't
                    // kill the process.  This can lead to a bug where Chrome is halfway RTL, where
                    // stale natively-loaded resources are not reloaded (http://crbug.com/552618).
                    if (!mInitialLocale.equals(Locale.getDefault())) {
                        Log.e(TAG, "Killing process because of locale change.");
                        Process.killProcess(Process.myPid());
                    }

                    DeviceFormFactor.resetValuesIfNeeded(mApplication);
                }
            }
        };
    }
}
