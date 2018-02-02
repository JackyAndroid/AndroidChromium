// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.infobar.InfoBarControlLayout.InfoBarArrayAdapter;

import java.util.ArrayList;

/**
 * Language panel shown in the translate infobar.
 */
public class TranslateLanguagePanel
        implements TranslateSubPanel, AdapterView.OnItemSelectedListener {

    private static final int LANGUAGE_TYPE_SOURCE = 0;
    private static final int LANGUAGE_TYPE_TARGET = 1;

    // UI elements.
    private Spinner mSourceSpinner;
    private Spinner mTargetSpinner;

    // Items that are not interacted with.
    // Provided by the caller, the new languages will be set here if the user
    // clicks "done".
    private final TranslateOptions mOptions;

    // This object will be used to keep the state for the time the
    // panel is opened it can be totally discarded in the end if the user
    // clicks "cancel".
    private final TranslateOptions mSessionOptions;

    private InfoBarArrayAdapter<SpinnerLanguageElement> mSourceAdapter;
    private InfoBarArrayAdapter<SpinnerLanguageElement> mTargetAdapter;

    private final SubPanelListener mListener;

    /**
     * Display language drop downs so they can be picked as source or
     * target for a translation.
     *
     * @param listener triggered when the panel is closed
     * @param options will be modified with the new languages selected.
     */
    public TranslateLanguagePanel(SubPanelListener listener, TranslateOptions options) {
        mListener = listener;
        mOptions = options;
        mSessionOptions = new TranslateOptions(mOptions);
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        mSourceSpinner = null;
        mTargetSpinner = null;

        String changeLanguage = context.getString(R.string.translate_infobar_change_languages);
        layout.setMessage(changeLanguage);

        setUpSpinners(context, layout);

        // Set up the buttons.
        layout.setButtons(context.getString(R.string.translate_button_done),
                context.getString(R.string.cancel));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mOptions.setSourceLanguage(mSessionOptions.sourceLanguageCode());
            mOptions.setTargetLanguage(mSessionOptions.targetLanguageCode());
        }
        mListener.onPanelClosed(ActionType.NONE);
    }

    private void setUpSpinners(Context context, InfoBarLayout layout) {
        // Set up the spinners.
        InfoBarControlLayout controlLayout = layout.addControlLayout();
        mSourceAdapter = new InfoBarArrayAdapter<SpinnerLanguageElement>(
                context, context.getString(R.string.translate_options_source_hint));
        mTargetAdapter = new InfoBarArrayAdapter<SpinnerLanguageElement>(
                context, context.getString(R.string.translate_options_target_hint));

        mSourceSpinner =
                controlLayout.addSpinner(R.id.translate_infobar_source_spinner, mSourceAdapter);
        mTargetSpinner =
                controlLayout.addSpinner(R.id.translate_infobar_target_spinner, mTargetAdapter);

        mSourceSpinner.setOnItemSelectedListener(this);
        mTargetSpinner.setOnItemSelectedListener(this);
        reloadSpinners();

        // Compute the minimum value width for one Spinner and use it for the other.
        mTargetAdapter.setMinWidthRequiredForValues(
                mSourceAdapter.computeMinWidthRequiredForValues());
    }

    private void reloadSpinners() {
        mSourceAdapter.clear();
        mTargetAdapter.clear();

        mSourceAdapter.addAll(createSpinnerLanguages(mSessionOptions.targetLanguageCode()));
        mTargetAdapter.addAll(createSpinnerLanguages(mSessionOptions.sourceLanguageCode()));

        int originalSourceSelection = mSourceSpinner.getSelectedItemPosition();
        int newSourceSelection = getSelectionPosition(LANGUAGE_TYPE_SOURCE);
        if (originalSourceSelection != newSourceSelection) {
            mSourceSpinner.setSelection(newSourceSelection);
        }

        int originalTargetSelection = mTargetSpinner.getSelectedItemPosition();
        int newTargetSelection = getSelectionPosition(LANGUAGE_TYPE_TARGET);
        if (originalTargetSelection != newTargetSelection) {
            mTargetSpinner.setSelection(newTargetSelection);
        }
    }

    private int getSelectionPosition(int languageType) {
        String position_code = languageType == LANGUAGE_TYPE_SOURCE
                ? mSessionOptions.sourceLanguageCode()
                : mSessionOptions.targetLanguageCode();

        // Since the source and target languages cannot appear in both spinners,
        // the index for the source language can be off by one if comes after the
        // target language alphabetically (and vice versa).
        String opposite_code = languageType == LANGUAGE_TYPE_SOURCE
                ? mSessionOptions.targetLanguageCode()
                : mSessionOptions.sourceLanguageCode();

        int position = -1;
        int opposite = -1;

        for (int i = 0; i < mSessionOptions.allLanguages().size(); ++i) {
            if (mSessionOptions.allLanguages().get(i).mLanguageCode.equals(position_code)) {
                position = i;
            }
            if (mSessionOptions.allLanguages().get(i).mLanguageCode.equals(opposite_code)) {
                opposite = i;
            }
            if (opposite > -1 && position > -1) break;
        }
        if (opposite < position) position -= 1;

        return position;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
        Spinner spinner = (Spinner) adapter;
        String newCode = ((SpinnerLanguageElement) spinner.getSelectedItem()).getLanguageCode();
        if (spinner == mSourceSpinner && !newCode.equals(mSessionOptions.sourceLanguageCode())) {
            mSessionOptions.setSourceLanguage(newCode);
            reloadSpinners();
        }
        if (spinner == mTargetSpinner && !newCode.equals(mSessionOptions.targetLanguageCode())) {
            mSessionOptions.setTargetLanguage(newCode);
            reloadSpinners();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapter) {
    }

    /**
     * Determines what languages will be shown in the Spinner.
     * @param avoidCode ISO code of the language to avoid displaying.
     * Use "" to display all languages.
     */
    private ArrayList<SpinnerLanguageElement> createSpinnerLanguages(String avoidCode) {
        ArrayList<SpinnerLanguageElement> result = new ArrayList<SpinnerLanguageElement>();
        for (TranslateOptions.TranslateLanguagePair language : mSessionOptions.allLanguages()) {
            if (!language.mLanguageCode.equals(avoidCode)) {
                result.add(new SpinnerLanguageElement(
                        language.mLanguageRepresentation, language.mLanguageCode));
            }
        }
        return result;
    }

    /**
     * The element that goes inside the spinner.
     */
    private static class SpinnerLanguageElement {
        private final String mLanguageName;
        private final String mLanguageCode;

        public SpinnerLanguageElement(String languageName, String languageCode) {
            mLanguageName = languageName;
            mLanguageCode = languageCode;
        }

        public String getLanguageCode() {
            return mLanguageCode;
        }

        /**
         * This is the text displayed in the spinner element so make sure no debug information
         * is added.
         */
        @Override
        public String toString() {
            return mLanguageName;
        }
    }
}
