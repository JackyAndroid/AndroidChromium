// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * An {@link Adapter} that works with a {@link RecyclerView}. It sorts the given {@link List} of
 * {@link TimedItem}s according to their date, and divides them into sub lists and displays them in
 * different sections.
 * <p>
 * Subclasses should not care about the how date headers are placed in the list. Instead, they
 * should call {@link #loadItems(List)} with a list of {@link TimedItem}, and this adapter will
 * insert the headers automatically.
 */
public abstract class DateDividedAdapter extends Adapter<RecyclerView.ViewHolder> {

    /**
     * Interface that the {@link Adapter} uses to interact with the items it manages.
     */
    public interface TimedItem {
        /** @return The timestamp for this item. */
        long getTimestamp();

        /**
         * Returns an ID that uniquely identifies this TimedItem and doesn't change.
         * To avoid colliding with IDs generated for Date headers, at least one of the upper 32
         * bits of the long should be set.
         * @return ID that can uniquely identify the TimedItem.
         */
        long getStableId();
    }

    private static class DateViewHolder extends RecyclerView.ViewHolder {
        private TextView mTextView;

        public DateViewHolder(View view) {
            super(view);
            if (view instanceof TextView) mTextView = (TextView) view;
        }

        public void setDate(Date date) {
            // Calender.getInstance() may take long time to run, so Calendar object should be reused
            // as much as possible.
            Pair<Calendar, Calendar> pair = getCachedCalendars();
            Calendar cal1 = pair.first, cal2 = pair.second;
            cal1.setTimeInMillis(System.currentTimeMillis());
            cal2.setTime(date);

            StringBuilder builder = new StringBuilder();
            if (compareCalendar(cal1, cal2) == 0) {
                builder.append(mTextView.getContext().getString(R.string.today));
                builder.append(" - ");
            } else {
                // Set cal1 to yesterday.
                cal1.add(Calendar.DATE, -1);
                if (compareCalendar(cal1, cal2) == 0) {
                    builder.append(mTextView.getContext().getString(R.string.yesterday));
                    builder.append(" - ");
                }
            }
            builder.append(DateUtils.formatDateTime(mTextView.getContext(), date.getTime(),
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR));
            mTextView.setText(builder);
        }
    }

    /**
     * A bucket of items with the same date.
     */
    private static class ItemGroup {
        private final Date mDate;
        private final List<TimedItem> mItems = new ArrayList<>();

        private boolean mIsSorted;

        public ItemGroup(TimedItem item) {
            mDate = new Date(item.getTimestamp());
            mItems.add(item);
            mIsSorted = true;
        }

        public void addItem(TimedItem item) {
            mItems.add(item);
            mIsSorted = false;
        }

        /**
         * @return Whether the given date happens in the same day as the items in this group.
         */
        public boolean isSameDay(Date otherDate) {
            return compareDate(mDate, otherDate) == 0;
        }

        /**
         * @return The size of this group.
         */
        public int size() {
            // Plus 1 to account for the date header.
            return mItems.size() + 1;
        }

        public TimedItem getItemAt(int index) {
            // 0 is allocated to the date header.
            if (index == 0) return null;

            sortIfNeeded();
            return mItems.get(index - 1);
        }

        /**
         * Rather than sorting the list each time a new item is added, the list is sorted when
         * something requires a correct ordering of the items.
         */
        private void sortIfNeeded() {
            if (mIsSorted) return;
            mIsSorted = true;

            Collections.sort(mItems, new Comparator<TimedItem>() {
                @Override
                public int compare(TimedItem lhs, TimedItem rhs) {
                    // More recent items are listed first.  Ideally we'd use Long.compare, but that
                    // is an API level 19 call for some inexplicable reason.
                    long timeDelta = lhs.getTimestamp() - rhs.getTimestamp();
                    if (timeDelta > 0) {
                        return -1;
                    } else if (timeDelta == 0) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        }
    }

    // Cached async tasks to get the two Calendar objects, which are used when comparing dates.
    private static final AsyncTask<Void, Void, Calendar> sCal1 = createCalendar();
    private static final AsyncTask<Void, Void, Calendar> sCal2 = createCalendar();

    public static final int TYPE_DATE = 0;
    public static final int TYPE_NORMAL = 1;

    private int mSize;
    private SortedSet<ItemGroup> mItems = new TreeSet<>(new Comparator<ItemGroup>() {
        @Override
        public int compare(ItemGroup lhs, ItemGroup rhs) {
            return compareDate(lhs.mDate, rhs.mDate);
        }
    });

    /**
     * Creates a {@link ViewHolder} in the given view parent.
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    protected abstract ViewHolder createViewHolder(ViewGroup parent);

    /**
     * Binds the {@link ViewHolder} with the given {@link TimedItem}.
     * @see #onBindViewHolder(ViewHolder, int)
     */
    protected abstract void bindViewHolderForTimedItem(ViewHolder viewHolder, TimedItem item);

    /**
     * Gets the resource id of the view showing the date header.
     * Contract for subclasses: this view should be a {@link TextView}.
     */
    protected abstract int getTimedItemViewResId();

    /**
     * Loads a list of {@link TimedItem}s to this adapter. Any previous data will be removed.
     */
    public void loadItems(List<? extends TimedItem> timedItems) {
        mSize = 0;
        mItems.clear();
        for (TimedItem timedItem : timedItems) {
            Date date = new Date(timedItem.getTimestamp());
            boolean found = false;
            for (ItemGroup item : mItems) {
                if (item.isSameDay(date)) {
                    found = true;
                    item.addItem(timedItem);
                    mSize++;
                    break;
                }
            }
            if (!found) {
                // Create a new ItemGroup with the date for the new item. This increases the
                // size by two because we add new views for the date and the item itself.
                mItems.add(new ItemGroup(timedItem));
                mSize += 2;
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Removes all items from this adapter.
     */
    public void clear() {
        mSize = 0;
        mItems.clear();
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (!hasStableIds()) return RecyclerView.NO_ID;

        Pair<Date, TimedItem> pair = getItemAt(position);
        return pair.second == null ? getStableIdFromDate(pair.first) : pair.second.getStableId();
    }

    /**
     * Gets the item at the given position. For date headers, {@link TimedItem} will be null; for
     * normal items, {@link Date} will be null.
     */
    public Pair<Date, TimedItem> getItemAt(int position) {
        Pair<ItemGroup, Integer> pair = getGroupAt(position);
        ItemGroup group = pair.first;
        return new Pair<>(group.mDate, group.getItemAt(pair.second));
    }

    @Override
    public final int getItemViewType(int position) {
        Pair<ItemGroup, Integer> pair = getGroupAt(position);
        return pair.second == 0 ? TYPE_DATE : TYPE_NORMAL;
    }

    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE) {
            return new DateViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                    getTimedItemViewResId(), parent, false));
        } else if (viewType == TYPE_NORMAL) {
            return createViewHolder(parent);
        }
        return null;
    }

    @Override
    public final void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Pair<Date, TimedItem> pair = getItemAt(position);
        if (holder instanceof DateViewHolder) {
            ((DateViewHolder) holder).setDate(pair.first);
        } else {
            bindViewHolderForTimedItem(holder, pair.second);
        }
    }

    @Override
    public final int getItemCount() {
        return mSize;
    }

    /**
     * Utility method to traverse all groups and find the {@link ItemGroup} for the given position.
     */
    private Pair<ItemGroup, Integer> getGroupAt(int position) {
        // TODO(ianwen): Optimize the performance if the number of groups becomes too large.
        int i = position;
        for (ItemGroup group : mItems) {
            if (i >= group.size()) {
                i -= group.size();
            } else {
                return new Pair<>(group, i);
            }
        }
        assert false;
        return null;
    }

    /**
     * Creates a long ID that identifies a particular day in history.
     * @param date Date to process.
     * @return Long that has the day of the year (1-365) in the lowest 16 bits and the year in the
     *         next 16 bits over.
     */
    private static long getStableIdFromDate(Date date) {
        Pair<Calendar, Calendar> pair = getCachedCalendars();
        Calendar calendar = pair.first;
        calendar.setTime(date);
        long dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        long year = calendar.get(Calendar.YEAR);
        return (year << 16) + dayOfYear;
    }

    /**
     * Compares two {@link Date}s. Note if you already have two {@link Calendar} objects, use
     * {@link #compareCalendar(Calendar, Calendar)} instead.
     * @return 0 if date1 and date2 are in the same day; 1 if date1 is before date2; -1 otherwise.
     */
    private static int compareDate(Date date1, Date date2) {
        Pair<Calendar, Calendar> pair = getCachedCalendars();
        Calendar cal1 = pair.first, cal2 = pair.second;
        cal1.setTime(date1);
        cal2.setTime(date2);
        return compareCalendar(cal1, cal2);
    }

    /**
     * @return 0 if cal1 and cal2 are in the same day; 1 if cal1 happens before cal2; -1 otherwise.
     */
    private static int compareCalendar(Calendar cal1, Calendar cal2) {
        boolean sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return 0;
        } else if (cal1.before(cal2)) {
            return 1;
        } else {
            return -1;
        }
    }

    /**
     * Convenient getter for {@link #sCal1} and {@link #sCal2}.
     */
    private static Pair<Calendar, Calendar> getCachedCalendars() {
        Calendar cal1, cal2;
        try {
            cal1 = sCal1.get();
            cal2 = sCal2.get();
        } catch (InterruptedException | ExecutionException e) {
            // We've tried our best. If AsyncTask really does not work, we give up. :(
            cal1 = Calendar.getInstance();
            cal2 = Calendar.getInstance();
        }
        return new Pair<>(cal1, cal2);
    }

    /**
     * Wraps {@link Calendar#getInstance()} in an {@link AsyncTask} to avoid Strict mode violation.
     */
    private static AsyncTask<Void, Void, Calendar> createCalendar() {
        return new AsyncTask<Void, Void, Calendar>() {
            @Override
            protected Calendar doInBackground(Void... unused) {
                return Calendar.getInstance();
            }
        }.execute();
    }
}
