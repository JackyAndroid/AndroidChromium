// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import static org.chromium.chrome.browser.ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeBrowserProvider.BookmarkNode;
import org.chromium.chrome.browser.ChromeBrowserProvider.Type;
import org.chromium.chrome.browser.ChromeBrowserProviderClient;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.ui.base.LocalizationUtils;

/**
 * The user interface for selecting a bookmark folder.
 */
public class SelectBookmarkFolderFragment extends AsyncTaskFragment implements OnClickListener {
    /**
     * Defines the constants used as arguments to this fragment.
     * @see android.app.Fragment#setArguments(Bundle)
     */
    private static class Arguments {
        private Arguments() {}

        public static final String ALLOW_FOLDER_ADDITION = "allowAdd";
        public static final String FOLDER_ID_TO_SELECT = "selectedFolder";
        public static final String IS_FOLDER = "isFolder";
    }

    private Button mNewFolderButton;

    private ListView mFoldersList;
    private FolderListAdapter mFoldersAdapter;
    private TextView mEmptyFoldersView;

    private boolean mAllowFolderAddition;
    private long mFolderIdToSelect;
    // Used to determine if a bookmark's or a folder's parent is being changed.
    private boolean mIsFolder;

    private OnActionListener mActionListener;

    /**
     * The maximum depth that will be indented.  Folders with a depth greater than this will
     * all appear at this same depth.
     */
    private int mMaximumFolderIndentDepth = 8;

    /**
     * Constructs a new fragment in charge of handling bookmark folder selection.
     * @param allowFolderAddition Whether this fragment should allow additional folders to be added
     *         as children.
     * @param folderIdToSelect The ID of the folder to select when shown initially.
     * @return The selection fragment.
     */
    public static SelectBookmarkFolderFragment newInstance(
            boolean allowFolderAddition, long folderIdToSelect, boolean isFolder) {
        SelectBookmarkFolderFragment fragment = new SelectBookmarkFolderFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(Arguments.ALLOW_FOLDER_ADDITION, allowFolderAddition);
        arguments.putLong(Arguments.FOLDER_ID_TO_SELECT, folderIdToSelect);
        arguments.putBoolean(Arguments.IS_FOLDER, isFolder);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Retrieves the current action listener for this fragment.
     * Used by testing to intercept calls as a proxy.
     */
    @VisibleForTesting
    public OnActionListener getOnActionListenerForTest() {
        return mActionListener;
    }

    /**
     * Sets the action listener for this fragment.
     * @param listener The listener to be set.
     */
    public void setOnActionListener(OnActionListener listener) {
        mActionListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAllowFolderAddition = getArguments().getBoolean(Arguments.ALLOW_FOLDER_ADDITION);
        mFolderIdToSelect = getArguments().getLong(
                Arguments.FOLDER_ID_TO_SELECT, INVALID_BOOKMARK_ID);
        mIsFolder = getArguments().getBoolean(Arguments.IS_FOLDER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.select_bookmark_folder, container, false);

        mNewFolderButton = (Button) contentView.findViewById(R.id.new_folder_btn);
        if (mAllowFolderAddition) {
            mNewFolderButton.setOnClickListener(this);
        } else {
            mNewFolderButton.setVisibility(View.GONE);
        }

        mFoldersList = (ListView) contentView.findViewById(R.id.bookmark_folder_list);
        mEmptyFoldersView = (TextView) contentView.findViewById(R.id.empty_folders);
        mFoldersList.setEmptyView(mEmptyFoldersView);

        if (mFoldersAdapter != null) mFoldersList.setAdapter(mFoldersAdapter);
        return contentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mFoldersAdapter == null) {
            mFoldersAdapter = new FolderListAdapter(getActivity().getApplicationContext());
            mFoldersList.setAdapter(mFoldersAdapter);

            long selectedFolder = INVALID_BOOKMARK_ID;
            if (savedInstanceState == null) {
                // Only use the folder ID passed in from the intent if the activity is not being
                // restored.  During restoration, the ListView will handle reselecting the
                // previously selected entity and we do not want to override that with the initial
                // value.
                selectedFolder = mFolderIdToSelect;
            }

            loadAllFolders(selectedFolder);
        } else {
            if (!areFoldersLoaded()) {
                loadAllFolders(mFolderIdToSelect);
            }
        }

        mMaximumFolderIndentDepth =
                getResources().getInteger(R.integer.select_bookmark_folder_max_depth_indent);
    }

    @Override
    public void onClick(View v) {
        if (mActionListener == null) {
            Log.d(getClass().getName(), "No OnResultListener specified -- onClick == NoOp");
            return;
        }
        if (v == mNewFolderButton) {
            long parentId = INVALID_BOOKMARK_ID;
            String parentName = null;
            for (int i = 0; i < mFoldersAdapter.getCount(); i++) {
                FolderListEntry selectedFolder = mFoldersAdapter.getItem(i);
                if (selectedFolder.mFolder.id() == mFolderIdToSelect) {
                    parentId = selectedFolder.mFolder.id();
                    parentName = selectedFolder.mFolder.name();
                }
            }
            mActionListener.triggerNewFolderCreation(parentId, parentName);
        }
    }

    /**
     * @return Whether or not the bookmark folders have been loaded asynchronously yet.
     */
    @VisibleForTesting
    public boolean areFoldersLoaded() {
        return mFoldersAdapter.getCount() > 0;
    }

