// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.ChromeBrowserProviderClient;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarksModel.AddBookmarkCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageFreeUpSpaceCallback;
import org.chromium.chrome.browser.offlinepages.OfflinePageFreeUpSpaceDialog;
import org.chromium.chrome.browser.offlinepages.OfflinePageOpenStorageSettingsDialog;
import org.chromium.chrome.browser.offlinepages.OfflinePageStorageSpacePolicy;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * A class holding static util functions for enhanced bookmark.
 */
public class EnhancedBookmarkUtils {

    private static final String BOOKMARK_SAVE_NAME = "SaveBookmark";
    private static final int[] DEFAULT_BACKGROUND_COLORS = {
            0xFFE64A19,
            0xFFF09300,
            0xFFAFB42B,
            0xFF689F38,
            0xFF0B8043,
            0xFF0097A7,
            0xFF7B1FA2,
            0xFFC2185B
    };

    /**
     * @return True if enhanced bookmark feature is enabled.
     */
    public static boolean isEnhancedBookmarkEnabled() {
        return BookmarksBridge.isEnhancedBookmarksEnabled();
    }

    /**
     * If the tab has already been bookmarked, start {@link EnhancedBookmarkEditActivity} for the
     * bookmark. If not, add the bookmark to bookmarkmodel, and show a snackbar notifying the user.
     * @param idToAdd The bookmark ID if the tab has already been bookmarked.
     * @param bookmarkModel The enhanced bookmark model.
     * @param tab The tab to add or edit a bookmark.
     * @param snackbarManager The SnackbarManager used to show the snackbar.
     * @param activity Current activity.
     */
    public static void addOrEditBookmark(long idToAdd, EnhancedBookmarksModel bookmarkModel,
            Tab tab, SnackbarManager snackbarManager, Activity activity) {
        if (idToAdd != ChromeBrowserProviderClient.INVALID_BOOKMARK_ID) {
            startEditActivity(activity, new BookmarkId(idToAdd, BookmarkType.NORMAL),
                    tab.getWebContents());
            return;
        }

        BookmarkId parent = bookmarkModel.getDefaultFolder();
        bookmarkModel.addBookmarkAsync(parent, bookmarkModel.getChildCount(parent), tab.getTitle(),
                tab.getUrl(), tab.getWebContents(), tab.isShowingErrorPage(),
                createAddBookmarkCallback(bookmarkModel, snackbarManager, activity));
    }

    /**
     * Saves an offline copy for the specified tab that is bookmarked. A snackbar will be shown to
     * notify the user.
     * @param bookmarkId The bookmark ID for the tab.
     * @param bookmarkModel The enhanced bookmark model.
     * @param tab The bookmarked tab to save an offline copy.
     * @param snackbarManager The SnackbarManager used to show the snackbar.
     * @param activity Current activity.
     */
    public static void saveBookmarkOffline(long bookmarkId, EnhancedBookmarksModel bookmarkModel,
            Tab tab, final SnackbarManager snackbarManager, Activity activity) {
        assert bookmarkId != ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
        bookmarkModel.saveOfflinePage(new BookmarkId(bookmarkId, BookmarkType.NORMAL),
                tab.getWebContents(),
                createAddBookmarkCallback(bookmarkModel, snackbarManager, activity));
    }

