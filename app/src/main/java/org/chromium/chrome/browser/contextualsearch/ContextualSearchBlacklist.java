// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Blacklist used to suppress selections.
 */
public class ContextualSearchBlacklist {

    /**
     * Reasons that may cause a selection to be blacklisted.
     */
    public enum BlacklistReason {
        NONE,
        NUMBER,
        DETERMINER,
        PREPOSITION,
        NAVIGATION,
        MISC
    }

    // Number pattern.
    private static final String DIGITS_PATTERN = "^\\d+$";
    private static final Pattern mDigitsPattern = Pattern.compile(DIGITS_PATTERN);

    // Blacklist.
    private static final Map<String, BlacklistReason> BLACKLIST;
    static {
        Map<String, BlacklistReason> codes = new HashMap<>();

        codes.put("a", BlacklistReason.DETERMINER);
        codes.put("el", BlacklistReason.DETERMINER);
        codes.put("la", BlacklistReason.DETERMINER);
        codes.put("that", BlacklistReason.DETERMINER);
        codes.put("the", BlacklistReason.DETERMINER);
        codes.put("this", BlacklistReason.DETERMINER);
        codes.put("un", BlacklistReason.DETERMINER);
        codes.put("your", BlacklistReason.DETERMINER);

        codes.put("by", BlacklistReason.PREPOSITION);
        codes.put("con", BlacklistReason.PREPOSITION);
        codes.put("del", BlacklistReason.PREPOSITION);
        codes.put("en", BlacklistReason.PREPOSITION);
        codes.put("for", BlacklistReason.PREPOSITION);
        codes.put("from", BlacklistReason.PREPOSITION);
        codes.put("in", BlacklistReason.PREPOSITION);
        codes.put("of", BlacklistReason.PREPOSITION);
        codes.put("on", BlacklistReason.PREPOSITION);
        codes.put("para", BlacklistReason.PREPOSITION);
        codes.put("por", BlacklistReason.PREPOSITION);
        codes.put("to", BlacklistReason.PREPOSITION);
        codes.put("with", BlacklistReason.PREPOSITION);

        codes.put("account", BlacklistReason.NAVIGATION);
        codes.put("com", BlacklistReason.NAVIGATION);
        codes.put("continuar", BlacklistReason.NAVIGATION);
        codes.put("continue", BlacklistReason.NAVIGATION);
        codes.put("download", BlacklistReason.NAVIGATION);
        codes.put("descargar", BlacklistReason.NAVIGATION);
        codes.put("facebook", BlacklistReason.NAVIGATION);
        codes.put("google", BlacklistReason.NAVIGATION);
        codes.put("here1234567891011", BlacklistReason.NAVIGATION);
        codes.put("https", BlacklistReason.NAVIGATION);
        codes.put("lanjutkan", BlacklistReason.NAVIGATION);
        codes.put("menu", BlacklistReason.NAVIGATION);
        codes.put("more", BlacklistReason.NAVIGATION);
        codes.put("next", BlacklistReason.NAVIGATION);
        codes.put("play", BlacklistReason.NAVIGATION);
        codes.put("prevnext", BlacklistReason.NAVIGATION);
        codes.put("search", BlacklistReason.NAVIGATION);
        codes.put("video", BlacklistReason.NAVIGATION);
        codes.put("videos", BlacklistReason.NAVIGATION);
        codes.put("whatsapp", BlacklistReason.NAVIGATION);
        codes.put("www", BlacklistReason.NAVIGATION);
        codes.put("youtube", BlacklistReason.NAVIGATION);
        codes.put("دانلود", BlacklistReason.NAVIGATION);

        codes.put("and", BlacklistReason.MISC);
        codes.put("android", BlacklistReason.MISC);
        codes.put("are", BlacklistReason.MISC);
        codes.put("available", BlacklistReason.MISC);
        codes.put("de", BlacklistReason.MISC);
        codes.put("do", BlacklistReason.MISC);
        codes.put("e", BlacklistReason.MISC);
        codes.put("have", BlacklistReason.MISC);
        codes.put("is", BlacklistReason.MISC);
        codes.put("m", BlacklistReason.MISC);
        codes.put("mobile", BlacklistReason.MISC);
        codes.put("no", BlacklistReason.MISC);
        codes.put("offline", BlacklistReason.MISC);
        codes.put("online", BlacklistReason.MISC);
        codes.put("or", BlacklistReason.MISC);
        codes.put("page", BlacklistReason.MISC);
        codes.put("que", BlacklistReason.MISC);
        codes.put("se", BlacklistReason.MISC);
        codes.put("videollamadas", BlacklistReason.MISC);
        codes.put("waiting", BlacklistReason.MISC);
        codes.put("was", BlacklistReason.MISC);
        codes.put("x", BlacklistReason.MISC);
        codes.put("y", BlacklistReason.MISC);
        codes.put("you", BlacklistReason.MISC);

        BLACKLIST = Collections.unmodifiableMap(codes);
    }

