// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Environment;

import org.chromium.base.Callback;
import org.chromium.base.FileUtils;
import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.WebContents;
import org.chromium.net.ConnectionType;
import org.chromium.net.NetworkChangeNotifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * A class holding static util functions for offline pages.
 */
public class OfflinePageUtils {
    private static final String TAG = "OfflinePageUtils";
    /** Background task tag to differentiate from other task types */
    public static final String TASK_TAG = "OfflinePageUtils";

    public static final String EXTERNAL_MHTML_FILE_PATH = "offline-pages";

    private static final int DEFAULT_SNACKBAR_DURATION_MS = 6 * 1000; // 6 second

    private static final long STORAGE_ALMOST_FULL_THRESHOLD_BYTES = 10L * (1 << 20); // 10M

    // Used instead of the constant so tests can override the value.
    private static int sSnackbarDurationMs = DEFAULT_SNACKBAR_DURATION_MS;

    private static OfflinePageUtils sInstance;

    private static File sOfflineSharingDirectory;

    private static OfflinePageUtils getInstance() {
        if (sInstance == null) {
            sInstance = new OfflinePageUtils();
        }
        return sInstance;
    }

    /**
     * Returns the number of free bytes on the storage.
     */
    public static long getFreeSpaceInBytes() {
        return Environment.getDataDirectory().getUsableSpace();
    }

    /**
     * Returns the number of total bytes on the storage.
     */
    public static long getTotalSpaceInBytes() {
        return Environment.getDataDirectory().getTotalSpace();
    }

    /**
     * Returns true if the network is connected.
     */
    public static boolean isConnected() {
        return NetworkChangeNotifier.isOnline();
    }

    /*
     * Save an offline copy for the bookmarked page asynchronously.
     *
     * @param bookmarkId The ID of the page to save an offline copy.
     * @param tab A {@link Tab} object.
     * @param callback The callback to be invoked when the offline copy is saved.
     */
    public static void saveBookmarkOffline(BookmarkId bookmarkId, Tab tab) {
        // If bookmark ID is missing there is nothing to save here.
        if (bookmarkId == null) return;

        // Making sure the feature is enabled.
        if (!OfflinePageBridge.isOfflineBookmarksEnabled()) return;

        // Making sure tab is worth keeping.
        if (shouldSkipSavingTabOffline(tab)) return;

        OfflinePageBridge offlinePageBridge = getInstance().getOfflinePageBridge(tab.getProfile());
        if (offlinePageBridge == null) return;

        WebContents webContents = tab.getWebContents();
        ClientId clientId = ClientId.createClientIdForBookmarkId(bookmarkId);

        // TODO(fgorski): Ensure that request is queued if the model is not loaded.
        offlinePageBridge.savePage(webContents, clientId, new OfflinePageBridge.SavePageCallback() {
            @Override
            public void onSavePageDone(int savePageResult, String url, long offlineId) {
                // TODO(fgorski): Decide if we need to do anything with result.
                // Perhaps some UMA reporting, but that can really happen someplace else.
            }
        });
    }

    /**
     * Indicates whether we should skip saving the given tab as an offline page.
     * A tab shouldn't be saved offline if it shows an error page or a sad tab page.
     */
    private static boolean shouldSkipSavingTabOffline(Tab tab) {
        WebContents webContents = tab.getWebContents();
        return tab.isShowingErrorPage() || tab.isShowingSadTab() || webContents == null
                || webContents.isDestroyed() || webContents.isIncognito();
    }

    /**
     * Strips scheme from the original URL of the offline page. This is meant to be used by UI.
     * @param onlineUrl an online URL to from which the scheme is removed
     * @return onlineUrl without the scheme
     */
    public static String stripSchemeFromOnlineUrl(String onlineUrl) {
        onlineUrl = onlineUrl.trim();
        // Offline pages are only saved for https:// and http:// schemes.
        if (onlineUrl.startsWith("https://")) {
            return onlineUrl.substring(8);
        } else if (onlineUrl.startsWith("http://")) {
            return onlineUrl.substring(7);
        } else {
            return onlineUrl;
        }
    }

