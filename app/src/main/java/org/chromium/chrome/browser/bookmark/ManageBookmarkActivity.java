// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentManager.OnBackStackChangedListener;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.WindowManager;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * This class provides the adding bookmark UI. It is also required by
 * android.provider.Browser.saveBookmark
 */
public class ManageBookmarkActivity extends FragmentActivity {

    private static final String BOOKMARK_ID_URI_PARAM = "id";
    private static final String BOOKMARK_IS_FOLDER_URI_PARAM = "isfolder";

    private static final String TAG = "ManageBookmarkActivity";

    /* TODO(gb-deprecation): Use android.provider.BrowserContract.Bookmarks.IS_FOLDER */
    public static final String BOOKMARK_INTENT_IS_FOLDER = "folder";
    /* TODO(gb-deprecation): Use android.provider.BrowserContract.Bookmarks.TITLE */
    public static final String BOOKMARK_INTENT_TITLE = "title";
    /* TODO(gb-deprecation): Use android.provider.BrowserContract.Bookmarks.URL */
    public static final String BOOKMARK_INTENT_URL = "url";
    /* TODO(gb-deprecation): Use android.provider.BrowserContract.Bookmarks._ID */
    public static final String BOOKMARK_INTENT_ID = "_id";

    /**
     * The tag used when adding the base add/edit bookmark fragment.
     */
    @VisibleForTesting
    public static final String BASE_ADD_EDIT_FRAGMENT_TAG = "AddEdit";
    /**
     * The tag used when adding the folder selection fragment triggered by the base
     * add/edit bookmark fragment.
     */
    @VisibleForTesting
    public static final String BASE_SELECT_FOLDER_FRAGMENT_TAG = "SelectFolder";
    /**
     * The tag used when adding the add new bookmark folder fragment triggered by the initial
     * folder selection fragment.
     */
    @VisibleForTesting
    public static final String ADD_FOLDER_FRAGMENT_TAG = "AddFolder";
    /**
     * The tag used when adding the folder selection fragment triggered by the add new bookmark
     * folder fragment.
     */
    protected static final String ADD_FOLDER_SELECT_FOLDER_FRAGMENT_TAG = "AddFolderSelectFolder";