    // Metrics codes.
    private static final int NONE_SEEN = 0;
    private static final int NONE_NOT_SEEN = 1;
    private static final int NUMBER_SEEN = 2;
    private static final int NUMBER_NOT_SEEN = 3;
    private static final int DETERMINER_SEEN = 4;
    private static final int DETERMINER_NOT_SEEN = 5;
    private static final int PREPOSITION_SEEN = 6;
    private static final int PREPOSITION_NOT_SEEN = 7;
    private static final int NAVIGATION_SEEN = 8;
    private static final int NAVIGATION_NOT_SEEN = 9;
    private static final int MISC_SEEN = 10;
    private static final int MISC_NOT_SEEN = 11;
    public static final int BLACKLIST_BOUNDARY = 12;

    // Blacklist Metrics Code map.
    // TODO(pedrosimonetti): Design better solution for getting metrics codes and use it elsewhere.
    private static final Map<BlacklistSeenKey, Integer> BLACKLIST_METRICS_CODE;
    static {
        Map<BlacklistSeenKey, Integer> codes = new HashMap<>();

        codes.put(new BlacklistSeenKey(BlacklistReason.NONE, true), NONE_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.NONE, false), NONE_NOT_SEEN);

        codes.put(new BlacklistSeenKey(BlacklistReason.NUMBER, true), NUMBER_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.NUMBER, false), NUMBER_NOT_SEEN);

        codes.put(new BlacklistSeenKey(BlacklistReason.DETERMINER, true), DETERMINER_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.DETERMINER, false), DETERMINER_NOT_SEEN);

        codes.put(new BlacklistSeenKey(BlacklistReason.PREPOSITION, true), PREPOSITION_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.PREPOSITION, false), PREPOSITION_NOT_SEEN);

        codes.put(new BlacklistSeenKey(BlacklistReason.NAVIGATION, true), NAVIGATION_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.NAVIGATION, false), NAVIGATION_NOT_SEEN);

        codes.put(new BlacklistSeenKey(BlacklistReason.MISC, true), MISC_SEEN);
        codes.put(new BlacklistSeenKey(BlacklistReason.MISC, false), MISC_NOT_SEEN);

        BLACKLIST_METRICS_CODE = Collections.unmodifiableMap(codes);
    }

    // Key used in the Blacklist Metrics Code map.
    static class BlacklistSeenKey {
        final BlacklistReason mReason;
        final boolean mWasSeen;
        final int mHashCode;

        BlacklistSeenKey(BlacklistReason reason, boolean wasSeen) {
            mReason = reason;
            mWasSeen = wasSeen;
            mHashCode = 31 * reason.hashCode() + (wasSeen ? 1231 : 1237);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BlacklistSeenKey)) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            BlacklistSeenKey other = (BlacklistSeenKey) obj;
            return mReason.equals(other.mReason) && mWasSeen == other.mWasSeen;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }

    /**
     * Tests the selection against the blacklist heuristics, returning a reason to suppress the
     * selection, or BlacklistReason.NONE, if no reason was found to suppress it.
     * @param selection The given selection.
     * @return The reason to suppress or not the selection.
     */
    public static BlacklistReason findReasonToSuppressSelection(String selection) {
        selection = selection.toLowerCase(Locale.getDefault());

        if (isNumber(selection)) {
            return BlacklistReason.NUMBER;
        }

        BlacklistReason blacklistReason = BLACKLIST.get(selection);
        if (blacklistReason != null) {
            return blacklistReason;
        }

        return BlacklistReason.NONE;
    }

    /**
     * @param reason The reason for blacklisting.
     * @param wasSeen Whether the results were seen.
     * @return The code used to log the blacklist metrics.
     */
    public static Integer getBlacklistMetricsCode(BlacklistReason reason, boolean wasSeen) {
        return BLACKLIST_METRICS_CODE.get(new BlacklistSeenKey(reason, wasSeen));
    }

    /**
     * @param selection A given selection.
     * @return Whether the given |selection| represents a number.
     */
    private static boolean isNumber(String selection) {
        return mDigitsPattern.matcher(selection).find();
    }
}
