// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

/**
 * Interface to allow propagating change events upwards in the tree.
 */
public interface NodeParent {
    /**
     * Notifies that {@code count} items starting at position {@code index} under the {@code child}
     * have changed.
     * @param child The child whose items have changed.
     * @param index The starting position of the range of changed items, relative to the
     *         {@code child}.
     * @param count The number of changed items.
     * @see android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeChanged(int, int)
     */
    void onItemRangeChanged(TreeNode child, int index, int count);

    /**
     * Notifies that {@code count} items starting at position {@code index} under the {@code child}
     * have been added.
     * @param child The child to which items have been added.
     * @param index The starting position of the range of added items, relative to the child.
     * @param count The number of added items.
     * @see android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeInserted(int, int)
     */
    void onItemRangeInserted(TreeNode child, int index, int count);

    /**
     * Notifies that {@code count} items starting at position {@code index} under the {@code child}
     * have been removed.
     * @param child The child from which items have been removed.
     * @param index The starting position of the range of removed items, relative to the child.
     * @param count The number of removed items.
     * @see android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeRemoved(int, int)
     */
    void onItemRangeRemoved(TreeNode child, int index, int count);
}
