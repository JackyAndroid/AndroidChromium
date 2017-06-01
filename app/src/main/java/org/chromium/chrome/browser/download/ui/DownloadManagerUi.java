// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.chromium.base.ContextUtils;
import org.chromium.base.FileUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.ui.base.DeviceFormFactor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Displays and manages the UI for the download manager.
 */

public class DownloadManagerUi implements OnMenuItemClickListener {

    /**
     * Interface to observe the changes in the download manager ui. This should be implemented by
     * the ui components that is shown, in order to let them get proper notifications.
     */
    public interface DownloadUiObserver {
        /**
         * Called when the filter has been changed by the user.
         */
        public void onFilterChanged(int filter);

        /**
         * Called when the download manager is not shown anymore.
         */
        public void onManagerDestroyed();
    }

    private static class DownloadBackendProvider implements BackendProvider {
        private OfflinePageDownloadBridge mOfflinePageBridge;
        private SelectionDelegate<DownloadHistoryItemWrapper> mSelectionDelegate;
        private ThumbnailProvider mThumbnailProvider;

        DownloadBackendProvider() {
            Resources resources = ContextUtils.getApplicationContext().getResources();
            int iconSize = resources.getDimensionPixelSize(R.dimen.downloads_item_icon_size);

            mOfflinePageBridge = new OfflinePageDownloadBridge(
                    Profile.getLastUsedProfile().getOriginalProfile());
            mSelectionDelegate = new SelectionDelegate<DownloadHistoryItemWrapper>();
            mThumbnailProvider = new ThumbnailProviderImpl(iconSize);
        }

        @Override
        public DownloadDelegate getDownloadDelegate() {
            return DownloadManagerService.getDownloadManagerService(
                    ContextUtils.getApplicationContext());
        }

        @Override
        public OfflinePageDownloadBridge getOfflinePageBridge() {
            return mOfflinePageBridge;
        }

        @Override
        public ThumbnailProvider getThumbnailProvider() {
            return mThumbnailProvider;
        }

        @Override
        public SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate() {
            return mSelectionDelegate;
        }

        @Override
        public void destroy() {
            getOfflinePageBridge().destroy();

            mThumbnailProvider.destroy();
            mThumbnailProvider = null;
        }
    }

    private class UndoDeletionSnackbarController implements SnackbarController {
        @Override
        public void onAction(Object actionData) {
            @SuppressWarnings("unchecked")
            List<DownloadHistoryItemWrapper> items = (List<DownloadHistoryItemWrapper>) actionData;

            // Deletion was undone. Add items back to the adapter.
            mHistoryAdapter.reAddItemsToAdapter(items);

            RecordUserAction.record("Android.DownloadManager.UndoDelete");
        }

        @Override
        public void onDismissNoAction(Object actionData) {
            @SuppressWarnings("unchecked")
            List<DownloadHistoryItemWrapper> items = (List<DownloadHistoryItemWrapper>) actionData;

            // Deletion was not undone. Remove downloads from backend.
            final ArrayList<File> filesToDelete = new ArrayList<>();

            // Some types of DownloadHistoryItemWrappers delete their own files when #remove()
            // is called. Determine which files are not deleted by the #remove() call.
            for (int i = 0; i < items.size(); i++) {
                DownloadHistoryItemWrapper wrappedItem  = items.get(i);
                if (!wrappedItem.remove()) filesToDelete.add(wrappedItem.getFile());
            }

            // Delete the files associated with the download items (if necessary) using a single
            // AsyncTask that batch deletes all of the files. The thread pool has a finite
            // number of tasks that can be queued at once. If too many tasks are queued an
            // exception is thrown. See crbug.com/643811.
            if (filesToDelete.size() != 0) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        FileUtils.batchDeleteFiles(filesToDelete);
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            RecordUserAction.record("Android.DownloadManager.Delete");
        }
    }

    private static BackendProvider sProviderForTests;

    private final DownloadHistoryAdapter mHistoryAdapter;
    private final FilterAdapter mFilterAdapter;
    private final ObserverList<DownloadUiObserver> mObservers = new ObserverList<>();
    private final BackendProvider mBackendProvider;

    private final SpaceDisplay mSpaceDisplay;
    private final ListView mFilterView;
    private final UndoDeletionSnackbarController mUndoDeletionSnackbarController;

    private BasicNativePage mNativePage;
    private Activity mActivity;
    private ViewGroup mMainView;
    private DownloadManagerToolbar mToolbar;
    private SelectableListLayout mSelectableListLayout;

