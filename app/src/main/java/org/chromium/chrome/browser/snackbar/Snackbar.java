// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.graphics.Bitmap;

import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;

/**
 * A snackbar shows a message at the bottom of the screen and optionally contains an action button.
 * To show a snackbar, create the snackbar using {@link #make}, configure it using the various
 * set*() methods, and show it using {@link SnackbarManager#showSnackbar(Snackbar)}. Example:
 *
 *   SnackbarManager.showSnackbar(
 *           Snackbar.make("Closed example.com", controller, Snackbar.UMA_TAB_CLOSE_UNDO)
 *           .setAction("undo", actionData));
 */
public class Snackbar {
    /**
     * Snackbars that are created as an immediate response to user's action. These snackbars are
     * managed in a stack and will be swiped away altogether after timeout.
     */
    public static final int TYPE_ACTION = 0;

    /**
     * Snackbars that are for notification purposes. These snackbars are stored in a queue and thus
     * are of lower priority, compared to {@link #TYPE_ACTION}. Notification snackbars are dismissed
     * one by one.
     */
    public static final int TYPE_NOTIFICATION = 1;

    /**
     * UMA Identifiers of features using snackbar. See SnackbarIdentifier enum in histograms.
     */
    public static final int UMA_TEST_SNACKBAR = -2;
    public static final int UMA_UNKNOWN = -1;
    public static final int UMA_BOOKMARK_ADDED = 0;
    public static final int UMA_BOOKMARK_DELETE_UNDO = 1;
    public static final int UMA_NTP_MOST_VISITED_DELETE_UNDO = 2;
    public static final int UMA_OFFLINE_PAGE_RELOAD = 3;
    public static final int UMA_AUTO_LOGIN = 4;
    public static final int UMA_OMNIBOX_GEOLOCATION = 5;
    public static final int UMA_LOFI = 6;
    public static final int UMA_DATA_USE_STARTED = 7;
    public static final int UMA_DATA_USE_ENDED = 8;
    public static final int UMA_DOWNLOAD_SUCCEEDED = 9;
    public static final int UMA_DOWNLOAD_FAILED = 10;
    public static final int UMA_TAB_CLOSE_UNDO = 11;
    public static final int UMA_TAB_CLOSE_ALL_UNDO = 12;
    public static final int UMA_DOWNLOAD_DELETE_UNDO = 13;
    public static final int UMA_SPECIAL_LOCALE = 14;
    public static final int UMA_BLIMP = 15;
    public static final int UMA_DATA_REDUCTION_PROMO = 16;

    private SnackbarController mController;
    private CharSequence mText;
    private String mTemplateText;
    private String mActionText;
    private Object mActionData;
    private int mBackgroundColor;
    private boolean mSingleLine = true;
    private int mDurationMs;
    private Bitmap mProfileImage;
    private int mType;
    private int mIdentifier = UMA_UNKNOWN;

    // Prevent instantiation.
    private Snackbar() {}

    /**
     * Creates and returns a snackbar to display the given text. If this is a snackbar for a new
     * feature shown to the user, please add the feature name to SnackbarIdentifier in histograms.
     *
     * @param text The text to show on the snackbar.
     * @param controller The SnackbarController to receive callbacks about the snackbar's state.
     * @param type Type of the snackbar. Either {@link #TYPE_ACTION} or {@link #TYPE_NOTIFICATION}.
     * @param identifier The feature code of the snackbar. Should be one of the UMA* constants above
     */
    public static Snackbar make(CharSequence text, SnackbarController controller, int type,
            int identifier) {
        Snackbar s = new Snackbar();
        s.mText = text;
        s.mController = controller;
        s.mType = type;
        s.mIdentifier = identifier;
        return s;
    }

    /**
     * Sets the template text to show on the snackbar, e.g. "Closed %s". See
     * {@link TemplatePreservingTextView} for details on how the template text is used.
     */
    public Snackbar setTemplateText(String templateText) {
        mTemplateText = templateText;
        return this;
    }

    /**
     * Sets the action button to show on the snackbar.
     * @param actionText The text to show on the button. If null, the button will not be shown.
     * @param actionData An object to be passed to {@link SnackbarController#onAction} or
     *        {@link SnackbarController#onDismissNoAction} when the button is pressed or the
     *        snackbar is dismissed.
     */
    public Snackbar setAction(String actionText, Object actionData) {
        mActionText = actionText;
        mActionData = actionData;
        return this;
    }

    /**
     * Sets the identity profile image that will be displayed at the beginning of the snackbar.
     * If null, there won't be a profile image. The ability to have an icon is exclusive to
     * identity snackbars.
     */
    public Snackbar setProfileImage(Bitmap profileImage) {
        mProfileImage = profileImage;
        return this;
    }

    /**
     * Sets whether the snackbar text should be limited to a single line and ellipsized if needed.
     */
    public Snackbar setSingleLine(boolean singleLine) {
        mSingleLine = singleLine;
        return this;
    }

    /**
     * Sets the number of milliseconds that the snackbar will appear for. If 0, the snackbar will
     * use the default duration.
     */
    public Snackbar setDuration(int durationMs) {
        mDurationMs = durationMs;
        return this;
    }

    /**
     * Sets the background color for the snackbar. If 0, the snackbar will use default color.
     */
    public Snackbar setBackgroundColor(int color) {
        mBackgroundColor = color;
        return this;
    }

    SnackbarController getController() {
        return mController;
    }

    CharSequence getText() {
        return mText;
    }

    String getTemplateText() {
        return mTemplateText;
    }

    String getActionText() {
        return mActionText;
    }

    Object getActionData() {
        return mActionData;
    }

    boolean getSingleLine() {
        return mSingleLine;
    }

    int getDuration() {
        return mDurationMs;
    }

    int getIdentifier() {
        return mIdentifier;
    }

    /**
     * If method returns zero, then default color for snackbar will be used.
     */
    int getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * If method returns null, then no profileImage will be shown in snackbar.
     */
    Bitmap getProfileImage() {
        return mProfileImage;
    }

    /**
     * @return Whether the snackbar is of {@link #TYPE_ACTION}.
     */
    boolean isTypeAction() {
        return mType == TYPE_ACTION;
    }
}
