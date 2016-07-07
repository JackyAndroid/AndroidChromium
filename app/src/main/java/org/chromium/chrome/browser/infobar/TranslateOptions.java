// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * A class that keeps the state of the different translation options and
 * languages.
 */
public class TranslateOptions {

    // This would be an enum but they are not good for mobile.
    // The checkBoundaries method below needs to be updated if new options are added.
    private static final int NEVER_LANGUAGE = 0;
    private static final int NEVER_DOMAIN = 1;
    private static final int ALWAYS_LANGUAGE = 2;

    private final String[] mAllLanguages;

    // Will reflect the state before the object was ever modified
    private final boolean[] mOriginalOptions;
    private final int mOriginalSourceLanguageIndex;
    private final int mOriginalTargetLanguageIndex;

    private final boolean[] mOptions;
    private int mSourceLanguageIndex;
    private int mTargetLanguageIndex;
    private final boolean mTriggeredFromMenu;

    private TranslateOptions(int sourceLanguageCode, int targetLanguageCode, String[] allLanguages,
            boolean neverLanguage, boolean neverDomain, boolean alwaysLanguage,
            boolean triggeredFromMenu, boolean[] originalOptions) {
        mAllLanguages = allLanguages;
        mSourceLanguageIndex = sourceLanguageCode;
        mTargetLanguageIndex = targetLanguageCode;
        mTriggeredFromMenu = triggeredFromMenu;

        mOptions = new boolean[3];
        mOptions[NEVER_LANGUAGE] = neverLanguage;
        mOptions[NEVER_DOMAIN] = neverDomain;
        mOptions[ALWAYS_LANGUAGE] = alwaysLanguage;


        if (originalOptions == null) {
            mOriginalOptions = mOptions.clone();
        } else {
            mOriginalOptions = originalOptions.clone();
        }

        mOriginalSourceLanguageIndex = mSourceLanguageIndex;
        mOriginalTargetLanguageIndex = mTargetLanguageIndex;
    }

    public TranslateOptions(int sourceLanguageCode, int targetLanguageCode, String[] allLanguages,
            boolean alwaysTranslate, boolean triggeredFromMenu) {
        this(sourceLanguageCode, targetLanguageCode, allLanguages, false, false, alwaysTranslate,
                triggeredFromMenu, null);
    }

    /**
     * Copy constructor
     */
    public TranslateOptions(TranslateOptions other) {
        this(other.mSourceLanguageIndex, other.mTargetLanguageIndex, other.mAllLanguages,
                other.mOptions[NEVER_LANGUAGE], other.mOptions[NEVER_DOMAIN],
                other.mOptions[ALWAYS_LANGUAGE], other.mTriggeredFromMenu,
                other.mOriginalOptions);
    }

    public String sourceLanguage() {
        if (checkLanguageBoundaries(mSourceLanguageIndex)) {
            return mAllLanguages[mSourceLanguageIndex];
        }
        return "";
    }

    public String targetLanguage() {
        if (checkLanguageBoundaries(mTargetLanguageIndex)) {
            return mAllLanguages[mTargetLanguageIndex];
        }
        return "";
    }

    public int sourceLanguageIndex() {
        return checkLanguageBoundaries(mSourceLanguageIndex) ? mSourceLanguageIndex : 0;
    }

    public int targetLanguageIndex() {
        return checkLanguageBoundaries(mTargetLanguageIndex) ? mTargetLanguageIndex : 0;
    }

    public boolean triggeredFromMenu() {
        return mTriggeredFromMenu;
    }

    public boolean optionsChanged() {
        return (mSourceLanguageIndex != mOriginalSourceLanguageIndex)
                || (mTargetLanguageIndex != mOriginalTargetLanguageIndex)
                || (mOptions[NEVER_LANGUAGE] != mOriginalOptions[NEVER_LANGUAGE])
                || (mOptions[NEVER_DOMAIN] != mOriginalOptions[NEVER_DOMAIN])
                || (mOptions[ALWAYS_LANGUAGE] != mOriginalOptions[ALWAYS_LANGUAGE]);
    }


    public List<String> allLanguages() {
        return Collections.unmodifiableList(Arrays.asList(mAllLanguages));
    }

    public boolean neverTranslateLanguageState() {
        return mOptions[NEVER_LANGUAGE];
    }

    public boolean alwaysTranslateLanguageState() {
        return mOptions[ALWAYS_LANGUAGE];
    }

    public boolean neverTranslateDomainState() {
        return mOptions[NEVER_DOMAIN];
    }

    public boolean setSourceLanguage(int languageIndex) {
        boolean canSet = canSetLanguage(languageIndex, mTargetLanguageIndex);
        if (canSet) {
            mSourceLanguageIndex = languageIndex;
        }
        return canSet;
    }

    public boolean setTargetLanguage(int languageIndex) {
        boolean canSet = canSetLanguage(mSourceLanguageIndex, languageIndex);
        if (canSet) {
            mTargetLanguageIndex = languageIndex;
        }
        return canSet;
    }

    /**
     * Sets the new state of never translate domain.
     *
     * @return true if the toggling was possible
     */
    public boolean toggleNeverTranslateDomainState(boolean value) {
        return toggleState(NEVER_DOMAIN, value);
    }

    /**
     * Sets the new state of never translate language.
     *
     * @return true if the toggling was possible
     */
    public boolean toggleNeverTranslateLanguageState(boolean value) {
        // Do not toggle if we are activating NeverLanguge but AlwaysTranslate
        // for a language pair with the same source language is already active.
        if (mOptions[ALWAYS_LANGUAGE] && value) {
            return false;
        }
        return toggleState(NEVER_LANGUAGE, value);
    }

    /**
     * Sets the new state of never translate a language pair.
     *
     * @return true if the toggling was possible
     */
    public boolean toggleAlwaysTranslateLanguageState(boolean value) {
        // Do not toggle if we are activating AlwaysLanguge but NeverLanguage is active already.
        if (mOptions[NEVER_LANGUAGE] && value) {
            return false;
        }
        return toggleState(ALWAYS_LANGUAGE, value);
    }

    private boolean toggleState(int element, boolean newValue) {
        if (!checkElementBoundaries(element)) return false;

        mOptions[element] = newValue;
        return true;
    }


    private boolean checkLanguageBoundaries(int index) {
        return index >= 0 && index < mAllLanguages.length;
    }

    private boolean canSetLanguage(int sourceIndex, int targetIndex) {
        if (sourceIndex == targetIndex) return false;
        return checkLanguageBoundaries(sourceIndex) && checkLanguageBoundaries(targetIndex);
    }


    private static boolean checkElementBoundaries(int element) {
        return element >= NEVER_LANGUAGE && element <= ALWAYS_LANGUAGE;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append(sourceLanguage())
                .append(" -> ")
                .append(targetLanguage())
                .append(" - ")
                .append("Never Language:")
                .append(mOptions[NEVER_LANGUAGE])
                .append(" Always Language:")
                .append(mOptions[ALWAYS_LANGUAGE])
                .append(" Never Domain:")
                .append(mOptions[NEVER_DOMAIN])
                .toString();
    }
}