    public DownloadManagerUi(
            Activity activity, boolean isOffTheRecord, ComponentName parentComponent) {
        mActivity = activity;
        mBackendProvider =
                sProviderForTests == null ? new DownloadBackendProvider() : sProviderForTests;

        mMainView = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.download_main, null);

        DrawerLayout drawerLayout = null;
        if (!DeviceFormFactor.isLargeTablet(activity)) {
            drawerLayout = (DrawerLayout) mMainView;
            addDrawerListener(drawerLayout);
        }

        mSelectableListLayout =
                (SelectableListLayout) mMainView.findViewById(R.id.selectable_list);

        mSelectableListLayout.initializeEmptyView(R.drawable.downloads_big,
                R.string.download_manager_ui_empty);

        mHistoryAdapter = new DownloadHistoryAdapter(isOffTheRecord, parentComponent);
        mSelectableListLayout.initializeRecyclerView(mHistoryAdapter);
        mHistoryAdapter.initialize(mBackendProvider);
        addObserver(mHistoryAdapter);

        mSpaceDisplay = new SpaceDisplay(mMainView, mHistoryAdapter);
        mHistoryAdapter.registerAdapterDataObserver(mSpaceDisplay);
        mSpaceDisplay.onChanged();

        mFilterAdapter = new FilterAdapter();
        mFilterAdapter.initialize(this);
        addObserver(mFilterAdapter);

        mToolbar = (DownloadManagerToolbar) mSelectableListLayout.initializeToolbar(
                R.layout.download_manager_toolbar, mBackendProvider.getSelectionDelegate(),
                0, drawerLayout, R.id.normal_menu_group, R.id.selection_mode_menu_group, this);
        mToolbar.setTitle(R.string.menu_downloads);
        addObserver(mToolbar);

        mFilterView = (ListView) mMainView.findViewById(R.id.section_list);
        mFilterView.setAdapter(mFilterAdapter);
        mFilterView.setOnItemClickListener(mFilterAdapter);

