// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import static org.chromium.chrome.browser.ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeBrowserProvider;
import org.chromium.chrome.browser.ChromeBrowserProvider.BookmarkNode;
import org.chromium.chrome.browser.ChromeBrowserProviderClient;

import javax.annotation.Nullable;

/**
 * UI container that manages adding or editing bookmark nodes.
 */
public class AddEditBookmarkFragment extends AsyncTaskFragment implements OnClickListener {
    private static final String TAG = "AddEditBookmarkFragment";

    /**
     * Defines the constants used as arguments to this fragment.
     * @see android.app.Fragment#setArguments(Bundle)
     */
    private static class Arguments {
        private Arguments() {}

        public static final String MODE = "mode";
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String URL = "url";
        public static final String PARENT_FOLDER_ID = "parentId";
        public static final String PARENT_FOLDER_NAME = "parentName";
    }

    /**
     * Determines the operating mode/UI of this fragment.
     */
    private enum Mode {
        ADD_BOOKMARK,
        EDIT_BOOKMARK,
        ADD_FOLDER(true),
        EDIT_FOLDER(true);

        private final boolean mIsFolder;

        private Mode() {
            mIsFolder = false;
        }

        private Mode(boolean isFolder) {
            mIsFolder = isFolder;
        }

        /** @return Whether this mode is operating on a folder (creation or editing). */
        protected boolean isFolder() {
            return mIsFolder;
        }
    }

    private Button mFolderInput;
    private Button mRemoveButton;
    private Button mOkButton;
    private Button mCancelButton;

    private EditText mTitleInput;
    private EditText mUrlInput;

    /** The initial name of a bookmark (will only be used if adding a new node). */
    private String mInitialName;

    /** The initial URL of a bookmark (will only be used if adding a new non-folder node). */
    private String mInitialUrl;

    private long mParentFolderId = INVALID_BOOKMARK_ID;

    /** The name of the parent folder to this bookmark. */
    private String mParentFolderName;

    /** The mode of the current activity to distinguish between adding vs editing. */
    private Mode mActivityMode = Mode.ADD_BOOKMARK;

    /**
     * The ID of the current bookmark being edited (if adding a new bookmark then this will
     * be null).
     */
    private Long mBookmarkId;

    /** The listener that will be notified when the actions of this fragment are completed. */
    private OnActionListener mActionListener;

    /** Lock for synchronizing calls to the loaded status checks. */
    private final Object mLoadedLock = new Object();
    private boolean mBookmarkNodeLoaded = false;
    private boolean mDefaultFolderLoaded = false;