    private static AddBookmarkCallback createAddBookmarkCallback(
            final EnhancedBookmarksModel bookmarkModel, final SnackbarManager snackbarManager,
            final Activity activity) {
        return new AddBookmarkCallback() {
            @Override
            public void onBookmarkAdded(BookmarkId bookmarkId, int saveResult) {
                SnackbarController snackbarController = null;
                int messageId;
                int buttonId = 0;

                OfflinePageBridge offlinePageBridge = bookmarkModel.getOfflinePageBridge();
                if (offlinePageBridge == null) {
                    messageId = R.string.enhanced_bookmark_page_saved;
                } else if (saveResult == AddBookmarkCallback.SKIPPED) {
                    messageId = R.string.offline_pages_page_skipped;
                } else if (OfflinePageUtils.isStorageAlmostFull()) {
                    messageId = saveResult == AddBookmarkCallback.SAVED
                            ? R.string.offline_pages_page_saved_storage_near_full
                            : R.string.offline_pages_page_failed_to_save_storage_near_full;
                    // Show "Free up space" button.
                    buttonId = R.string.offline_pages_free_up_space_title;
                    snackbarController = createSnackbarControllerForFreeUpSpaceButton(
                            bookmarkModel, snackbarManager, activity);
                } else {
                    messageId = saveResult == AddBookmarkCallback.SAVED
                            ? R.string.offline_pages_page_saved
                            : R.string.offline_pages_page_failed_to_save;
                }

                // Show "Edit" button when "Free up space" button is not desired, regardless whether
                // the offline page was saved successfuly, because a bookmark was created and user
                // might want to edit title.
                if (buttonId == 0) {
                    buttonId = R.string.enhanced_bookmark_item_edit;
                    snackbarController = createSnackbarControllerForEditButton(
                            bookmarkModel, activity, bookmarkId);
                }

                snackbarManager.showSnackbar(
                        Snackbar.make(activity.getString(messageId), snackbarController)
                                .setAction(activity.getString(buttonId), null)
                                .setSingleLine(false));
            }
        };
    }

    /**
     * Creates a snackbar controller for a case where "Edit" button is shown to edit the newly
     * created bookmark.
     */
    private static SnackbarController createSnackbarControllerForEditButton(
            final EnhancedBookmarksModel bookmarkModel, final Activity activity,
            final BookmarkId bookmarkId) {
        return new SnackbarController() {
            @Override
            public void onDismissForEachType(boolean isTimeout) {}

            @Override
            public void onDismissNoAction(Object actionData) {
                // This method will be called only if the snackbar is dismissed by timeout.
                bookmarkModel.destroy();
            }

            @Override
            public void onAction(Object actionData) {
                // Show edit activity with the name of parent folder highlighted.
                startEditActivity(activity, bookmarkId, null);
                bookmarkModel.destroy();
            }
        };
    }

    /**
     * Creates a snackbar controller for a case where "Free up space" button is shown to clean up
     * space taken by the offline pages.
     */
    private static SnackbarController createSnackbarControllerForFreeUpSpaceButton(
            final EnhancedBookmarksModel bookmarkModel, final SnackbarManager snackbarManager,
            final Activity activity) {
        return new SnackbarController() {
            @Override
            public void onDismissForEachType(boolean isTimeout) {}

            @Override
            public void onDismissNoAction(Object actionData) {
                // This method will be called only if the snackbar is dismissed by timeout.
                RecordUserAction.record(
                        "OfflinePages.SaveStatusSnackbar.FreeUpSpaceButtonNotClicked");
                bookmarkModel.destroy();
            }

            @Override
            public void onAction(Object actionData) {
                RecordUserAction.record("OfflinePages.SaveStatusSnackbar.FreeUpSpaceButtonClicked");
                OfflinePageStorageSpacePolicy policy =
                        new OfflinePageStorageSpacePolicy(bookmarkModel.getOfflinePageBridge());
                if (policy.hasPagesToCleanUp()) {
                    OfflinePageFreeUpSpaceCallback callback = new OfflinePageFreeUpSpaceCallback() {
                        @Override
                        public void onFreeUpSpaceDone() {
                            snackbarManager.showSnackbar(
                                    OfflinePageFreeUpSpaceDialog.createStorageClearedSnackbar(
                                            activity));
                            bookmarkModel.destroy();
                        }
                        @Override
                        public void onFreeUpSpaceCancelled() {
                            bookmarkModel.destroy();
                        }
                    };
                    OfflinePageFreeUpSpaceDialog dialog = OfflinePageFreeUpSpaceDialog.newInstance(
                            bookmarkModel.getOfflinePageBridge(), callback);
                    dialog.show(activity.getFragmentManager(), null);
                } else {
                    OfflinePageOpenStorageSettingsDialog.showDialog(activity);
                }
            }
        };
    }

