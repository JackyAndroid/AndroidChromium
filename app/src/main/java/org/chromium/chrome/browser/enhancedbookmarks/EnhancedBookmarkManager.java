// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewSwitcher;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.components.bookmarks.BookmarkId;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * The new bookmark manager that is planned to replace the existing bookmark manager. It holds all
 * views and shared logics between tablet and phone. For tablet/phone specific logics, see
 * {@link EnhancedBookmarkActivity} (phone) and {@link EnhancedBookmarkPage} (tablet).
 */
public class EnhancedBookmarkManager implements EnhancedBookmarkDelegate {
    private static final String PREF_LAST_USED_URL = "enhanced_bookmark_last_used_url";
    private static final int FAVICON_MAX_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private Activity mActivity;
    private ViewGroup mMainView;
    private EnhancedBookmarksModel mEnhancedBookmarksModel;
    private EnhancedBookmarkUndoController mUndoController;
    private final ObserverList<EnhancedBookmarkUIObserver> mUIObservers =
            new ObserverList<EnhancedBookmarkUIObserver>();
    private Set<BookmarkId> mSelectedBookmarks = new HashSet<>();
    private EnhancedBookmarkStateChangeListener mUrlChangeListener;
    private EnhancedBookmarkContentView mContentView;
    private EnhancedBookmarkSearchView mSearchView;
    private ViewSwitcher mViewSwitcher;
    private DrawerLayout mDrawer;
    private EnhancedBookmarkDrawerListView mDrawerListView;
    private final Stack<UIState> mStateStack = new Stack<>();
    private LargeIconBridge mLargeIconBridge;

    private final BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {

        @Override
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            // If the folder is removed in folder mode, show the parent folder or falls back to all
            // bookmarks mode.
            if (getCurrentState() == UIState.STATE_FOLDER
                    && node.getId().equals(mStateStack.peek().mFolder)) {
                if (mEnhancedBookmarksModel.getTopLevelFolderIDs(true, true).contains(
                        node.getId())) {
                    openAllBookmarks();
                } else {
                    openFolder(parent.getId());
                }
            }
            clearSelection();
        }

        @Override
        public void bookmarkNodeMoved(BookmarkItem oldParent, int oldIndex, BookmarkItem newParent,
                int newIndex) {
            clearSelection();
        }

        @Override
        public void bookmarkModelLoaded() {
            initializeIfBookmarkModelLoaded();
        }

