// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.Arrays;
import java.util.List;

/**
 * The adapter that populates the list popup for password generation with data. If the constructor
 * parameter passwordDisplayed is true, then this adapter makes the popup display two items in the
 * list: (1) the password suggestion and (2) an explanation of the password generation feature. If
 * the passwordDisplayed parameter is false, then the adapter shows only the explanation item.
 */
public class PasswordGenerationAdapter extends BaseAdapter {
    private final Context mContext;
    private final Delegate mDelegate;
    private final List<Integer> mViewTypes;
    private final String mPassword;
    private final String mSuggestionTitle;
    private final String mExplanationText;
    private final int mExplanationTextLinkRangeStart;
    private final int mExplanationTextLinkRangeEnd;
    private final int mSuggestionMeasuredWidth;

    /**
     * UI shows a generated password suggestion.
     */
    private static final int SUGGESTION = 0;

    /**
     * UI shows an explanation about storing passwords in Chrome.
     */
    private static final int EXPLANATION = 1;

    /**
     * There're 2 types of views: SUGGESTION and EXPLANATION.
     */
    private static final int VIEW_TYPE_COUNT = 2;

    /**
     * Handler for clicks on the "saved passwords" link.
     */
    public interface Delegate {
        /**
         * Called when the user clicks the "saved passwords" link.
         */
        public void onSavedPasswordsLinkClicked();
    }

    /**
     * Builds the adapter to display views using data from delegate.
     * @param context Android context.
     * @param delegate The handler for clicking on the "saved passwords" link.
     * @param passwordDisplayed Whether the auto-generated password should be suggested.
     * @param password The auto-generated password to suggest.
     * @param suggestionTitle The translated title of the suggestion part of the UI.
     * @param explanationText The translated text for the explanation part of the UI.
     * @param explanationTextLinkRangeStart The start of the range in the explanation text that
     * should be a link to the saved passwords.
     * @param explanationTextLinkRangeEnd The end of the range in the explanation text that should
     * be a link to the saved passwords.
     * @param anchorWidthInDp The width of the anchor to which the popup is attached. Used to size
     * the explanation view.
     */
    public PasswordGenerationAdapter(Context context, Delegate delegate, boolean passwordDisplayed,
            String password, String suggestionTitle, String explanationText,
            int explanationTextLinkRangeStart, int explanationTextLinkRangeEnd,
            float anchorWidthInDp) {
        super();
        mContext = context;
        mDelegate = delegate;
        mViewTypes = passwordDisplayed ? Arrays.asList(SUGGESTION, EXPLANATION)
                : Arrays.asList(EXPLANATION);
        mPassword = password;
        mSuggestionTitle = suggestionTitle;
        mExplanationText = explanationText;
        mExplanationTextLinkRangeStart = explanationTextLinkRangeStart;
        mExplanationTextLinkRangeEnd = explanationTextLinkRangeEnd;

        int horizontalMarginInPx = Math.round(mContext.getResources().getDimension(
                R.dimen.password_generation_horizontal_margin));
        int anchorWidthInPx = Math.round(anchorWidthInDp
                * mContext.getResources().getDisplayMetrics().density);
        View suggestion = getViewForType(SUGGESTION).findViewById(
                R.id.password_generation_suggestion);
        suggestion.setMinimumWidth(anchorWidthInPx - 2 * horizontalMarginInPx);
        suggestion.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mSuggestionMeasuredWidth = suggestion.getMeasuredWidth();
    }

    /**
     * Used by list popup window to draw an element.
     * @param position The position of the element in the popup list.
     * @param convertView If not null, the element view where the data goes.
     * @param parent The list popup.
     * @return The view of the popup list element at the given position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return convertView != null ? convertView : getViewForType(mViewTypes.get(position));
    }

    /**
     * Builds the view of this type.
     * @param type The type of view to build.
     * @return The view for this viewType.
     */
    private View getViewForType(int type) {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = null;
        switch (type) {
            case SUGGESTION:
                view = inflater.inflate(R.layout.password_generation_popup_suggestion, null);
                ((TextView) view.findViewById(R.id.password_generation_title))
                        .setText(mSuggestionTitle);
                ((TextView) view.findViewById(R.id.password_generation_password))
                        .setText(mPassword);
                break;

            case EXPLANATION:
                view = inflater.inflate(R.layout.password_generation_popup_explanation, null);
                TextView explanation = (TextView) view
                        .findViewById(R.id.password_generation_explanation);
                SpannableString explanationSpan = new SpannableString(mExplanationText);
                explanationSpan.setSpan(
                        new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                mDelegate.onSavedPasswordsLinkClicked();
                            }

                            @Override
                            public void updateDrawState(TextPaint textPaint) {
                                textPaint.setUnderlineText(false);
                                textPaint.setColor(ApiCompatibilityUtils.getColor(
                                        mContext.getResources(),
                                        R.color.password_generation_link_text_color));
                            }
                        }, mExplanationTextLinkRangeStart, mExplanationTextLinkRangeEnd,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                explanation.setText(explanationSpan);
                explanation.setMovementMethod(LinkMovementMethod.getInstance());
                explanation.setLayoutParams(new LayoutParams(mSuggestionMeasuredWidth,
                        LayoutParams.WRAP_CONTENT));
                break;

            default:
                assert false : "Unknown view type";
                break;
        }

        return view;
    }

    /**
     * Returns the data item associated with this position in the data set.
     * @return Always null.
     */
    @Override
    public Object getItem(int position) {
        return null;
    }

    /**
     * Returns the row ID for the data set item at this position.
     * @return Always position.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Used by the popup window to determine which view should be reused to render the list item at
     * this position.
     * @return Either SUGGESTION or EXPLANATION.
     */
    @Override
    public int getItemViewType(int position) {
        return mViewTypes.get(position);
    }

    /**
     * Used by the popup window to determine how many different views should be reused to render the
     * popup.
     * @return Always 2.
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    /**
     * Used by the popup window to determine how many items should be displayed in the list.
     * @return Either 1 or 2.
     */
    @Override
    public int getCount() {
        return mViewTypes.size();
    }

    /**
     * Used by list popup window to check if all of the elements are enabled. All password
     * generation popups have an explanation element, which is not selectable. Therefore, this
     * method always returns false: some of the items are disabled.
     * @return boolean Always false.
     */
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    /**
     * Used by list popup window to check if the element at this position is enabled. Only the
     * suggestion element is enabled.
     * @return boolean True if the view at position is a suggestion.
     */
    @Override
    public boolean isEnabled(int position) {
        return mViewTypes.get(position) == SUGGESTION;
    }
}
