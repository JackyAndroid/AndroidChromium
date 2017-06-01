// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.widget.TextViewWithClickableSpans;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A general-purpose dialog for presenting a list of things to pick from.
 *
 * The dialog is shown by the ItemChooserDialog constructor, and always calls
 * ItemSelectedCallback.onItemSelected() as it's closing.
 */
public class ItemChooserDialog {
    /**
     * An interface to implement to get a callback when something has been
     * selected.
     */
    public interface ItemSelectedCallback {
        /**
         * Returns the user selection.
         *
         * @param id The id of the item selected. Blank if the dialog was closed
         * without selecting anything.
         */
        void onItemSelected(String id);
    }

    /**
     * A class representing one data row in the picker.
     */
    public static class ItemChooserRow {
        private final String mKey;
        private String mDescription;

        public ItemChooserRow(String key, String description) {
            mKey = key;
            mDescription = description;
        }

        /**
         * Returns true if all parameters match the corresponding member.
         *
         * @param key Expected item unique identifier.
         * @param description Expected item description.
         */
        public boolean hasSameContents(String key, String description) {
            if (!TextUtils.equals(mKey, key)) return false;
            if (!TextUtils.equals(mDescription, description)) return false;
            return true;
        }
    }

    /**
     * The labels to show in the dialog.
     */
    public static class ItemChooserLabels {
        // The title at the top of the dialog.
        public final CharSequence title;
        // The message to show while there are no results.
        public final CharSequence searching;
        // The message to show when no results were produced.
        public final CharSequence noneFound;
        // A status message to show above the button row after an item has
        // been added and discovery is still ongoing.
        public final CharSequence statusActive;
        // A status message to show above the button row after discovery has
        // stopped and no devices have been found.
        public final CharSequence statusIdleNoneFound;
        // A status message to show above the button row after an item has
        // been added and discovery has stopped.
        public final CharSequence statusIdleSomeFound;
        // The label for the positive button (e.g. Select/Pair).
        public final CharSequence positiveButton;

        public ItemChooserLabels(CharSequence title, CharSequence searching, CharSequence noneFound,
                CharSequence statusActive, CharSequence statusIdleNoneFound,
                CharSequence statusIdleSomeFound, CharSequence positiveButton) {
            this.title = title;
            this.searching = searching;
            this.noneFound = noneFound;
            this.statusActive = statusActive;
            this.statusIdleNoneFound = statusIdleNoneFound;
            this.statusIdleSomeFound = statusIdleSomeFound;
            this.positiveButton = positiveButton;
        }
    }

    /**
     * Item holder for performance boost.
     */
    private static class ViewHolder {
        private TextView mTextView;

        public ViewHolder(View view) {
            mTextView = (TextView) view.findViewById(R.id.description);
        }
    }

    /**
     * The various states the dialog can represent.
     */
    private enum State { STARTING, PROGRESS_UPDATE_AVAILABLE, DISCOVERY_IDLE }