    /**
     * Shows enhanced bookmark main UI, if it is turned on. Does nothing if it is turned off.
     * @return True if enhanced bookmark is on, false otherwise.
     */
    public static boolean showEnhancedBookmarkIfEnabled(Activity activity) {
        if (!isEnhancedBookmarkEnabled()) {
            return false;
        }
        if (DeviceFormFactor.isTablet(activity)) {
            openBookmark(activity, UrlConstants.BOOKMARKS_URL);
        } else {
            activity.startActivity(new Intent(activity, EnhancedBookmarkActivity.class));
        }
        return true;
    }

    /**
     * Starts an {@link EnhancedBookmarkEditActivity} for the given {@link BookmarkId}.
     */
    public static void startEditActivity(
            Context context, BookmarkId bookmarkId, WebContents webContents) {
        Intent intent = new Intent(context, EnhancedBookmarkEditActivity.class);
        intent.putExtra(EnhancedBookmarkEditActivity.INTENT_BOOKMARK_ID, bookmarkId.toString());
        if (webContents != null) {
            intent.putExtra(EnhancedBookmarkEditActivity.INTENT_WEB_CONTENTS, webContents);
        }
        if (context instanceof EnhancedBookmarkActivity) {
            ((EnhancedBookmarkActivity) context).startActivityForResult(
                    intent, EnhancedBookmarkActivity.EDIT_BOOKMARK_REQUEST_CODE);
        } else {
            context.startActivity(intent);
        }
    }

    /**
     * Generate color based on bookmarked url's hash code. Same color will
     * always be returned given same bookmark item.
     *
     * @param item bookmark the color represents for
     * @return int for the generated color
     */
    public static int generateBackgroundColor(BookmarkItem item) {
        int normalizedIndex = MathUtils.positiveModulo(item.getUrl().hashCode(),
                DEFAULT_BACKGROUND_COLORS.length);
        return DEFAULT_BACKGROUND_COLORS[normalizedIndex];
    }

    /**
     * Save the bookmark in bundle to save state of a fragment/activity.
     * @param bundle Argument holder or savedInstanceState of the fragment/activity.
     * @param bookmark The bookmark to save.
     */
    public static void saveBookmarkIdToBundle(Bundle bundle, BookmarkId bookmark) {
        bundle.putString(BOOKMARK_SAVE_NAME, bookmark.toString());
    }

    /**
     * Retrieve the bookmark previously saved in the arguments bundle.
     * @param bundle Argument holder or savedInstanceState of the fragment/activity.
     * @return The ID of the bookmark to retrieve.
     */
    public static BookmarkId getBookmarkIdFromBundle(Bundle bundle) {
        return BookmarkId.getBookmarkIdFromString(bundle.getString(BOOKMARK_SAVE_NAME));
    }

    public static void openBookmark(Activity activity, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setClassName(activity.getApplicationContext().getPackageName(),
                ChromeLauncherActivity.class.getName());
        intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                activity.getApplicationContext().getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        IntentHandler.startActivityForTrustedIntent(intent, activity);
    }

    /**
     * Get dominant color from bitmap. This function uses favicon helper to fulfil its task.
     * @param bitmap The bitmap to extract color from.
     * @return The dominant color in ARGB format.
     */
    public static int getDominantColorForBitmap(Bitmap bitmap) {
        int mDominantColor = FaviconHelper.getDominantColorForBitmap(bitmap);
        // FaviconHelper returns color in ABGR format, do a manual conversion here.
        int red = (mDominantColor & 0xff) << 16;
        int green = mDominantColor & 0xff00;
        int blue = (mDominantColor & 0xff0000) >> 16;
        int alpha = mDominantColor & 0xff000000;
        return alpha + red + green + blue;
    }

    /**
     * Updates the title of chrome shown in recent tasks. It only takes effect in document mode.
     */
    public static void setTaskDescriptionInDocumentMode(Activity activity, String description) {
        if (FeatureUtilities.isDocumentMode(activity)) {
            // Setting icon to be null and color to be 0 will means "take no effect".
            ApiCompatibilityUtils.setTaskDescription(activity, description, null, 0);
        }
    }

    /**
     * Closes the EnhancedBookmark Activity on Phone. Does nothing on tablet.
     */
    public static void finishActivityOnPhone(Context context) {
        if (context instanceof EnhancedBookmarkActivity) {
            ((Activity) context).finish();
        }
    }
}