        mUndoDeletionSnackbarController = new UndoDeletionSnackbarController();
    }

    /**
     * Sets the {@link BasicNativePage} that holds this manager.
     */
    public void setBasicNativePage(BasicNativePage delegate) {
        mNativePage = delegate;
    }

    /**
     * Called when the activity/native page is destroyed.
     */
    public void onDestroyed() {
        for (DownloadUiObserver observer : mObservers) {
            observer.onManagerDestroyed();
            removeObserver(observer);
        }

        dismissUndoDeletionSnackbars();

        mBackendProvider.destroy();

        mHistoryAdapter.unregisterAdapterDataObserver(mSpaceDisplay);

        mSelectableListLayout.onDestroyed();
    }

    /**
     * Called when the UI needs to react to the back button being pressed.
     *
     * @return Whether the back button was handled.
     */
    public boolean onBackPressed() {
        if (mMainView instanceof DrawerLayout) {
            DrawerLayout drawerLayout = (DrawerLayout) mMainView;
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                closeDrawer();
                return true;
            }
        }
        if (mBackendProvider.getSelectionDelegate().isSelectionEnabled()) {
            mBackendProvider.getSelectionDelegate().clearSelection();
            return true;
        }
        return false;
    }

    /**
     * @return The view that shows the main download UI.
     */
    public ViewGroup getView() {
        return mMainView;
    }

    /**
     * Sets the download manager to the state that the url represents.
     */
    public void updateForUrl(String url) {
        int filter = DownloadFilter.getFilterFromUrl(url);
        onFilterChanged(filter);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.close_menu_id && !DeviceFormFactor.isTablet(mActivity)) {
            mActivity.finish();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_delete_menu_id) {
            deleteSelectedItems();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_share_menu_id) {
            shareSelectedItems();
            return true;
        }
        return false;
    }

    /**
     * @see DrawerLayout#openDrawer(int)
     */
    @VisibleForTesting
    public void openDrawer() {
        if (mMainView instanceof DrawerLayout) {
            ((DrawerLayout) mMainView).openDrawer(GravityCompat.START);
        }
    }

    /**
     * Adds a {@link DownloadUiObserver} to observe the changes in the download manager.
     */
    public void addObserver(DownloadUiObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes a {@link DownloadUiObserver} that were added in
     * {@link #addObserver(DownloadUiObserver)}
     */
    public void removeObserver(DownloadUiObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @see DrawerLayout#closeDrawer(int)
     */
    void closeDrawer() {
        if (mMainView instanceof DrawerLayout) {
            ((DrawerLayout) mMainView).closeDrawer(GravityCompat.START);
        }
    }

    /**
     * @return The activity that holds the download UI.
     */
    Activity getActivity() {
        return mActivity;
    }

    /**
     * @return The BackendProvider associated with the download UI.
     */
    public BackendProvider getBackendProvider() {
        return mBackendProvider;
    }

    /** Called when the filter has been changed by the user. */
    void onFilterChanged(int filter) {
        mBackendProvider.getSelectionDelegate().clearSelection();

        for (DownloadUiObserver observer : mObservers) {
            observer.onFilterChanged(filter);
        }

        if (mNativePage != null) {
            mNativePage.onStateChange(DownloadFilter.getUrlForFilter(filter));
        }

        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Filter", filter,
                DownloadFilter.FILTER_BOUNDARY);
    }

    private void shareSelectedItems() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        assert selectedItems.size() > 0;

        mActivity.startActivity(Intent.createChooser(createShareIntent(),
                mActivity.getString(R.string.share_link_chooser_title)));

        // TODO(twellington): ideally the intent chooser would be started with
        //                    startActivityForResult() and the selection would only be cleared after
        //                    receiving an OK response. See crbug.com/638916.
        mBackendProvider.getSelectionDelegate().clearSelection();
    }

    /**
     * @return An Intent to share the selected items.
     */
    @VisibleForTesting
    public Intent createShareIntent() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        return DownloadUtils.createShareIntent(selectedItems);
    }

    private void deleteSelectedItems() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        final List<DownloadHistoryItemWrapper> itemsToDelete = getItemsForDeletion();

        mBackendProvider.getSelectionDelegate().clearSelection();

        if (itemsToDelete.isEmpty()) return;

        mHistoryAdapter.removeItemsFromAdapter(itemsToDelete);

        dismissUndoDeletionSnackbars();

        boolean singleItemDeleted = selectedItems.size() == 1;
        String snackbarText = singleItemDeleted ? selectedItems.get(0).getDisplayFileName() :
                String.format(Locale.getDefault(), "%d", selectedItems.size());
        int snackbarTemplateId = singleItemDeleted ? R.string.undo_bar_delete_message
                : R.string.undo_bar_multiple_downloads_delete_message;

        Snackbar snackbar = Snackbar.make(snackbarText, mUndoDeletionSnackbarController,
                Snackbar.TYPE_ACTION, Snackbar.UMA_DOWNLOAD_DELETE_UNDO);
        snackbar.setAction(mActivity.getString(R.string.undo), itemsToDelete);
        snackbar.setTemplateText(mActivity.getString(snackbarTemplateId));

        ((SnackbarManageable) mActivity).getSnackbarManager().showSnackbar(snackbar);
    }

    private List<DownloadHistoryItemWrapper> getItemsForDeletion() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        List<DownloadHistoryItemWrapper> itemsToRemove = new ArrayList<>();
        Set<String> filePathsToRemove = new HashSet<>();

        for (DownloadHistoryItemWrapper item : selectedItems) {
            if (!filePathsToRemove.contains(item.getFilePath())) {
                List<DownloadHistoryItemWrapper> itemsForFilePath =
                        mHistoryAdapter.getItemsForFilePath(item.getFilePath());
                if (itemsForFilePath != null) {
                    itemsToRemove.addAll(itemsForFilePath);
                }
                filePathsToRemove.add(item.getFilePath());
            }
        }

        return itemsToRemove;
    }

    private void addDrawerListener(DrawerLayout drawer) {
        drawer.addDrawerListener(new DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                RecordUserAction.record("Android.DownloadManager.OpenDrawer");
            }

            @Override
            public void onDrawerClosed(View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });
    }

    private void dismissUndoDeletionSnackbars() {
        ((SnackbarManageable) mActivity).getSnackbarManager().dismissSnackbars(
                mUndoDeletionSnackbarController);
    }

    @VisibleForTesting
    public SnackbarManager getSnackbarManagerForTesting() {
        return ((SnackbarManageable) mActivity).getSnackbarManager();
    }

    /** Returns the {@link DownloadManagerToolbar}. */
    @VisibleForTesting
    public DownloadManagerToolbar getDownloadManagerToolbarForTests() {
        return mToolbar;
    }

    /** Returns the {@link DownloadHistoryAdapter}. */
    @VisibleForTesting
    public DownloadHistoryAdapter getDownloadHistoryAdapterForTests() {
        return mHistoryAdapter;
    }

    /** Returns the {@link SpaceDisplay}. */
    public SpaceDisplay getSpaceDisplayForTests() {
        return mSpaceDisplay;
    }

    /** Sets a BackendProvider that is used in place of a real one. */
    @VisibleForTesting
    public static void setProviderForTests(BackendProvider provider) {
        sProviderForTests = provider;
    }
}