    /**
     * Creates a new add bookmark folder fragment.
     *
     * @param parentFolderId The ID of the default parent folder for the new folder.
     * @param parentFolderName The name of the default parent folder for the new folder.
     * @return An initialized add new bookmark folder fragment.
     */
    public static AddEditBookmarkFragment newAddNewFolderInstance(
            long parentFolderId, String parentFolderName) {
        AddEditBookmarkFragment fragment = new AddEditBookmarkFragment();
        Bundle arguments = new Bundle();
        arguments.putSerializable(Arguments.MODE, Mode.ADD_FOLDER);
        arguments.putLong(Arguments.PARENT_FOLDER_ID, parentFolderId);
        arguments.putString(Arguments.PARENT_FOLDER_NAME, parentFolderName);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Creates a new editing bookmark fragment where all the details of the bookmark node are to
     * be loaded from the model.
     *
     * @param isFolder Whether the node being edited is a folder.
     * @param bookmarkId The ID of the node being edited.
     * @return An initialized edit bookmark fragment.
     */
    public static AddEditBookmarkFragment newEditInstance(boolean isFolder, long bookmarkId) {
        AddEditBookmarkFragment fragment = new AddEditBookmarkFragment();
        Bundle arguments = new Bundle();
        arguments.putSerializable(Arguments.MODE, isFolder ? Mode.EDIT_FOLDER : Mode.EDIT_BOOKMARK);
        arguments.putLong(Arguments.ID, bookmarkId);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Creates a new add/edit bookmark fragment with the given details.
     *
     * @param isFolder Whether the node being edited is a folder.
     * @param bookmarkId The ID of the node being edited (or null if adding a new bookmark node).
     * @param name The name of the bookmark being added/edited.
     * @param url The url of the bookmark being added/edited (not applicable if isFolder is set)
     * @return An initialized add or edit bookmark fragment.
     */
    public static AddEditBookmarkFragment newInstance(
            boolean isFolder,
            @Nullable Long bookmarkId,
            @Nullable String name,
            @Nullable String url) {
        AddEditBookmarkFragment fragment = new AddEditBookmarkFragment();
        Bundle arguments = new Bundle();
        if (bookmarkId == null || bookmarkId == INVALID_BOOKMARK_ID) {
            arguments.putSerializable(
                    Arguments.MODE, isFolder ? Mode.ADD_FOLDER : Mode.ADD_BOOKMARK);
        } else {
            arguments.putSerializable(
                    Arguments.MODE, isFolder ? Mode.EDIT_FOLDER : Mode.EDIT_BOOKMARK);
            arguments.putLong(Arguments.ID, bookmarkId);
        }
        if (name != null) arguments.putString(Arguments.NAME, name);
        if (url != null) arguments.putString(Arguments.URL, url);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Sets the action listener for this fragment.
     * @param listener The listener to be set.
     */
    public void setOnActionListener(OnActionListener listener) {
        mActionListener = listener;
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
     * @return Whether the default bookmark folder has been loaded.
     */
    protected boolean isDefaultFolderLoaded() {
        synchronized (mLoadedLock) {
            return mDefaultFolderLoaded;
        }
    }

    /**
     * @return Whether the bookmark node has been loaded from the backend model.
     */
    protected boolean isBookmarkNodeLoaded() {
        synchronized (mLoadedLock) {
            return mBookmarkNodeLoaded;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mActivityMode = (Mode) args.get(Arguments.MODE);
        if (mActivityMode == null) {
            Log.e(getClass().getName(), "Created new AddEditBookmarkFragment without a mode "
                    + "defined.  Make sure arguments are correctly populated.");
            mActivityMode = Mode.ADD_BOOKMARK;
            return;
        }
        if (args.containsKey(Arguments.ID)) mBookmarkId = args.getLong(Arguments.ID);
        mParentFolderId = args.getLong(Arguments.PARENT_FOLDER_ID, INVALID_BOOKMARK_ID);
        mParentFolderName = args.getString(Arguments.PARENT_FOLDER_NAME, null);
        mInitialName = args.getString(Arguments.NAME, null);
        mInitialUrl = args.getString(Arguments.URL, null);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.add_bookmark, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRemoveButton = (Button) getView().findViewById(R.id.remove);
        mRemoveButton.setOnClickListener(this);
        mOkButton = (Button) getView().findViewById(R.id.ok);
        mOkButton.setOnClickListener(this);
        mCancelButton = (Button) getView().findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(this);
        mTitleInput = (EditText) getView().findViewById(R.id.bookmark_title_input);
        clearErrorWhenNonEmpty(mTitleInput);
        mUrlInput = (EditText) getView().findViewById(R.id.bookmark_url_input);
        clearErrorWhenNonEmpty(mUrlInput);
        mFolderInput = (Button) getView().findViewById(R.id.bookmark_folder_select);
        mFolderInput.setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        int actionTitleTextResId = R.string.edit_bookmark;
        int bookmarkInputDescResId = R.string.accessibility_bookmark_title_textbox;

        switch (mActivityMode) {
            case ADD_BOOKMARK:
                actionTitleTextResId = R.string.add_bookmark;
                bookmarkInputDescResId = R.string.accessibility_bookmark_title_textbox;
                break;
            case EDIT_BOOKMARK:
                actionTitleTextResId = R.string.edit_bookmark;
                bookmarkInputDescResId = R.string.accessibility_bookmark_title_textbox;
                break;
            case ADD_FOLDER:
                actionTitleTextResId = R.string.add_folder;
                bookmarkInputDescResId = R.string.accessibility_bookmark_folder_name_textbox;
                break;
            case EDIT_FOLDER:
                actionTitleTextResId = R.string.edit_folder;
                bookmarkInputDescResId = R.string.accessibility_bookmark_folder_name_textbox;
                break;
            default:
                assert false;
        }

        TextView actionTitle = (TextView) getView().findViewById(R.id.bookmark_action_title);
        EditText titleInput = (EditText) getView().findViewById(R.id.bookmark_title_input);
        actionTitle.setText(actionTitleTextResId);
        titleInput.setContentDescription(getResources().getString(bookmarkInputDescResId));

        if (mActivityMode == Mode.ADD_BOOKMARK || mActivityMode == Mode.ADD_FOLDER) {
            mRemoveButton.setVisibility(View.GONE);
        }
        if (mActivityMode.isFolder()) {
            hideUrlInputRow();
        }

        initializeFolderInputContent(savedInstanceState);
        initializeStateFromIntent(savedInstanceState);

        if (mActivityMode == Mode.ADD_FOLDER) {
            mTitleInput.post(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) return;

                    mTitleInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mTitleInput, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void hideUrlInputRow() {
        getView().findViewById(R.id.bookmark_url_label).setVisibility(View.GONE);
        getView().findViewById(R.id.bookmark_url_input).setVisibility(View.GONE);
    }

    private void initializeStateFromIntent(Bundle savedInstanceState) {
        // If restoring from a previous state, do not set the fields to their initial values
        // (allow the individual views to reset their values correctly).
        if (savedInstanceState != null) {
            return;
        }

        if (!TextUtils.isEmpty(mInitialName)) mTitleInput.setText(mInitialName);
        if (!TextUtils.isEmpty(mTitleInput.getText())) {
            mTitleInput.setSelection(mTitleInput.getText().length());
        }
        if (!TextUtils.isEmpty(mInitialUrl)) mUrlInput.setText(mInitialUrl);
    }

    private void initializeFolderInputContent(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            long savedParentId = savedInstanceState.getLong(
                    Arguments.PARENT_FOLDER_ID, INVALID_BOOKMARK_ID);
            if (savedParentId != INVALID_BOOKMARK_ID) {
                mParentFolderId = savedParentId;
                mFolderInput.setText(savedInstanceState.getString(Arguments.PARENT_FOLDER_NAME));
                return;
            }
        } else if (mParentFolderId != INVALID_BOOKMARK_ID && mParentFolderName != null) {
            mFolderInput.setText(mParentFolderName);
            return;
        }

        // Start a new asynchronous fragment task to read the bookmark folder data.
        if (!isFragmentAsyncTaskRunning() && !isDefaultFolderLoaded() && !isBookmarkNodeLoaded()) {
            runFragmentAsyncTask(new LoadBookmarkNodeTask(
                    (mActivityMode == Mode.ADD_BOOKMARK || mActivityMode == Mode.ADD_FOLDER)
                            ? null : mBookmarkId),
                    getActivity().getString(R.string.loading_bookmark));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!isVisible()) return;

        outState.putString(Arguments.PARENT_FOLDER_NAME, mFolderInput.getText().toString());
        outState.putLong(Arguments.PARENT_FOLDER_ID, mParentFolderId);
    }

    @Override
    public void onClick(View v) {
        if (mActionListener == null) {
            Log.d(getClass().getName(), "No OnResultListener specified -- onClick == NoOp");
            return;
        }
        String title = mTitleInput.getText().toString().trim();
        String url = mUrlInput.getText().toString().trim();
        if (v == mOkButton) {
            if (!checkValidInputs(title, url)) return;
            if (isFragmentAsyncTaskRunning()) {
                Log.e(TAG, "Pending asynchronous task when trying to add/update bookmarks.");
                return;
            }

            ContentValues bookmarkValues = new ContentValues();
            if (!mActivityMode.isFolder()) {
                bookmarkValues.put(BookmarkColumns.URL, url);
            }
            bookmarkValues.put(BookmarkColumns.TITLE, title);
            bookmarkValues.put(
                    ChromeBrowserProvider.BOOKMARK_PARENT_ID_PARAM, mParentFolderId);
            bookmarkValues.put(
                    ChromeBrowserProvider.BOOKMARK_IS_FOLDER_PARAM, mActivityMode.isFolder());

            runFragmentAsyncTask(new InsertUpdateBookmarkTask(
                    bookmarkValues,
                    (mActivityMode == Mode.ADD_BOOKMARK || mActivityMode == Mode.ADD_FOLDER)
                            ? null : mBookmarkId),
                    getActivity().getString(R.string.saving_bookmark));

        } else if (v == mRemoveButton) {
            if (mActivityMode == Mode.ADD_BOOKMARK) {
                throw new AssertionError(
                        "The remove functionality should be disabled for new bookmarks.");
            }

            if (isFragmentAsyncTaskRunning()) {
                Log.e(TAG, "Pending asynchronous task when trying to delete bookmarks.");
                return;
            }

            runFragmentAsyncTask(new DeleteBookmarkTask(mBookmarkId),
                    getActivity().getString(R.string.removing_bookmark));

        } else if (v == mCancelButton) {
            mActionListener.onCancel();
        } else if (v == mFolderInput) {
            mActionListener.triggerFolderSelection();
        }
    }

    /**
     * Adds a listener to |textView| that will clear the TextView's error once the user types
     * something.
     * Note that we needed this function in EnhancedBookmarkAddEditFolderDialog.java and just
     * duplicated there instead of sharing, because the old bookmark UI is supposed go away anyways.
     * If you have found this message in the latest repository in 2016, you can come by Kibeom's
     * desk and punch him.
     */
    private void clearErrorWhenNonEmpty(final TextView textView) {
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() != 0) {
                    textView.setError(null);
                }
            }
        });
    }

    private boolean checkValidInputs(String title, String url) {
        Resources resources = getResources();
        boolean validInput = true;
        if (!mActivityMode.isFolder() && url.isEmpty()) {
            mUrlInput.setError(resources.getText(R.string.bookmark_missing_url));
            mUrlInput.requestFocus();
            validInput = false;
        }
        if (title.isEmpty()) {
            mTitleInput.setError(resources.getText(R.string.bookmark_missing_title));
            mTitleInput.requestFocus();
            validInput = false;
        }
        return validInput;
    }

    @VisibleForTesting
    public boolean isLoadingBookmarks() {
        return mCurrentTask instanceof LoadBookmarkNodeTask;
    }

    /**
     * Sets the parent folder information for this bookmark node (updating the view if present).
     * @param id The ID of the parent folder.
     * @param name The name of the parent folder.
     */
    protected void setParentFolderInfo(long id, String name) {
        mParentFolderName = name;
        mParentFolderId = id;
        if (isVisible()) {
            mFolderInput.setText(name);
        }
    }

    /**
     * @return The ID of the parent node of this bookmark.
     */
    protected long getParentFolderId() {
        return mParentFolderId;
    }

    /**
     * @return boolean Whether a folder or bookmark is being created/edited.
     */
    protected boolean isFolder() {
        return mActivityMode.isFolder();
    }

    private void handleGetBookmarkNode(BookmarkNode result) {
        if (getActivity() == null || getActivity().isFinishing()) return;

        if (result == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setMessage(getActivity().getResources().getText(R.string.invalid_bookmark))
                    .setPositiveButton(getActivity().getResources().getText(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    getActivity().finish();
                                }
                            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    getActivity().finish();
                                }
                            });
            builder.create().show();
            return;
        }

        mTitleInput.setText(result.name());
        if (result.isUrl()) mUrlInput.setText(result.url());
        if (result.parent() != null) {
            setParentFolderInfo(result.parent().id(), result.parent().name());
        }

        synchronized (mLoadedLock) {
            mBookmarkNodeLoaded = true;
        }
    }

