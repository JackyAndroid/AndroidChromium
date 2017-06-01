// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.provider;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.database.SQLiteCursor;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.BrowserStartupController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/**
 * This class provides access to user data stored in Chrome, such as bookmarks, most visited pages,
 * etc. It is used to support android.provider.Browser.
 */
public class ChromeBrowserProvider extends ContentProvider {
    private static final String TAG = "ChromeBrowserProvider";

    /**
     * A projection of {@link #SEARCHES_URI} that contains {@link SearchColumns#ID},
     * {@link SearchColumns#SEARCH}, and {@link SearchColumns#DATE}.
     */
    @VisibleForTesting
    @SuppressFBWarnings("MS_PKGPROTECT")
    public static final String[] SEARCHES_PROJECTION = new String[] {
            // if you change column order you must also change indices below
            SearchColumns.ID, // 0
            SearchColumns.SEARCH, // 1
            SearchColumns.DATE, // 2
    };

    /* these indices dependent on SEARCHES_PROJECTION */
    @VisibleForTesting
    public static final int SEARCHES_PROJECTION_SEARCH_INDEX = 1;
    @VisibleForTesting
    public static final int SEARCHES_PROJECTION_DATE_INDEX = 2;

    // The permission required for using the bookmark folders API. Android build system does
    // not generate Manifest.java for java libraries, hence use the permission name string. When
    // making changes to this permission, also update the permission in AndroidManifest.xml.
    private static final String PERMISSION_READ_WRITE_BOOKMARKS = "READ_WRITE_BOOKMARK_FOLDERS";

    // Defines Chrome's API authority, so it can be run and tested
    // independently.
    private static final String API_AUTHORITY_SUFFIX = ".browser";

    private static final String BROWSER_CONTRACT_API_AUTHORITY =
            "com.google.android.apps.chrome.browser-contract";

    // These values are taken from android.provider.BrowserContract.java since
    // that class is hidden from the SDK.
    private static final String BROWSER_CONTRACT_AUTHORITY = "com.android.browser";
    private static final String BROWSER_CONTRACT_HISTORY_CONTENT_TYPE =
            "vnd.android.cursor.dir/browser-history";
    private static final String BROWSER_CONTRACT_HISTORY_CONTENT_ITEM_TYPE =
            "vnd.android.cursor.item/browser-history";
    private static final String BROWSER_CONTRACT_BOOKMARK_CONTENT_TYPE =
            "vnd.android.cursor.dir/bookmark";
    private static final String BROWSER_CONTRACT_BOOKMARK_CONTENT_ITEM_TYPE =
            "vnd.android.cursor.item/bookmark";
    private static final String BROWSER_CONTRACT_SEARCH_CONTENT_TYPE =
            "vnd.android.cursor.dir/searches";
    private static final String BROWSER_CONTRACT_SEARCH_CONTENT_ITEM_TYPE =
            "vnd.android.cursor.item/searches";

    // This Authority is for internal interface. It's concatenated with
    // Context.getPackageName() so that we can install different channels
    // SxS and have different authorities.
    private static final String AUTHORITY_SUFFIX = ".ChromeBrowserProvider";
    private static final String BOOKMARKS_PATH = "bookmarks";
    private static final String SEARCHES_PATH = "searches";
    private static final String HISTORY_PATH = "history";
    private static final String COMBINED_PATH = "combined";

    public static final Uri BROWSER_CONTRACTS_BOOKMAKRS_API_URI = buildContentUri(
            BROWSER_CONTRACT_API_AUTHORITY, BOOKMARKS_PATH);

    public static final Uri BROWSER_CONTRACTS_SEARCHES_API_URI = buildContentUri(
            BROWSER_CONTRACT_API_AUTHORITY, SEARCHES_PATH);

    public static final Uri BROWSER_CONTRACTS_HISTORY_API_URI = buildContentUri(
            BROWSER_CONTRACT_API_AUTHORITY, HISTORY_PATH);

    public static final Uri BROWSER_CONTRACTS_COMBINED_API_URI = buildContentUri(
            BROWSER_CONTRACT_API_AUTHORITY, COMBINED_PATH);

    /** The parameter used to specify a bookmark parent ID in ContentValues. */
    public static final String BOOKMARK_PARENT_ID_PARAM = "parentId";

    /** The parameter used to specify whether this is a bookmark folder. */
    public static final String BOOKMARK_IS_FOLDER_PARAM = "isFolder";

    /**
     * Invalid ID value for the Android ContentProvider API calls.
     * The value 0 is intentional: if the ID represents a bookmark node then it's the root node
     * and not accessible. Otherwise it represents a SQLite row id, so 0 is also invalid.
     */
    public static final long INVALID_CONTENT_PROVIDER_ID = 0;

    // ID used to indicate an invalid id for bookmark nodes.
    private static final long INVALID_BOOKMARK_ID = -1;

    private static final String LAST_MODIFIED_BOOKMARK_FOLDER_ID_KEY = "last_bookmark_folder_id";

    private static final int URI_MATCH_BOOKMARKS = 0;
    private static final int URI_MATCH_BOOKMARKS_ID = 1;
    private static final int URL_MATCH_API_BOOKMARK = 2;
    private static final int URL_MATCH_API_BOOKMARK_ID = 3;
    private static final int URL_MATCH_API_SEARCHES = 4;
    private static final int URL_MATCH_API_SEARCHES_ID = 5;
    private static final int URL_MATCH_API_HISTORY_CONTENT = 6;
    private static final int URL_MATCH_API_HISTORY_CONTENT_ID = 7;
    private static final int URL_MATCH_API_BOOKMARK_CONTENT = 8;
    private static final int URL_MATCH_API_BOOKMARK_CONTENT_ID = 9;
    private static final int URL_MATCH_BOOKMARK_SUGGESTIONS_ID = 10;
    private static final int URL_MATCH_BOOKMARK_HISTORY_SUGGESTIONS_ID = 11;

