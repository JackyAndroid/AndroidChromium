// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import org.chromium.chrome.browser.ChromeBrowserProvider.BookmarkNode;

import java.io.Serializable;

/**
 * Exposes the custom API methods for ChromeBrowserProvider.
 */
public class ChromeBrowserProviderClient {
    private static final String TAG = "ChromeBrowserProviderClient";

    // Returned by some of the methods in this class.
    public static final long INVALID_BOOKMARK_ID = ChromeBrowserProvider.INVALID_BOOKMARK_ID;

    // Flags used with getBookmarkNode.
    /** Retrieve the node corresponding to the id provided in getBookmarkNode. */
    public static final int GET_NODE = 0x00000000;

    /** Retrieve the parent of the node requested in getBookmarkNode. */
    public static final int GET_PARENT = 0x00000001;

    /** Retrieve the immediate children of the node requested in getBookmarkNode. */
    public static final int GET_CHILDREN = 0x00000002;

    /** Retrieve the favicon or touch icon, if any, in all the nodes returned by getBookmarkNode. */
    public static final int GET_FAVICONS = 0x00000004;

    /** Retrieve the thumbnail, if any, in all the nodes returned by getBookmarkNode. */
    public static final int GET_THUMBNAILS = 0x00000008;

    /**
     * Verifies if a bookmark node given by its ID exists in the bookmark model.
     *
     * @return True if the provided bookmark node exists in the bookmark model.
     */
    public static boolean bookmarkNodeExists(Context context, long nodeId) {
        Boolean result = chromeBrowserProviderCall(Boolean.class,
                ChromeBrowserProvider.CLIENT_API_BOOKMARK_NODE_EXISTS,
                context, argsToBundle(nodeId));
        return result != null ? result.booleanValue() : false;
    }

    /**
     * Creates a bookmark folder or returns its ID if it already exists.
     * This method does not update the last modified folder in the UI.
     *
     * @param title Title of the new or existing bookmark folder.
     * @param parentId ID of the parent folder. Must be in the Mobile Bookmarks branch.
     * @return The ID of the new created folder (or INVALID_BOOKMARK_ID on error).
     *         Will return the ID of any existing folder in the same parent with the same name.
     */
    public static long createBookmarksFolderOnce(Context context, String title, long parentId) {
        Long id = chromeBrowserProviderCall(Long.class,
                ChromeBrowserProvider.CLIENT_API_CREATE_BOOKMARKS_FOLDER_ONCE, context,
                argsToBundle(title, parentId));
        return id != null ? id.longValue() : INVALID_BOOKMARK_ID;
    }

    /**
     * Retrieves the bookmark folder hierarchy of editable nodes, returning its root node.
     *
     * @return The root node of the bookmark folder hierarchy with all its descendant folders
     *         that are editable by the user, populated or null in case of error.
     *         Note that only folders are returned.
     */
    public static BookmarkNode getEditableBookmarkFolderHierarchy(Context context) {
        return chromeBrowserProviderCall(BookmarkNode.class,
                ChromeBrowserProvider.CLIENT_API_GET_EDITABLE_BOOKMARK_FOLDER_HIERARCHY, context,
                argsToBundle());
    }

    /**
     * Removes all bookmarks and bookmark folders that the user can edit.
     * Only the permanent bookmark folders remain after this operation, and any managed bookmarks.
     */
    public static void removeAllUserBookmarks(Context context) {
        chromeBrowserProviderCall(BookmarkNode.class,
                ChromeBrowserProvider.CLIENT_API_DELETE_ALL_USER_BOOKMARKS, context,
                argsToBundle());
    }