    private void handleDefaultBookmarkNode(BookmarkNode result) {
        if (getActivity() == null || getActivity().isFinishing()) return;

        if (result != null) {
            setParentFolderInfo(result.id(), result.name());
        } else {
            mFolderInput.setError(getResources().getText(R.string.default_folder_error));
        }

        synchronized (mLoadedLock) {
            mDefaultFolderLoaded = true;
        }
    }

    /**
     * Asynchronously retrieves the requested bookmark node showing a progress dialog if the task
     * takes too long.
     */
    private class LoadBookmarkNodeTask extends FragmentAsyncTask {
        private final Context mContext;
        private final Long mBookmarkId;
        private BookmarkNode mResult;

        /**
         * @param bookmarkId Id of the bookmark node to retrieve. Will retrieve the default bookmark
         *                   folder if null.
         */
        LoadBookmarkNodeTask(Long bookmarkId) {
            // The activity might be temporarily detached during the background operation if the
            // screen is rotated. Make sure to use a valid context even in that case.
            mContext = getActivity().getApplicationContext();
            mBookmarkId = bookmarkId;
        }

        @Override
        protected void runBackgroundTask() {
            mResult = (mBookmarkId == null)
                    ? ChromeBrowserProviderClient.getDefaultBookmarkFolder(mContext)
                    : ChromeBrowserProviderClient.getBookmarkNode(mContext, mBookmarkId,
                            ChromeBrowserProviderClient.GET_PARENT);
        }

