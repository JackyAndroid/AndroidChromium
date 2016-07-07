// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;

/**
 * A snackbar shows a message at the bottom of the screen and optionally contains an action button.
 * To show a snackbar, create the snackbar using {@link #make}, configure it using the various
 * set*() methods, and show it using {@link SnackbarManager#showSnackbar(Snackbar)}. Example:
 *
 *   SnackbarManager.showSnackbar(
 *           Snackbar.make("Closed example.com", controller)
 *           .setAction("undo", actionData)
 *           .setDuration(3000));
 */
public class Snackbar {

    private SnackbarController mController;
    private CharSequence mText;
    private String mTemplateText;
    private String mActionText;
    private Object mActionData;
    private int mBackgroundColor;
    private boolean mSingleLine = true;
    private int mDurationMs;

    // Prevent instantiation.
    private Snackbar() {}

    /**
     * Creates and returns a snackbar to display the given text.
     * @param text The text to show on the snackbar.
     * @param controller The SnackbarController to receive callbacks about the snackbar's state.
     */
    public static Snackbar make(CharSequence text, SnackbarController controller) {
        Snackbar s = new Snackbar();
        s.mText = text;
        s.mController = controller;
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
     * @param actionData An object to be passed to {@linkSnackbarController#onAction} or
     *        {@link SnackbarController#onDismissNoAction} when the button is pressed or the
     *        snackbar is dismissed.
     */
    public Snackbar setAction(String actionText, Object actionData) {
        mActionText = actionText;
        mActionData = actionData;
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

    /**
     * If method returns zero, then default color for snackbar will be used.
     */
    int getBackgroundColor() {
        return mBackgroundColor;
    }
}