    /**
     * Shows the snackbar for the current tab to provide offline specific information if needed.
     * @param activity The activity owning the tab.
     * @param tab The current tab.
     */
    public static void showOfflineSnackbarIfNecessary(ChromeActivity activity, Tab tab) {
        if (OfflinePageTabObserver.getInstance() == null) {
            SnackbarController snackbarController =
                    createReloadSnackbarController(activity.getTabModelSelector());
            OfflinePageTabObserver.init(
                    activity.getBaseContext(), activity.getSnackbarManager(), snackbarController);
        }

        showOfflineSnackbarIfNecessary(tab);
    }

    /**
     * Shows the snackbar for the current tab to provide offline specific information if needed.
     * This method is used by testing for dependency injecting a snackbar controller.
     * @param context android context
     * @param snackbarManager The snackbar manager to show and dismiss snackbars.
     * @param tab The current tab.
     * @param snackbarController The snackbar controller to control snackbar behavior.
     */
    static void showOfflineSnackbarIfNecessary(Tab tab) {
        // Set up the tab observer to watch for the tab being shown (not hidden) and a valid
        // connection. When both conditions are met a snackbar is shown.
        OfflinePageTabObserver.addObserverForTab(tab);
    }

    /**
     * Shows the "reload" snackbar for the given tab.
     * @param activity The activity owning the tab.
     * @param snackbarController Class to show the snackbar.
     */
    public static void showReloadSnackbar(Context context, SnackbarManager snackbarManager,
            final SnackbarController snackbarController, int tabId) {
        if (tabId == Tab.INVALID_TAB_ID) return;

        Log.d(TAG, "showReloadSnackbar called with controller " + snackbarController);
        Snackbar snackbar =
                Snackbar.make(context.getString(R.string.offline_pages_viewing_offline_page),
                        snackbarController, Snackbar.TYPE_ACTION, Snackbar.UMA_OFFLINE_PAGE_RELOAD)
                        .setSingleLine(false).setAction(context.getString(R.string.reload), tabId);
        snackbar.setDuration(sSnackbarDurationMs);
        snackbarManager.showSnackbar(snackbar);
    }

    /**
     * Gets a snackbar controller that we can use to show our snackbar.
     * @param tabModelSelector used to retrieve a tab by ID
     */
    private static SnackbarController createReloadSnackbarController(
            final TabModelSelector tabModelSelector) {
        Log.d(TAG, "building snackbar controller");

        return new SnackbarController() {
            @Override
            public void onAction(Object actionData) {
                assert actionData != null;
                int tabId = (int) actionData;
                RecordUserAction.record("OfflinePages.ReloadButtonClicked");
                Tab foundTab = tabModelSelector.getTabById(tabId);
                if (foundTab == null) return;
                // Delegates to Tab to reload the page. Tab will send the correct header in order to
                // load the right page.
                foundTab.reload();
            }

            @Override
            public void onDismissNoAction(Object actionData) {
                RecordUserAction.record("OfflinePages.ReloadButtonNotClicked");
            }
        };
    }

    public static DeviceConditions getDeviceConditions(Context context) {
        return getInstance().getDeviceConditionsImpl(context);
    }

