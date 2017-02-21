// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import org.chromium.base.ObserverList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A generic delegate used to keep track of selected items.
 * @param <E> The type of the selectable items this delegate interacts with.
 */
public class SelectionDelegate<E> {

    /**
     * Observer interface to be notified of selection changes.
     */
    public interface SelectionObserver<E> {
        /**
         * Called when the set of selected items has changed.
         * @param selectedItems The list of currently selected items. An empty list indicates there
         *                      is no selection.
         */
        public void onSelectionStateChange(List<E> selectedItems);
    }

    private Set<E> mSelectedItems = new HashSet<>();
    private ObserverList<SelectionObserver<E>> mObservers = new ObserverList<>();

    /**
     * Toggles the selected state for the given item.
     * @param item The item to toggle.
     * @return Whether the item is selected.
     */
    public boolean toggleSelectionForItem(E item) {
        if (mSelectedItems.contains(item)) mSelectedItems.remove(item);
        else mSelectedItems.add(item);

        notifyObservers();

        return isItemSelected(item);
    }

    /**
     * True if the item is selected. False otherwise.
     * @param item The item.
     * @return Whether the item is selected.
     */
    public boolean isItemSelected(E item) {
        return mSelectedItems.contains(item);
    }

    /**
     * @return Whether any items are selected.
     */
    public boolean isSelectionEnabled() {
        return !mSelectedItems.isEmpty();
    }

   /**
    * Clears all selected items.
    */
    public void clearSelection() {
        mSelectedItems.clear();
        notifyObservers();
    }

    /**
     * @return The list of selected items.
     */
    public List<E> getSelectedItems() {
        return new ArrayList<E>(mSelectedItems);
    }

    /**
     * Adds a SelectionObserver to be notified of selection changes.
     * @param observer The SelectionObserver to add.
     */
    public void addObserver(SelectionObserver<E> observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes a SelectionObserver.
     * @param observer The SelectionObserver to remove.
     */
    public void removeObserver(SelectionObserver<E> observer) {
        mObservers.removeObserver(observer);
    }

    private void notifyObservers() {
        List<E> selectedItems = getSelectedItems();
        for (SelectionObserver<E> observer : mObservers) {
            observer.onSelectionStateChange(selectedItems);
        }
    }

}