    private void loadAllFolders(long folderId) {
        if (isFragmentAsyncTaskRunning()) return;
        runFragmentAsyncTask(new LoadAllFoldersTask(folderId),
                getActivity().getString(R.string.loading_bookmark));
    }

    private void handleLoadAllFolders(BookmarkNode result, long selectedFolderId,
            boolean syncEnabled) {
        if (getActivity() == null || getActivity().isFinishing()) return;

        mFoldersAdapter.clear();
        if (result == null) {
            mEmptyFoldersView.setText(R.string.bookmark_folder_tree_error);
        } else {
            mEmptyFoldersView.setText(R.string.no_bookmark_folders);

            // The root node is just a placeholder, so directly add it's children.
            for (BookmarkNode child : result.children()) {
                if (!syncEnabled) {
                    Type type = child.type();
                    if (type == Type.BOOKMARK_BAR || type == Type.OTHER_NODE) {
                        continue;
                    }
                }
                addFolderItem(child, 0, selectedFolderId);
            }
        }
    }

    private void addFolderItem(BookmarkNode folder, int depth, long selectedFolderId) {
        boolean isSelectedFolder = (folder.id() == selectedFolderId);
        mFoldersAdapter.add(new FolderListEntry(folder, depth, isSelectedFolder));
        // Hiding sub folders will prevent current folder to be moved under a sub folder.
        if (folder.id() != selectedFolderId || !mIsFolder) {
            for (BookmarkNode child : folder.children()) {
                addFolderItem(child, depth + 1, selectedFolderId);
            }
        }
    }

    /**
     * Data object used in the list adapter.
     */
    private static class FolderListEntry {
        final BookmarkNode mFolder;
        final int mDepth;
        final boolean mIsSelectedFolder;

        FolderListEntry(BookmarkNode folder, int depth, boolean isSelectedFolder) {
            mFolder = folder;
            mDepth = depth;
            mIsSelectedFolder = isSelectedFolder;
        }

        @Override
        public String toString() {
            return mFolder.name();
        }
    }

    /**
     * List adapter for the folder selection view.
     */
    private class FolderListAdapter extends ArrayAdapter<FolderListEntry> {
        private final int mDefaultPaddingLeft;
        private final int mPaddingLeftInc;

        public FolderListAdapter(Context context) {
            super(context, R.layout.select_bookmark_folder_item);

            Resources resources = context.getResources();
            mDefaultPaddingLeft =
                    resources.getDimensionPixelSize(R.dimen.select_bookmark_folder_item_left);
            mPaddingLeftInc =
                    resources.getDimensionPixelSize(R.dimen.select_bookmark_folder_item_inc_left);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            final FolderListEntry entry = getItem(position);

            BitmapDrawable icon = TintedDrawable.constructTintedDrawable(
                    getResources(), R.drawable.eb_folder);
            ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    view, icon, null, null, null);

            // TODO: For folders that exceed the maximum depth, come up with a UI treatment to
            //       give some indication of that.
            int paddingLeft = mDefaultPaddingLeft
                    + Math.min(entry.mDepth, mMaximumFolderIndentDepth) * mPaddingLeftInc;
            if (LocalizationUtils.isLayoutRtl()) {
                view.setPadding(0, 0, paddingLeft, 0);
            } else {
                view.setPadding(paddingLeft, 0, 0, 0);
            }
            view.setTypeface(null, entry.mIsSelectedFolder ? Typeface.BOLD : Typeface.NORMAL);
            view.setBackgroundResource(R.drawable.btn_bg_holo);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    executeFolderSelection(entry.mFolder.id(), entry.mFolder.name());
                }
            });
            return view;
        }

        @Override
        public long getItemId(int position) {
            return getCount() > 0 ? getItem(position).mFolder.id() : -1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }

    /**
     * Select the folder to be used for the Add/Edit Fragment.
     * @param folderId Id of the selected folder.
     * @param folderName Name of the selected folder.
     */
    public void executeFolderSelection(long folderId, String folderName) {
        getFragmentManager().popBackStackImmediate();
        ((AddEditBookmarkFragment) getTargetFragment()).setParentFolderInfo(
                folderId, folderName);
    }

    /**
     * Asynchronously retrieves all the bookmark folders that the user can edit,
     * showing a progress dialog if the task takes too long.
     */
    private class LoadAllFoldersTask extends FragmentAsyncTask {
        private final Context mContext;
        private final long mFolderId;
        private BookmarkNode mResult;

        LoadAllFoldersTask(long folderId) {
            mContext = getActivity().getApplicationContext();
            mFolderId = folderId;
        }

        @Override
        protected void runBackgroundTask() {
            mResult = ChromeBrowserProviderClient.getEditableBookmarkFolderHierarchy(mContext);
        }

        @Override
        protected void onTaskFinished() {
            handleLoadAllFolders(mResult, mFolderId, AndroidSyncSettings.isSyncEnabled(mContext));
        }

        @Override
        protected void setDependentUIEnabled(boolean enabled) {
            mNewFolderButton.setEnabled(enabled);
        }
    }

    /**
     * Listener to handle actions triggered by this fragment.
     */
    public static interface OnActionListener {
        /**
         * Triggered when the user asks to create a new subfolder.
         * @param selectedFolderId The currently selected folder ID, which should be used as the
         *         default parent of the newly added folder.
         * @param selectedFolderName The currently selected folder name.
         */
        public void triggerNewFolderCreation(long selectedFolderId, String selectedFolderName);
    }
}
