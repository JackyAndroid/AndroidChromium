// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.StrictMode;
import android.provider.Browser;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.download.ui.BackendProvider;
import org.chromium.chrome.browser.download.ui.BackendProvider.DownloadDelegate;
import org.chromium.chrome.browser.download.ui.DownloadFilter;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A class containing some utility static methods.
 */
public class DownloadUtils {

    private static final String TAG = "download";

    private static final String DEFAULT_MIME_TYPE = "*/*";
    private static final String MIME_TYPE_DELIMITER = "/";

    private static final String EXTRA_IS_OFF_THE_RECORD =
            "org.chromium.chrome.browser.download.IS_OFF_THE_RECORD";

    private static final String PREF_IS_DOWNLOAD_HOME_ENABLED =
            "org.chromium.chrome.browser.download.IS_DOWNLOAD_HOME_ENABLED";

    /**
     * @return Whether or not the Download Home is enabled.
     */
    public static boolean isDownloadHomeEnabled() {
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        return preferences.getBoolean(PREF_IS_DOWNLOAD_HOME_ENABLED, false);
    }

    /**
     * Caches the native flag that enables the Download Home in SharedPreferences.
     * This is necessary because the DownloadActivity can be opened before native has been loaded.
     */
    public static void cacheIsDownloadHomeEnabled() {
        boolean isEnabled = ChromeFeatureList.isEnabled("DownloadsUi");
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        preferences.edit().putBoolean(PREF_IS_DOWNLOAD_HOME_ENABLED, isEnabled).apply();
    }

    /**
     * Displays the download manager UI. Note the UI is different on tablets and on phones.
     * @return Whether the UI was shown.
     */
    public static boolean showDownloadManager(@Nullable Activity activity, @Nullable Tab tab) {
        if (!isDownloadHomeEnabled()) return false;

        // Figure out what tab was last being viewed by the user.
        if (activity == null) activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (tab == null && activity instanceof ChromeTabbedActivity) {
            tab = ((ChromeTabbedActivity) activity).getActivityTab();
        }

        Context appContext = ContextUtils.getApplicationContext();
        if (DeviceFormFactor.isTablet(appContext)) {
            // Download Home shows up as a tab on tablets.
            LoadUrlParams params = new LoadUrlParams(UrlConstants.DOWNLOADS_URL);
            if (tab == null || !tab.isInitialized()) {
                // Open a new tab, which pops Chrome into the foreground.
                TabDelegate delegate = new TabDelegate(false);
                delegate.createNewTab(params, TabLaunchType.FROM_CHROME_UI, null);
            } else {
                // Download Home shows up inside an existing tab, but only if the last Activity was
                // the ChromeTabbedActivity.
                tab.loadUrl(params);

                // Bring Chrome to the foreground, if possible.
                Intent intent = Tab.createBringTabToFrontIntent(tab.getId());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    IntentUtils.safeStartActivity(appContext, intent);
                }
            }
        } else {
            // Download Home shows up as a new Activity on phones.
            Intent intent = new Intent();
            intent.setClass(appContext, DownloadActivity.class);
            if (tab != null) intent.putExtra(EXTRA_IS_OFF_THE_RECORD, tab.isIncognito());
            if (activity == null) {
                // Stands alone in its own task.
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(intent);
            } else {
                // Sits on top of another Activity.
                intent.putExtra(IntentHandler.EXTRA_PARENT_COMPONENT, activity.getComponentName());
                activity.startActivity(intent);
            }
        }