    // TODO : Using Android.provider.Browser.HISTORY_PROJECTION once THUMBNAIL,
    // TOUCH_ICON, and USER_ENTERED fields are supported.
    private static final String[] BOOKMARK_DEFAULT_PROJECTION = new String[] {
        BookmarkColumns.ID, BookmarkColumns.URL, BookmarkColumns.VISITS,
        BookmarkColumns.DATE, BookmarkColumns.BOOKMARK, BookmarkColumns.TITLE,
        BookmarkColumns.FAVICON, BookmarkColumns.CREATED
    };

    private static final String[] SUGGEST_PROJECTION = new String[] {
        BookmarkColumns.ID,
        BookmarkColumns.TITLE,
        BookmarkColumns.URL,
        BookmarkColumns.DATE,
        BookmarkColumns.BOOKMARK
    };

    private final Object mInitializeUriMatcherLock = new Object();
    private final Object mLoadNativeLock = new Object();
    private UriMatcher mUriMatcher;
    private long mLastModifiedBookmarkFolderId = INVALID_BOOKMARK_ID;
    private long mNativeChromeBrowserProvider;
    private BookmarkNode mMobileBookmarksFolder;

    private void ensureUriMatcherInitialized() {
        synchronized (mInitializeUriMatcherLock) {
            if (mUriMatcher != null) return;

            mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            // The internal URIs
            String authority = getContext().getPackageName() + AUTHORITY_SUFFIX;
            mUriMatcher.addURI(authority, BOOKMARKS_PATH, URI_MATCH_BOOKMARKS);
            mUriMatcher.addURI(authority, BOOKMARKS_PATH + "/#", URI_MATCH_BOOKMARKS_ID);
            // The internal authority for public APIs
            String apiAuthority = getContext().getPackageName() + API_AUTHORITY_SUFFIX;
            mUriMatcher.addURI(apiAuthority, BOOKMARKS_PATH, URL_MATCH_API_BOOKMARK);
            mUriMatcher.addURI(apiAuthority, BOOKMARKS_PATH + "/#", URL_MATCH_API_BOOKMARK_ID);
            mUriMatcher.addURI(apiAuthority, SEARCHES_PATH, URL_MATCH_API_SEARCHES);
            mUriMatcher.addURI(apiAuthority, SEARCHES_PATH + "/#", URL_MATCH_API_SEARCHES_ID);
            mUriMatcher.addURI(apiAuthority, HISTORY_PATH, URL_MATCH_API_HISTORY_CONTENT);
            mUriMatcher.addURI(apiAuthority, HISTORY_PATH + "/#", URL_MATCH_API_HISTORY_CONTENT_ID);
            mUriMatcher.addURI(apiAuthority, COMBINED_PATH, URL_MATCH_API_BOOKMARK);
            mUriMatcher.addURI(apiAuthority, COMBINED_PATH + "/#", URL_MATCH_API_BOOKMARK_ID);
            // The internal authority for BrowserContracts
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, HISTORY_PATH,
                               URL_MATCH_API_HISTORY_CONTENT);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, HISTORY_PATH + "/#",
                               URL_MATCH_API_HISTORY_CONTENT_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, COMBINED_PATH,
                               URL_MATCH_API_BOOKMARK);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, COMBINED_PATH + "/#",
                               URL_MATCH_API_BOOKMARK_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, SEARCHES_PATH,
                               URL_MATCH_API_SEARCHES);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, SEARCHES_PATH + "/#",
                               URL_MATCH_API_SEARCHES_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, BOOKMARKS_PATH,
                               URL_MATCH_API_BOOKMARK_CONTENT);
            mUriMatcher.addURI(BROWSER_CONTRACT_API_AUTHORITY, BOOKMARKS_PATH + "/#",
                               URL_MATCH_API_BOOKMARK_CONTENT_ID);
            // Added the Android Framework URIs, so the provider can easily switched
            // by adding 'browser' and 'com.android.browser' in manifest.
            // The Android's BrowserContract
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, HISTORY_PATH,
                               URL_MATCH_API_HISTORY_CONTENT);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, HISTORY_PATH + "/#",
                               URL_MATCH_API_HISTORY_CONTENT_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, "combined", URL_MATCH_API_BOOKMARK);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, "combined/#", URL_MATCH_API_BOOKMARK_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, SEARCHES_PATH, URL_MATCH_API_SEARCHES);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, SEARCHES_PATH + "/#",
                               URL_MATCH_API_SEARCHES_ID);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, BOOKMARKS_PATH,
                               URL_MATCH_API_BOOKMARK_CONTENT);
            mUriMatcher.addURI(BROWSER_CONTRACT_AUTHORITY, BOOKMARKS_PATH + "/#",
                               URL_MATCH_API_BOOKMARK_CONTENT_ID);
            // For supporting android.provider.browser.BookmarkColumns and
            // SearchColumns
            mUriMatcher.addURI("browser", BOOKMARKS_PATH, URL_MATCH_API_BOOKMARK);
            mUriMatcher.addURI("browser", BOOKMARKS_PATH + "/#", URL_MATCH_API_BOOKMARK_ID);
            mUriMatcher.addURI("browser", SEARCHES_PATH, URL_MATCH_API_SEARCHES);
            mUriMatcher.addURI("browser", SEARCHES_PATH + "/#", URL_MATCH_API_SEARCHES_ID);

            mUriMatcher.addURI(apiAuthority,
                               BOOKMARKS_PATH + "/" + SearchManager.SUGGEST_URI_PATH_QUERY,
                               URL_MATCH_BOOKMARK_SUGGESTIONS_ID);
            mUriMatcher.addURI(apiAuthority,
                               SearchManager.SUGGEST_URI_PATH_QUERY,
                               URL_MATCH_BOOKMARK_HISTORY_SUGGESTIONS_ID);
        }
    }

    @Override
    public boolean onCreate() {
        ContentApplication.initCommandLine(getContext());

        BrowserStartupController.get(getContext(), LibraryProcessType.PROCESS_BROWSER)
                .addStartupCompletedObserver(
                        new BrowserStartupController.StartupCallback() {
                            @Override
                            public void onSuccess(boolean alreadyStarted) {
                                ensureNativeSideInitialized();
                            }

                            @Override
                            public void onFailure() {
                            }
                        });

        return true;
    }

    /**
     * Lazily fetches the last modified bookmark folder id.
     */
    private long getLastModifiedBookmarkFolderId() {
        if (mLastModifiedBookmarkFolderId == INVALID_BOOKMARK_ID) {
            SharedPreferences sharedPreferences =
                    ContextUtils.getAppSharedPreferences();
            mLastModifiedBookmarkFolderId = sharedPreferences.getLong(
                    LAST_MODIFIED_BOOKMARK_FOLDER_ID_KEY, INVALID_BOOKMARK_ID);
        }
        return mLastModifiedBookmarkFolderId;
    }

    private String buildSuggestWhere(String selection, int argc) {
        StringBuilder sb = new StringBuilder(selection);
        for (int i = 0; i < argc - 1; i++) {
            sb.append(" OR ");
            sb.append(selection);
        }
        return sb.toString();
    }

    private String getReadWritePermissionNameForBookmarkFolders() {
        return getContext().getApplicationContext().getPackageName() + ".permission."
                + PERMISSION_READ_WRITE_BOOKMARKS;
    }

    private Cursor getBookmarkHistorySuggestions(String selection, String[] selectionArgs,
            String sortOrder, boolean excludeHistory) {
        boolean matchTitles = false;
        Vector<String> args = new Vector<String>();
        String like = selectionArgs[0] + "%";
        if (selectionArgs[0].startsWith("http") || selectionArgs[0].startsWith("file")) {
            args.add(like);
        } else {
            // Match against common URL prefixes.
            args.add("http://" + like);
            args.add("https://" + like);
            args.add("http://www." + like);
            args.add("https://www." + like);
            args.add("file://" + like);
            matchTitles = true;
        }

        StringBuilder urlWhere = new StringBuilder("(");
        urlWhere.append(buildSuggestWhere(selection, args.size()));
        if (matchTitles) {
            args.add(like);
            urlWhere.append(" OR title LIKE ?");
        }
        urlWhere.append(")");

        if (excludeHistory) {
            urlWhere.append(" AND bookmark=?");
            args.add("1");
        }

        selectionArgs = args.toArray(selectionArgs);
        Cursor cursor = queryBookmarkFromAPI(SUGGEST_PROJECTION, urlWhere.toString(),
                selectionArgs, sortOrder);
        return new ChromeBrowserProviderSuggestionsCursor(cursor);
    }

    /**
     * @see android.content.ContentUris#parseId(Uri)
     * @return The id from a content URI or -1 if the URI has no id or is malformed.
     */
    private static long getContentUriId(Uri uri) {
        try {
            return ContentUris.parseId(uri);
        } catch (UnsupportedOperationException e) {
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (!canHandleContentProviderApiCall()) return null;

        // Starting with M, other apps are no longer allowed to access bookmarks. But returning null
        // might break old apps, so return an empty Cursor instead.
        if (!hasReadAccess()) return new MatrixCursor(BOOKMARK_DEFAULT_PROJECTION, 0);

        // Check for invalid id values if provided.
        long bookmarkId = getContentUriId(uri);
        if (bookmarkId == INVALID_CONTENT_PROVIDER_ID) return null;

        int match = mUriMatcher.match(uri);
        Cursor cursor = null;
        switch (match) {
            case URL_MATCH_BOOKMARK_SUGGESTIONS_ID:
                cursor = getBookmarkHistorySuggestions(selection, selectionArgs, sortOrder, true);
                break;
            case URL_MATCH_BOOKMARK_HISTORY_SUGGESTIONS_ID:
                cursor = getBookmarkHistorySuggestions(selection, selectionArgs, sortOrder, false);
                break;
            case URL_MATCH_API_BOOKMARK:
                cursor = queryBookmarkFromAPI(projection, selection, selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_BOOKMARK_ID:
                cursor = queryBookmarkFromAPI(projection, buildWhereClause(bookmarkId, selection),
                        selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_SEARCHES:
                cursor = querySearchTermFromAPI(projection, selection, selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_SEARCHES_ID:
                cursor = querySearchTermFromAPI(projection, buildWhereClause(bookmarkId, selection),
                        selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_HISTORY_CONTENT:
                cursor = queryBookmarkFromAPI(projection, buildHistoryWhereClause(selection),
                        selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_HISTORY_CONTENT_ID:
                cursor = queryBookmarkFromAPI(projection,
                        buildHistoryWhereClause(bookmarkId, selection), selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT:
                cursor = queryBookmarkFromAPI(projection, buildBookmarkWhereClause(selection),
                        selectionArgs, sortOrder);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT_ID:
                cursor = queryBookmarkFromAPI(projection,
                        buildBookmarkWhereClause(bookmarkId, selection), selectionArgs, sortOrder);
                break;
            default:
                throw new IllegalArgumentException(TAG + ": query - unknown URL uri = " + uri);
        }
        if (cursor == null) {
            cursor = new MatrixCursor(new String[] { });
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    @SuppressFBWarnings("SF_SWITCH_FALLTHROUGH")
    public Uri insert(Uri uri, ContentValues values) {
        if (!canHandleContentProviderApiCall() || !hasWriteAccess()) return null;

        int match = mUriMatcher.match(uri);
        Uri res = null;
        long id;
        switch (match) {
            case URI_MATCH_BOOKMARKS:
                id = addBookmark(values);
                if (id == INVALID_BOOKMARK_ID) return null;
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT:
                values.put(BookmarkColumns.BOOKMARK, 1);
                //$FALL-THROUGH$
            case URL_MATCH_API_BOOKMARK:
            case URL_MATCH_API_HISTORY_CONTENT:
                id = addBookmarkFromAPI(values);
                if (id == INVALID_CONTENT_PROVIDER_ID) return null;
                break;
            case URL_MATCH_API_SEARCHES:
                id = addSearchTermFromAPI(values);
                if (id == INVALID_CONTENT_PROVIDER_ID) return null;
                break;
            default:
                throw new IllegalArgumentException(TAG + ": insert - unknown URL " + uri);
        }

        res = ContentUris.withAppendedId(uri, id);
        notifyChange(res);
        return res;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (!canHandleContentProviderApiCall() || !hasWriteAccess()) return 0;

        // Check for invalid id values if provided.
        long bookmarkId = getContentUriId(uri);
        if (bookmarkId == INVALID_CONTENT_PROVIDER_ID) return 0;

        int match = mUriMatcher.match(uri);
        int result;
        switch (match) {
            case URI_MATCH_BOOKMARKS_ID :
                result = nativeRemoveBookmark(mNativeChromeBrowserProvider, bookmarkId);
                break;
            case URL_MATCH_API_BOOKMARK_ID:
                result = removeBookmarkFromAPI(
                        buildWhereClause(bookmarkId, selection), selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK:
                result = removeBookmarkFromAPI(selection, selectionArgs);
                break;
            case URL_MATCH_API_SEARCHES_ID:
                result = removeSearchFromAPI(buildWhereClause(bookmarkId, selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_SEARCHES:
                result = removeSearchFromAPI(selection, selectionArgs);
                break;
            case URL_MATCH_API_HISTORY_CONTENT:
                result = removeHistoryFromAPI(selection, selectionArgs);
                break;
            case URL_MATCH_API_HISTORY_CONTENT_ID:
                result = removeHistoryFromAPI(buildWhereClause(bookmarkId, selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT:
                result = removeBookmarkFromAPI(buildBookmarkWhereClause(selection), selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT_ID:
                result = removeBookmarkFromAPI(buildBookmarkWhereClause(bookmarkId, selection),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException(TAG + ": delete - unknown URL " + uri);
        }
        if (result != 0) notifyChange(uri);
        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (!canHandleContentProviderApiCall() || !hasWriteAccess()) return 0;

        // Check for invalid id values if provided.
        long bookmarkId = getContentUriId(uri);
        if (bookmarkId == INVALID_CONTENT_PROVIDER_ID) return 0;

        int match = mUriMatcher.match(uri);
        int result;
        switch (match) {
            case URI_MATCH_BOOKMARKS_ID:
                String url = null;
                if (values.containsKey(BookmarkColumns.URL)) {
                    url = values.getAsString(BookmarkColumns.URL);
                }
                String title = values.getAsString(BookmarkColumns.TITLE);
                long parentId = INVALID_BOOKMARK_ID;
                if (values.containsKey(BOOKMARK_PARENT_ID_PARAM)) {
                    parentId = values.getAsLong(BOOKMARK_PARENT_ID_PARAM);
                }
                result = nativeUpdateBookmark(mNativeChromeBrowserProvider, bookmarkId, url, title,
                        parentId);
                updateLastModifiedBookmarkFolder(parentId);
                break;
            case URL_MATCH_API_BOOKMARK_ID:
                result = updateBookmarkFromAPI(values, buildWhereClause(bookmarkId, selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK:
                result = updateBookmarkFromAPI(values, selection, selectionArgs);
                break;
            case URL_MATCH_API_SEARCHES_ID:
                result = updateSearchTermFromAPI(values, buildWhereClause(bookmarkId, selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_SEARCHES:
                result = updateSearchTermFromAPI(values, selection, selectionArgs);
                break;
            case URL_MATCH_API_HISTORY_CONTENT:
                result = updateBookmarkFromAPI(values, buildHistoryWhereClause(selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_HISTORY_CONTENT_ID:
                result = updateBookmarkFromAPI(values,
                        buildHistoryWhereClause(bookmarkId, selection), selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT:
                result = updateBookmarkFromAPI(values, buildBookmarkWhereClause(selection),
                        selectionArgs);
                break;
            case URL_MATCH_API_BOOKMARK_CONTENT_ID:
                result = updateBookmarkFromAPI(values,
                        buildBookmarkWhereClause(bookmarkId, selection), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException(TAG + ": update - unknown URL " + uri);
        }
        if (result != 0) notifyChange(uri);
        return result;
    }

    @Override
    public String getType(Uri uri) {
        ensureUriMatcherInitialized();
        int match = mUriMatcher.match(uri);
        switch (match) {
            case URI_MATCH_BOOKMARKS:
            case URL_MATCH_API_BOOKMARK:
                return BROWSER_CONTRACT_BOOKMARK_CONTENT_TYPE;
            case URI_MATCH_BOOKMARKS_ID:
            case URL_MATCH_API_BOOKMARK_ID:
                return BROWSER_CONTRACT_BOOKMARK_CONTENT_ITEM_TYPE;
            case URL_MATCH_API_SEARCHES:
                return BROWSER_CONTRACT_SEARCH_CONTENT_TYPE;
            case URL_MATCH_API_SEARCHES_ID:
                return BROWSER_CONTRACT_SEARCH_CONTENT_ITEM_TYPE;
            case URL_MATCH_API_HISTORY_CONTENT:
                return BROWSER_CONTRACT_HISTORY_CONTENT_TYPE;
            case URL_MATCH_API_HISTORY_CONTENT_ID:
                return BROWSER_CONTRACT_HISTORY_CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException(TAG + ": getType - unknown URL " + uri);
        }
    }

    private long addBookmark(ContentValues values) {
        String url = values.getAsString(BookmarkColumns.URL);
        String title = values.getAsString(BookmarkColumns.TITLE);
        boolean isFolder = false;
        if (values.containsKey(BOOKMARK_IS_FOLDER_PARAM)) {
            isFolder = values.getAsBoolean(BOOKMARK_IS_FOLDER_PARAM);
        }
        long parentId = INVALID_BOOKMARK_ID;
        if (values.containsKey(BOOKMARK_PARENT_ID_PARAM)) {
            parentId = values.getAsLong(BOOKMARK_PARENT_ID_PARAM);
        }
        long id = nativeAddBookmark(mNativeChromeBrowserProvider, url, title, isFolder, parentId);
        if (id == INVALID_BOOKMARK_ID) return id;

        if (isFolder) {
            updateLastModifiedBookmarkFolder(id);
        } else {
            updateLastModifiedBookmarkFolder(parentId);
        }
        return id;
    }

    private void updateLastModifiedBookmarkFolder(long id) {
        if (getLastModifiedBookmarkFolderId() == id) return;

        mLastModifiedBookmarkFolderId = id;
        SharedPreferences sharedPreferences =
                ContextUtils.getAppSharedPreferences();
        sharedPreferences.edit()
                .putLong(LAST_MODIFIED_BOOKMARK_FOLDER_ID_KEY, mLastModifiedBookmarkFolderId)
                .apply();
    }

    @VisibleForTesting
    public static String getApiAuthority(Context context) {
        return context.getPackageName() + API_AUTHORITY_SUFFIX;
    }

    @VisibleForTesting
    public static Uri getBookmarksApiUri(Context context) {
        return buildContentUri(getApiAuthority(context), BOOKMARKS_PATH);
    }

    @VisibleForTesting
    public static Uri getSearchesApiUri(Context context) {
        return buildContentUri(getApiAuthority(context), SEARCHES_PATH);
    }

    /**
     * Checks whether Chrome is sufficiently initialized to handle a call to the
     * ChromeBrowserProvider.
     */
    private boolean canHandleContentProviderApiCall() {
        if (isInUiThread()) return false;
        ensureUriMatcherInitialized();
        if (mNativeChromeBrowserProvider != 0) return true;
        synchronized (mLoadNativeLock) {
            ThreadUtils.runOnUiThreadBlocking(new Runnable() {
                @Override
                @SuppressFBWarnings("DM_EXIT")
                public void run() {
                    if (mNativeChromeBrowserProvider != 0) return;
                    try {
                        ChromeBrowserInitializer.getInstance(getContext())
                                .handleSynchronousStartup();
                    } catch (ProcessInitException e) {
                        // Chrome browser runs in the background, so exit silently; but do exit,
                        // since otherwise the next attempt to use Chrome will find a broken JNI.
                        System.exit(-1);
                    }
                    ensureNativeSideInitialized();
                }
            });
        }
        return true;
    }

    /**
     * @return Whether the caller has read access to history and bookmarks information.
     */
    private boolean hasReadAccess() {
        return hasPermission("com.android.browser.permission.READ_HISTORY_BOOKMARKS");
    }

    /**
     * @return Whether the caller has write access to history and bookmarks information.
     */
    private boolean hasWriteAccess() {
        return hasPermission("com.android.browser.permission.WRITE_HISTORY_BOOKMARKS");
    }

    /**
     * The type of a BookmarkNode.
     */
    public enum Type {
        URL,
        FOLDER,
        BOOKMARK_BAR,
        OTHER_NODE,
        MOBILE
    }

    /**
     * Simple Data Object representing the chrome bookmark node.
     */
    public static class BookmarkNode implements Parcelable {
        private final long mId;
        private final String mName;
        private final String mUrl;
        private final Type mType;
        private final BookmarkNode mParent;
        private final List<BookmarkNode> mChildren = new ArrayList<BookmarkNode>();

        // Favicon and thumbnail optionally set in a 2-step procedure.
        private byte[] mFavicon;
        private byte[] mThumbnail;

        /** Used to pass structured data back from the native code. */
        @VisibleForTesting
        public BookmarkNode(long id, Type type, String name, String url, BookmarkNode parent) {
            mId = id;
            mName = name;
            mUrl = url;
            mType = type;
            mParent = parent;
        }

        /**
         * @return The id of this bookmark entry.
         */
        public long id() {
            return mId;
        }

        /**
         * @return The name of this bookmark entry.
         */
        public String name() {
            return mName;
        }

        /**
         * @return The URL of this bookmark entry.
         */
        public String url() {
            return mUrl;
        }

        /**
         * @return The type of this bookmark entry.
         */
        public Type type() {
            return mType;
        }

        /**
         * @return The bookmark favicon, if any.
         */
        @SuppressFBWarnings("EI_EXPOSE_REP")
        public byte[] favicon() {
            return mFavicon;
        }

        /**
         * @return The bookmark thumbnail, if any.
         */
        @SuppressFBWarnings("EI_EXPOSE_REP")
        public byte[] thumbnail() {
            return mThumbnail;
        }

        /**
         * @return The parent folder of this bookmark entry.
         */
        public BookmarkNode parent() {
            return mParent;
        }

        /**
         * Adds a child to this node.
         *
         * <p>
         * Used solely by the native code.
         */
        @VisibleForTesting
        public void addChild(BookmarkNode child) {
            mChildren.add(child);
        }

        /**
         * @return The child bookmark nodes of this node.
         */
        public List<BookmarkNode> children() {
            return mChildren;
        }

        /**
         * @return Whether this node represents a bookmarked URL or not.
         */
        public boolean isUrl() {
            return mUrl != null;
        }

        /**
         * @return true if the two individual nodes contain the same information.
         * The existence of parent and children nodes is checked, but their contents are not.
         */
        @VisibleForTesting
        public boolean equalContents(BookmarkNode node) {
            return node != null
                    && mId == node.mId
                    && !(mName == null ^ node.mName == null)
                    && (mName == null || mName.equals(node.mName))
                    && !(mUrl == null ^ node.mUrl == null)
                    && (mUrl == null || mUrl.equals(node.mUrl))
                    && mType == node.mType
                    && byteArrayEqual(mFavicon, node.mFavicon)
                    && byteArrayEqual(mThumbnail, node.mThumbnail)
                    && !(mParent == null ^ node.mParent == null)
                    && children().size() == node.children().size();
        }

        private static boolean byteArrayEqual(byte[] byte1, byte[] byte2) {
            if (byte1 == null && byte2 != null) return byte2.length == 0;
            if (byte2 == null && byte1 != null) return byte1.length == 0;
            return Arrays.equals(byte1, byte2);
        }

        @VisibleForTesting
        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public void setFavicon(byte[] favicon) {
            mFavicon = favicon;
        }

        @VisibleForTesting
        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public void setThumbnail(byte[] thumbnail) {
            mThumbnail = thumbnail;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // Write the current node id.
            dest.writeLong(mId);

            // Serialize the full hierarchy from the root.
            getHierarchyRoot().writeNodeContentsRecursive(dest);
        }

        @VisibleForTesting
        public BookmarkNode getHierarchyRoot() {
            BookmarkNode root = this;
            while (root.parent() != null) {
                root = root.parent();
            }
            return root;
        }

        private void writeNodeContentsRecursive(Parcel dest) {
            writeNodeContents(dest);
            dest.writeInt(mChildren.size());
            for (BookmarkNode child : mChildren) {
                child.writeNodeContentsRecursive(dest);
            }
        }

        private void writeNodeContents(Parcel dest) {
            dest.writeLong(mId);
            dest.writeString(mName);
            dest.writeString(mUrl);
            dest.writeInt(mType.ordinal());
            dest.writeByteArray(mFavicon);
            dest.writeByteArray(mThumbnail);
            dest.writeLong(mParent != null ? mParent.mId : INVALID_BOOKMARK_ID);
        }

        public static final Creator<BookmarkNode> CREATOR = new Creator<BookmarkNode>() {
            private LongSparseArray<BookmarkNode> mNodeMap;

            @Override
            public BookmarkNode createFromParcel(Parcel source) {
                mNodeMap = new LongSparseArray<>();
                long currentNodeId = source.readLong();
                readNodeContentsRecursive(source);
                BookmarkNode node = getNode(currentNodeId);
                mNodeMap.clear();
                return node;
            }

            @Override
            public BookmarkNode[] newArray(int size) {
                return new BookmarkNode[size];
            }

            private BookmarkNode getNode(long id) {
                if (id == INVALID_BOOKMARK_ID) return null;
                Long nodeId = Long.valueOf(id);
                if (mNodeMap.indexOfKey(nodeId) < 0) {
                    Log.e(TAG, "Invalid BookmarkNode hierarchy. Unknown id " + id);
                    return null;
                }
                return mNodeMap.get(nodeId);
            }

            private BookmarkNode readNodeContents(Parcel source) {
                long id = source.readLong();
                String name = source.readString();
                String url = source.readString();
                int type = source.readInt();
                byte[] favicon = source.createByteArray();
                byte[] thumbnail = source.createByteArray();
                long parentId = source.readLong();
                if (type < 0 || type >= Type.values().length) {
                    Log.w(TAG, "Invalid node type ordinal value.");
                    return null;
                }

                BookmarkNode node = new BookmarkNode(id, Type.values()[type], name, url,
                        getNode(parentId));
                node.setFavicon(favicon);
                node.setThumbnail(thumbnail);
                return node;
            }

            private BookmarkNode readNodeContentsRecursive(Parcel source) {
                BookmarkNode node = readNodeContents(source);
                if (node == null) return null;

                Long nodeId = Long.valueOf(node.id());
                if (mNodeMap.indexOfKey(nodeId) >= 0) {
                    Log.e(TAG, "Invalid BookmarkNode hierarchy. Duplicate id " + node.id());
                    return null;
                }
                mNodeMap.put(nodeId, node);

                int numChildren = source.readInt();
                for (int i = 0; i < numChildren; ++i) {
                    node.addChild(readNodeContentsRecursive(source));
                }

                return node;
            }
        };
    }

    private long addBookmarkFromAPI(ContentValues values) {
        BookmarkRow row = BookmarkRow.fromContentValues(values);
        if (row.mUrl == null) {
            throw new IllegalArgumentException("Must have a bookmark URL");
        }
        return nativeAddBookmarkFromAPI(mNativeChromeBrowserProvider,
                row.mUrl, row.mCreated, row.mIsBookmark, row.mDate, row.mFavicon,
                row.mTitle, row.mVisits, row.mParentId);
    }

    private Cursor queryBookmarkFromAPI(String[] projectionIn, String selection,
            String[] selectionArgs, String sortOrder) {
        String[] projection = null;
        if (projectionIn == null || projectionIn.length == 0) {
            projection = BOOKMARK_DEFAULT_PROJECTION;
        } else {
            projection = projectionIn;
        }

        return nativeQueryBookmarkFromAPI(mNativeChromeBrowserProvider, projection, selection,
                selectionArgs, sortOrder);
    }

    private int updateBookmarkFromAPI(ContentValues values, String selection,
            String[] selectionArgs) {
        BookmarkRow row = BookmarkRow.fromContentValues(values);
        return nativeUpdateBookmarkFromAPI(mNativeChromeBrowserProvider,
                row.mUrl, row.mCreated, row.mIsBookmark, row.mDate,
                row.mFavicon, row.mTitle, row.mVisits, row.mParentId, selection, selectionArgs);
    }

    private int removeBookmarkFromAPI(String selection, String[] selectionArgs) {
        return nativeRemoveBookmarkFromAPI(mNativeChromeBrowserProvider, selection, selectionArgs);
    }

    private int removeHistoryFromAPI(String selection, String[] selectionArgs) {
        return nativeRemoveHistoryFromAPI(mNativeChromeBrowserProvider, selection, selectionArgs);
    }

    @CalledByNative
    private void onBookmarkChanged() {
        notifyChange(buildAPIContentUri(getContext(), BOOKMARKS_PATH));
    }

    @CalledByNative
    private void onHistoryChanged() {
        notifyChange(buildAPIContentUri(getContext(), HISTORY_PATH));
    }

    @CalledByNative
    private void onSearchTermChanged() {
        notifyChange(buildAPIContentUri(getContext(), SEARCHES_PATH));
    }

    private long addSearchTermFromAPI(ContentValues values) {
        SearchRow row = SearchRow.fromContentValues(values);
        if (row.mTerm == null) {
            throw new IllegalArgumentException("Must have a search term");
        }
        return nativeAddSearchTermFromAPI(mNativeChromeBrowserProvider, row.mTerm, row.mDate);
    }

    private int updateSearchTermFromAPI(ContentValues values, String selection,
            String[] selectionArgs) {
        SearchRow row = SearchRow.fromContentValues(values);
        return nativeUpdateSearchTermFromAPI(mNativeChromeBrowserProvider,
                row.mTerm, row.mDate, selection, selectionArgs);
    }

    private Cursor querySearchTermFromAPI(String[] projectionIn, String selection,
            String[] selectionArgs, String sortOrder) {
        String[] projection = null;
        if (projectionIn == null || projectionIn.length == 0) {
            projection = SEARCHES_PROJECTION;
        } else {
            projection = projectionIn;
        }
        return nativeQuerySearchTermFromAPI(mNativeChromeBrowserProvider, projection, selection,
                selectionArgs, sortOrder);
    }

    private int removeSearchFromAPI(String selection, String[] selectionArgs) {
        return nativeRemoveSearchTermFromAPI(mNativeChromeBrowserProvider,
                selection, selectionArgs);
    }

    private static boolean isInUiThread() {
        if (!ThreadUtils.runningOnUiThread()) return false;

        if (!"REL".equals(Build.VERSION.CODENAME)) {
            throw new IllegalStateException("Shouldn't run in the UI thread");
        }

        Log.w(TAG, "ChromeBrowserProvider methods cannot be called from the UI thread.");
        return true;
    }

    private static Uri buildContentUri(String authority, String path) {
        return Uri.parse("content://" + authority + "/" + path);
    }

    private static Uri buildAPIContentUri(Context context, String path) {
        return buildContentUri(context.getPackageName() + API_AUTHORITY_SUFFIX, path);
    }

    private static String buildWhereClause(long id, String selection) {
        StringBuilder sb = new StringBuilder();
        sb.append(BaseColumns._ID);
        sb.append(" = ");
        sb.append(id);
        if (!TextUtils.isEmpty(selection)) {
            sb.append(" AND (");
            sb.append(selection);
            sb.append(")");
        }
        return sb.toString();
    }

    private static String buildHistoryWhereClause(long id, String selection) {
        return buildWhereClause(id, buildBookmarkWhereClause(selection, false));
    }

    private static String buildHistoryWhereClause(String selection) {
        return buildBookmarkWhereClause(selection, false);
    }

    /**
     * @return a SQL where class which is inserted the bookmark condition.
     */
    private static String buildBookmarkWhereClause(String selection, boolean isBookmark) {
        StringBuilder sb = new StringBuilder();
        sb.append(BookmarkColumns.BOOKMARK);
        sb.append(isBookmark ? " = 1 " : " = 0");
        if (!TextUtils.isEmpty(selection)) {
            sb.append(" AND (");
            sb.append(selection);
            sb.append(")");
        }
        return sb.toString();
    }

    private static String buildBookmarkWhereClause(long id, String selection) {
        return buildWhereClause(id, buildBookmarkWhereClause(selection, true));
    }

    private static String buildBookmarkWhereClause(String selection) {
        return buildBookmarkWhereClause(selection, true);
    }

    // Wrap the value of BookmarkColumn.
    private static class BookmarkRow {
        Boolean mIsBookmark;
        Long mCreated;
        String mUrl;
        Long mDate;
        byte[] mFavicon;
        String mTitle;
        Integer mVisits;
        long mParentId;

        static BookmarkRow fromContentValues(ContentValues values) {
            BookmarkRow row = new BookmarkRow();
            if (values.containsKey(BookmarkColumns.URL)) {
                row.mUrl = values.getAsString(BookmarkColumns.URL);
            }
            if (values.containsKey(BookmarkColumns.BOOKMARK)) {
                row.mIsBookmark = values.getAsInteger(BookmarkColumns.BOOKMARK) != 0;
            }
            if (values.containsKey(BookmarkColumns.CREATED)) {
                row.mCreated = values.getAsLong(BookmarkColumns.CREATED);
            }
            if (values.containsKey(BookmarkColumns.DATE)) {
                row.mDate = values.getAsLong(BookmarkColumns.DATE);
            }
            if (values.containsKey(BookmarkColumns.FAVICON)) {
                row.mFavicon = values.getAsByteArray(BookmarkColumns.FAVICON);
                // We need to know that the caller set the favicon column.
                if (row.mFavicon == null) {
                    row.mFavicon = new byte[0];
                }
            }
            if (values.containsKey(BookmarkColumns.TITLE)) {
                row.mTitle = values.getAsString(BookmarkColumns.TITLE);
            }
            if (values.containsKey(BookmarkColumns.VISITS)) {
                row.mVisits = values.getAsInteger(BookmarkColumns.VISITS);
            }
            if (values.containsKey(BOOKMARK_PARENT_ID_PARAM)) {
                row.mParentId = values.getAsLong(BOOKMARK_PARENT_ID_PARAM);
            }
            return row;
        }
    }

    // Wrap the value of SearchColumn.
    private static class SearchRow {
        String mTerm;
        Long mDate;

        static SearchRow fromContentValues(ContentValues values) {
            SearchRow row = new SearchRow();
            if (values.containsKey(SearchColumns.SEARCH)) {
                row.mTerm = values.getAsString(SearchColumns.SEARCH);
            }
            if (values.containsKey(SearchColumns.DATE)) {
                row.mDate = values.getAsLong(SearchColumns.DATE);
            }
            return row;
        }
    }

    /**
     * Initialize native side if it hasn't been already initialized.
     * This is called from BrowserStartupCallback during normal startup except when called
     * through one of the public ContentProvider APIs.
     */
    private void ensureNativeSideInitialized() {
        ThreadUtils.assertOnUiThread();
        if (mNativeChromeBrowserProvider == 0) mNativeChromeBrowserProvider = nativeInit();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            // Per Object#finalize(), finalizers are run on a single VM-wide finalizer thread, and
            // the native objects need to be destroyed on the UI thread.
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ensureNativeChromeDestroyedOnUIThread();
                }
            });
        } finally {
            super.finalize();
        }
    }

    /**
     * This method should only run on UI thread.
     */
    private void ensureNativeChromeDestroyedOnUIThread() {
        if (mNativeChromeBrowserProvider != 0) {
            nativeDestroy(mNativeChromeBrowserProvider);
            mNativeChromeBrowserProvider = 0;
        }
    }

    @SuppressLint("NewApi")
    private void notifyChange(final Uri uri) {
        // If the calling user is different than current one, we need to post a
        // task to notify change, otherwise, a system level hidden permission
        // INTERACT_ACROSS_USERS_FULL is needed.
        // The related APIs were added in API 17, it should be safe to fallback to
        // normal way for notifying change, because caller can't be other users in
        // devices whose API level is less than API 17.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            UserHandle callingUserHandle = Binder.getCallingUserHandle();
            if (callingUserHandle != null
                    && !callingUserHandle.equals(android.os.Process.myUserHandle())) {
                ThreadUtils.postOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getContext().getContentResolver().notifyChange(uri, null);
                    }
                });
                return;
            }
        }
        getContext().getContentResolver().notifyChange(uri, null);
    }

    private boolean hasPermission(String permission) {
        boolean isSystemOrGoogleCaller = ExternalAuthUtils.getInstance().isCallerValid(
                getContext(), ExternalAuthUtils.FLAG_SHOULD_BE_GOOGLE_SIGNED
                        | ExternalAuthUtils.FLAG_SHOULD_BE_SYSTEM);
        if (isSystemOrGoogleCaller) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().checkCallingOrSelfPermission(
                    getReadWritePermissionNameForBookmarkFolders())
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return getContext().checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeChromeBrowserProvider);

    // Public API native methods.
    private native long nativeAddBookmark(long nativeChromeBrowserProvider,
            String url, String title, boolean isFolder, long parentId);

    private native int nativeRemoveBookmark(long nativeChromeBrowserProvider, long id);

    private native int nativeUpdateBookmark(long nativeChromeBrowserProvider,
            long id, String url, String title, long parentId);

    private native long nativeAddBookmarkFromAPI(long nativeChromeBrowserProvider,
            String url, Long created, Boolean isBookmark, Long date, byte[] favicon,
            String title, Integer visits, long parentId);

    private native SQLiteCursor nativeQueryBookmarkFromAPI(long nativeChromeBrowserProvider,
            String[] projection, String selection, String[] selectionArgs, String sortOrder);

    private native int nativeUpdateBookmarkFromAPI(long nativeChromeBrowserProvider,
            String url, Long created, Boolean isBookmark, Long date, byte[] favicon,
            String title, Integer visits, long parentId, String selection, String[] selectionArgs);

    private native int nativeRemoveBookmarkFromAPI(long nativeChromeBrowserProvider,
            String selection, String[] selectionArgs);

    private native int nativeRemoveHistoryFromAPI(long nativeChromeBrowserProvider,
            String selection, String[] selectionArgs);

    private native long nativeAddSearchTermFromAPI(long nativeChromeBrowserProvider,
            String term, Long date);

    private native SQLiteCursor nativeQuerySearchTermFromAPI(long nativeChromeBrowserProvider,
            String[] projection, String selection, String[] selectionArgs, String sortOrder);

    private native int nativeUpdateSearchTermFromAPI(long nativeChromeBrowserProvider,
            String search, Long date, String selection, String[] selectionArgs);

    private native int nativeRemoveSearchTermFromAPI(long nativeChromeBrowserProvider,
            String selection, String[] selectionArgs);
}
