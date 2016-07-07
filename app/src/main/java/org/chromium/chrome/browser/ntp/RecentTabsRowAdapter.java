// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSession;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSessionTab;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSessionWindow;
import org.chromium.chrome.browser.ntp.RecentlyClosedBridge.RecentlyClosedTab;
import org.chromium.ui.WindowOpenDisposition;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Row adapter for presenting recently closed tabs, synced tabs from other devices, the sync or
 * sign in promo, and currently open tabs (only in document mode) in a grouped list view.
 */
public class RecentTabsRowAdapter extends BaseExpandableListAdapter {
    private static final int MAX_NUM_FAVICONS_TO_CACHE = 256;

    private enum ChildType {
        NONE, DEFAULT_CONTENT, SYNC_PROMO
    }

    private enum GroupType {
        CONTENT, VISIBLE_SEPARATOR, INVISIBLE_SEPARATOR
    }

    private final Activity mActivity;
    private final ArrayList<Group> mGroups;
    private final Drawable mDefaultFavicon;
    private final RecentTabsManager mRecentTabsManager;
    private final RecentlyClosedTabsGroup mRecentlyClosedTabsGroup = new RecentlyClosedTabsGroup();
    private final SeparatorGroup mVisibleSeparatorGroup = new SeparatorGroup(true);
    private final SeparatorGroup mInvisibleSeparatorGroup = new SeparatorGroup(false);
    private final long mInitializationTimestamp;  // Seconds since Unix epoch.
    private final FaviconCache mFaviconCache;
    private final int mFaviconSize;

    /**
     * A generic group of objects to be shown in the RecentTabsRowAdapter, such as the list of
     * recently closed tabs.
     */
    abstract class Group {
        /**
         * @return The type of group: GroupType.CONTENT or GroupType.SEPARATOR.
         */
        abstract GroupType getGroupType();

        /**
         * @return The number of children in this group.
         */
        abstract int getChildrenCount();

        /**
         * @return The child type.
         */
        abstract ChildType getChildType();


        /**
         * @return The child at the position childPosition.
         */
        abstract Object getChild(int childPosition);

        /**
         * Returns the view corresponding to the child view at a given position.
         *
         * @param childPosition The position of the child.
         * @param isLastChild Whether this child is the last one.
         * @param convertView The re-usable child view (may be null).
         * @param parent The parent view group.
         *
         * @return The view corresponding to the child.
         */
        View getChildView(int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            View childView = convertView;
            if (childView == null) {
                LayoutInflater inflater = LayoutInflater.from(mActivity);
                childView = inflater.inflate(R.layout.recent_tabs_list_item, parent, false);

                ViewHolder viewHolder = new ViewHolder();
                viewHolder.textView = (TextView) childView;
                childView.setTag(viewHolder);
            }

            ViewHolder viewHolder = (ViewHolder) childView.getTag();
            configureChildView(childPosition, viewHolder);

            return childView;
        }

        /**
         * Configures a view inflated from recent_tabs_list_item.xml to display information about
         * a child in this group.
         *
         * @param childPosition The position of the child within this group.
         * @param viewHolder The ViewHolder with references to pieces of the view.
         */
        abstract void configureChildView(int childPosition, ViewHolder viewHolder);

        /**
         * Returns the view corresponding to this group.
         *
         * @param isExpanded Whether the group is expanded.
         * @param convertView The re-usable group view (may be null).
         * @param parent The parent view group.
         *
         * @return The view corresponding to the group.
         */
        public View getGroupView(boolean isExpanded, View convertView, ViewGroup parent) {
            assert convertView == null || convertView instanceof RecentTabsGroupView;
            RecentTabsGroupView groupView = (RecentTabsGroupView) convertView;
            if (groupView == null) {
                groupView = (RecentTabsGroupView) LayoutInflater.from(mActivity).inflate(
                        R.layout.recent_tabs_group_item, parent, false);
                groupView.initialize(mInitializationTimestamp);
            }
            configureGroupView(groupView, isExpanded);
            return groupView;
        }

