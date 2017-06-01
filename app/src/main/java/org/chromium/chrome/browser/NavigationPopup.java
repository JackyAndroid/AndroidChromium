// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.NavigationHistory;
import org.chromium.ui.base.LocalizationUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * A popup that handles displaying the navigation history for a given tab.
 */
public class NavigationPopup extends ListPopupWindow implements AdapterView.OnItemClickListener {

    private static final int MAXIMUM_HISTORY_ITEMS = 8;

    private final Profile mProfile;
    private final Context mContext;
    private final NavigationController mNavigationController;
    private final NavigationHistory mHistory;
    private final NavigationAdapter mAdapter;
    private final ListItemFactory mListItemFactory;

    private final int mFaviconSize;

    private Bitmap mDefaultFavicon;

    /**
      * Loads the favicons asynchronously.
      */
    private FaviconHelper mFaviconHelper;

    private boolean mInitialized;

    /**
     * Constructs a new popup with the given history information.
     *
     * @param profile The profile used for fetching favicons.
     * @param context The context used for building the popup.
     * @param navigationController The controller which takes care of page navigations.
     * @param isForward Whether to request forward navigation entries.
     */
    public NavigationPopup(Profile profile, Context context,
            NavigationController navigationController, boolean isForward) {
        super(context, null, android.R.attr.popupMenuStyle);
        mProfile = profile;
        mContext = context;
        mNavigationController = navigationController;
        mHistory = mNavigationController.getDirectedNavigationHistory(
                isForward, MAXIMUM_HISTORY_ITEMS);
        mAdapter = new NavigationAdapter();

        mFaviconSize = mContext.getResources().getDimensionPixelSize(R.dimen.default_favicon_size);

        setModal(true);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setOnItemClickListener(this);

        setAdapter(new HeaderViewListAdapter(null, null, mAdapter));

        mListItemFactory = new ListItemFactory(context);
    }

    /**
     * @return Whether a navigation popup is valid for the given page.
     */
    public boolean shouldBeShown() {
        return mHistory.getEntryCount() > 0;
    }

    @Override
    public void show() {
        if (!mInitialized) initialize();
        super.show();
    }

    @Override
    public void dismiss() {
        if (mInitialized) mFaviconHelper.destroy();
        mInitialized = false;
        super.dismiss();
    }