    /**
     * Internally used to temporarily disable the back button while performing non-cancelable
     * asynchronous tasks that modify the bookmark model.
     */
    private boolean mIsBackEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getApplication() instanceof ChromeApplication) {
                ((ChromeApplication) getApplication())
                        .startBrowserProcessesAndLoadLibrariesSync(true);
            }
        } catch (ProcessInitException e) {
            Log.e(TAG, "Unable to load native library.", e);
            ChromeApplication.reportStartupErrorAndExit(e);
            return;
        }
        if (!DeviceFormFactor.isTablet(this)) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        if (savedInstanceState == null) {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.add(
                    android.R.id.content, generateBaseFragment(), BASE_ADD_EDIT_FRAGMENT_TAG);
            fragmentTransaction.commit();
        } else {
            initializeFragmentState();
        }

        // When adding or removing fragments, ensure the keyboard is hidden if visible as the
        // editing fields are no longer on the screen.
        getFragmentManager().addOnBackStackChangedListener(new OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                UiUtils.hideKeyboard(findViewById(android.R.id.content));
            }
        });
        if (checkPermission(Manifest.permission.NFC, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED) {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter != null) nfcAdapter.setNdefPushMessage(null, this);
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Disables closing animation only for large/xlarge screen because the activity shows in
        // fullscreen in other cases and so closing animation doesn't look unnatural.
        if (getResources().getConfiguration().isLayoutSizeAtLeast(
                Configuration.SCREENLAYOUT_SIZE_LARGE)) {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (!mIsBackEnabled) return;
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            finish();
        } else {
            getFragmentManager().popBackStackImmediate();
        }
    }

    /**
     * Initializes the fragment state after a state restoration.
     * <p>
     * Reinitializes all of the event listeners on the fragments as they are not persisted across
     * activity recreation (and were referencing the old activity anyway).
     * <p>
     * The hidden state of the fragments are also not persisted across activity changes, so we need
     * to hide and show the fragments accordingly (since we know that hierarchy of the fragments
     * we can do this as a simple nested conditional).
     */
    private void initializeFragmentState() {
        AddEditBookmarkFragment baseAddEditFragment = (AddEditBookmarkFragment) getFragmentManager()
                .findFragmentByTag(BASE_ADD_EDIT_FRAGMENT_TAG);
        setActionListenerOnAddEdit(baseAddEditFragment);

        Fragment selectFolderFragment =
                getFragmentManager().findFragmentByTag(BASE_SELECT_FOLDER_FRAGMENT_TAG);
        if (selectFolderFragment != null) {
            setActionListenerOnFolderSelection(
                    (SelectBookmarkFolderFragment) selectFolderFragment);

            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.hide(baseAddEditFragment);

            Fragment addFolderFragment =
                    getFragmentManager().findFragmentByTag(ADD_FOLDER_FRAGMENT_TAG);
            if (addFolderFragment != null) {
                fragmentTransaction.hide(selectFolderFragment);
                setActionListenerOnAddEdit((AddEditBookmarkFragment) addFolderFragment);

                Fragment addFolderSelectFolderFragment = getFragmentManager().findFragmentByTag(
                        ADD_FOLDER_SELECT_FOLDER_FRAGMENT_TAG);
                if (addFolderSelectFolderFragment != null) {
                    setActionListenerOnFolderSelection(
                            (SelectBookmarkFolderFragment) addFolderSelectFolderFragment);
                    fragmentTransaction.hide(addFolderFragment);
                    fragmentTransaction.show(addFolderSelectFolderFragment);
                } else {
                    fragmentTransaction.show(addFolderFragment);
                }
            } else {
                fragmentTransaction.show(selectFolderFragment);
            }
            fragmentTransaction.commit();
        }
    }

    /**
     * Creates the base add/edit bookmark fragment based on the intent passed to this activity.
     *
     * @return The appropriate fragment based on the intent parameters.
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH")
    private AddEditBookmarkFragment generateBaseFragment() {
        if (getIntent() == null) {
            throw new IllegalArgumentException("intent can not be null");
        }
        Intent intent = getIntent();
        Uri intentUri = intent.getData();

        Long bookmarkId = null;
        boolean isFolder = false;
        AddEditBookmarkFragment addEditFragment;
        if (intentUri != null && intentUri.getHost().equals("editbookmark")) {
            isFolder = intentUri.getBooleanQueryParameter(BOOKMARK_IS_FOLDER_URI_PARAM, false);
            String bookmarkIdParam = intentUri.getQueryParameter(BOOKMARK_ID_URI_PARAM);
            if (bookmarkIdParam != null) bookmarkId = Long.parseLong(bookmarkIdParam);
            addEditFragment = AddEditBookmarkFragment.newEditInstance(isFolder, bookmarkId);
        } else {
            Bundle extras = intent.getExtras();
            String url = null;
            String name = null;
            if (extras != null) {
                isFolder = extras.getBoolean(BOOKMARK_INTENT_IS_FOLDER, false);

                if (extras.containsKey(BOOKMARK_INTENT_TITLE)) {
                    name = extras.getString(BOOKMARK_INTENT_TITLE);
                }
                if (extras.containsKey(BOOKMARK_INTENT_URL)) {
                    url = extras.getString(BOOKMARK_INTENT_URL);
                    url = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
                }
                if (extras.containsKey(BOOKMARK_INTENT_ID)) {
                    bookmarkId = extras.getLong(BOOKMARK_INTENT_ID);
                }
            }
            addEditFragment = AddEditBookmarkFragment.newInstance(isFolder, bookmarkId, name, url);
        }
        setActionListenerOnAddEdit(addEditFragment);
        return addEditFragment;
    }

    /**
     * Creates and sets an action listener on the add/edit bookmark fragment.
     * @param addEditFragment The fragment that the listener should be attached to.
     */
    private void setActionListenerOnAddEdit(final AddEditBookmarkFragment addEditFragment) {
        addEditFragment.setOnActionListener(new AddEditBookmarkFragment.OnActionListener() {
            @Override
            public void onCancel() {
                finishAddEdit();
            }


            @Override
            public void onNodeEdited(long id) {
                finishAddEdit();
            }

            @Override
            public void onFolderCreated(long id, String name) {
                finishAddEdit();
                if (getFragmentManager().getBackStackEntryCount() != 0) {
                    ((SelectBookmarkFolderFragment) addEditFragment.getTargetFragment())
                            .executeFolderSelection(id, name);
                }
            }

            @Override
            public void triggerFolderSelection() {
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();

                boolean isRoot = getFragmentManager().getBackStackEntryCount() == 0;
                SelectBookmarkFolderFragment selectFolder =
                        SelectBookmarkFolderFragment.newInstance(
                                // Only allow the root add/edit bookmark UI to create new bookmark
                                // folders during selection.
                                isRoot,
                                addEditFragment.getParentFolderId(),
                                addEditFragment.isFolder());
                selectFolder.setTargetFragment(addEditFragment, 0);
                setActionListenerOnFolderSelection(selectFolder);

                String tag = isRoot ? BASE_SELECT_FOLDER_FRAGMENT_TAG
                        : ADD_FOLDER_SELECT_FOLDER_FRAGMENT_TAG;
                fragmentTransaction.add(android.R.id.content, selectFolder, tag);
                fragmentTransaction.hide(addEditFragment);
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }

            @Override
            public void onRemove() {
                finishAddEdit();
            }

            @Override
            public void setBackEnabled(boolean enabled) {
                mIsBackEnabled = enabled;
            }

            private void finishAddEdit() {
                if (getFragmentManager().getBackStackEntryCount() == 0) {
                    finish();
                } else {
                    getFragmentManager().popBackStackImmediate();
                }
            }
        });
    }

    /**
     * Creates and sets an action listener on the bookmark folder selection fragment.
     * @param selectFolder The fragment that the listener should be attached to.
     */
    private void setActionListenerOnFolderSelection(
            final SelectBookmarkFolderFragment selectFolder) {
        selectFolder.setOnActionListener(new SelectBookmarkFolderFragment.OnActionListener() {
            @Override
            public void triggerNewFolderCreation(long selectedFolderId, String selectedFolderName) {
                newFolder(selectFolder, selectedFolderId, selectedFolderName);
            }
        });
    }

    /**
     * Create and display the fragment for creating a new bookmark folder.
     * @param triggeringFragment The fragment that requested the new folder fragment to be shown.
     * @param parentFolderId The ID of the bookmark folder to used as the default parent of the
     *         folder.
     * @param parentName The name of the bookmark folder to be used at the default parent.
     */
    private void newFolder(Fragment triggeringFragment, long parentFolderId, String parentName) {
        AddEditBookmarkFragment newFolderFragment =
                AddEditBookmarkFragment.newAddNewFolderInstance(parentFolderId, parentName);
        newFolderFragment.setTargetFragment(triggeringFragment, 0);
        setActionListenerOnAddEdit(newFolderFragment);
        getFragmentManager().beginTransaction()
                .hide(triggeringFragment)
                .add(android.R.id.content, newFolderFragment, ADD_FOLDER_FRAGMENT_TAG)
                .addToBackStack(null)
                .commit();
    }
}
