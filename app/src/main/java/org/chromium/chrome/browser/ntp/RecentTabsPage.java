// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.util.ViewUtils;

import java.util.concurrent.TimeUnit;

/**
 * The native recent tabs page. Lists recently closed tabs, open windows and tabs from the user's
 * synced devices, and snapshot documents sent from Chrome to Mobile in an expandable list view.
 */
public class RecentTabsPage
        implements NativePage, ApplicationStatus.ActivityStateListener,
                   ExpandableListView.OnChildClickListener,
                   ExpandableListView.OnGroupCollapseListener,
                   ExpandableListView.OnGroupExpandListener, RecentTabsManager.UpdatedCallback,
                   View.OnAttachStateChangeListener, View.OnCreateContextMenuListener,
                   InvalidationAwareThumbnailProvider {
    private final Activity mActivity;
    private final ExpandableListView mListView;
    private final String mTitle;
    private final ViewGroup mView;

    private RecentTabsManager mRecentTabsManager;
    private RecentTabsRowAdapter mAdapter;

    private boolean mSnapshotContentChanged;
    private int mSnapshotListPosition;
    private int mSnapshotListTop;
    private int mSnapshotWidth;
    private int mSnapshotHeight;

    private final int mThemeColor;

    /**
     * Whether the page is in the foreground and is visible.
     */
    private boolean mInForeground;

    /**
     * Whether {@link #mView} is attached to the application window.
     */
    private boolean mIsAttachedToWindow;

    /**
     * The time, whichever is most recent, that the page:
     * - Moved to the foreground
     * - Became visible
     */
    private long mForegroundTimeMs;

    /**
     * Constructor returns an instance of RecentTabsPage.
     *
     * @param activity The activity this view belongs to.
     * @param recentTabsManager The RecentTabsManager which provides the model data.
     */
    public RecentTabsPage(Activity activity, RecentTabsManager recentTabsManager) {
        mActivity = activity;
        mRecentTabsManager = recentTabsManager;

        mTitle = activity.getResources().getString(R.string.recent_tabs);
        mThemeColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), R.color.default_primary_color);
        mRecentTabsManager.setUpdatedCallback(this);
        LayoutInflater inflater = LayoutInflater.from(activity);
        mView = (ViewGroup) inflater.inflate(R.layout.recent_tabs_page, null);
        mListView = (ExpandableListView) mView.findViewById(R.id.odp_listview);
        mAdapter = buildAdapter(activity, recentTabsManager);
        mListView.setAdapter(mAdapter);
        mListView.setOnChildClickListener(this);
        mListView.setGroupIndicator(null);
        mListView.setOnGroupCollapseListener(this);
        mListView.setOnGroupExpandListener(this);
        mListView.setOnCreateContextMenuListener(this);

        mView.addOnAttachStateChangeListener(this);
        ApplicationStatus.registerStateListenerForActivity(this, activity);
        // {@link #mInForeground} will be updated once the view is attached to the window.

        onUpdated();
    }

    private static RecentTabsRowAdapter buildAdapter(Activity activity,
            RecentTabsManager recentTabsManager) {
        return new RecentTabsRowAdapter(activity, recentTabsManager);
    }

    /**
     * Updates whether the page is in the foreground based on whether the application is in the
     * foreground and whether {@link #mView} is attached to the application window. If the page is
     * no longer in the foreground, records the time that the page spent in the foreground to UMA.
     */
    private void updateForegroundState() {
        boolean inForeground = mIsAttachedToWindow
                && ApplicationStatus.getStateForActivity(mActivity) == ActivityState.RESUMED;
        if (mInForeground == inForeground) {
            return;
        }

        mInForeground = inForeground;
        if (mInForeground) {
            mForegroundTimeMs = SystemClock.elapsedRealtime();
            StartupMetrics.getInstance().recordOpenedRecents();
        } else {
            RecordHistogram.recordLongTimesHistogram("NewTabPage.RecentTabsPage.TimeVisibleAndroid",
                    SystemClock.elapsedRealtime() - mForegroundTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    // NativePage overrides

    @Override
    public String getUrl() {
        return UrlConstants.RECENT_TABS_URL;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public int getBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public String getHost() {
        return UrlConstants.RECENT_TABS_HOST;
    }

    @Override
    public void destroy() {
        assert getView().getParent() == null : "Destroy called before removed from window";
        mRecentTabsManager.destroy();
        mRecentTabsManager = null;
        mAdapter.notifyDataSetInvalidated();
        mAdapter = null;
        mListView.setAdapter((RecentTabsRowAdapter) null);

        mView.removeOnAttachStateChangeListener(this);
        ApplicationStatus.unregisterActivityStateListener(this);
    }

    @Override
    public void updateForUrl(String url) {
    }

    // ApplicationStatus.ActivityStateListener
    @Override
    public void onActivityStateChange(Activity activity, int state) {
        // Called when the user locks the screen or moves Chrome to the background via the task
        // switcher.
        updateForegroundState();
    }

    // View.OnAttachStateChangeListener
    @Override
    public void onViewAttachedToWindow(View view) {
        // Called when the user opens the RecentTabsPage or switches back to the RecentTabsPage from
        // another tab.
        mIsAttachedToWindow = true;
        updateForegroundState();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        // Called when the user navigates from the RecentTabsPage or switches to another tab.
        mIsAttachedToWindow = false;
        updateForegroundState();
    }

    // ExpandableListView.OnChildClickedListener
    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        return mAdapter.getGroup(groupPosition).onChildClick(childPosition);
    }

    // ExpandableListView.OnGroupExpandedListener
    @Override
    public void onGroupExpand(int groupPosition) {
        mAdapter.getGroup(groupPosition).setCollapsed(false);
        mSnapshotContentChanged = true;
    }

    // ExpandableListView.OnGroupCollapsedListener
    @Override
    public void onGroupCollapse(int groupPosition) {
        mAdapter.getGroup(groupPosition).setCollapsed(true);
        mSnapshotContentChanged = true;
    }

    // RecentTabsManager.UpdatedCallback
    @Override
    public void onUpdated() {
        mAdapter.notifyDataSetChanged();
        for (int i = 0; i < mAdapter.getGroupCount(); i++) {
            if (mAdapter.getGroup(i).isCollapsed()) {
                mListView.collapseGroup(i);
            } else {
                mListView.expandGroup(i);
            }
        }
        mSnapshotContentChanged = true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // Would prefer to have this context menu view managed internal to RecentTabsGroupView
        // Unfortunately, setting either onCreateContextMenuListener or onLongClickListener
        // disables the native onClick (expand/collapse) behaviour of the group view.
        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);

        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            mAdapter.getGroup(groupPosition).onCreateContextMenuForGroup(menu, mActivity);
        } else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);
            mAdapter.getGroup(groupPosition).onCreateContextMenuForChild(childPosition, menu,
                    mActivity);
        }
    }

    // InvalidationAwareThumbnailProvider

    @Override
    public boolean shouldCaptureThumbnail() {
        if (mView.getWidth() == 0 || mView.getHeight() == 0) return false;

        View topItem = mListView.getChildAt(0);
        return mSnapshotContentChanged
                || mSnapshotListPosition != mListView.getFirstVisiblePosition()
                || mSnapshotListTop != (topItem == null ? 0 : topItem.getTop())
                || mView.getWidth() != mSnapshotWidth
                || mView.getHeight() != mSnapshotHeight;
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        ViewUtils.captureBitmap(mView, canvas);
        mSnapshotContentChanged = false;
        mSnapshotListPosition = mListView.getFirstVisiblePosition();
        View topItem = mListView.getChildAt(0);
        mSnapshotListTop = topItem == null ? 0 : topItem.getTop();
        mSnapshotWidth = mView.getWidth();
        mSnapshotHeight = mView.getHeight();
    }
}