    /**
     * Records UMA data when the Offline Pages Background Load service awakens.
     * @param context android context
     */
    public static void recordWakeupUMA(Context context, long taskScheduledTimeMillis) {
        DeviceConditions deviceConditions = getDeviceConditions(context);
        if (deviceConditions == null) return;

        // Report charging state.
        RecordHistogram.recordBooleanHistogram(
                "OfflinePages.Wakeup.ConnectedToPower", deviceConditions.isPowerConnected());

        // Report battery percentage.
        RecordHistogram.recordPercentageHistogram(
                "OfflinePages.Wakeup.BatteryPercentage", deviceConditions.getBatteryPercentage());

        // Report the default network found (or none, if we aren't connected).
        int connectionType = deviceConditions.getNetConnectionType();
        Log.d(TAG, "Found default network of type " + connectionType);
        RecordHistogram.recordEnumeratedHistogram("OfflinePages.Wakeup.NetworkAvailable",
                connectionType, ConnectionType.CONNECTION_LAST + 1);

        // Collect UMA on the time since the request started.
        long nowMillis = System.currentTimeMillis();
        long delayInMilliseconds = nowMillis - taskScheduledTimeMillis;
        if (delayInMilliseconds <= 0) {
            return;
        }
        RecordHistogram.recordLongTimesHistogram(
                "OfflinePages.Wakeup.DelayTime",
                delayInMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Share an offline copy of the current page.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param mainActivity Activity that is used to access package manager.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (see
     *                 TargetChosenReceiver#isSupported()).
     * @param currentTab The current tab for which sharing is being done.
     */
    public static void shareOfflinePage(final boolean shareDirectly, final boolean saveLastUsed,
            final Activity mainActivity, final String text, final Uri screenshotUri,
            final ShareHelper.TargetChosenCallback callback, final Tab currentTab) {
        final String url = currentTab.getUrl();
        final String title = currentTab.getTitle();
        final OfflinePageBridge offlinePageBridge =
                OfflinePageBridge.getForProfile(currentTab.getProfile());

        if (offlinePageBridge == null) {
            Log.e(TAG, "Unable to perform sharing on current tab.");
            return;
        }

        OfflinePageItem offlinePage = currentTab.getOfflinePage();
        if (offlinePage != null) {
            // If we're currently on offline page get the saved file directly.
            prepareFileAndShare(shareDirectly, saveLastUsed, mainActivity, title, text,
                                url, screenshotUri, callback, offlinePage.getFilePath());
            return;
        }

        // If this is an online page, share the offline copy of it.
        Callback<OfflinePageItem> prepareForSharing = onGotOfflinePageItemToShare(shareDirectly,
                saveLastUsed, mainActivity, title, text, url, screenshotUri, callback);
        offlinePageBridge.selectPageForOnlineUrl(url, currentTab.getId(),
                selectPageForOnlineUrlCallback(currentTab.getWebContents(), offlinePageBridge,
                        prepareForSharing));
    }

    /**
     * Callback for receiving the OfflinePageItem and use it to call prepareForSharing.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param mainActivity Activity that is used to access package manager
     * @param title Title of the page.
     * @param onlineUrl Online URL associated with the offline page that is used to access the
     *                  offline page file path.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param mContext The application context.
     * @return a callback of OfflinePageItem
     */
    private static Callback<OfflinePageItem> onGotOfflinePageItemToShare(
            final boolean shareDirectly, final boolean saveLastUsed, final Activity mainActivity,
            final String title, final String text, final String onlineUrl, final Uri screenshotUri,
            final ShareHelper.TargetChosenCallback callback) {
        return new Callback<OfflinePageItem>() {
            @Override
            public void onResult(OfflinePageItem item) {
                String offlineFilePath = (item == null) ? null : item.getFilePath();
                prepareFileAndShare(shareDirectly, saveLastUsed, mainActivity, title, text,
                        onlineUrl, screenshotUri, callback, offlineFilePath);
            }
        };
    }

    /**
     * Takes the offline page item from selectPageForOnlineURL. If it exists, invokes
     * |prepareForSharing| with it.  Otherwise, saves a page for the online URL and invokes
     * |prepareForSharing| with the result when it's ready.
     * @param webContents Contents of the page to save.
     * @param offlinePageBridge A static copy of the offlinePageBridge.
     * @param prepareForSharing Callback of a single OfflinePageItem that is used to call
     *                          prepareForSharing
     * @return a callback of OfflinePageItem
     */
    private static Callback<OfflinePageItem> selectPageForOnlineUrlCallback(
            final WebContents webContents, final OfflinePageBridge offlinePageBridge,
            final Callback<OfflinePageItem> prepareForSharing) {
        return new Callback<OfflinePageItem>() {
            @Override
            public void onResult(OfflinePageItem item) {
                if (item == null) {
                    // If the page has no offline copy, save the page offline.
                    ClientId clientId = ClientId.createGuidClientIdForNamespace(
                            OfflinePageBridge.SHARE_NAMESPACE);
                    offlinePageBridge.savePage(webContents, clientId,
                            savePageCallback(prepareForSharing, offlinePageBridge));
                    return;
                }
                // If the online page has offline copy associated with it, use the file directly.
                prepareForSharing.onResult(item);
            }
        };
    }

    /**
     * Saves the web page loaded into web contents. If page saved successfully, get the offline
     * page item with the save page result and use it to invoke |prepareForSharing|. Otherwise,
     * invokes |prepareForSharing| with null.
     * @param prepareForSharing Callback of a single OfflinePageItem that is used to call
     *                          prepareForSharing
     * @param offlinePageBridge A static copy of the offlinePageBridge.
     * @return a call back of a list of OfflinePageItem
     */
    private static OfflinePageBridge.SavePageCallback savePageCallback(
            final Callback<OfflinePageItem> prepareForSharing,
            final OfflinePageBridge offlinePageBridge) {
        return new OfflinePageBridge.SavePageCallback() {
            @Override
            public void onSavePageDone(int savePageResult, String url, long offlineId) {
                if (savePageResult != SavePageResult.SUCCESS) {
                    Log.e(TAG, "Unable to save the page.");
                    prepareForSharing.onResult(null);
                    return;
                }

                offlinePageBridge.getPageByOfflineId(offlineId, prepareForSharing);
            }
        };
    }

    /**
     * If file path of offline page is not null, do file operations needed for the page to be
     * shared. Otherwise, only share the online url.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param activity Activity that is used to access package manager
     * @param title Title of the page.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param onlineUrl Online URL associated with the offline page that is used to access the
     *                  offline page file path.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (on
     *                 older Android versions).
     * @param filePath File path of the offline page.
     */
    private static void prepareFileAndShare(final boolean shareDirectly, final boolean saveLastUsed,
            final Activity activity, final String title, final String text, final String onlineUrl,
            final Uri screenshotUri, final ShareHelper.TargetChosenCallback callback,
            final String filePath) {
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                if (filePath == null) return null;

                File offlinePageOriginal = new File(filePath);
                File shareableDir = getDirectoryForOfflineSharing(activity);

                if (shareableDir == null) {
                    Log.e(TAG, "Unable to create subdirectory in shareable directory");
                    return null;
                }

                String fileName = rewriteOfflineFileName(offlinePageOriginal.getName());
                File offlinePageShareable = new File(shareableDir, fileName);

                if (offlinePageShareable.exists()) {
                    try {
                        // Old shareable files are stored in an external directory, which may cause
                        // problems when:
                        // 1. Files been changed by external sources.
                        // 2. Difference in file size that results in partial overwrite.
                        // Thus the file is deleted before we make a new copy.
                        offlinePageShareable.delete();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to delete: " + offlinePageOriginal.getName(), e);
                        return null;
                    }
                }
                if (copyToShareableLocation(offlinePageOriginal, offlinePageShareable)) {
                    return offlinePageShareable;
                }

                return null;
            }

            @Override
            protected void onPostExecute(File offlinePageShareable) {
                Uri offlineUri = null;
                if (offlinePageShareable != null) {
                    offlineUri = Uri.fromFile(offlinePageShareable);
                }
                ShareHelper.share(shareDirectly, saveLastUsed, activity, title, text, onlineUrl,
                        offlineUri, screenshotUri, callback);
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Copies the file from internal storage to a sharable directory.
     * @param src The original file to be copied.
     * @param dst The destination file.
     */
    @VisibleForTesting
    static boolean copyToShareableLocation(File src, File dst) {
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(src);
            outputStream = new FileOutputStream(dst);

            FileChannel inChannel = inputStream.getChannel();
            FileChannel outChannel = outputStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy the file: " + src.getName(), e);
            return false;
        } finally {
            StreamUtil.closeQuietly(inputStream);
            StreamUtil.closeQuietly(outputStream);
        }
        return true;
    }

    /**
     * Gets the directory to use for sharing offline pages, creating it if necessary.
     * @param context Context that is used to access external cache directory.
     * @return Path to the directory where shared files are stored.
     */
    @VisibleForTesting
    static File getDirectoryForOfflineSharing(Context context) {
        if (sOfflineSharingDirectory == null) {
            sOfflineSharingDirectory =
                    new File(context.getExternalCacheDir(), EXTERNAL_MHTML_FILE_PATH);
        }
        if (!sOfflineSharingDirectory.exists() && !sOfflineSharingDirectory.mkdir()) {
            sOfflineSharingDirectory = null;
        }
        return sOfflineSharingDirectory;
    }

    /**
     * Rewrite file name so that it does not contain periods except the one to separate the file
     * extension.
     * This step is used to ensure that file name can be recognized by intent filter (.*\\.mhtml")
     * as Android's path pattern only matches the first dot that appears in a file path.
     * @pram fileName Name of the offline page file.
     */
    @VisibleForTesting
    static String rewriteOfflineFileName(String fileName) {
        fileName = fileName.replaceAll("\\s+", "");
        return fileName.replaceAll("\\.(?=.*\\.)", "_");
    }

    /**
     * Clears all shared mhtml files.
     * @param context Context that is used to access external cache directory.
     */
    public static void clearSharedOfflineFiles(final Context context) {
        if (!OfflinePageBridge.isPageSharingEnabled()) return;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                File offlinePath = getDirectoryForOfflineSharing(context);
                if (offlinePath != null) {
                    FileUtils.recursivelyDeleteFile(offlinePath);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Retrieves the extra request header to reload the offline page.
     * @param tab The current tab.
     * @return The extra request header string.
     */
    public static String getOfflinePageHeaderForReload(Tab tab) {
        OfflinePageBridge offlinePageBridge = getInstance().getOfflinePageBridge(tab.getProfile());
        if (offlinePageBridge == null) return "";
        return offlinePageBridge.getOfflinePageHeaderForReload(tab.getWebContents());
    }

    private static boolean isPowerConnected(Intent batteryStatus) {
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isConnected = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        Log.d(TAG, "Power connected is " + isConnected);
        return isConnected;
    }

    private static int batteryPercentage(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (scale == 0) return 0;

        int percentage = Math.round(100 * level / (float) scale);
        Log.d(TAG, "Battery Percentage is " + percentage);
        return percentage;
    }

    protected OfflinePageBridge getOfflinePageBridge(Profile profile) {
        return OfflinePageBridge.getForProfile(profile);
    }

    /** Returns the current device conditions. May be overridden for testing. */
    protected DeviceConditions getDeviceConditionsImpl(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // Note this is a sticky intent, so we aren't really registering a receiver, just getting
        // the sticky intent.  That means that we don't need to unregister the filter later.
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return null;

        return new DeviceConditions(isPowerConnected(batteryStatus),
                batteryPercentage(batteryStatus),
                NetworkChangeNotifier.getInstance().getCurrentConnectionType());
    }

    @VisibleForTesting
    static void setInstanceForTesting(OfflinePageUtils instance) {
        sInstance = instance;
    }

    @VisibleForTesting
    public static void setSnackbarDurationForTesting(int durationMs) {
        sSnackbarDurationMs = durationMs;
    }
}