        @Override
        protected void onTaskFinished() {
            if (mBookmarkId == null) {
                handleDefaultBookmarkNode(mResult);
            } else {
                handleGetBookmarkNode(mResult);
            }
        }

        @Override
        protected void setDependentUIEnabled(boolean enabled) {
            mOkButton.setEnabled(enabled);
            mFolderInput.setEnabled(enabled);

            switch (mActivityMode) {
                case ADD_BOOKMARK:
                case ADD_FOLDER:
                    break;
                case EDIT_BOOKMARK:
                case EDIT_FOLDER:
                    mTitleInput.setEnabled(enabled);
                    mUrlInput.setEnabled(enabled);
                    mRemoveButton.setEnabled(enabled);

                    if (!enabled) {
                        mUrlInput.setText(R.string.loading_bookmark);
                        mTitleInput.setText(R.string.loading_bookmark);
                    }
                    break;
                default:
                    assert false;
            }
        }
    }

    /**
     * Asynchronously inserts or updates a bookmark showing a progress dialog if the task takes
     * too long.
     */
    private class InsertUpdateBookmarkTask extends FragmentAsyncTask {
        private final Context mContext;
        private final ContentValues mBookmarkValues;
        private Long mBookmarkId;

        /**
         * @param bookmarkValues Contents of the bookmark to be inserted or updated.
         * @param bookmarkId Id of the bookmark node to update. If null the bookmark will be
         *                   inserted.
         */
        InsertUpdateBookmarkTask(ContentValues bookmarkValues, Long bookmarkId) {
            mContext = getActivity().getApplicationContext();
            mBookmarkValues = bookmarkValues;
            mBookmarkId = bookmarkId;
        }