        /**
         * Configures an RecentTabsGroupView to display the header of this group.
         * @param groupView The RecentTabsGroupView to configure.
         * @param isExpanded Whether the view is currently expanded.
         */
        abstract void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded);

        /**
         * Sets whether this group is collapsed (i.e. whether only the header is visible).
         */
        abstract void setCollapsed(boolean isCollapsed);

        /**
         * @return Whether this group is collapsed.
         */
        abstract boolean isCollapsed();

        /**
         * Called when a child item is clicked.
         * @param childPosition The position of the child in the group.
         * @return Whether the click was handled.
         */
        abstract boolean onChildClick(int childPosition);

        /**
         * Called when the context menu for the group view is being built.
         * @param menu The context menu being built.
         * @param activity The current activity.
         */
        void onCreateContextMenuForGroup(ContextMenu menu, Activity activity) {
        }

        /**
         * Called when a context menu for one of the child views is being built.
         * @param childPosition The position of the child in the group.
         * @param menu The context menu being built.
         * @param activity The current activity.
         */
        void onCreateContextMenuForChild(int childPosition, ContextMenu menu,
                Activity activity) {
        }
    }

    /**
     * A group containing all the tabs currently open on this device.
     */
    class CurrentlyOpenTabsGroup extends Group {
        private static final int NUM_DEFAULT_VISIBLE_TABS = 6;

        private final List<CurrentlyOpenTab> mCurrentlyOpenTabs;
        private final boolean mShowingAll;

        public CurrentlyOpenTabsGroup(List<CurrentlyOpenTab> tabsList) {
            mCurrentlyOpenTabs = tabsList;
            mShowingAll = mRecentTabsManager.isCurrentlyOpenTabsShowingAll();
        }

        private boolean isMoreButton(int childPosition) {
            return !mShowingAll && childPosition
                    == Math.min(NUM_DEFAULT_VISIBLE_TABS, mCurrentlyOpenTabs.size());
        }

        @Override
        GroupType getGroupType() {
            return GroupType.CONTENT;
        }

        @Override
        int getChildrenCount() {
            if (mShowingAll) return mCurrentlyOpenTabs.size();
            return Math.min(NUM_DEFAULT_VISIBLE_TABS, mCurrentlyOpenTabs.size() - 1) + 1;
        }

        @Override
        ChildType getChildType() {
            return ChildType.DEFAULT_CONTENT;
        }

        @Override
        CurrentlyOpenTab getChild(int childPosition) {
            if (isMoreButton(childPosition)) return null;

            return mCurrentlyOpenTabs.get(childPosition);
        }

        @Override
        void configureChildView(int childPosition, ViewHolder viewHolder) {
            if (isMoreButton(childPosition)) {
                Resources resources = mActivity.getResources();
                String text = resources.getString(R.string.recent_tabs_show_more);
                viewHolder.textView.setText(text);
                Drawable drawable =  ApiCompatibilityUtils.getDrawable(
                        resources, R.drawable.more_horiz);
                ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        viewHolder.textView, drawable, null, null, null);
            } else {
                CurrentlyOpenTab openTab = getChild(childPosition);
                viewHolder.textView.setText(TextUtils.isEmpty(openTab.getTitle()) ? openTab.getUrl()
                        : openTab.getTitle());
                loadLocalFavicon(viewHolder, openTab.getUrl());
            }
        }

        @Override
        void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded) {
            groupView.configureForCurrentlyOpenTabs(isExpanded);
        }

        @Override
        void setCollapsed(boolean isCollapsed) {
            mRecentTabsManager.setCurrentlyOpenTabsCollapsed(isCollapsed);
        }

        @Override
        boolean isCollapsed() {
            return mRecentTabsManager.isCurrentlyOpenTabsCollapsed();
        }

        @Override
        boolean onChildClick(int childPosition) {
            if (isMoreButton(childPosition)) {
                mRecentTabsManager.setCurrentlyOpenTabsShowAll(true);
            } else {
                getChild(childPosition).getRunnable().run();
            }
            return true;
        }

        @Override
        void onCreateContextMenuForChild(final int childPosition, ContextMenu menu,
                Activity activity) {
            if (isMoreButton(childPosition)) return;
            OnMenuItemClickListener listener = new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mRecentTabsManager.closeTab(getChild(childPosition));
                    return true;
                }
            };
            menu.add(R.string.close_tab).setOnMenuItemClickListener(listener);
        }
    }

    /**
     * A group containing all the tabs associated with a foreign session from a synced device.
     */
    class ForeignSessionGroup extends Group {
        private final ForeignSession mForeignSession;

        ForeignSessionGroup(ForeignSession foreignSession) {
            mForeignSession = foreignSession;
        }

        @Override
        public GroupType getGroupType() {
            return GroupType.CONTENT;
        }

        @Override
        public int getChildrenCount() {
            int count = 0;
            for (ForeignSessionWindow window : mForeignSession.windows) {
                count += window.tabs.size();
            }
            return count;
        }

        @Override
        public ChildType getChildType() {
            return ChildType.DEFAULT_CONTENT;
        }

        @Override
        public ForeignSessionTab getChild(int childPosition) {
            for (ForeignSessionWindow window : mForeignSession.windows) {
                if (childPosition < window.tabs.size()) {
                    return window.tabs.get(childPosition);
                }
                childPosition -= window.tabs.size();
            }
            assert false;
            return null;
        }

        @Override
        public void configureChildView(int childPosition, ViewHolder viewHolder) {
            ForeignSessionTab sessionTab = getChild(childPosition);
            viewHolder.textView.setText(TextUtils.isEmpty(sessionTab.title) ? sessionTab.url
                    : sessionTab.title);
            loadSyncedFavicon(viewHolder, sessionTab.url);
        }

        @Override
        public void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded) {
            groupView.configureForForeignSession(mForeignSession, isExpanded);
        }

        @Override
        public void setCollapsed(boolean isCollapsed) {
            mRecentTabsManager.setForeignSessionCollapsed(mForeignSession, isCollapsed);
        }

        @Override
        public boolean isCollapsed() {
            return mRecentTabsManager.getForeignSessionCollapsed(mForeignSession);
        }

        @Override
        public boolean onChildClick(int childPosition) {
            ForeignSessionTab foreignSessionTab = getChild(childPosition);
            mRecentTabsManager.openForeignSessionTab(mForeignSession, foreignSessionTab,
                    WindowOpenDisposition.CURRENT_TAB);
            return true;
        }

        @Override
        public void onCreateContextMenuForGroup(ContextMenu menu, Activity activity) {
            OnMenuItemClickListener listener = new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mRecentTabsManager.deleteForeignSession(mForeignSession);
                    return true;
                }
            };
            menu.add(R.string.ntp_recent_tabs_remove_menu_option)
                    .setOnMenuItemClickListener(listener);
        }

        @Override
        public void onCreateContextMenuForChild(int childPosition, ContextMenu menu,
                Activity activity) {
            final ForeignSessionTab foreignSessionTab = getChild(childPosition);
            OnMenuItemClickListener listener = new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mRecentTabsManager.openForeignSessionTab(mForeignSession, foreignSessionTab,
                            WindowOpenDisposition.NEW_BACKGROUND_TAB);
                    return true;
                }
            };
            menu.add(R.string.contextmenu_open_in_new_tab).setOnMenuItemClickListener(listener);
        }
    }

    /**
     * A group containing tabs that were recently closed on this device and a link to the history
     * page.
     */
    class RecentlyClosedTabsGroup extends Group {
        private static final int ID_OPEN_IN_NEW_TAB = 1;
        private static final int ID_REMOVE_ALL = 2;

        @Override
        public GroupType getGroupType() {
            return GroupType.CONTENT;
        }

        @Override
        public int getChildrenCount() {
            // The number of children is the number of recently closed tabs, plus one for the "Show
            // full history" item.
            return 1 + mRecentTabsManager.getRecentlyClosedTabs().size();
        }

        @Override
        public ChildType getChildType() {
            return ChildType.DEFAULT_CONTENT;
        }

        /**
         * @param childPosition The index of an item in the recently closed list.
         * @return Whether the item at childPosition is the link to the history page.
         */
        private boolean isHistoryLink(int childPosition) {
            return childPosition == mRecentTabsManager.getRecentlyClosedTabs().size();
        }

        @Override
        public RecentlyClosedTab getChild(int childPosition) {
            if (isHistoryLink(childPosition)) {
                return null;
            }
            return mRecentTabsManager.getRecentlyClosedTabs().get(childPosition);
        }

        @Override
        public void configureChildView(int childPosition, ViewHolder viewHolder) {
            if (isHistoryLink(childPosition)) {
                viewHolder.textView.setText(R.string.show_full_history);
                ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        viewHolder.textView, R.drawable.history_favicon, 0, 0, 0);
            } else {
                RecentlyClosedTab tab = getChild(childPosition);
                String title = NewTabPageView.getTitleForDisplay(tab.title, tab.url);
                viewHolder.textView.setText(title);
                loadLocalFavicon(viewHolder, tab.url);
            }
        }

        @Override
        public void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded) {
            groupView.configureForRecentlyClosedTabs(isExpanded);
        }

        @Override
        public void setCollapsed(boolean isCollapsed) {
            mRecentTabsManager.setRecentlyClosedTabsCollapsed(isCollapsed);
        }

        @Override
        public boolean isCollapsed() {
            return mRecentTabsManager.isRecentlyClosedTabsCollapsed();
        }

        @Override
        public boolean onChildClick(int childPosition) {
            if (isHistoryLink(childPosition)) {
                mRecentTabsManager.openHistoryPage();
            } else {
                mRecentTabsManager.openRecentlyClosedTab(getChild(childPosition),
                        WindowOpenDisposition.CURRENT_TAB);
            }
            return true;
        }

        @Override
        public void onCreateContextMenuForGroup(ContextMenu menu, Activity activity) {
        }

        @Override
        public void onCreateContextMenuForChild(final int childPosition, ContextMenu menu,
                Activity activity) {
            final RecentlyClosedTab recentlyClosedTab = getChild(childPosition);
            if (recentlyClosedTab == null) return;
            OnMenuItemClickListener listener = new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case ID_REMOVE_ALL:
                            mRecentTabsManager.clearRecentlyClosedTabs();
                            break;
                        case ID_OPEN_IN_NEW_TAB:
                            mRecentTabsManager.openRecentlyClosedTab(recentlyClosedTab,
                                    WindowOpenDisposition.NEW_BACKGROUND_TAB);
                            break;
                        default:
                            assert false;
                    }
                    return true;
                }
            };
            menu.add(ContextMenu.NONE, ID_OPEN_IN_NEW_TAB, ContextMenu.NONE,
                    R.string.contextmenu_open_in_new_tab).setOnMenuItemClickListener(listener);
            menu.add(ContextMenu.NONE, ID_REMOVE_ALL, ContextMenu.NONE,
                    R.string.remove_all).setOnMenuItemClickListener(listener);
        }
    }

    /**
     * A group containing a blank separator.
     */
    class SeparatorGroup extends Group {
        private final boolean mIsVisible;

        public SeparatorGroup(boolean isVisible) {
            mIsVisible = isVisible;
        }

        @Override
        public GroupType getGroupType() {
            return mIsVisible ? GroupType.VISIBLE_SEPARATOR : GroupType.INVISIBLE_SEPARATOR;
        }

        @Override
        public ChildType getChildType() {
            return ChildType.NONE;
        }

        @Override
        public int getChildrenCount() {
            return 0;
        }

        @Override
        public View getGroupView(boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                int layout = mIsVisible
                        ? R.layout.recent_tabs_group_separator_visible
                        : R.layout.recent_tabs_group_separator_invisible;
                convertView = LayoutInflater.from(mActivity).inflate(layout, parent, false);
            }
            return convertView;
        }

        @Override
        public void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded) {
        }

        @Override
        public void configureChildView(int childPosition, ViewHolder viewHolder) {
        }

        @Override
        public Object getChild(int childPosition) {
            return null;
        }

        @Override
        public void setCollapsed(boolean isCollapsed) {
        }

        @Override
        public boolean isCollapsed() {
            return false;
        }

        @Override
        public boolean onChildClick(int childPosition) {
            return false;
        }
    }

    /**
     * A group containing the Sync Promo.
     */
    class SyncPromoGroup extends Group {
        @Override
        public GroupType getGroupType() {
            return GroupType.CONTENT;
        }

        @Override
        public ChildType getChildType() {
            return ChildType.SYNC_PROMO;
        }

        @Override
        public int getChildrenCount() {
            return 1;
        }

        @Override
        public Object getChild(int childPosition) {
            return null;
        }

        @Override
        View getChildView(int childPosition, boolean isLastChild, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = new RecentTabsPromoView(mActivity, mRecentTabsManager, null);
            }
            return convertView;
        }

        @Override
        public void configureChildView(int childPosition, ViewHolder viewHolder) {
        }

        @Override
        public void configureGroupView(RecentTabsGroupView groupView, boolean isExpanded) {
            groupView.configureForSyncPromo(isExpanded);
        }

        @Override
        public void setCollapsed(boolean isCollapsed) {
            mRecentTabsManager.setSyncPromoCollapsed(isCollapsed);
        }

        @Override
        public boolean isCollapsed() {
            return mRecentTabsManager.isSyncPromoCollapsed();
        }

        @Override
        public boolean onChildClick(int childPosition) {
            return false;
        }
    }

    private static class FaviconCache {
        private static final String SYNCED_FAVICON_PREFIX = "Synced";
        private static final String LOCAL_FAVICON_PREFIX = "Local";

        private final LruCache<String, Drawable> mMemoryCache;

        public FaviconCache(int size) {
            mMemoryCache = new LruCache<String, Drawable>(size);
        }

        public Drawable getSyncedFaviconImage(String url) {
            return mMemoryCache.get(SYNCED_FAVICON_PREFIX + url);
        }

        public void putSycnedFaviconImage(String url, Drawable image) {
            mMemoryCache.put(SYNCED_FAVICON_PREFIX + url, image);
        }

        public Drawable getLocalFaviconImage(String url) {
            return mMemoryCache.get(LOCAL_FAVICON_PREFIX + url);
        }

        public void putLocalFaviconImage(String url, Drawable image) {
            mMemoryCache.put(LOCAL_FAVICON_PREFIX + url, image);
        }
    }

    /**
     * Creates an RecentTabsRowAdapter used to populate an ExpandableList with other
     * devices and foreign tab cells.
     *
     * @param activity The Android activity this adapter will work in.
     * @param recentTabsManager The RecentTabsManager that will act as the data source.
     */
    public RecentTabsRowAdapter(Activity activity, RecentTabsManager recentTabsManager) {
        mActivity = activity;
        mRecentTabsManager = recentTabsManager;
        mGroups = new ArrayList<Group>();
        mInitializationTimestamp =
            TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        mFaviconCache = buildFaviconCache(MAX_NUM_FAVICONS_TO_CACHE);

        Resources resources = activity.getResources();
        mDefaultFavicon = ApiCompatibilityUtils.getDrawable(resources, R.drawable.default_favicon);
        mFaviconSize = resources.getDimensionPixelSize(R.dimen.default_favicon_size);
    }

    private static FaviconCache buildFaviconCache(int size) {
        return new FaviconCache(size);
    }

    /**
     * ViewHolder class optimizes looking up table row fields. findViewById is only called once
     * per row view initialization, and the references are cached here. Also stores a reference to
     * the favicon image callback; so that we can make sure we load the correct favicon.
     */
    private static class ViewHolder {
        public TextView textView;
        public FaviconImageCallback imageCallback;
    }

    private Drawable faviconDrawable(Bitmap image) {
        if (image == null) return null;
        return new BitmapDrawable(mActivity.getResources(), Bitmap.createScaledBitmap(image,
                mFaviconSize, mFaviconSize, true));
    }

    private void loadSyncedFavicon(final ViewHolder viewHolder, final String url) {
        Drawable image = mFaviconCache.getSyncedFaviconImage(url);
        if (image == null) {
            image = faviconDrawable(mRecentTabsManager.getSyncedFaviconImageForURL(url));
            image = (image == null) ? mDefaultFavicon : image;
            mFaviconCache.putSycnedFaviconImage(url, image);
        }
        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(viewHolder.textView,
                image, null, null, null);
    }

    private void loadLocalFavicon(final ViewHolder viewHolder, final String url) {
        Drawable image;
        if (url == null) {
            // URL is null for print jobs, for example.
            image = mDefaultFavicon;
        } else {
            image = mFaviconCache.getLocalFaviconImage(url);
            if (image == null) {
                FaviconImageCallback imageCallback = new FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap bitmap, String iconUrl) {
                        if (this != viewHolder.imageCallback) return;
                        Drawable image = faviconDrawable(bitmap);
                        image = (image == null) ? mDefaultFavicon : image;
                        mFaviconCache.putLocalFaviconImage(url, image);
                        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                viewHolder.textView, image, null, null, null);
                    }
                };
                viewHolder.imageCallback = imageCallback;
                mRecentTabsManager.getLocalFaviconForUrl(url, mFaviconSize, imageCallback);
                image = mDefaultFavicon;
            }
        }
        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(viewHolder.textView,
                image, null, null, null);
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
            View convertView, ViewGroup parent) {
        return getGroup(groupPosition).getChildView(childPosition, isLastChild, convertView,
                parent);
    }

    // BaseExpandableListAdapter group related implementations
    @Override
    public int getGroupCount() {
        return mGroups.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public Group getGroup(int groupPosition) {
        return mGroups.get(groupPosition);
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
            ViewGroup parent) {
        return getGroup(groupPosition).getGroupView(isExpanded, convertView, parent);
    }

    // BaseExpandableListAdapter child related implementations
    @Override
    public int getChildrenCount(int groupPosition) {
        return getGroup(groupPosition).getChildrenCount();
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return getGroup(groupPosition).getChild(childPosition);
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    // BaseExpandableListAdapter misc. implementation
    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public int getGroupType(int groupPosition) {
        return getGroup(groupPosition).getGroupType().ordinal();
    }

    @Override
    public int getGroupTypeCount() {
        return GroupType.values().length;
    }

    private void addGroup(Group group) {
        if (!DeviceFormFactor.isTablet(mActivity)) {
            mGroups.add(group);
        } else {
            if (mGroups.size() == 0) {
                mGroups.add(mInvisibleSeparatorGroup);
            }
            mGroups.add(group);
            mGroups.add(mInvisibleSeparatorGroup);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        mGroups.clear();
        List<CurrentlyOpenTab> tabList = mRecentTabsManager.getCurrentlyOpenTabs();
        if (tabList != null && !tabList.isEmpty()) {
            addGroup(new CurrentlyOpenTabsGroup(tabList));
        }
        addGroup(mRecentlyClosedTabsGroup);
        for (ForeignSession session : mRecentTabsManager.getForeignSessions()) {
            addGroup(new ForeignSessionGroup(session));
        }
        if (mRecentTabsManager.shouldDisplaySyncPromo()) {
            addGroup(new SyncPromoGroup());
        }

        // Add separator line after the recently closed tabs group.
        int recentlyClosedIndex = mGroups.indexOf(mRecentlyClosedTabsGroup);
        if (DeviceFormFactor.isTablet(mActivity)) {
            if (recentlyClosedIndex != mGroups.size() - 2) {
                mGroups.set(recentlyClosedIndex + 1, mVisibleSeparatorGroup);
            }
        } else if (recentlyClosedIndex != mGroups.size() - 1) {
            mGroups.add(recentlyClosedIndex + 1, mVisibleSeparatorGroup);
        }

        super.notifyDataSetChanged();
    }

    @Override
    public int getChildType(int groupPosition, int childPosition) {
        return mGroups.get(groupPosition).getChildType().ordinal();
    }

    @Override
    public int getChildTypeCount() {
        return ChildType.values().length;
    }
}
