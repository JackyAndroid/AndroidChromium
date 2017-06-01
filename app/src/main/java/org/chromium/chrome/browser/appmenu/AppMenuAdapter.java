// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omaha.UpdateMenuItemHelper;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.util.List;

/**
 * ListAdapter to customize the view of items in the list.
 *
 * The icon row in the menu is a special case of a MenuItem that displays multiple buttons in a row.
 * If, for some unfathomable reason, you need to add yet another icon to the row (the current max
 * is five), then you will need to:
 *
 * 1) Update icon_row_menu_item.xml to have as many buttons as you need.
 * 2) Edit the {@link BUTTON_IDS} to reference your new button.
 * 3) Hope that the icon row still fits on small phones.
 */
class AppMenuAdapter extends BaseAdapter {
    /**
     * Regular Android menu item that contains a title and an icon if icon is specified.
     */
    private static final int STANDARD_MENU_ITEM = 0;

    /**
     * Menu item that has two buttons, the first one is a title and the second one is an icon.
     * It is different from the regular menu item because it contains two separate buttons.
     */
    private static final int TITLE_BUTTON_MENU_ITEM = 1;

    /**
     * Menu item that has three buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int THREE_BUTTON_MENU_ITEM = 2;

    /**
     * Menu item that has four buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int FOUR_BUTTON_MENU_ITEM = 3;

    /**
     * Menu item that has five buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int FIVE_BUTTON_MENU_ITEM = 4;

    /**
     * Menu item for updating Chrome; uses a custom layout.
     */
    private static final int UPDATE_MENU_ITEM = 5;

    /**
     * The number of view types specified above.  If you add a view type you MUST increment this.
     */
    private static final int VIEW_TYPE_COUNT = 6;

    /** IDs of all of the buttons in icon_row_menu_item.xml. */
    private static final int[] BUTTON_IDS = {
        R.id.button_one,
        R.id.button_two,
        R.id.button_three,
        R.id.button_four,
        R.id.button_five
    };

    /** MenuItem Animation Constants */
    private static final int ENTER_ITEM_DURATION_MS = 350;
    private static final int ENTER_ITEM_BASE_DELAY_MS = 80;
    private static final int ENTER_ITEM_ADDL_DELAY_MS = 30;
    private static final float ENTER_STANDARD_ITEM_OFFSET_Y_DP = -10.f;
    private static final float ENTER_STANDARD_ITEM_OFFSET_X_DP = 10.f;

    private final AppMenu mAppMenu;
    private final LayoutInflater mInflater;
    private final List<MenuItem> mMenuItems;
    private final int mNumMenuItems;
    private final float mDpToPx;