    /**
     * An adapter for keeping track of which items to show in the dialog.
     */
    public class ItemAdapter extends ArrayAdapter<ItemChooserRow>
            implements AdapterView.OnItemClickListener {
        private final LayoutInflater mInflater;

        // The zero-based index of the item currently selected in the dialog,
        // or -1 (INVALID_POSITION) if nothing is selected.
        private int mSelectedItem = ListView.INVALID_POSITION;

        // A set of keys that are marked as disabled in the dialog.
        private Set<String> mDisabledEntries = new HashSet<String>();

        // Item descriptions are counted in a map.
        private Map<String, Integer> mItemDescriptionMap = new HashMap<>();

        // Map of keys to items so that we can access the items in O(1).
        private Map<String, ItemChooserRow> mKeyToItemMap = new HashMap<>();

        public ItemAdapter(Context context, int resource) {
            super(context, resource);

            mInflater = LayoutInflater.from(context);
        }

        @Override
        public boolean isEmpty() {
            boolean isEmpty = super.isEmpty();
            if (isEmpty) {
                assert mKeyToItemMap.isEmpty();
                assert mDisabledEntries.isEmpty();
                assert mItemDescriptionMap.isEmpty();
            } else {
                assert !mKeyToItemMap.isEmpty();
                assert !mItemDescriptionMap.isEmpty();
            }
            return isEmpty;
        }

        public void addOrUpdate(String key, String description) {
            ItemChooserRow oldItem = mKeyToItemMap.get(key);
            if (oldItem != null) {
                if (oldItem.hasSameContents(key, description)) {
                    // No need to update anything.
                    return;
                }

                if (!TextUtils.equals(oldItem.mDescription, description)) {
                    removeFromDescriptionsMap(oldItem.mDescription);
                    oldItem.mDescription = description;
                    addToDescriptionsMap(oldItem.mDescription);
                }

                notifyDataSetChanged();
                return;
            }

            assert !mKeyToItemMap.containsKey(key);
            ItemChooserRow newItem = new ItemChooserRow(key, description);
            mKeyToItemMap.put(key, newItem);

            addToDescriptionsMap(newItem.mDescription);
            add(newItem);
        }

        public void removeItemWithKey(String key) {
            ItemChooserRow oldItem = mKeyToItemMap.remove(key);
            if (oldItem == null) return;
            int oldItemPosition = getPosition(oldItem);
            // If the removed item is the item that is currently selected, deselect it
            // and disable the confirm button. Otherwise if the removed item is before
            // the currently selected item, the currently selected item's index needs
            // to be adjusted by one.
            if (oldItemPosition == mSelectedItem) {
                mSelectedItem = ListView.INVALID_POSITION;
                mConfirmButton.setEnabled(false);
            } else if (oldItemPosition < mSelectedItem) {
                --mSelectedItem;
            }
            removeFromDescriptionsMap(oldItem.mDescription);
            super.remove(oldItem);
        }

        @Override
        public void clear() {
            mSelectedItem = ListView.INVALID_POSITION;
            mKeyToItemMap.clear();
            mDisabledEntries.clear();
            mItemDescriptionMap.clear();
            mConfirmButton.setEnabled(false);
            super.clear();
        }

        /**
         * Returns the key of the currently selected item or blank if nothing is
         * selected.
         */
        public String getSelectedItemKey() {
            if (mSelectedItem == ListView.INVALID_POSITION) return "";
            ItemChooserRow row = getItem(mSelectedItem);
            if (row == null) return "";
            return row.mKey;
        }

        /**
         * Returns the text to be displayed on the chooser for an item. For items with the same
         * description, their unique keys are appended to distinguish them.
         * @param position The index of the item.
         */
        public String getDisplayText(int position) {
            ItemChooserRow item = getItem(position);
            String description = item.mDescription;
            int counter = mItemDescriptionMap.get(description);
            return counter == 1 ? description
                    : mActivity.getString(R.string.item_chooser_item_name_with_id, description,
                            item.mKey);
        }

        /**
         * Sets whether the item is enabled. Disabled items are grayed out.
         * @param id The id of the item to affect.
         * @param enabled Whether the item should be enabled or not.
         */
        public void setEnabled(String id, boolean enabled) {
            if (enabled) {
                mDisabledEntries.remove(id);
            } else {
                mDisabledEntries.add(id);
            }

            if (mSelectedItem != ListView.INVALID_POSITION) {
                ItemChooserRow selectedRow = getItem(mSelectedItem);
                if (id.equals(selectedRow.mKey)) {
                    mConfirmButton.setEnabled(enabled);
                }
            }

            notifyDataSetChanged();
        }

        @Override
        public boolean isEnabled(int position) {
            ItemChooserRow item = getItem(position);
            return !mDisabledEntries.contains(item.mKey);
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder row;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.item_chooser_dialog_row, parent, false);
                row = new ViewHolder(convertView);
                convertView.setTag(row);
            } else {
                row = (ViewHolder) convertView.getTag();
            }

