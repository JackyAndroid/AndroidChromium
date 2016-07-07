// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.CheckBox;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Java version of the translate infobar
 */
public class TranslateInfoBar extends InfoBar implements SubPanelListener {
    // Needs to be kept in sync with the Type enum in translate_infobar_delegate.h.
    public static final int BEFORE_TRANSLATE_INFOBAR = 0;
    public static final int TRANSLATING_INFOBAR = 1;
    public static final int AFTER_TRANSLATE_INFOBAR = 2;
    public static final int TRANSLATE_ERROR_INFOBAR = 3;
    public static final int MAX_INFOBAR_INDEX = 4;

    // Defines what subpanel needs to be shown, if any
    public static final int NO_PANEL = 0;
    public static final int LANGUAGE_PANEL = 1;
    public static final int NEVER_PANEL = 2;
    public static final int ALWAYS_PANEL = 3;
    public static final int MAX_PANEL_INDEX = 4;

    private long mNativeTranslateInfoBarPtr;
    private int mInfoBarType;
    private final TranslateOptions mOptions;
    private int mOptionsPanelViewType;
    private TranslateSubPanel mSubPanel;
    private final boolean mShouldShowNeverBar;

    @CalledByNative
    private static InfoBar show(int translateBarType, int sourceLanguageIndex,
            int targetLanguageIndex, boolean autoTranslatePair, boolean showNeverInfobar,
            boolean triggeredFromMenu, String[] languages) {
        return new TranslateInfoBar(translateBarType, sourceLanguageIndex, targetLanguageIndex,
                autoTranslatePair, showNeverInfobar, triggeredFromMenu, languages);
    }

    private TranslateInfoBar(int infoBarType, int sourceLanguageIndex, int targetLanguageIndex,
            boolean autoTranslatePair, boolean shouldShowNeverBar, boolean triggeredFromMenu,
            String[] languages) {
        super(null, R.drawable.infobar_translate, null, null);
        mOptions = new TranslateOptions(sourceLanguageIndex, targetLanguageIndex, languages,
                autoTranslatePair, triggeredFromMenu);
        mInfoBarType = infoBarType;
        mShouldShowNeverBar = shouldShowNeverBar;
        mOptionsPanelViewType = NO_PANEL;
    }

    @Override
    public void onCloseButtonClicked() {
        if (getInfoBarType() == BEFORE_TRANSLATE_INFOBAR && mOptionsPanelViewType == NO_PANEL) {
            // Make it behave exactly as the Nope Button.
            onButtonClicked(false);
        } else {
            super.onCloseButtonClicked();
        }
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        if (mSubPanel != null) {
            mSubPanel.onButtonClicked(isPrimaryButton);
            return;
        }

        int action = actionFor(isPrimaryButton);

        if (getInfoBarType() == BEFORE_TRANSLATE_INFOBAR && mOptionsPanelViewType == NO_PANEL
                && action == ActionType.CANCEL && needsNeverPanel()) {
            // "Nope" was clicked and instead of dismissing we need to show
            // the extra never panel.
            swapPanel(NEVER_PANEL);
        } else {
            onTranslateInfoBarButtonClicked(action);
        }
    }

    /**
     * Based on the infobar and the button pressed figure out what action needs to happen.
     */
    private int actionFor(boolean isPrimaryButton) {
        int action = ActionType.NONE;
        int infobarType = getInfoBarType();
        switch (infobarType) {
            case TranslateInfoBar.BEFORE_TRANSLATE_INFOBAR:
                action = isPrimaryButton
                        ? ActionType.TRANSLATE : ActionType.CANCEL;
                break;
            case TranslateInfoBar.AFTER_TRANSLATE_INFOBAR:
                if (!isPrimaryButton) {
                    action = ActionType.TRANSLATE_SHOW_ORIGINAL;
                }
                break;
            case TranslateInfoBar.TRANSLATE_ERROR_INFOBAR:
                // retry
                action = ActionType.TRANSLATE;
                break;
            default:
                break;
        }
        return action;
    }