    /**
     * Retrieves a bookmark node given its ID or null if no such node exists.
     * The parent and immediate child nodes can be also retrieved by enabling the getParent
     * and getChildren flags. No deeper child nodes can be retrieved with this method.
     *
     * @param nodeId The ID of the bookmark node to be retrieved.
     * @param flags Combination of constants telling what information of the node is required.
     * @return The bookmark node corresponding to the provided ID.
     */
    public static BookmarkNode getBookmarkNode(Context context, long nodeId, int flags) {
        return chromeBrowserProviderCall(BookmarkNode.class,
                ChromeBrowserProvider.CLIENT_API_GET_BOOKMARK_NODE, context,
                argsToBundle(nodeId,
                        (flags & GET_PARENT) != 0,
                        (flags & GET_CHILDREN) != 0,
                        (flags & GET_FAVICONS) != 0,
                        (flags & GET_THUMBNAILS) != 0));
    }

    /**
     * Retrieves the current default folder for UI based bookmark operations.
     * The result depends on where the last successful bookmark operation was performed by the user.
     *
     * @return The default bookmark folder for new bookmarks or null in case of error.
     *         No parent or children are populated in the returned node.
     */
    public static BookmarkNode getDefaultBookmarkFolder(Context context) {
        return chromeBrowserProviderCall(BookmarkNode.class,
                ChromeBrowserProvider.CLIENT_API_GET_DEFAULT_BOOKMARK_FOLDER, context,
                argsToBundle());
    }

    /**
     * Returns the ID of the Mobile Bookmarks folder.
     *
     * @return The ID of the Mobile Bookmarks folder or INVALID_BOOKMARK_ID in case of error.
     */
    public static long getMobileBookmarksFolderId(Context context) {
        Long id = chromeBrowserProviderCall(Long.class,
                ChromeBrowserProvider.CLIENT_API_GET_MOBILE_BOOKMARKS_FOLDER_ID, context,
                argsToBundle());
        return id != null ? id.longValue() : INVALID_BOOKMARK_ID;
    }

    /**
     * Checks if a bookmark node is in the Mobile Bookmarks folder branch.
     *
     * @return True if the ID belongs to a node in the Mobile Bookmarks folder branch.
     */
    public static boolean isBookmarkInMobileBookmarksBranch(Context context, long nodeId) {
        Boolean result = chromeBrowserProviderCall(Boolean.class,
                ChromeBrowserProvider.CLIENT_API_IS_BOOKMARK_IN_MOBILE_BOOKMARKS_BRANCH, context,
                argsToBundle(nodeId));
        return result != null ? result.booleanValue() : false;
    }

    // --------------------- End of the client API --------------------- //

    private static Uri getPrivateProviderUri(Context context) {
        // The Bookmarks Uri uses the private provider authority.
        return ChromeBrowserProvider.getBookmarksUri(context);
    }


    private static Bundle argsToBundle(Object ... args) {
        Bundle methodArgs = new Bundle();
        for (int i = 0; i < args.length; ++i) {
            Class<? extends Object> argClass = args[i].getClass();
            if (Parcelable.class.isAssignableFrom(argClass)) {
                methodArgs.putParcelable(ChromeBrowserProvider.argKey(i), (Parcelable) args[i]);
            } else if (Serializable.class.isAssignableFrom(argClass)) {
                methodArgs.putSerializable(ChromeBrowserProvider.argKey(i), (Serializable) args[i]);
            } else {
                Log.e(TAG, "Argument implements neither Parcelable nor Serializable.");
                return null;
            }
        }
        return methodArgs;
    }

    private static <T> T chromeBrowserProviderCall(Class<T> returnType, String name,
            Context context, Bundle args) {
        android.util.Log.i(TAG, "before executing " + name + " call");
        Bundle result = context.getContentResolver().call(getPrivateProviderUri(context),
                name, null, args);
        android.util.Log.i(TAG, "after executing " + name + " call");
        if (result == null) return null;
        if (Parcelable.class.isAssignableFrom(returnType)) {
            return returnType.cast(
                    result.getParcelable(ChromeBrowserProvider.CLIENT_API_RESULT_KEY));
        } else {
            return returnType.cast(result.get(ChromeBrowserProvider.CLIENT_API_RESULT_KEY));
        }
    }
}
