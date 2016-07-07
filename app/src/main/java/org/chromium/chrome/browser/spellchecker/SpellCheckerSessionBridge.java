// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.spellchecker;

import android.content.Context;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;

import java.util.ArrayList;

/**
 * JNI interface for native SpellCheckerSessionBridge to use Android's spellchecker.
 */
public class SpellCheckerSessionBridge implements SpellCheckerSessionListener {
    private final long mNativeSpellCheckerSessionBridge;
    private final SpellCheckerSession mSpellCheckerSession;

    /**
     * Constructs a SpellCheckerSessionBridge object as well as its SpellCheckerSession object.
     * @param nativeSpellCheckerSessionBridge Pointer to the native SpellCheckerSessionBridge.
     */
    private SpellCheckerSessionBridge(long nativeSpellCheckerSessionBridge) {
        mNativeSpellCheckerSessionBridge = nativeSpellCheckerSessionBridge;

        Context context = ApplicationStatus.getApplicationContext();
        final TextServicesManager textServicesManager =
                (TextServicesManager) context.getSystemService(
                        Context.TEXT_SERVICES_MANAGER_SERVICE);

        // This combination of parameters will cause the spellchecker to be based off of
        // the language specified at "Settings > Language & input > Spell checker > Language".
        // If that setting is set to "Use system language" and the system language is not on the
        // list of supported spellcheck languages, this call will return null.  This call will also
        // return null if the user has turned spellchecking off at "Settings > Language & input >
        // Spell checker".
        mSpellCheckerSession = textServicesManager.newSpellCheckerSession(null, null, this, true);
    }

    /**
     * Returns a new SpellCheckerSessionBridge object if the internal SpellCheckerSession object
     * was able to be created.
     * @param nativeSpellCheckerSessionBridge Pointer to the native SpellCheckerSessionBridge.
     */
    @CalledByNative
    private static SpellCheckerSessionBridge create(long nativeSpellCheckerSessionBridge) {
        SpellCheckerSessionBridge bridge =
                new SpellCheckerSessionBridge(nativeSpellCheckerSessionBridge);
        if (bridge.mSpellCheckerSession == null) {
            return null;
        }
        return bridge;
    }

    /**
     * Queries the input text against the SpellCheckerSession.
     * @param text Text to be queried.
     */
    @CalledByNative
    private void requestTextCheck(String text) {
        // SpellCheckerSession thinks that any word ending with a period is a typo.
        // We trim the period off before sending the text for spellchecking in order to avoid
        // unnecessary red underlines when the user ends a sentence with a period.
        // Filed as an Android bug here: https://code.google.com/p/android/issues/detail?id=183294
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        mSpellCheckerSession.getSentenceSuggestions(new TextInfo[] {new TextInfo(text)}, 0);
    }

    /**
     * Checks for typos and sends results back to native through a JNI call.
     * @param results Results returned by the Android spellchecker.
     */
    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        ArrayList<Integer> offsets = new ArrayList<Integer>();
        ArrayList<Integer> lengths = new ArrayList<Integer>();

        for (SentenceSuggestionsInfo result : results) {
            for (int i = 0; i < result.getSuggestionsCount(); i++) {
                // If a word looks like a typo, record its offset and length.
                if ((result.getSuggestionsInfoAt(i).getSuggestionsAttributes()
                        & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO)
                        == SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) {
                    offsets.add(result.getOffsetAt(i));
                    lengths.add(result.getLengthAt(i));
                }
            }
        }

        nativeProcessSpellCheckResults(mNativeSpellCheckerSessionBridge,
                convertListToArray(offsets), convertListToArray(lengths));
    }

    /**
     * Helper method to convert an ArrayList of Integer objects into an array of primitive ints
     * for easier JNI handling of these objects on the native side.
     * @param list List to be converted to an array.
     */
    private int[] convertListToArray(ArrayList<Integer> list) {
        int[] array = new int[list.size()];
        for (int index = 0; index < array.length; index++) {
            array[index] = list.get(index).intValue();
        }
        return array;
    }

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {}

    private native void nativeProcessSpellCheckResults(
            long nativeSpellCheckerSessionBridge, int[] offsets, int[] lengths);
}
