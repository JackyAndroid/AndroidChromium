// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.base.PowerMonitor;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.bookmarkswidget.BookmarkWidgetProvider;
import org.chromium.chrome.browser.crash.CrashFileManager;
import org.chromium.chrome.browser.crash.MinidumpUploadService;
import org.chromium.chrome.browser.init.ProcessInitializationHandler;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.media.MediaCaptureNotificationService;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.physicalweb.PhysicalWeb;
import org.chromium.chrome.browser.precache.PrecacheLauncher;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.chrome.browser.webapps.WebApkVersionManager;
import org.chromium.content.browser.ChildProcessLauncher;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Handler for application level tasks to be completed on deferred startup.
 */
public class DeferredStartupHandler {
    private static final String TAG = "DeferredStartupHandler";
    /** Prevents race conditions when deleting snapshot database. */
    private static final Object SNAPSHOT_DATABASE_LOCK = new Object();
    private static final String SNAPSHOT_DATABASE_REMOVED = "snapshot_database_removed";
    private static final String SNAPSHOT_DATABASE_NAME = "snapshots.db";

    private static class Holder {
        private static final DeferredStartupHandler INSTANCE = new DeferredStartupHandler();
    }

    private boolean mDeferredStartupInitializedForApp;
    private boolean mDeferredStartupCompletedForApp;
    private long mDeferredStartupDuration;
    private long mMaxTaskDuration;
    private final Context mAppContext;

    private final Queue<Runnable> mDeferredTasks;

    /**
     * This class is an application specific object that handles the deferred startup.
     * @return The singleton instance of {@link DeferredStartupHandler}.
     */
    public static DeferredStartupHandler getInstance() {
        return Holder.INSTANCE;
    }

    private DeferredStartupHandler() {
        mAppContext = ContextUtils.getApplicationContext();
        mDeferredTasks = new LinkedList<>();
    }

