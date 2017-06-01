// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.List;

/**
 * A RadioButton with a title and descriptive text to the right.
 */
public class RadioButtonWithDescription extends RelativeLayout implements OnClickListener {
    private RadioButton mRadioButton;
    private TextView mTitle;
    private TextView mDescription;

    private List<RadioButtonWithDescription> mGroup;

    private static final String SUPER_STATE_KEY = "superState";
    private static final String CHECKED_KEY = "isChecked";

    /**
     * Constructor for inflating via XML.
     */
    public RadioButtonWithDescription(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.radio_button_with_description, this, true);

        mRadioButton = (RadioButton) findViewById(R.id.radio_button);
        mTitle = (TextView) findViewById(R.id.title);
        mDescription = (TextView) findViewById(R.id.description);

        if (attrs != null) applyAttributes(attrs);

        // We want RadioButtonWithDescription to handle the clicks itself.
        mRadioButton.setClickable(false);
        setOnClickListener(this);
    }

    private void applyAttributes(AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs,
                R.styleable.RadioButtonWithDescription, 0, 0);

        String titleText = a.getString(R.styleable.RadioButtonWithDescription_titleText);
        if (titleText != null) mTitle.setText(titleText);

        a.recycle();
    }

    @Override
    public void onClick(View v) {
        if (mGroup != null) {
            for (RadioButtonWithDescription button : mGroup) {
                button.setChecked(false);
            }
        }

        setChecked(true);
    }

    /**
     * Sets the text shown in the title section.
     */
    public void setTitleText(CharSequence text) {
        mTitle.setText(text);
    }

    /**
     * Sets the text shown in the description section.
     */
    public void setDescriptionText(CharSequence text) {
        mDescription.setText(text);
    }

    /**
     * Returns true if checked.
     */
    public boolean isChecked() {
        return mRadioButton.isChecked();
    }

    /**
     * Sets the checked status.
     */
    public void setChecked(boolean checked) {
        mRadioButton.setChecked(checked);
    }

    /**
     * Sets the group of RadioButtonWithDescriptions that should be unchecked when this button is
     * checked.
     * @param group A list containing all elements of the group.
     */
    public void setRadioButtonGroup(List<RadioButtonWithDescription> group) {
        mGroup = group;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        // Since this View is designed to be used multiple times in the same layout and contains
        // children with ids, Android gets confused. This is because it will see two Views in the
        // hierarchy with the same id (eg, the RadioButton that makes up this View).
        //
        // eg:
        // LinearLayout (no id):
        // |-> RadioButtonWithDescription (id=sync_confirm_import_choice)
        // |   |-> RadioButton            (id=radio_button)
        // |   |-> TextView               (id=title)
        // |   \-> TextView               (id=description)
        // \-> RadioButtonWithDescription (id=sync_keep_separate_choice)
        //     |-> RadioButton            (id=radio_button)
        //     |-> TextView               (id=title)
        //     \-> TextView               (id=description)
        //
        // This causes the automagic state saving and recovery to do the wrong thing and restore all
        // of these Views to the state of the last one it saved.
        // Therefore we manually save the state of the child Views here so the state can be
        // associated with the id of the RadioButtonWithDescription, which should be unique and
        // not the id of the RadioButtons, which will be duplicated.
        //
        // Note: We disable Activity recreation on many config changes (such as orientation),
        // but not for all of them (eg, locale or font scale change). So this code will only be
        // called on the latter ones, or when the Activity is destroyed due to memory constraints.
        Bundle saveState = new Bundle();
        saveState.putParcelable(SUPER_STATE_KEY, super.onSaveInstanceState());
        saveState.putBoolean(CHECKED_KEY, isChecked());
        return saveState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            super.onRestoreInstanceState(((Bundle) state).getParcelable(SUPER_STATE_KEY));
            setChecked(((Bundle) state).getBoolean(CHECKED_KEY));
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        // This method and dispatchRestoreInstanceState prevent the Android automagic state save
        // and restore from touching this View's children.
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }
}