    private void initialize() {
        ThreadUtils.assertOnUiThread();
        mInitialized = true;
        mFaviconHelper = new FaviconHelper();

        Set<String> requestedUrls = new HashSet<String>();
        for (int i = 0; i < mHistory.getEntryCount(); i++) {
            NavigationEntry entry = mHistory.getEntryAtIndex(i);
            if (entry.getFavicon() != null) continue;
            final String pageUrl = entry.getUrl();
            if (!requestedUrls.contains(pageUrl)) {
                FaviconImageCallback imageCallback = new FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap bitmap, String iconUrl) {
                        NavigationPopup.this.onFaviconAvailable(pageUrl, bitmap);
                    }
                };
                mFaviconHelper.getLocalFaviconImageForURL(
                        mProfile, pageUrl, mFaviconSize, imageCallback);
                requestedUrls.add(pageUrl);
            }
        }

        FaviconImageCallback historyImageCallback = new FaviconImageCallback() {
            @Override
            public void onFaviconAvailable(Bitmap bitmap, String iconUrl) {
                NavigationPopup.this.onFaviconAvailable(UrlConstants.HISTORY_URL, bitmap);
            }
        };
        mFaviconHelper.getLocalFaviconImageForURL(
                mProfile, UrlConstants.HISTORY_URL, mFaviconSize, historyImageCallback);
    }

    /**
     * Called when favicon data requested by {@link #initialize()} is retrieved.
     * @param pageUrl the page for which the favicon was retrieved.
     * @param favicon the favicon data.
     */
    private void onFaviconAvailable(String pageUrl, Object favicon) {
        if (favicon == null) {
            if (mDefaultFavicon == null) {
                mDefaultFavicon = BitmapFactory.decodeResource(
                        mContext.getResources(), R.drawable.default_favicon);
            }
            favicon = mDefaultFavicon;
        }
        for (int i = 0; i < mHistory.getEntryCount(); i++) {
            NavigationEntry entry = mHistory.getEntryAtIndex(i);
            if (TextUtils.equals(pageUrl, entry.getUrl())) entry.updateFavicon((Bitmap) favicon);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        NavigationEntry entry = (NavigationEntry) parent.getItemAtPosition(position);
        mNavigationController.goToNavigationIndex(entry.getIndex());
        dismiss();
    }

    private void updateBitmapForTextView(TextView view, Bitmap bitmap) {
        Drawable faviconDrawable = null;
        if (bitmap != null) {
            faviconDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            ((BitmapDrawable) faviconDrawable).setGravity(Gravity.FILL);
        } else {
            faviconDrawable = new ColorDrawable(Color.TRANSPARENT);
        }
        faviconDrawable.setBounds(0, 0, mFaviconSize, mFaviconSize);
        view.setCompoundDrawables(faviconDrawable, null, null, null);
    }

    private static class ListItemFactory {
        private static final int LIST_ITEM_HEIGHT_DP = 48;
        private static final int PADDING_DP = 8;
        private static final int TEXT_SIZE_SP = 18;
        private static final float FADE_LENGTH_DP = 25.0f;
        private static final float FADE_STOP = 0.75f;

        int mFadeEdgeLength;
        int mFadePadding;
        int mListItemHeight;
        int mPadding;
        boolean mIsLayoutDirectionRTL;
        Context mContext;

        public ListItemFactory(Context context) {
            mContext = context;
            computeFadeDimensions();
        }

        private void computeFadeDimensions() {
            // Fade with linear gradient starting 25dp from right margin.
            // Reaches 0% opacity at 75% length. (Simulated with extra padding)
            float density = mContext.getResources().getDisplayMetrics().density;
            float fadeLength = (FADE_LENGTH_DP * density);
            mFadeEdgeLength = (int) (fadeLength * FADE_STOP);
            mFadePadding = (int) (fadeLength * (1 - FADE_STOP));
            mListItemHeight = (int) (density * LIST_ITEM_HEIGHT_DP);
            mPadding = (int) (density * PADDING_DP);
            mIsLayoutDirectionRTL = LocalizationUtils.isLayoutRtl();
        }

        public TextView createListItem() {
            TextView view = new TextView(mContext);
            view.setFadingEdgeLength(mFadeEdgeLength);
            view.setHorizontalFadingEdgeEnabled(true);
            view.setSingleLine();
            view.setTextSize(TEXT_SIZE_SP);
            view.setMinimumHeight(mListItemHeight);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setCompoundDrawablePadding(mPadding);
            if (!mIsLayoutDirectionRTL) {
                view.setPadding(mPadding, 0, mPadding + mFadePadding , 0);
            } else {
                view.setPadding(mPadding + mFadePadding, 0, mPadding, 0);
            }
            return view;
        }
    }

    private class NavigationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mHistory.getEntryCount();
        }

        @Override
        public Object getItem(int position) {
            return mHistory.getEntryAtIndex(position);
        }

        @Override
        public long getItemId(int position) {
            return ((NavigationEntry) getItem(position)).getIndex();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = mListItemFactory.createListItem();
            }
            NavigationEntry entry = (NavigationEntry) getItem(position);

            String entryText = entry.getTitle();
            if (TextUtils.isEmpty(entryText)) entryText = entry.getVirtualUrl();
            if (TextUtils.isEmpty(entryText)) entryText = entry.getUrl();
            view.setText(entryText);
            updateBitmapForTextView(view, entry.getFavicon());

            return view;
        }
    }
}
