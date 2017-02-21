// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A data structure that holds all the {@link Snackbar}s managed by {@link SnackbarManager}.
 */
class SnackbarCollection {
    private Deque<Snackbar> mSnackbars = new LinkedList<>();

    /**
     * Adds a new snackbar to the collection. If the new snackbar is of
     * {@link Snackbar#TYPE_ACTION} and current snackbar is of
     * {@link Snackbar#TYPE_NOTIFICATION}, the current snackbar will be removed from the
     * collection immediately.
     */
    void add(Snackbar snackbar) {
        if (snackbar.isTypeAction()) {
            if (getCurrent() != null && !getCurrent().isTypeAction()) {
                removeCurrent(false);
            }
            mSnackbars.addFirst(snackbar);
        } else {
            mSnackbars.addLast(snackbar);
        }
    }

    /**
     * Removes the current snackbar from the collection after the user has clicked on the action
     * button.
     */
    Snackbar removeCurrentDueToAction() {
        return removeCurrent(true);
    }

    private Snackbar removeCurrent(boolean isAction) {
        Snackbar current = mSnackbars.pollFirst();
        if (current != null) {
            SnackbarController controller = current.getController();
            if (isAction) controller.onAction(current.getActionData());
            else controller.onDismissNoAction(current.getActionData());
        }
        return current;
    }

    /**
     * @return The snackbar that is currently displayed.
     */
    Snackbar getCurrent() {
        return mSnackbars.peekFirst();
    }

    boolean isEmpty() {
        return mSnackbars.isEmpty();
    }

    void clear() {
        while (!isEmpty()) {
            removeCurrent(false);
        }
    }

    void removeCurrentDueToTimeout() {
        removeCurrent(false);
        Snackbar current;
        while ((current = getCurrent()) != null && current.isTypeAction()) {
            removeCurrent(false);
        }
    }

    boolean removeMatchingSnackbars(SnackbarController controller) {
        boolean snackbarRemoved = false;
        Iterator<Snackbar> iter = mSnackbars.iterator();
        while (iter.hasNext()) {
            Snackbar snackbar = iter.next();
            if (snackbar.getController() == controller) {
                iter.remove();
                controller.onDismissNoAction(snackbar.getActionData());
                snackbarRemoved = true;
            }
        }
        return snackbarRemoved;
    }

    boolean removeMatchingSnackbars(SnackbarController controller, Object data) {
        boolean snackbarRemoved = false;
        Iterator<Snackbar> iter = mSnackbars.iterator();
        while (iter.hasNext()) {
            Snackbar snackbar = iter.next();
            if (snackbar.getController() == controller
                    && objectsAreEqual(snackbar.getActionData(), data)) {
                iter.remove();
                controller.onDismissNoAction(data);
                snackbarRemoved = true;
            }
        }
        return snackbarRemoved;
    }

    private static boolean objectsAreEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}