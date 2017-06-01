// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;

import java.util.List;

/**
 * An inner node in the tree: the root of a subtree, with a list of child nodes.
 */
public abstract class InnerNode extends ChildNode implements NodeParent {
    public InnerNode(NodeParent parent) {
        super(parent);
    }

    protected abstract List<TreeNode> getChildren();

    private int getChildIndexForPosition(int position) {
        List<TreeNode> children = getChildren();
        int numItems = 0;
        int numChildren = children.size();
        for (int i = 0; i < numChildren; i++) {
            numItems += children.get(i).getItemCount();
            if (position < numItems) return i;
        }
        return -1;
    }

    private int getStartingOffsetForChildIndex(int childIndex) {
        List<TreeNode> children = getChildren();
        if (childIndex < 0 || childIndex >= children.size()) {
            throw new IndexOutOfBoundsException(childIndex + "/" + children.size());
        }

        int offset = 0;
        for (int i = 0; i < childIndex; i++) {
            offset += children.get(i).getItemCount();
        }
        return offset;
    }

    int getStartingOffsetForChild(TreeNode child) {
        return getStartingOffsetForChildIndex(getChildren().indexOf(child));
    }

    /**
     * Returns the child whose subtree contains the item at the given position.
     */
    TreeNode getChildForPosition(int position) {
        return getChildren().get(getChildIndexForPosition(position));
    }

    @Override
    public int getItemCount() {
        int numItems = 0;
        for (TreeNode child : getChildren()) {
            numItems += child.getItemCount();
        }
        return numItems;
    }

    @Override
    @ItemViewType
    public int getItemViewType(int position) {
        int index = getChildIndexForPosition(position);
        return getChildren().get(index).getItemViewType(
                position - getStartingOffsetForChildIndex(index));
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder, int position) {
        int index = getChildIndexForPosition(position);
        getChildren().get(index).onBindViewHolder(
                holder, position - getStartingOffsetForChildIndex(index));
    }

    @Override
    public SnippetArticle getSuggestionAt(int position) {
        int index = getChildIndexForPosition(position);
        return getChildren().get(index).getSuggestionAt(
                position - getStartingOffsetForChildIndex(index));
    }

    @Override
    public int getDismissSiblingPosDelta(int position) {
        int index = getChildIndexForPosition(position);
        return getChildren().get(index).getDismissSiblingPosDelta(
                position - getStartingOffsetForChildIndex(index));
    }

    @Override
    public void onItemRangeChanged(TreeNode child, int index, int count) {
        notifyItemRangeChanged(getStartingOffsetForChild(child) + index, count);
    }

    @Override
    public void onItemRangeInserted(TreeNode child, int index, int count) {
        notifyItemRangeInserted(getStartingOffsetForChild(child) + index, count);
    }

    @Override
    public void onItemRangeRemoved(TreeNode child, int index, int count) {
        notifyItemRangeRemoved(getStartingOffsetForChild(child) + index, count);
    }
}
