// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.os.Parcelable;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ViewSwitcher;

import org.json.JSONArray;
import org.json.JSONException;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.bookmarks.BookmarkSearchRow.SearchHistoryDelegate;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.ui.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for searching bookmarks. Search results will be updated when user is typing. Before
 * typing, a list of search history is shown.
 */
public class BookmarkSearchView extends LinearLayout implements OnItemClickListener,
        OnEditorActionListener, BookmarkUIObserver, SearchHistoryDelegate {
    /**
     * A custom {@link ViewSwitcher} that wraps another {@link ViewSwitcher} inside.
     */
    public static class HistoryResultSwitcher extends ViewSwitcher {
        ViewSwitcher mResultEmptySwitcher;

        /**
         * Constructor for xml inflation.
         */
        public HistoryResultSwitcher(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
            mResultEmptySwitcher = (ViewSwitcher) findViewById(R.id.result_empty_switcher);
        }

        void showHistory() {
            if (getCurrentView().getId() == R.id.bookmark_history_list) return;
            showNext();
        }

        void showResult() {
            if (getCurrentView().getId() == R.id.bookmark_history_list) showNext();
            if (mResultEmptySwitcher.getCurrentView().getId() == R.id.bookmark_search_empty_view) {
                mResultEmptySwitcher.showNext();
            }
        }

        void showEmpty() {
            if (getCurrentView().getId() == R.id.bookmark_history_list) showNext();
            if (mResultEmptySwitcher.getCurrentView().getId() == R.id.bookmark_result_list) {
                mResultEmptySwitcher.showNext();
            }
        }
    }

    private static enum UIState {HISTORY, RESULT, EMPTY}

    private static final String PREF_SEARCH_HISTORY = "bookmark_search_history";
    private static final int SEARCH_HISTORY_MAX_ENTRIES = 10;
    private static final int HISTORY_ITEM_PADDING_START_DP = 72;
    private static final int MAXIMUM_NUMBER_OF_RESULTS = 500;

    private BookmarkModel mBookmarkModel;
    private BookmarkDelegate mDelegate;
    private EditText mSearchText;
    private ListView mResultList;
    private ListView mHistoryList;
    private HistoryResultSwitcher mHistoryResultSwitcher;
    private UIState mCurrentUIState;

    private BookmarkModelObserver mModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkModelChanged() {
            if (mCurrentUIState == UIState.RESULT || mCurrentUIState == UIState.EMPTY) {
                sendSearchQuery();
            }
        }

        @Override
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            // If isDoingExtensiveChanges is false, it will fall back to bookmarkModelChange()
            if (isDoingExtensiveChanges && mCurrentUIState == UIState.RESULT) {
                sendSearchQuery();
            }
        }
    };

    /**
     * Constructor for inflating from XML.
     */
    public BookmarkSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSearchText = (EditText) findViewById(R.id.bookmark_search_text);
        mResultList = (ListView) findViewById(R.id.bookmark_result_list);
        mHistoryList = (ListView) findViewById(R.id.bookmark_history_list);
        mHistoryResultSwitcher = (HistoryResultSwitcher) findViewById(R.id.history_result_switcher);

        Toolbar searchBar = (Toolbar) findViewById(R.id.search_bar);
        searchBar.setNavigationIcon(R.drawable.back_normal);
        searchBar.setNavigationContentDescription(R.string.accessibility_toolbar_btn_back);
        searchBar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mHistoryList.setOnItemClickListener(this);
        mSearchText.setOnEditorActionListener(this);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (TextUtils.isEmpty(s.toString().trim())) {
                    resetUI();
                } else {
                    sendSearchQuery();
                }
            }
        });
        mCurrentUIState = UIState.HISTORY;
    }

    private void updateHistoryList() {
        mHistoryList.setAdapter(new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_list_item_1, android.R.id.text1,
                readHistoryList()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View textView = super.getView(position, convertView, parent);
                // Set padding start to specific size.
                int paddingStart = (int) (HISTORY_ITEM_PADDING_START_DP
                        * getResources().getDisplayMetrics().density);
                ApiCompatibilityUtils.setPaddingRelative(textView, paddingStart,
                        textView.getPaddingTop(), textView.getPaddingRight(),
                        textView.getPaddingBottom());
                return textView;
            }
        });
    }

    private void resetUI() {
        setUIState(UIState.HISTORY);
        mResultList.setAdapter(null);
        if (!TextUtils.isEmpty(mSearchText.getText())) mSearchText.setText("");
    }

    private void sendSearchQuery() {
        String currentText = mSearchText.getText().toString().trim();
        if (TextUtils.isEmpty(currentText)) return;

        List<BookmarkMatch> results = mBookmarkModel.searchBookmarks(currentText,
                MAXIMUM_NUMBER_OF_RESULTS);
        populateResultListView(results);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // To intercept hardware key, a view must have focus.
        if (mDelegate == null) return super.dispatchKeyEvent(event);

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    state.startTracking(event, this);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()
                        && state.isTracking(event)) {
                    onBackPressed();
                    return true;
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        // No-op because state saving/restoring is intentionally omitted in this View. This is
        // to fix a crash in Android M that TextView's old text is sometimes restored even if
        // setText("") is called in onVisibilityChange(). crbug.com/596783
    }

    /**
     * Make result list visible and popuplate the list with given list of bookmarks.
     */
    private void populateResultListView(List<BookmarkMatch> ids) {
        if (ids.isEmpty()) {
            setUIState(UIState.EMPTY);
        } else {
            setUIState(UIState.RESULT);
            mResultList.setAdapter(new ResultListAdapter(ids, mDelegate));
        }
    }

    private void setUIState(UIState state) {
        if (mCurrentUIState == state) return;
        mCurrentUIState = state;
        if (state == UIState.HISTORY) {
            mHistoryResultSwitcher.showHistory();
            updateHistoryList();
        } else if (state == UIState.RESULT) {
            mHistoryResultSwitcher.showResult();
        } else if (state == UIState.EMPTY) {
            mHistoryResultSwitcher.showEmpty();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // This method might be called very early. Null check on bookmark model here.
        if (mBookmarkModel == null) return;

        if (visibility == View.VISIBLE) {
            mBookmarkModel.addObserver(mModelObserver);
            updateHistoryList();
            mSearchText.requestFocus();
            UiUtils.showKeyboard(mSearchText);
        } else {
            UiUtils.hideKeyboard(mSearchText);
            mBookmarkModel.removeObserver(mModelObserver);
            resetUI();
            clearFocus();
        }
    }

    private void onBackPressed() {
        if (mCurrentUIState == UIState.HISTORY) {
            mDelegate.closeSearchUI();
        } else {
            resetUI();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        assert parent == mHistoryList : "Only history list should have onItemClickListener.";
        mSearchText.setText((String) parent.getAdapter().getItem(position));
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            UiUtils.hideKeyboard(v);

            // History is saved either when the user clicks search button or a search result is
            // clicked.
            saveSearchHistory();
        }
        return false;
    }

    private void saveHistoryList(List<String> history) {
        JSONArray jsonArray = new JSONArray(history);
        ContextUtils.getAppSharedPreferences().edit()
                .putString(PREF_SEARCH_HISTORY, jsonArray.toString()).apply();
    }

    private List<String> readHistoryList() {
        try {
            String unformatted = ContextUtils.getAppSharedPreferences()
                    .getString(PREF_SEARCH_HISTORY, "[]");
            JSONArray jsonArray = new JSONArray(unformatted);
            ArrayList<String> result = new ArrayList<String>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (JSONException e) {
            return new ArrayList<String>();
        }
    }

    /**
     * Adds the current search text as top entry of the list.
     */
    private List<String> addCurrentTextToHistoryList(List<String> history) {
        String text = mSearchText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return history;

        history.remove(text);
        history.add(0, text);
        if (history.size() > SEARCH_HISTORY_MAX_ENTRIES) {
            history.remove(history.size() - 1);
        }
        return history;
    }

    // SearchHistoryDelegate implementation

    @Override
    public void saveSearchHistory() {
        saveHistoryList((addCurrentTextToHistoryList(readHistoryList())));
    }

    // BookmarkUIObserver implementation

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        mBookmarkModel = mDelegate.getModel();
    }

    @Override
    public void onDestroy() {
        mBookmarkModel.removeObserver(mModelObserver);
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
    }

    private class ResultListAdapter extends BaseAdapter {
        private BookmarkDelegate mDelegate;
        private List<BookmarkMatch> mBookmarktList;

        public ResultListAdapter(List<BookmarkMatch> bookmarkMatches,
                BookmarkDelegate delegate) {
            mDelegate = delegate;
            mBookmarktList = bookmarkMatches;
        }

        @Override
        public int getCount() {
            return mBookmarktList.size();
        }

        @Override
        public BookmarkMatch getItem(int position) {
            return mBookmarktList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final BookmarkMatch bookmarkMatch = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.bookmark_search_row, parent, false);
            }
            final BookmarkSearchRow row = (BookmarkSearchRow) convertView;
            row.onBookmarkDelegateInitialized(mDelegate);
            row.setBookmarkId(bookmarkMatch.getBookmarkId());
            row.setSearchHistoryDelegate(BookmarkSearchView.this);
            return convertView;
        }
    }
}