    private CharSequence getMessageText(Context context) {
        switch (getInfoBarType()) {
            case BEFORE_TRANSLATE_INFOBAR:
                String template = context.getString(R.string.translate_infobar_text);
                return formatBeforeInfoBarMessage(template, mOptions.sourceLanguage(),
                        mOptions.targetLanguage(), LANGUAGE_PANEL);
            case AFTER_TRANSLATE_INFOBAR:
                String translated = context.getString(
                        R.string.translate_infobar_translation_done, mOptions.targetLanguage());
                if (needsAlwaysPanel()) {
                    String moreOptions = context.getString(R.string.more);
                    return formatAfterTranslateInfoBarMessage(translated, moreOptions,
                            ALWAYS_PANEL);
                } else {
                    return translated;
                }
            case TRANSLATING_INFOBAR:
                return context.getString(R.string.translate_infobar_translating,
                        mOptions.targetLanguage());
            default:
                return context.getString(R.string.translate_infobar_error);
        }
    }

    private String getPrimaryButtonText(Context context) {
        switch (getInfoBarType()) {
            case BEFORE_TRANSLATE_INFOBAR:
                return context.getString(R.string.translate_button);
            case AFTER_TRANSLATE_INFOBAR:
                if (!needsAlwaysPanel()) {
                    return context.getString(R.string.translate_button_done);
                }
                return null;
            case TRANSLATE_ERROR_INFOBAR:
                return context.getString(R.string.translate_retry);
            default:
                return null; // no inner buttons on the remaining infobars
        }
    }