            row.mTextView.setEnabled(isEnabled(position));
            row.mTextView.setText(getDisplayText(position));

            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
            mSelectedItem = position;
            mConfirmButton.setEnabled(true);
            notifyDataSetChanged();
        }

        private void addToDescriptionsMap(String description) {
            int count = mItemDescriptionMap.containsKey(description)
                    ? mItemDescriptionMap.get(description)
                    : 0;
            mItemDescriptionMap.put(description, count + 1);
        }

        private void removeFromDescriptionsMap(String description) {
            if (!mItemDescriptionMap.containsKey(description)) {
                return;
            }
            int count = mItemDescriptionMap.get(description);
            if (count == 1) {
                mItemDescriptionMap.remove(description);
            } else {
                mItemDescriptionMap.put(description, count - 1);
            }
        }
    }

    private Activity mActivity;

    // The dialog this class encapsulates.
    private Dialog mDialog;

    // The callback to notify when the user selected an item.
    private ItemSelectedCallback mItemSelectedCallback;

    // Individual UI elements.
    private TextViewWithClickableSpans mTitle;
    private TextViewWithClickableSpans mEmptyMessage;
    private ProgressBar mProgressBar;
    private ListView mListView;
    private TextView mStatus;
    private Button mConfirmButton;

    // The labels to display in the dialog.
    private ItemChooserLabels mLabels;

    // The adapter containing the items to show in the dialog.
    private ItemAdapter mItemAdapter;

    // How much of the height of the screen should be taken up by the listview.
    private static final float LISTVIEW_HEIGHT_PERCENT = 0.30f;
    // The height of a row of the listview in dp.
    private static final int LIST_ROW_HEIGHT_DP = 48;
    // The minimum height of the listview in the dialog (in dp).
    private static final int MIN_HEIGHT_DP = (int) (LIST_ROW_HEIGHT_DP * 1.5);
    // The maximum height of the listview in the dialog (in dp).
    private static final int MAX_HEIGHT_DP = (int) (LIST_ROW_HEIGHT_DP * 8.5);

    /**
     * Creates the ItemChooserPopup and displays it (and starts waiting for data).
     *
     * @param activity Activity which is used for launching a dialog.
     * @param callback The callback used to communicate back what was selected.
     * @param labels The labels to show in the dialog.
     */
    public ItemChooserDialog(
            Activity activity, ItemSelectedCallback callback, ItemChooserLabels labels) {
        mActivity = activity;
        mItemSelectedCallback = callback;
        mLabels = labels;

        LinearLayout dialogContainer = (LinearLayout) LayoutInflater.from(mActivity).inflate(
                R.layout.item_chooser_dialog, null);

        mListView = (ListView) dialogContainer.findViewById(R.id.items);
        mProgressBar = (ProgressBar) dialogContainer.findViewById(R.id.progress);
        mStatus = (TextView) dialogContainer.findViewById(R.id.status);
        mTitle = (TextViewWithClickableSpans) dialogContainer.findViewById(
                R.id.dialog_title);
        mEmptyMessage =
                (TextViewWithClickableSpans) dialogContainer.findViewById(R.id.not_found_message);

        mTitle.setText(labels.title);
        mTitle.setMovementMethod(LinkMovementMethod.getInstance());

        mEmptyMessage.setMovementMethod(LinkMovementMethod.getInstance());
        mStatus.setMovementMethod(LinkMovementMethod.getInstance());

        mConfirmButton = (Button) dialogContainer.findViewById(R.id.positive);
        mConfirmButton.setText(labels.positiveButton);
        mConfirmButton.setEnabled(false);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mItemSelectedCallback.onItemSelected(mItemAdapter.getSelectedItemKey());
                mDialog.setOnDismissListener(null);
                mDialog.dismiss();
            }
        });

        mItemAdapter = new ItemAdapter(mActivity, R.layout.item_chooser_dialog_row);
        mItemAdapter.setNotifyOnChange(true);
        mListView.setAdapter(mItemAdapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setEmptyView(mEmptyMessage);
        mListView.setOnItemClickListener(mItemAdapter);
        mListView.setDivider(null);
        setState(State.STARTING);

        // The list is the main element in the dialog and it should grow and
        // shrink according to the size of the screen available.
        View listViewContainer = dialogContainer.findViewById(R.id.container);
        listViewContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                getListHeight(mActivity.getWindow().getDecorView().getHeight(),
                        mActivity.getResources().getDisplayMetrics().density)));

        showDialogForView(dialogContainer);
    }

    // Computes the height of the device list, bound to half-multiples of the
    // row height so that it's obvious if there are more elements to scroll to.
    @VisibleForTesting
    static int getListHeight(int decorHeight, float density) {
        float heightDp = decorHeight / density * LISTVIEW_HEIGHT_PERCENT;
        // Round to (an integer + 0.5) times LIST_ROW_HEIGHT.
        heightDp = (Math.round(heightDp / LIST_ROW_HEIGHT_DP - 0.5f) + 0.5f) * LIST_ROW_HEIGHT_DP;
        heightDp = MathUtils.clamp(heightDp, MIN_HEIGHT_DP, MAX_HEIGHT_DP);
        return (int) Math.round(heightDp * density);
    }

    private void showDialogForView(View view) {
        mDialog = new Dialog(mActivity) {
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (!hasFocus) super.dismiss();
            }
        };
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.addContentView(view,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                                              LinearLayout.LayoutParams.MATCH_PARENT));
        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mItemSelectedCallback.onItemSelected("");
            }
        });

        Window window = mDialog.getWindow();
        if (!DeviceFormFactor.isTablet(mActivity)) {
            // On smaller screens, make the dialog fill the width of the screen,
            // and appear at the top.
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setGravity(Gravity.TOP);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                             ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        mDialog.show();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    /**
     * Adds an item to the end of the list to show in the dialog if the item
     * was not in the chooser. Otherwise updates the items description.
     *
     * @param key Unique identifier for that item.
     * @param description Text in the row.
     */
    public void addOrUpdateItem(String key, String description) {
        mProgressBar.setVisibility(View.GONE);
        mItemAdapter.addOrUpdate(key, description);
        setState(State.PROGRESS_UPDATE_AVAILABLE);
    }

    /**
     * Removes an item that is shown in the dialog.
     *
     * @param key Unique identifier for the item.
     */
    public void removeItemFromList(String key) {
        mItemAdapter.removeItemWithKey(key);
        setState(State.DISCOVERY_IDLE);
    }

    /**
     * Indicates the chooser that no more items will be added.
     */
    public void setIdleState() {
        mProgressBar.setVisibility(View.GONE);
        setState(State.DISCOVERY_IDLE);
    }

    /**
     * Sets whether the item is enabled.
     * @param key Unique indetifier for the item.
     * @param enabled Whether the item should be enabled or not.
     */
    public void setEnabled(String key, boolean enabled) {
        mItemAdapter.setEnabled(key, enabled);
    }

    /**
     * Clear all items from the dialog.
     */
    public void clear() {
        mItemAdapter.clear();
        setState(State.STARTING);
    }

    /**
     * Shows an error message in the dialog.
     */
    public void setErrorState(SpannableString errorMessage, SpannableString errorStatus) {
        mListView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mEmptyMessage.setText(errorMessage);
        mEmptyMessage.setVisibility(View.VISIBLE);
        mStatus.setText(errorStatus);
    }

    private void setState(State state) {
        switch (state) {
            case STARTING:
                mStatus.setText(mLabels.searching);
                mListView.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.VISIBLE);
                mEmptyMessage.setVisibility(View.GONE);
                break;
            case PROGRESS_UPDATE_AVAILABLE:
                mStatus.setText(mLabels.statusActive);
                mProgressBar.setVisibility(View.GONE);
                mListView.setVisibility(View.VISIBLE);
                break;
            case DISCOVERY_IDLE:
                boolean showEmptyMessage = mItemAdapter.isEmpty();
                mStatus.setText(showEmptyMessage
                        ? mLabels.statusIdleNoneFound : mLabels.statusIdleSomeFound);
                mEmptyMessage.setText(mLabels.noneFound);
                mEmptyMessage.setVisibility(showEmptyMessage ? View.VISIBLE : View.GONE);
                break;
        }
    }

    /**
     * Returns the dialog associated with this class. For use with tests only.
     */
    @VisibleForTesting
    public Dialog getDialogForTesting() {
        return mDialog;
    }

    /**
     * Returns the ItemAdapter associated with this class. For use with tests only.
     */
    @VisibleForTesting
    public ItemAdapter getItemAdapterForTesting() {
        return mItemAdapter;
    }
}
