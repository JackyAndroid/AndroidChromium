// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Checkable;
import android.widget.FrameLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;

import java.util.List;

/**
 * An item that can be selected. When selected, the item will be highlighted. A selection is
 * initially established via long-press. If a selection is already established, clicking on the item
 * will toggle its selection.
 *
 * @param <E> The type of the item associated with this SelectableItemView.
 */
public abstract class SelectableItemView<E> extends FrameLayout implements Checkable,
        OnClickListener, OnLongClickListener, SelectionObserver<E> {
    private SelectionDelegate<E> mSelectionDelegate;
    private SelectableItemHighlightView mHighlightView;
    private E mItem;

    /**
     * Constructor for inflating from XML.
     */
    public SelectableItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Destroys and cleans up itself.
     */
    public void destroy() {
        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
    }

    /**
     * Sets the SelectionDelegate and registers this object as an observer. The SelectionDelegate
     * must be set before the item can respond to click events.
     * @param delegate The SelectionDelegate that will inform this item of selection changes.
     */
    public void setSelectionDelegate(SelectionDelegate<E> delegate) {
        if (mSelectionDelegate != delegate) {
            if (mSelectionDelegate != null) mSelectionDelegate.removeObserver(this);
            mSelectionDelegate = delegate;
            mSelectionDelegate.addObserver(this);
        }
    }

    /**
     * @param item The item associated with this SelectableItemView.
     */
    public void setItem(E item) {
        mItem = item;
        setChecked(mSelectionDelegate.isItemSelected(item));
    }

    // FrameLayout implementations.
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        inflate(getContext(), R.layout.selectable_item_highlight_view, this);
        mHighlightView = (SelectableItemHighlightView) findViewById(R.id.highlight);

        setOnClickListener(this);
        setOnLongClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelectionDelegate != null) {
            setChecked(mSelectionDelegate.isItemSelected(mItem));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setChecked(false);
    }

    // OnClickListener implementation.
    @Override
    public final void onClick(View view) {
        assert view == this;

        if (mSelectionDelegate.isSelectionEnabled()) {
            onLongClick(view);
        }  else {
            onClick();
        }
    }

    // OnLongClickListener implementation.
    @Override
    public boolean onLongClick(View view) {
        assert view == this;
        boolean checked = mSelectionDelegate.toggleSelectionForItem(mItem);
        setChecked(checked);
        return true;
    }

    // Checkable implementations.
    @Override
    public boolean isChecked() {
        return mHighlightView.isChecked();
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        mHighlightView.setChecked(checked);
    }

    // SelectionObserver implementation.
    @Override
    public void onSelectionStateChange(List<E> selectedItems) {
        setChecked(mSelectionDelegate.isItemSelected(mItem));
    }

    /**
     * Same as {@link OnClickListener#onClick(View)} on this.
     * Subclasses should override this instead of setting their own OnClickListener because this
     * class handles onClick events in selection mode, and won't forward events to subclasses in
     * that case.
     */
    protected abstract void onClick();
}
