// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

/**
 * A node in the tree that has a parent and can notify it about changes.
 *
 * This class mostly serves as a convenience base class for implementations of {@link TreeNode}.
 */
public abstract class ChildNode implements TreeNode {
    private final NodeParent mParent;

    protected ChildNode(NodeParent parent) {
        mParent = parent;
    }

    protected void notifyItemRangeChanged(int index, int count) {
        mParent.onItemRangeChanged(this, index, count);
    }

    protected void notifyItemRangeInserted(int index, int count) {
        mParent.onItemRangeInserted(this, index, count);
    }

    protected void notifyItemRangeRemoved(int index, int count) {
        mParent.onItemRangeRemoved(this, index, count);
    }

    protected void notifyItemChanged(int index) {
        notifyItemRangeChanged(index, 1);
    }

    protected void notifyItemInserted(int index) {
        notifyItemRangeInserted(index, 1);
    }

    protected void notifyItemRemoved(int index) {
        notifyItemRangeRemoved(index, 1);
    }
}
