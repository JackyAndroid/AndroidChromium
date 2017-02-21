// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import android.view.ViewGroup;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * Used to decide which panel should be showing on screen at any moment.
 */
public class OverlayPanelManager {

    /**
     * Priority of an OverlayPanel; used for deciding which panel will be shown when there are
     * multiple candidates.
     */
    public static enum PanelPriority {
        LOW,
        MEDIUM,
        HIGH;
    }

    /** The initial size of the priority queue for suppressed panels. */
    private static final int INITIAL_QUEUE_CAPACITY = 3;

    /** A map of panels that this class is managing. */
    private final Set<OverlayPanel> mPanelSet;

    /** The panel that is currently being displayed. */
    private OverlayPanel mActivePanel;

    /**
     * If a panel was being shown and another panel with higher priority was requested to show,
     * the lower priority one is stored here.
     */
    private Queue<OverlayPanel> mSuppressedPanels;

    /** When a panel is suppressed, this is the panel waiting for the close animation to finish. */
    private OverlayPanel mPendingPanel;

    /** When a panel is suppressed, this the reason the pending panel is to be shown. */
    private StateChangeReason mPendingReason;

    /** This handles resource loading for each panels. */
    private DynamicResourceLoader mDynamicResourceLoader;

    /** This is the view group that all views related to the panel will be put into. */
    private ViewGroup mContainerViewGroup;

    /**
     * Default constructor.
     */
    public OverlayPanelManager() {
        mSuppressedPanels = new PriorityQueue<>(INITIAL_QUEUE_CAPACITY,
                new Comparator<OverlayPanel>() {
                    @Override
                    public int compare(OverlayPanel p1, OverlayPanel p2) {
                        // The head of the queue is the smallest element, so subtract p1's priority
                        // from p2's priority.
                        return p2.getPriority().ordinal() - p1.getPriority().ordinal();
                    }
                });
        mPanelSet = new HashSet<>();
    }

    /**
     * Request that a panel with the specified ID be shown. This does not necessarily mean the
     * panel will be shown.
     * @param panel The panel to show.
     * @param reason The reason the panel is going to be shown.
     */
    public void requestPanelShow(OverlayPanel panel, StateChangeReason reason) {
        if (panel == null || panel == mActivePanel) return;

        if (mActivePanel == null) {
            // If no panel is currently showing, simply show the requesting panel.
            mActivePanel = panel;
            // TODO(mdjones): peekPanel should not be exposed publicly since the manager
            // controls if a panel should show or not.
            mActivePanel.peekPanel(reason);

        } else if (panel.getPriority().ordinal() > mActivePanel.getPriority().ordinal()) {
            // If a panel with higher priority than the active one requests to be shown, suppress
            // the active panel and show the requesting one. closePanel will trigger
            // notifyPanelClosed.
            mPendingPanel = panel;
            mPendingReason = reason;
            mActivePanel.closePanel(StateChangeReason.SUPPRESS, true);

        } else if (panel.canBeSuppressed()) {
            // If a panel was showing and the requesting panel has a lower priority, suppress it
            // if possible.
            if (!mSuppressedPanels.contains(panel)) mSuppressedPanels.add(panel);
        }
    }

    /**
     * Notify the manager that some other object hid the panel.
     * NOTE(mdjones): It is possible that a panel other than the one currently showing was hidden.
     * @param panel The panel that was closed.
     */
    public void notifyPanelClosed(OverlayPanel panel, StateChangeReason reason) {
        // TODO(mdjones): Close should behave like "requestShowPanel". The reason it currently does
        // not is because closing will cancel animation for that panel. This method waits for the
        // panel's "onClosed" event to fire, thus preserving the animation.
        if (panel == null) return;

        // If the reason to close was to suppress, only suppress the panel.
        if (reason == StateChangeReason.SUPPRESS) {
            if (mActivePanel == panel) {
                if (mActivePanel.canBeSuppressed()) {
                    mSuppressedPanels.add(mActivePanel);
                }
                mActivePanel = mPendingPanel;
                mActivePanel.peekPanel(mPendingReason);
                mPendingPanel = null;
                mPendingReason = StateChangeReason.UNKNOWN;
            }
        } else {
            // Normal close panel flow.
            if (panel == mActivePanel) {
                mActivePanel = null;
                if (!mSuppressedPanels.isEmpty()) {
                    mActivePanel = mSuppressedPanels.poll();
                    mActivePanel.peekPanel(StateChangeReason.UNSUPPRESS);
                }
            } else {
                mSuppressedPanels.remove(panel);
            }
        }
    }

    /**
     * Get the panel that has been determined to be active.
     * @return The active OverlayPanel.
     */
    @VisibleForTesting
    public OverlayPanel getActivePanel() {
        return mActivePanel;
    }

    /**
     * @return The size of the suppressed panel queue.
     */
    @VisibleForTesting
    public int getSuppressedQueueSize() {
        return mSuppressedPanels.size();
    }

    /**
     * Destroy all panels owned by this manager.
     */
    public void destroy() {
        for (OverlayPanel p : mPanelSet) {
            p.destroy();
        }
        mPanelSet.clear();
        mActivePanel = null;
        mSuppressedPanels.clear();

        // Clear references to held resources.
        mDynamicResourceLoader = null;
        mContainerViewGroup = null;
    }

    /**
     * Set the resource loader for all OverlayPanels.
     * @param host The OverlayPanel host.
     */
    public void setDynamicResourceLoader(DynamicResourceLoader loader) {
        mDynamicResourceLoader = loader;
        for (OverlayPanel p : mPanelSet) {
            p.setDynamicResourceLoader(loader);
        }
    }

    /**
     * Set the ViewGroup for all panels.
     * @param container The ViewGroup objects will be displayed in.
     */
    public void setContainerView(ViewGroup container) {
        mContainerViewGroup = container;
        for (OverlayPanel p : mPanelSet) {
            p.setContainerView(container);
        }
    }

    /**
     * Add a panel to the collection that is managed by this class. If any of the setters for this
     * class were called before a panel was added, that panel will still get those resources.
     * @param panel An OverlayPanel to be managed.
     */
    public void registerPanel(OverlayPanel panel) {
        // If any of the setters for this manager were called before some panel registration,
        // make sure that panel gets the appropriate resources.
        if (mDynamicResourceLoader != null) {
            panel.setDynamicResourceLoader(mDynamicResourceLoader);
        }
        if (mContainerViewGroup != null) {
            panel.setContainerView(mContainerViewGroup);
        }

        mPanelSet.add(panel);
    }
}