        return true;
    }

    /**
     * @return Whether or not the Intent corresponds to a DownloadActivity that should show off the
     *         record downloads.
     */
    public static boolean shouldShowOffTheRecordDownloads(Intent intent) {
        return IntentUtils.safeGetBooleanExtra(intent, EXTRA_IS_OFF_THE_RECORD, false);
    }

    /**
     * Records metrics related to downloading a page. Should be called after a tap on the download
     * page button.
     * @param tab The Tab containing the page being downloaded.
     */
    public static void recordDownloadPageMetrics(Tab tab) {
        RecordHistogram.recordPercentageHistogram("OfflinePages.SavePage.PercentLoaded",
                tab.getProgress());
    }

    /**
     * Shows a "Downloading..." toast. Should be called after a download has been started.
     * @param context The {@link Context} used to make the toast.
     */
    public static void showDownloadStartToast(Context context) {
        Toast.makeText(context, R.string.download_pending, Toast.LENGTH_SHORT).show();
    }

    /**
     * Issues a request to the {@link DownloadDelegate} associated with backendProvider to check
     * for externally removed downloads.
     * See {@link DownloadManagerService#checkForExternallyRemovedDownloads}.
     *
     * @param backendProvider The {@link BackendProvider} associated with the DownloadDelegate used
     *                        to check for externally removed downloads.
     * @param isOffTheRecord  Whether to check downloads for the off the record profile.
     */
    public static void checkForExternallyRemovedDownloads(BackendProvider backendProvider,
            boolean isOffTheRecord) {
        if (isOffTheRecord) {
            backendProvider.getDownloadDelegate().checkForExternallyRemovedDownloads(true);
        }
        backendProvider.getDownloadDelegate().checkForExternallyRemovedDownloads(false);
        RecordUserAction.record(
                "Android.DownloadManager.CheckForExternallyRemovedItems");
    }

    /**
     * Trigger the download of an Offline Page.
     * @param context Context to pull resources from.
     */
    public static void downloadOfflinePage(Context context, Tab tab) {
        final OfflinePageDownloadBridge bridge = new OfflinePageDownloadBridge(tab.getProfile());
        bridge.startDownload(tab);
        bridge.destroy();
        DownloadUtils.recordDownloadPageMetrics(tab);
    }

    /**
     * Whether the user should be allowed to download the current page.
     * @param tab Tab displaying the page that will be downloaded.
     * @return    Whether the "Download Page" button should be enabled.
     */
    public static boolean isAllowedToDownloadPage(Tab tab) {
        if (tab == null) return false;

        // Only allow HTTP and HTTPS pages, as that is these are the only scenarios supported by the
        // background/offline page saving.
        if (!tab.getUrl().startsWith(UrlConstants.HTTP_SCHEME)
                && !tab.getUrl().startsWith(UrlConstants.HTTPS_SCHEME)) {
            return false;
        }
        if (tab.isShowingErrorPage()) return false;
        if (tab.isShowingInterstitialPage()) return false;

        // Don't allow re-downloading the currently displayed offline page.
        if (tab.isOfflinePage()) return false;

        // Offline pages isn't supported in Incognito.
        if (tab.isIncognito()) return false;

        return true;
    }

    /**
     * Creates an Intent to open the file in another app by firing an Intent to Android.
     * @param fileUri  Uri pointing to the file.
     * @param mimeType MIME type for the file.
     * @return Intent that can be used to start an Activity for the file.
     */
    public static Intent createViewIntentForDownloadItem(Uri fileUri, String mimeType) {
        Intent fileIntent = new Intent(Intent.ACTION_VIEW);
        String normalizedMimeType = Intent.normalizeMimeType(mimeType);
        if (TextUtils.isEmpty(normalizedMimeType)) {
            fileIntent.setData(fileUri);
        } else {
            fileIntent.setDataAndType(fileUri, normalizedMimeType);
        }
        fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        fileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fileIntent;
    }

    /**
     * Creates an Intent to share {@code items} with another app by firing an Intent to Android.
     *
     * Sharing a DownloadItem shares the file itself, while sharing an OfflinePageItem shares the
     * URL.
     *
     * @param items Items to share.
     * @return      Intent that can be used to share the items.
     */
    public static Intent createShareIntent(List<DownloadHistoryItemWrapper> items) {
        Intent shareIntent = new Intent();
        String intentAction;
        ArrayList<Uri> itemUris = new ArrayList<Uri>();
        StringBuilder offlinePagesString = new StringBuilder();
        int selectedItemsFilterType = items.get(0).getFilterType();

        String intentMimeType = "";
        String[] intentMimeParts = {"", ""};

        for (int i = 0; i < items.size(); i++) {
            DownloadHistoryItemWrapper wrappedItem  = items.get(i);

            if (wrappedItem instanceof DownloadHistoryItemWrapper.OfflinePageItemWrapper) {
                if (offlinePagesString.length() != 0) {
                    offlinePagesString.append("\n");
                }
                offlinePagesString.append(wrappedItem.getUrl());
            } else {
                itemUris.add(getUriForItem(wrappedItem.getFile()));
            }

            if (selectedItemsFilterType != wrappedItem.getFilterType()) {
                selectedItemsFilterType = DownloadFilter.FILTER_ALL;
            }

            String mimeType = Intent.normalizeMimeType(wrappedItem.getMimeType());

            // If a mime type was not retrieved from the backend or could not be normalized,
            // set the mime type to the default.
            if (TextUtils.isEmpty(mimeType)) {
                intentMimeType = DEFAULT_MIME_TYPE;
                continue;
            }

            // If the intent mime type has not been set yet, set it to the mime type for this item.
            if (TextUtils.isEmpty(intentMimeType)) {
                intentMimeType = mimeType;
                if (!TextUtils.isEmpty(intentMimeType)) {
                    intentMimeParts = intentMimeType.split(MIME_TYPE_DELIMITER);
                    // Guard against invalid mime types.
                    if (intentMimeParts.length != 2) intentMimeType = DEFAULT_MIME_TYPE;
                }
                continue;
            }

            // Either the mime type is already the default or it matches the current item's mime
            // type. In either case, intentMimeType is already the correct value.
            if (TextUtils.equals(intentMimeType, DEFAULT_MIME_TYPE)
                    || TextUtils.equals(intentMimeType, mimeType)) {
                continue;
            }

            String[] mimeParts = mimeType.split(MIME_TYPE_DELIMITER);
            if (!TextUtils.equals(intentMimeParts[0], mimeParts[0])) {
                // The top-level types don't match; fallback to the default mime type.
                intentMimeType = DEFAULT_MIME_TYPE;
            } else {
                // The mime type should be {top-level type}/*
                intentMimeType = intentMimeParts[0] + MIME_TYPE_DELIMITER + "*";
            }
        }

        // Use Action_SEND if there is only one downloaded item or only text to share.
        if (itemUris.size() == 0 || (itemUris.size() == 1 && offlinePagesString.length() == 0)) {
            intentAction = Intent.ACTION_SEND;
        } else {
            intentAction = Intent.ACTION_SEND_MULTIPLE;
        }

        if (itemUris.size() == 1 && offlinePagesString.length() == 0) {
            // Sharing a DownloadItem.
            shareIntent.putExtra(Intent.EXTRA_STREAM, getUriForItem(items.get(0).getFile()));
        } else {
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, itemUris);
        }

        if (offlinePagesString.length() != 0) {
            shareIntent.putExtra(Intent.EXTRA_TEXT, offlinePagesString.toString());
        }

        shareIntent.setAction(intentAction);
        shareIntent.setType(intentMimeType);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        recordShareHistograms(items.size(), selectedItemsFilterType);

        return shareIntent;
    }

    private static Intent createShareIntent(Uri fileUri, String mimeType) {
        if (TextUtils.isEmpty(mimeType)) mimeType = DEFAULT_MIME_TYPE;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.setType(mimeType);
        return intent;
    }

    /**
     * Creates an Intent that allows viewing the given file in an internal media viewer.
     * @param fileUri  URI pointing at the file, ideally in file:// form.
     * @param shareUri URI pointing at the file, ideally in content:// form.
     * @param mimeType MIME type of the file.
     * @return Intent that can be fired to open the file.
     */
    public static Intent getMediaViewerIntentForDownloadItem(
            Uri fileUri, Uri shareUri, String mimeType) {
        Context context = ContextUtils.getApplicationContext();
        Intent viewIntent = createViewIntentForDownloadItem(fileUri, mimeType);

        Bitmap closeIcon = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.ic_arrow_back_white_24dp);
        Bitmap shareIcon = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.ic_share_white_24dp);

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(Color.BLACK);
        builder.setCloseButtonIcon(closeIcon);
        builder.setShowTitle(true);

        // Create a PendingIntent that can be used to view the file externally.
        // TODO(dfalcantara): Check if this is problematic in multi-window mode, where two
        //                    different viewers could be visible at the same time.
        Intent chooserIntent = Intent.createChooser(viewIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String openWithStr = context.getString(R.string.download_manager_open_with);
        PendingIntent pendingViewIntent = PendingIntent.getActivity(
                context, 0, chooserIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.addMenuItem(openWithStr, pendingViewIntent);

        // Create a PendingIntent that shares the file with external apps.
        PendingIntent pendingShareIntent = PendingIntent.getActivity(
                context, 0, createShareIntent(shareUri, mimeType), 0);
        builder.setActionButton(
                shareIcon, context.getString(R.string.share), pendingShareIntent, true);

        // Build up the Intent further.
        Intent intent = builder.build().intent;
        intent.setPackage(context.getPackageName());
        intent.setData(fileUri);
        intent.putExtra(CustomTabIntentDataProvider.EXTRA_IS_MEDIA_VIEWER, true);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        IntentHandler.addTrustedIntentExtras(intent, context);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(context, CustomTabActivity.class);
        return intent;
    }

    /**
     * Returns a URI that points at the file.
     * @param file File to get a URI for.
     * @return URI that points at that file, either as a content:// URI or a file:// URI.
     */
    public static Uri getUriForItem(File file) {
        Uri uri = null;

        // #getContentUriFromFile causes a disk read when it calls into FileProvider#getUriForFile.
        // Obtaining a content URI is on the critical path for creating a share intent after the
        // user taps on the share button, so even if we were to run this method on a background
        // thread we would have to wait. As it depends on user-selected items, we cannot
        // know/preload which URIs we need until the user presses share.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            // Try to obtain a content:// URI, which is preferred to a file:// URI so that
            // receiving apps don't attempt to determine the file's mime type (which often fails).
            uri = ContentUriUtils.getContentUriFromFile(ContextUtils.getApplicationContext(), file);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not create content uri: " + e);
        }
        StrictMode.setThreadPolicy(oldPolicy);

        if (uri == null) uri = Uri.fromFile(file);

        return uri;
    }

    private static void recordShareHistograms(int count, int filterType) {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Share.FileTypes",
                filterType, DownloadFilter.FILTER_BOUNDARY);

        RecordHistogram.recordLinearCountHistogram("Android.DownloadManager.Share.Count",
                count, 1, 20, 20);
    }

    /**
     * Fires an Intent to open a downloaded item.
     * @param context Context to use.
     * @param intent  Intent that can be fired.
     * @return Whether an Activity was successfully started for the Intent.
     */
    static boolean fireOpenIntentForDownload(Context context, Intent intent) {
        try {
            if (TextUtils.equals(intent.getPackage(), context.getPackageName())) {
                IntentHandler.startActivityForTrustedIntent(intent, context);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (ActivityNotFoundException ex) {
            Log.d(TAG, "Activity not found for " + intent.getType() + " over "
                    + intent.getData().getScheme(), ex);
        } catch (SecurityException ex) {
            Log.d(TAG, "cannot open intent: " + intent, ex);
        }

        return false;
    }
}