    private String getSecondaryButtonText(Context context) {
        switch (getInfoBarType()) {
            case BEFORE_TRANSLATE_INFOBAR:
                return context.getString(R.string.translate_nope);
            case AFTER_TRANSLATE_INFOBAR:
                if (!needsAlwaysPanel()) {
                    return context.getString(R.string.translate_show_original);
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        if (mOptionsPanelViewType == NO_PANEL) {
            mSubPanel = null;
        } else {
            mSubPanel = panelFor(mOptionsPanelViewType);
            if (mSubPanel != null) {
                mSubPanel.createContent(getContext(), layout);
            }
            return;
        }

        Context context = layout.getContext();
        layout.setMessage(getMessageText(context));
        layout.setButtons(getPrimaryButtonText(context), getSecondaryButtonText(context));

        if (getInfoBarType() == AFTER_TRANSLATE_INFOBAR
                && !needsAlwaysPanel()
                && !mOptions.triggeredFromMenu()) {
            // Long always translate version
            TranslateCheckBox checkBox = new TranslateCheckBox(context, mOptions, this);
            layout.setCustomContent(checkBox);
        }
    }

    // SubPanelListener implementation
    @Override
    public void onPanelClosed(int action) {
        setControlsEnabled(false);
        if (mOptionsPanelViewType == LANGUAGE_PANEL) {
            // Close the sub panel and show the infobar again.
            mOptionsPanelViewType = NO_PANEL;
            updateViewForCurrentState(createView());
        } else {
            // Apply options and close the infobar.
            onTranslateInfoBarButtonClicked(action);
        }
    }

    private void onTranslateInfoBarButtonClicked(int action) {
        onOptionsChanged();
        onButtonClicked(action);
    }

    @Override
    public void setControlsEnabled(boolean state) {
        super.setControlsEnabled(state);

        // Handle the "Always Translate" checkbox.
        ContentWrapperView wrapper = getContentWrapper(false);
        if (wrapper != null) {
            CheckBox checkBox = (CheckBox) wrapper.findViewById(R.id.infobar_extra_check);
            if (checkBox != null) checkBox.setEnabled(state);
        }
    }

    @Override
    public void onOptionsChanged() {
        if (mNativeTranslateInfoBarPtr == 0) return;

        if (mOptions.optionsChanged()) {
            nativeApplyTranslateOptions(mNativeTranslateInfoBarPtr, mOptions.sourceLanguageIndex(),
                    mOptions.targetLanguageIndex(), mOptions.alwaysTranslateLanguageState(),
                    mOptions.neverTranslateLanguageState(), mOptions.neverTranslateDomainState());
        }
    }

    private boolean needsNeverPanel() {
        return (getInfoBarType() == TranslateInfoBar.BEFORE_TRANSLATE_INFOBAR
                && mShouldShowNeverBar);
    }

    private boolean needsAlwaysPanel() {
        return (getInfoBarType() == TranslateInfoBar.AFTER_TRANSLATE_INFOBAR
                && mOptions.alwaysTranslateLanguageState()
                && !DeviceFormFactor.isTablet(getContext()));
    }

    /**
     * @param newPanel id of the new panel to swap in. Use NO_PANEL to
     *     simply remove the current panel.
     */
    private void swapPanel(int newPanel) {
        assert (newPanel >= NO_PANEL && newPanel < MAX_PANEL_INDEX);
        mOptionsPanelViewType = newPanel;
        updateViewForCurrentState(createView());
    }

    /**
     * @return a panel of the specified {@code type}
     */
    private TranslateSubPanel panelFor(int type) {
        assert (type >= NO_PANEL && type < MAX_PANEL_INDEX);
        switch (type) {
            case LANGUAGE_PANEL:
                return new TranslateLanguagePanel(this, mOptions);
            case NEVER_PANEL:
                return new TranslateNeverPanel(this, mOptions);
            case ALWAYS_PANEL:
                return new TranslateAlwaysPanel(this, mOptions);
            default:
                return null;
        }
    }

    /**
     * Swaps out the current view in the ContentViewWrapper.
     */
    private void updateViewForCurrentState(View replacement) {
        setControlsEnabled(false);
        getInfoBarContainer().swapInfoBarViews(this, replacement);
    }

    /**
     * @return a formatted message with links to {@code panelId}.
     */
    private CharSequence formatBeforeInfoBarMessage(String template, String sourceLanguage,
            String targetLanguage, final int panelId) {

        SpannableString formattedSourceLanguage = new SpannableString(sourceLanguage);
        formattedSourceLanguage.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                swapPanel(panelId);
            }
        }, 0, sourceLanguage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString formattedTargetLanguage = new SpannableString(targetLanguage);
        formattedTargetLanguage.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                swapPanel(panelId);
            }
        }, 0, targetLanguage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return TextUtils.expandTemplate(template, formattedSourceLanguage, formattedTargetLanguage);
    }

    /**
     * @return a formatted message with a link to {@code panelId}
     */
    private CharSequence formatAfterTranslateInfoBarMessage(String statement, String linkText,
            final int panelId) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        result.append(statement).append(" ");
        SpannableString formattedChange = new SpannableString(linkText);
        formattedChange.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                swapPanel(panelId);
            }
        }, 0, linkText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(formattedChange);
        return result;
    }

    int getInfoBarType() {
        return mInfoBarType;
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        mNativeTranslateInfoBarPtr = nativePtr;
    }

    @Override
    protected void onNativeDestroyed() {
        mNativeTranslateInfoBarPtr = 0;
        super.onNativeDestroyed();
    }

    @CalledByNative
    private void changeTranslateInfoBarType(int infoBarType) {
        if (infoBarType >= 0 && infoBarType < MAX_INFOBAR_INDEX) {
            mInfoBarType = infoBarType;
            updateViewForCurrentState(createView());
        } else {
            assert false : "Trying to change the InfoBar to a type that is invalid.";
        }
    }

    private native void nativeApplyTranslateOptions(long nativeTranslateInfoBar,
            int sourceLanguageIndex, int targetLanguageIndex, boolean alwaysTranslate,
            boolean neverTranslateLanguage, boolean neverTranslateSite);
}