    /**
     * Add the idle handler which will run deferred startup tasks in sequence when idle. This can
     * be called multiple times by different activities to schedule their own deferred startup
     * tasks.
     */
    public void queueDeferredTasksOnIdleHandler() {
        mMaxTaskDuration = 0;
        mDeferredStartupDuration = 0;
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Runnable currentTask = mDeferredTasks.poll();
                if (currentTask == null) {
                    if (mDeferredStartupInitializedForApp) {
                        mDeferredStartupCompletedForApp = true;
                        recordDeferredStartupStats();
                    }
                    return false;
                }

                long startTime = SystemClock.uptimeMillis();
                currentTask.run();
                long timeTaken = SystemClock.uptimeMillis() - startTime;

                mMaxTaskDuration = Math.max(mMaxTaskDuration, timeTaken);
                mDeferredStartupDuration += timeTaken;
                return true;
            }
        });
    }

    private void recordDeferredStartupStats() {
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpDuration",
                mDeferredStartupDuration,
                TimeUnit.MILLISECONDS);
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpMaxTaskDuration",
                mMaxTaskDuration,
                TimeUnit.MILLISECONDS);
        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUpCompleteTime",
                SystemClock.uptimeMillis() - UmaUtils.getForegroundStartTime(),
                TimeUnit.MILLISECONDS);
        LocaleManager.getInstance().recordStartupMetrics();
    }

    /**
     * Adds a single deferred task to the queue. The caller is responsible for calling
     * queueDeferredTasksOnIdleHandler after adding tasks.
     *
     * @param deferredTask The tasks to be run.
     */
    public void addDeferredTask(Runnable deferredTask) {
        ThreadUtils.assertOnUiThread();
        mDeferredTasks.add(deferredTask);
    }

    /**
     * Handle application level deferred startup tasks that can be lazily done after all
     * the necessary initialization has been completed. Any calls requiring network access should
     * probably go here.
     *
     * Keep these tasks short and break up long tasks into multiple smaller tasks, as they run on
     * the UI thread and are blocking. Remember to follow RAIL guidelines, as much as possible, and
     * that most devices are quite slow, so leave enough buffer.
     */
    @UiThread
    public void initDeferredStartupForApp() {
        if (mDeferredStartupInitializedForApp) return;
        mDeferredStartupInitializedForApp = true;
        ThreadUtils.assertOnUiThread();

        RecordHistogram.recordLongTimesHistogram(
                "UMA.Debug.EnableCrashUpload.DeferredStartUptime2",
                SystemClock.uptimeMillis() - UmaUtils.getForegroundStartTime(),
                TimeUnit.MILLISECONDS);

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Punt all tasks that may block on disk off onto a background thread.
                initAsyncDiskTask();

                AfterStartupTaskUtils.setStartupComplete();

                PartnerBrowserCustomizations.setOnInitializeAsyncFinished(new Runnable() {
                    @Override
                    public void run() {
                        String homepageUrl = HomepageManager.getHomepageUri(mAppContext);
                        LaunchMetrics.recordHomePageLaunchMetrics(
                                HomepageManager.isHomepageEnabled(mAppContext),
                                NewTabPage.isNTPUrl(homepageUrl), homepageUrl);
                    }
                });

                PartnerBookmarksShim.kickOffReading(mAppContext);

                PowerMonitor.create(mAppContext);

                ShareHelper.clearSharedImages();

                OfflinePageUtils.clearSharedOfflineFiles(mAppContext);
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Clear any media notifications that existed when Chrome was last killed.
                MediaCaptureNotificationService.clearMediaNotifications(mAppContext);

                startModerateBindingManagementIfNeeded();

                recordKeyboardLocaleUma();
            }
        });

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Start or stop Physical Web
                PhysicalWeb.onChromeStart();
            }
        });

        final ChromeApplication application = (ChromeApplication) mAppContext;

        mDeferredTasks.add(new Runnable() {
            @Override
            public void run() {
                // Starts syncing with GSA.
                application.createGsaHelper().startSync();
            }
        });

        ProcessInitializationHandler.getInstance().initializeDeferredStartupTasks();
    }

    private void initAsyncDiskTask() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    TraceEvent.begin("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                    long asyncTaskStartTime = SystemClock.uptimeMillis();
                    boolean crashDumpDisabled = CommandLine.getInstance().hasSwitch(
                            ChromeSwitches.DISABLE_CRASH_DUMP_UPLOAD);
                    if (!crashDumpDisabled) {
                        RecordHistogram.recordLongTimesHistogram(
                                "UMA.Debug.EnableCrashUpload.Uptime3",
                                asyncTaskStartTime - UmaUtils.getForegroundStartTime(),
                                TimeUnit.MILLISECONDS);
                        PrivacyPreferencesManager.getInstance().enablePotentialCrashUploading();
                        MinidumpUploadService.tryUploadAllCrashDumps(mAppContext);
                    }
                    CrashFileManager crashFileManager =
                            new CrashFileManager(mAppContext.getCacheDir());
                    crashFileManager.cleanOutAllNonFreshMinidumpFiles();

                    MinidumpUploadService.storeBreakpadUploadStatsInUma(
                            ChromePreferenceManager.getInstance(mAppContext));

                    // Force a widget refresh in order to wake up any possible zombie widgets.
                    // This is needed to ensure the right behavior when the process is suddenly
                    // killed.
                    BookmarkWidgetProvider.refreshAllWidgets(mAppContext);

                    // Initialize whether or not precaching is enabled.
                    PrecacheLauncher.updatePrecachingEnabled(mAppContext);

                    if (ChromeWebApkHost.isEnabled()) {
                        WebApkVersionManager.updateWebApksIfNeeded();
                    }

                    removeSnapshotDatabase();

                    cacheIsChromeDefaultBrowser();

                    RecordHistogram.recordLongTimesHistogram(
                            "UMA.Debug.EnableCrashUpload.DeferredStartUpDurationAsync",
                            SystemClock.uptimeMillis() - asyncTaskStartTime,
                            TimeUnit.MILLISECONDS);

                    return null;
                } finally {
                    TraceEvent.end("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void startModerateBindingManagementIfNeeded() {
        // Moderate binding doesn't apply to low end devices.
        if (SysUtils.isLowEndDevice()) return;

        boolean moderateBindingTillBackgrounded =
                FieldTrialList.findFullName("ModerateBindingOnBackgroundTabCreation")
                        .equals("Enabled");
        ChildProcessLauncher.startModerateBindingManagement(
                mAppContext, moderateBindingTillBackgrounded);
    }

    /**
     * Caches whether Chrome is set as a default browser on the device.
     */
    @WorkerThread
    private void cacheIsChromeDefaultBrowser() {
        // Retrieve whether Chrome is default in background to avoid strict mode checks.
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://www.madeupdomainforcheck123.com/"));
        ResolveInfo info = mAppContext.getPackageManager().resolveActivity(intent, 0);
        boolean isDefault = (info != null && info.match != 0
                && mAppContext.getPackageName().equals(info.activityInfo.packageName));
        ChromePreferenceManager.getInstance(mAppContext).setCachedChromeDefaultBrowser(isDefault);
    }

    /**
     * Deletes the snapshot database which is no longer used because the feature has been removed
     * in Chrome M41.
     */
    @WorkerThread
    private void removeSnapshotDatabase() {
        synchronized (SNAPSHOT_DATABASE_LOCK) {
            SharedPreferences prefs =
                    ContextUtils.getAppSharedPreferences();
            if (!prefs.getBoolean(SNAPSHOT_DATABASE_REMOVED, false)) {
                mAppContext.deleteDatabase(SNAPSHOT_DATABASE_NAME);
                prefs.edit().putBoolean(SNAPSHOT_DATABASE_REMOVED, true).apply();
            }
        }
    }

    private void recordKeyboardLocaleUma() {
        InputMethodManager imm =
                (InputMethodManager) mAppContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
        ArrayList<String> uniqueLanguages = new ArrayList<>();
        for (InputMethodInfo method : ims) {
            List<InputMethodSubtype> submethods =
                    imm.getEnabledInputMethodSubtypeList(method, true);
            for (InputMethodSubtype submethod : submethods) {
                if (submethod.getMode().equals("keyboard")) {
                    String language = submethod.getLocale().split("_")[0];
                    if (!uniqueLanguages.contains(language)) {
                        uniqueLanguages.add(language);
                    }
                }
            }
        }
        RecordHistogram.recordCountHistogram("InputMethod.ActiveCount", uniqueLanguages.size());

        InputMethodSubtype currentSubtype = imm.getCurrentInputMethodSubtype();
        Locale systemLocale = Locale.getDefault();
        if (currentSubtype != null && currentSubtype.getLocale() != null && systemLocale != null) {
            String keyboardLanguage = currentSubtype.getLocale().split("_")[0];
            boolean match = systemLocale.getLanguage().equalsIgnoreCase(keyboardLanguage);
            RecordHistogram.recordBooleanHistogram("InputMethod.MatchesSystemLanguage", match);
        }
    }

    /**
    * @return Whether deferred startup has been completed.
    */
    @VisibleForTesting
    public boolean isDeferredStartupCompleteForApp() {
        return mDeferredStartupCompletedForApp;
    }
}