    public AppMenuAdapter(AppMenu appMenu, List<MenuItem> menuItems, LayoutInflater inflater) {
        mAppMenu = appMenu;
        mMenuItems = menuItems;
        mInflater = inflater;
        mNumMenuItems = menuItems.size();
        mDpToPx = inflater.getContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public int getCount() {
        return mNumMenuItems;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        MenuItem item = getItem(position);
        int viewCount = item.hasSubMenu() ? item.getSubMenu().size() : 1;

        if (item.getItemId() == R.id.update_menu_id) {
            return UPDATE_MENU_ITEM;
        } else if (viewCount == 5) {
            return FIVE_BUTTON_MENU_ITEM;
        } else if (viewCount == 4) {
            return FOUR_BUTTON_MENU_ITEM;
        } else if (viewCount == 3) {
            return THREE_BUTTON_MENU_ITEM;
        } else if (viewCount == 2) {
            return TITLE_BUTTON_MENU_ITEM;
        }
        return STANDARD_MENU_ITEM;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getItemId();
    }

    @Override
    public MenuItem getItem(int position) {
        if (position == ListView.INVALID_POSITION) return null;
        assert position >= 0;
        assert position < mMenuItems.size();
        return mMenuItems.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final MenuItem item = getItem(position);
        switch (getItemViewType(position)) {
            case STANDARD_MENU_ITEM: {
                StandardMenuItemViewHolder holder = null;
                if (convertView == null
                        || !(convertView.getTag() instanceof StandardMenuItemViewHolder)) {
                    holder = new StandardMenuItemViewHolder();
                    convertView = mInflater.inflate(R.layout.menu_item, parent, false);
                    holder.text = (TextView) convertView.findViewById(R.id.menu_item_text);
                    holder.image = (AppMenuItemIcon) convertView.findViewById(R.id.menu_item_icon);
                    convertView.setTag(holder);
                    convertView.setTag(R.id.menu_item_enter_anim_id,
                            buildStandardItemEnterAnimator(convertView, position));
                } else {
                    holder = (StandardMenuItemViewHolder) convertView.getTag();
                }

                setupStandardMenuItemViewHolder(holder, convertView, item);
                break;
            }
            case UPDATE_MENU_ITEM: {
                CustomMenuItemViewHolder holder = null;
                if (convertView == null
                        || !(convertView.getTag() instanceof CustomMenuItemViewHolder)) {
                    holder = new CustomMenuItemViewHolder();
                    convertView = mInflater.inflate(R.layout.update_menu_item, parent, false);
                    holder.text = (TextView) convertView.findViewById(R.id.menu_item_text);
                    holder.image = (AppMenuItemIcon) convertView.findViewById(R.id.menu_item_icon);
                    holder.summary = (TextView) convertView.findViewById(R.id.menu_item_summary);
                    convertView.setTag(holder);
                    convertView.setTag(R.id.menu_item_enter_anim_id,
                            buildStandardItemEnterAnimator(convertView, position));
                } else {
                    holder = (CustomMenuItemViewHolder) convertView.getTag();
                }

                setupStandardMenuItemViewHolder(holder, convertView, item);
                String summary = UpdateMenuItemHelper.getInstance().getMenuItemSummaryText(
                        mInflater.getContext());
                if (TextUtils.isEmpty(summary)) {
                    holder.summary.setVisibility(View.GONE);
                } else {
                    holder.summary.setText(summary);
                }

                break;
            }
            case THREE_BUTTON_MENU_ITEM: {
                convertView = createMenuItemRow(convertView, parent, item, 3);
                break;
            }
            case FOUR_BUTTON_MENU_ITEM: {
                convertView = createMenuItemRow(convertView, parent, item, 4);
                break;
            }
            case FIVE_BUTTON_MENU_ITEM: {
                convertView = createMenuItemRow(convertView, parent, item, 5);
                break;
            }
            case TITLE_BUTTON_MENU_ITEM: {
                TitleButtonMenuItemViewHolder holder = null;
                if (convertView == null
                        || !(convertView.getTag() instanceof TitleButtonMenuItemViewHolder)) {
                    holder = new TitleButtonMenuItemViewHolder();
                    convertView = mInflater.inflate(R.layout.title_button_menu_item, parent, false);
                    holder.title = (TextView) convertView.findViewById(R.id.title);
                    holder.button = (TintedImageButton) convertView.findViewById(R.id.button);

                    View animatedView = convertView;

                    convertView.setTag(holder);
                    convertView.setTag(R.id.menu_item_enter_anim_id,
                            buildStandardItemEnterAnimator(animatedView, position));
                } else {
                    holder = (TitleButtonMenuItemViewHolder) convertView.getTag();
                }
                final MenuItem titleItem = item.hasSubMenu() ? item.getSubMenu().getItem(0) : item;
                holder.title.setText(titleItem.getTitle());
                holder.title.setEnabled(titleItem.isEnabled());
                holder.title.setFocusable(titleItem.isEnabled());
                holder.title.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAppMenu.onItemClick(titleItem);
                    }
                });

                if (item.getSubMenu().getItem(1).getIcon() != null) {
                    holder.button.setVisibility(View.VISIBLE);
                    setupImageButton(holder.button, item.getSubMenu().getItem(1));
                } else {
                    holder.button.setVisibility(View.GONE);
                }
                convertView.setFocusable(false);
                convertView.setEnabled(false);
                break;
            }
            default:
                assert false : "Unexpected MenuItem type";
        }
        return convertView;
    }

    private void setupImageButton(TintedImageButton button, final MenuItem item) {
        // Store and recover the level of image as button.setimageDrawable
        // resets drawable to default level.
        int currentLevel = item.getIcon().getLevel();
        button.setImageDrawable(item.getIcon());
        item.getIcon().setLevel(currentLevel);
        if (item.isChecked()) {
            button.setTint(ApiCompatibilityUtils.getColorStateList(
                    button.getResources(), R.color.blue_mode_tint));
        }
        button.setEnabled(item.isEnabled());
        button.setFocusable(item.isEnabled());
        button.setContentDescription(item.getTitleCondensed());

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAppMenu.onItemClick(item);
            }
        });

        // Menu items may be hidden by command line flags before they get to this point.
        button.setVisibility(item.isVisible() ? View.VISIBLE : View.GONE);
    }

    private void setupStandardMenuItemViewHolder(StandardMenuItemViewHolder holder,
            View convertView, final MenuItem item) {
        // Set up the icon.
        Drawable icon = item.getIcon();
        holder.image.setImageDrawable(icon);
        holder.image.setVisibility(icon == null ? View.GONE : View.VISIBLE);
        holder.image.setChecked(item.isChecked());
        holder.text.setText(item.getTitle());
        holder.text.setContentDescription(item.getTitleCondensed());

        boolean isEnabled = item.isEnabled();
        // Set the text color (using a color state list).
        holder.text.setEnabled(isEnabled);
        // This will ensure that the item is not highlighted when selected.
        convertView.setEnabled(isEnabled);

        convertView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAppMenu.onItemClick(item);
            }
        });
    }

    /**
     * This builds an {@link Animator} for the enter animation of a standard menu item.  This means
     * it will animate the alpha from 0 to 1 and translate the view from -10dp to 0dp on the y axis.
     *
     * @param view     The menu item {@link View} to be animated.
     * @param position The position in the menu.  This impacts the start delay of the animation.
     * @return         The {@link Animator}.
     */
    private Animator buildStandardItemEnterAnimator(final View view, int position) {
        final float offsetYPx = ENTER_STANDARD_ITEM_OFFSET_Y_DP * mDpToPx;
        final int startDelay = ENTER_ITEM_BASE_DELAY_MS + ENTER_ITEM_ADDL_DELAY_MS * position;

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0.f, 1.f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, offsetYPx, 0.f));
        animation.setDuration(ENTER_ITEM_DURATION_MS);
        animation.setStartDelay(startDelay);
        animation.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setAlpha(0.f);
            }
        });
        return animation;
    }

    /**
     * This builds an {@link Animator} for the enter animation of icon row menu items.  This means
     * it will animate the alpha from 0 to 1 and translate the views from 10dp to 0dp on the x axis.
     *
     * @param views        The list if icons in the menu item that should be animated.
     * @return             The {@link Animator}.
     */
    private Animator buildIconItemEnterAnimator(final ImageView[] views) {
        final boolean rtl = LocalizationUtils.isLayoutRtl();
        final float offsetXPx = ENTER_STANDARD_ITEM_OFFSET_X_DP * mDpToPx * (rtl ? -1.f : 1.f);
        final int maxViewsToAnimate = views.length;

        AnimatorSet animation = new AnimatorSet();
        AnimatorSet.Builder builder = null;
        for (int i = 0; i < maxViewsToAnimate; i++) {
            final int startDelay = ENTER_ITEM_ADDL_DELAY_MS * i;

            Animator alpha = ObjectAnimator.ofFloat(views[i], View.ALPHA, 0.f, 1.f);
            Animator translate = ObjectAnimator.ofFloat(views[i], View.TRANSLATION_X, offsetXPx, 0);
            alpha.setStartDelay(startDelay);
            translate.setStartDelay(startDelay);
            alpha.setDuration(ENTER_ITEM_DURATION_MS);
            translate.setDuration(ENTER_ITEM_DURATION_MS);

            if (builder == null) {
                builder = animation.play(alpha);
            } else {
                builder.with(alpha);
            }
            builder.with(translate);
        }
        animation.setStartDelay(ENTER_ITEM_BASE_DELAY_MS);
        animation.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (int i = 0; i < maxViewsToAnimate; i++) {
                    views[i].setAlpha(0.f);
                }
            }
        });
        return animation;
    }

    private View createMenuItemRow(
            View convertView, ViewGroup parent, MenuItem item, int numItems) {
        RowItemViewHolder holder = null;
        if (convertView == null
                || !(convertView.getTag() instanceof RowItemViewHolder)
                || ((RowItemViewHolder) convertView.getTag()).buttons.length != numItems) {
            holder = new RowItemViewHolder(numItems);
            convertView = mInflater.inflate(R.layout.icon_row_menu_item, parent, false);

            // Save references to all the buttons.
            for (int i = 0; i < numItems; i++) {
                holder.buttons[i] =
                        (TintedImageButton) convertView.findViewById(BUTTON_IDS[i]);
            }

            // Remove unused menu items.
            for (int j = numItems; j < 5; j++) {
                ((ViewGroup) convertView).removeView(convertView.findViewById(BUTTON_IDS[j]));
            }

            convertView.setTag(holder);
            convertView.setTag(R.id.menu_item_enter_anim_id,
                    buildIconItemEnterAnimator(holder.buttons));
        } else {
            holder = (RowItemViewHolder) convertView.getTag();
        }

        for (int i = 0; i < numItems; i++) {
            setupImageButton(holder.buttons[i], item.getSubMenu().getItem(i));
        }
        convertView.setFocusable(false);
        convertView.setEnabled(false);
        return convertView;
    }

    static class StandardMenuItemViewHolder {
        public TextView text;
        public AppMenuItemIcon image;
    }

    static class CustomMenuItemViewHolder extends StandardMenuItemViewHolder {
        public TextView summary;
    }

    private static class RowItemViewHolder {
        public TintedImageButton[] buttons;

        RowItemViewHolder(int numButtons) {
            buttons = new TintedImageButton[numButtons];
        }
    }

    static class TitleButtonMenuItemViewHolder {
        public TextView title;
        public TintedImageButton button;
    }
}