        @Override
        public void bookmarkModelChanged() {
            // If the folder no longer exists in folder mode, we need to fall back. Relying on the
            // default behavior by setting the folder mode again.
            if (getCurrentState() == UIState.STATE_FOLDER) {
                setState(mStateStack.peek());
            }
            clearSelection();
        }
    };

    /**
     * Creates an instance of {@link EnhancedBookmarkManager}. It also initializes resources,
     * bookmark models and jni bridges.
     * @param activity The activity context to use.
     */
    public EnhancedBookmarkManager(Activity activity) {
        mActivity = activity;
        mEnhancedBookmarksModel = new EnhancedBookmarksModel();
        mMainView = (ViewGroup) mActivity.getLayoutInflater().inflate(R.layout.eb_main, null);
        mDrawer = (DrawerLayout) mMainView.findViewById(R.id.eb_drawer_layout);
        mDrawerListView = (EnhancedBookmarkDrawerListView) mMainView.findViewById(
                R.id.eb_drawer_list);
        mContentView = (EnhancedBookmarkContentView) mMainView.findViewById(R.id.eb_content_view);
        mViewSwitcher = (ViewSwitcher) mMainView.findViewById(R.id.eb_view_switcher);
        mUndoController = new EnhancedBookmarkUndoController(activity, mEnhancedBookmarksModel,
                ((SnackbarManageable) activity).getSnackbarManager());
        mSearchView = (EnhancedBookmarkSearchView) getView().findViewById(R.id.eb_search_view);
        mEnhancedBookmarksModel.addObserver(mBookmarkModelObserver);
        initializeIfBookmarkModelLoaded();

        // Load partner bookmarks explicitly. We load partner bookmarks in the deferred startup
        // code, but that might be executed much later. Especially on L, showing loading
        // progress bar blocks that so it won't be loaded. http://crbug.com/429383
        PartnerBookmarksShim.kickOffReading(activity);

        mLargeIconBridge = new LargeIconBridge(Profile.getLastUsedProfile().getOriginalProfile());
        ActivityManager activityManager = ((ActivityManager) ApplicationStatus
                .getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE));
        int maxSize = Math.min(activityManager.getMemoryClass() / 4 * 1024 * 1024,
                FAVICON_MAX_CACHE_SIZE_BYTES);
        mLargeIconBridge.createCache(maxSize);
    }

    /**
     * Destroys and cleans up itself. This must be called after done using this class.
     */
    public void destroy() {
        for (EnhancedBookmarkUIObserver observer : mUIObservers) {
            observer.onDestroy();
        }
        assert mUIObservers.size() == 0;

        if (mUndoController != null) {
            mUndoController.destroy();
            mUndoController = null;
        }
        mEnhancedBookmarksModel.removeObserver(mBookmarkModelObserver);
        mEnhancedBookmarksModel.destroy();
        mEnhancedBookmarksModel = null;
        mLargeIconBridge.destroy();
        mLargeIconBridge = null;
    }

    /**
     * Called when the user presses the back key. This is only going to be called on Phone.
     * @return True if manager handles this event, false if it decides to ignore.
     */
    public boolean onBackPressed() {
        if (doesDrawerExist()) {
            if (mDrawer.isDrawerVisible(Gravity.START)) {
                mDrawer.closeDrawer(Gravity.START);
                return true;
            }
        }

        if (mContentView.onBackPressed()) return true;

        if (!mStateStack.empty()) {
            mStateStack.pop();
            if (!mStateStack.empty()) {
                setState(mStateStack.pop());
                return true;
            }
        }
        return false;
    }

    public View getView() {
        return mMainView;
    }

    /**
     * Sets the listener that reacts upon the change of the UI state of bookmark manager.
     */
    public void setUrlChangeListener(EnhancedBookmarkStateChangeListener urlListner) {
        mUrlChangeListener = urlListner;
    }

    /**
     * @return Current URL representing the UI state of bookmark manager. If no state has been shown
     *         yet in this session, on phone return last used state stored in preference; on tablet
     *         return the url previously set by {@link #updateForUrl(String)}.
     */
    public String getCurrentUrl() {
        if (mStateStack.isEmpty()) return null;
        return mStateStack.peek().mUrl;
    }

    /**
     * Updates UI based on the new URL on tablet. If the bookmark model is not loaded yet, creates a
     * temporary loading state carrying this url. This method is supposed to align with
     * {@link EnhancedBookmarkPage#updateForUrl(String)}
     * <p>
     * @param url The url to navigate to.
     */
    public void updateForUrl(String url) {
        if (mEnhancedBookmarksModel != null && mEnhancedBookmarksModel.isBookmarkModelLoaded()) {
            setState(UIState.createStateFromUrl(url, mEnhancedBookmarksModel));
        } else {
            // Note this does not guarantee to update the UI, as at this time the onCreateView()
            // might not has even been called yet.
            setState(UIState.createLoadingState(url));
        }
    }

    /**
     * Initialization method that has 3 different behaviors based on whether bookmark model is
     * loaded. If the bookmark model is not loaded yet, it pushes a loading state to backstack which
     * contains the url from preference. If the model is loaded and the backstack is empty, it
     * creates a state by fetching the last visited bookmark url stored in preference. If the
     * bookmark model is loaded but backstack contains a pending loading state, it creates a new
     * state by getting the url of the loading state and replace the previous loading state with the
     * new normal state.
     */
    private void initializeIfBookmarkModelLoaded() {
        if (mEnhancedBookmarksModel.isBookmarkModelLoaded()) {
            mSearchView.onEnhancedBookmarkDelegateInitialized(this);
            mDrawerListView.onEnhancedBookmarkDelegateInitialized(this);
            mContentView.onEnhancedBookmarkDelegateInitialized(this);
            if (mStateStack.isEmpty()) {
                setState(UIState.createStateFromUrl(getUrlFromPreference(),
                        mEnhancedBookmarksModel));
            } else if (mStateStack.peek().mState == UIState.STATE_LOADING) {
                String url = mStateStack.pop().mUrl;
                setState(UIState.createStateFromUrl(url, mEnhancedBookmarksModel));
            }
        } else {
            mContentView.showLoadingUi();
            mDrawerListView.showLoadingUi();
            mContentView.showLoadingUi();
            if (mStateStack.isEmpty() || mStateStack.peek().mState != UIState.STATE_LOADING) {
                setState(UIState.createLoadingState(getUrlFromPreference()));
            } else if (!mStateStack.isEmpty()) {
                // Refresh the UI. This is needed because on tablet, updateForUrl might set up
                // loading state too early and at that time all UI components are not created yet.
                // Therefore we need to set the previous loading state once again to trigger all UI
                // updates.
                setState(mStateStack.pop());
            }
        }
    }

    /**
     * Saves url to preference. Note this method should be used after the main view is attached to
     * an activity.
     */
    private void saveUrlToPreference(String url) {
        PreferenceManager.getDefaultSharedPreferences(mActivity).edit()
                .putString(PREF_LAST_USED_URL, url).apply();
    }

    /**
     * Fetches url to preference. Note this method should be used after the main view is attached to
     * an activity.
     */
    private String getUrlFromPreference() {
        return PreferenceManager.getDefaultSharedPreferences(mActivity).getString(
                PREF_LAST_USED_URL, UrlConstants.BOOKMARKS_URL);
    }

    /**
     * This is the ultimate internal method that updates UI and controls backstack. And it is the
     * only method that pushes states to {@link #mStateStack}.
     * <p>
     * If the given state is not valid, all_bookmark state will be shown. Afterwards, this method
     * checks the current state: if currently in loading state, it pops it out and adds the new
     * state to the back stack. It also notifies the {@link #mUrlChangeListener} (if any) that the
     * url has changed.
     * <p>
     * Also note that even if we store states to {@link #mStateStack}, on tablet the back navigation
     * and back button are not controlled by the manager: the tab handles back key and backstack
     * navigation.
     */
    private void setState(UIState state) {
        if (!state.isValid(mEnhancedBookmarksModel)) {
            state = UIState.createAllBookmarksState(mEnhancedBookmarksModel);
        }
        boolean saveUrl = true;
        if (mStateStack.isEmpty()) {
            // When offline page feature is enabled, show offline filter view if there is offline
            // page and there is no network connection.
            if (mEnhancedBookmarksModel.getOfflinePageBridge() != null
                    && !mEnhancedBookmarksModel.getOfflinePageBridge().getAllPages().isEmpty()
                    && !OfflinePageUtils.isConnected(ApplicationStatus.getApplicationContext())) {
                UIState filterState = UIState.createFilterState(
                        EnhancedBookmarkFilter.OFFLINE_PAGES, mEnhancedBookmarksModel);
                if (state.mState != UIState.STATE_LOADING) {
                    state = filterState;
                } else {
                    state.mUrl = filterState.mUrl;
                }
                // Showing offline filter view is just a temporary thing and it will not be saved
                // to the preference.
                saveUrl = false;
            }
        } else {
            if (mStateStack.peek().equals(state)) return;
            if (mStateStack.peek().mState == UIState.STATE_LOADING) {
                mStateStack.pop();
            }
        }
        mStateStack.push(state);
        if (state.mState != UIState.STATE_LOADING) {
            // Loading state may be pushed to the stack but should never be stored in preferences.
            if (saveUrl) saveUrlToPreference(state.mUrl);
            // If a loading state is replaced by another loading state, do not notify this change.
            if (mUrlChangeListener != null) mUrlChangeListener.onBookmarkUIStateChange(state.mUrl);
        }

        clearSelection();

        for (EnhancedBookmarkUIObserver observer : mUIObservers) {
            notifyStateChange(observer);
        }
    }

    // EnhancedBookmarkDelegate implementations.

    @Override
    public void openFolder(BookmarkId folder) {
        closeSearchUI();
        setState(UIState.createFolderState(folder, mEnhancedBookmarksModel));
    }

    @Override
    public void openAllBookmarks() {
        closeSearchUI();
        setState(UIState.createAllBookmarksState(mEnhancedBookmarksModel));
    }

    @Override
    public void openFilter(EnhancedBookmarkFilter filter) {
        setState(UIState.createFilterState(filter, mEnhancedBookmarksModel));
    }

    @Override
    public void clearSelection() {
        mSelectedBookmarks.clear();
        for (EnhancedBookmarkUIObserver observer : mUIObservers) {
            observer.onSelectionStateChange(new ArrayList<BookmarkId>(mSelectedBookmarks));
        }
    }

    @Override
    public boolean toggleSelectionForBookmark(BookmarkId bookmark) {
        if (!mEnhancedBookmarksModel.getBookmarkById(bookmark).isEditable()) return false;

        if (mSelectedBookmarks.contains(bookmark)) mSelectedBookmarks.remove(bookmark);
        else mSelectedBookmarks.add(bookmark);
        for (EnhancedBookmarkUIObserver observer : mUIObservers) {
            observer.onSelectionStateChange(new ArrayList<BookmarkId>(mSelectedBookmarks));
        }

        return isBookmarkSelected(bookmark);
    }

    @Override
    public boolean isBookmarkSelected(BookmarkId bookmark) {
        return mSelectedBookmarks.contains(bookmark);
    }

    @Override
    public boolean isSelectionEnabled() {
        return !mSelectedBookmarks.isEmpty();
    }

    @Override
    public List<BookmarkId> getSelectedBookmarks() {
        return new ArrayList<BookmarkId>(mSelectedBookmarks);
    }

    @Override
    public void notifyStateChange(EnhancedBookmarkUIObserver observer) {
        int state = getCurrentState();
        switch (state) {
            case UIState.STATE_ALL_BOOKMARKS:
                observer.onAllBookmarksStateSet();
                break;
            case UIState.STATE_FOLDER:
                observer.onFolderStateSet(mStateStack.peek().mFolder);
                break;
            case UIState.STATE_LOADING:
                // In loading state, onEnhancedBookmarkDelegateInitialized() is not called for all
                // UIObservers, which means that there will be no observers at the time. Do nothing.
                assert mUIObservers.isEmpty();
                break;
            case UIState.STATE_FILTER:
                observer.onFilterStateSet(mStateStack.peek().mFilter);
                break;
            default:
                assert false : "State not valid";
                break;
        }
    }

    @Override
    public boolean doesDrawerExist() {
        return mDrawer != null;
    }

    @Override
    public void closeDrawer() {
        if (!doesDrawerExist()) return;

        mDrawer.closeDrawer(Gravity.START);
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return mDrawer;
    }

    @Override
    public void openBookmark(BookmarkId bookmark, int launchLocation) {
        clearSelection();
        if (mEnhancedBookmarksModel.getBookmarkById(bookmark) != null) {
            String url = mEnhancedBookmarksModel.getLaunchUrlAndMarkAccessed(mActivity, bookmark);
            // TODO(jianli): Notify the user about the failure.
            if (TextUtils.isEmpty(url)) return;

            NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_BOOKMARK);
            RecordHistogram.recordEnumeratedHistogram("Stars.LaunchLocation", launchLocation,
                    LaunchLocation.COUNT);
            EnhancedBookmarkUtils.openBookmark(mActivity, url);
            EnhancedBookmarkUtils.finishActivityOnPhone(mActivity);
        }
    }

    @Override
    public void openSearchUI() {
        // Give search view focus, because it needs to handle back key event.
        mViewSwitcher.showNext();
    }

    @Override
    public void closeSearchUI() {
        if (mSearchView.getVisibility() != View.VISIBLE) return;
        mViewSwitcher.showPrevious();
    }

    @Override
    public void addUIObserver(EnhancedBookmarkUIObserver observer) {
        mUIObservers.addObserver(observer);
    }

    @Override
    public void removeUIObserver(EnhancedBookmarkUIObserver observer) {
        mUIObservers.removeObserver(observer);
    }

    @Override
    public EnhancedBookmarksModel getModel() {
        return mEnhancedBookmarksModel;
    }

    @Override
    public int getCurrentState() {
        if (mStateStack.isEmpty()) return UIState.STATE_LOADING;
        return mStateStack.peek().mState;
    }

    @Override
    public LargeIconBridge getLargeIconBridge() {
        return mLargeIconBridge;
    }

    @Override
    public SnackbarManager getSnackbarManager() {
        return ((SnackbarManageable) mActivity).getSnackbarManager();
    }

    /**
     * Internal state that represents a url. Note every state needs to have a _valid_ url. For
     * loading state, {@link #mUrl} indicates the target to open after the bookmark model is loaded.
     */
    static class UIState {
        private static final int STATE_INVALID = 0;
        static final int STATE_LOADING = 1;
        static final int STATE_ALL_BOOKMARKS = 2;
        static final int STATE_FOLDER = 3;
        static final int STATE_FILTER = 4;

        private static final String TAG = "UIState";
        private static final String URL_CHARSET = "UTF-8";
        /**
         * One of the STATE_* constants.
         */
        private int mState;
        private String mUrl;
        private BookmarkId mFolder;
        private EnhancedBookmarkFilter mFilter;

        private static UIState createLoadingState(String url) {
            UIState state = new UIState();
            state.mUrl = url;
            state.mState = STATE_LOADING;
            return state;
        }

        private static UIState createAllBookmarksState(EnhancedBookmarksModel bookmarkModel) {
            return createStateFromUrl(UrlConstants.BOOKMARKS_URL, bookmarkModel);
        }

        private static UIState createFolderState(BookmarkId folder,
                EnhancedBookmarksModel bookmarkModel) {
            return createStateFromUrl(UrlConstants.BOOKMARKS_FOLDER_URL + folder.toString(),
                    bookmarkModel);
        }

        private static UIState createFilterState(
                EnhancedBookmarkFilter filter, EnhancedBookmarksModel bookmarkModel) {
            return createStateFromUrl(
                    UrlConstants.BOOKMARKS_FILTER_URL + filter.toString(), bookmarkModel);
        }

        /**
         * @return A state corresponding to the url. If the url is not valid, return all_bookmarks.
         */
        private static UIState createStateFromUrl(String url,
                EnhancedBookmarksModel bookmarkModel) {
            UIState state = new UIState();
            state.mState = STATE_INVALID;
            state.mUrl = url;

            if (url.equals(UrlConstants.BOOKMARKS_URL)) {
                state.mState = STATE_ALL_BOOKMARKS;
            } else if (url.startsWith(UrlConstants.BOOKMARKS_FOLDER_URL)) {
                String suffix = decodeSuffix(url, UrlConstants.BOOKMARKS_FOLDER_URL);
                if (!suffix.isEmpty()) {
                    state.mFolder = BookmarkId.getBookmarkIdFromString(suffix);
                    state.mState = STATE_FOLDER;
                }
            } else if (url.startsWith(UrlConstants.BOOKMARKS_FILTER_URL)) {
                String suffix = decodeSuffix(url, UrlConstants.BOOKMARKS_FILTER_URL);
                if (!suffix.isEmpty()) {
                    state.mState = STATE_FILTER;
                    state.mFilter = EnhancedBookmarkFilter.valueOf(suffix);
                }
            }

            if (!state.isValid(bookmarkModel)) {
                state.mState = STATE_ALL_BOOKMARKS;
                state.mUrl = UrlConstants.BOOKMARKS_URL;
            }

            return state;
        }

        private UIState() {
        }

        /**
         * @return Whether this state is valid.
         */
        private boolean isValid(EnhancedBookmarksModel bookmarkModel) {
            if (mUrl == null || mState == STATE_INVALID) return false;

            if (mState == STATE_FOLDER) {
                if (mFolder == null) return false;

                return bookmarkModel.doesBookmarkExist(mFolder)
                        && !mFolder.equals(bookmarkModel.getRootFolderId());
            }

            return true;
        }

        private static String decodeSuffix(String url, String prefix) {
            String suffix = url.substring(prefix.length());
            try {
                suffix = URLDecoder.decode(suffix, URL_CHARSET);
            } catch (UnsupportedEncodingException e) {
                Log.w(TAG, "Bookmark URL parsing failed. " + URL_CHARSET + " not supported.");
            }
            return suffix;
        }

        private static String encodeUrl(String prefix, String suffix) {
            try {
                suffix = URLEncoder.encode(suffix, URL_CHARSET);
            } catch (UnsupportedEncodingException e) {
                Log.w(TAG, "Bookmark URL parsing failed. " + URL_CHARSET + " not supported.");
            }
            return prefix + suffix;
        }

        @Override
        public int hashCode() {
            return 31 * mUrl.hashCode() + mState;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UIState)) return false;
            UIState other = (UIState) obj;
            return mState == other.mState && mUrl.equals(other.mUrl);
        }
    }
}