        @Override
        protected void runBackgroundTask() {
            Uri bookmarksUri = ChromeBrowserProvider.getBookmarksUri(mContext);
            if (mBookmarkId == null) {
                Uri response = mContext.getContentResolver().insert(
                        bookmarksUri, mBookmarkValues);
                mBookmarkId = response != null ? ContentUris.parseId(response) :
                        INVALID_BOOKMARK_ID;
            } else {
                mContext.getContentResolver().update(
                        ContentUris.withAppendedId(bookmarksUri, mBookmarkId),
                        mBookmarkValues, null, null);
            }
        }

        @Override
        protected void onTaskFinished() {
            if (isFolder()) {
                mActionListener.onFolderCreated(mBookmarkId, mTitleInput.getText().toString());
            } else {
                mActionListener.onNodeEdited(mBookmarkId);
            }
        }

        @Override
        protected void setDependentUIEnabled(boolean enabled) {
            mOkButton.setEnabled(enabled);
            mRemoveButton.setEnabled(enabled);
            mCancelButton.setEnabled(enabled);
            mActionListener.setBackEnabled(enabled);
        }
    }

    /**
     * Asynchronously deletes a bookmark showing a progress dialog if the task takes too long.
     */
    private class DeleteBookmarkTask extends FragmentAsyncTask {
        private final Context mContext;
        private final long mBookmarkId;

        DeleteBookmarkTask(long bookmarkId) {
            mContext = getActivity().getApplicationContext();
            mBookmarkId = bookmarkId;
        }

        @Override
        protected void runBackgroundTask() {
            Uri bookmarksUri = ChromeBrowserProvider.getBookmarksUri(mContext);
            mContext.getContentResolver().delete(
                    ContentUris.withAppendedId(bookmarksUri, mBookmarkId),
                    null, null);
        }

        @Override
        protected void onTaskFinished() {
            mActionListener.onRemove();
        }

        @Override
        protected void setDependentUIEnabled(boolean enabled) {
            mOkButton.setEnabled(enabled);
            mRemoveButton.setEnabled(enabled);
            mCancelButton.setEnabled(enabled);
            mActionListener.setBackEnabled(enabled);
        }
    }

    /**
     * Listener to handle actions triggered by this fragment.
     */
    public static interface OnActionListener {
        /**
         * Called when the addition/editing of this bookmark node has been canceled.
         */
        public void onCancel();

        /**
         * The add/edit of this bookmark node was successful.
         * @param id The ID of the newly added bookmark (or the ID of the existing
         *         bookmark being edited).
         */
        public void onNodeEdited(long id);

        /**
         * The folder was succesfully created.
         * @param id The ID of the newly added folder.
         * @param name Name of the folder as well.
         */
        public void onFolderCreated(long id, String name);

        /**
         * Triggered when the user asks select a different bookmark folder for this node.
         */
        public void triggerFolderSelection();

        /**
         * Called when the bookmark node has been removed.
         */
        public void onRemove();

        /**
         * Called when the fragment wants to temporarily disable the back button.
         * Done only while performing an asynchronous task that changes the bookmark model.
         */
        public void setBackEnabled(boolean enabled);
    }
}
