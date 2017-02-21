// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.metrics.RecordHistogram;

/**
 * A class used to record journey metrics for the Payment Request feature.
 */
public class PaymentRequestJourneyLogger {
    public static final int SECTION_CONTACT_INFO = 0;
    public static final int SECTION_CREDIT_CARDS = 1;
    public static final int SECTION_SHIPPING_ADDRESS = 2;
    public static final int SECTION_MAX = 3;

    // The minimum expected value of CustomCountHistograms is always set to 1. It is still possible
    // to log the value 0 to that type of histogram.
    private static final int MIN_EXPECTED_SAMPLE = 1;
    private static final int MAX_EXPECTED_SAMPLE = 49;
    private static final int NUMBER_BUCKETS = 50;

    private static class SectionStats {
        private int mNumberSuggestionsShown;
        private int mNumberSelectionChanges;
        private int mNumberSelectionEdits;
        private int mNumberSelectionAdds;
        private boolean mIsRequested;
    }

    private SectionStats[] mSections;

    public PaymentRequestJourneyLogger() {
        mSections = new SectionStats[SECTION_MAX];
        for (int i = 0; i < mSections.length; ++i) {
            mSections[i] = new SectionStats();
        }
    }

    /*
     * Sets the number of suggestions shown for the specified section.
     *
     * @param section The section for which to log.
     * @param number The number of suggestions.
     */
    public void setNumberOfSuggestionsShown(int section, int number) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSuggestionsShown = number;
        mSections[section].mIsRequested = true;
    }

    /*
     * Increments the number of selection changes for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionChanges(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionChanges++;
    }

    /*
     * Increments the number of selection edits for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionEdits(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionEdits++;
    }

    /*
     * Increments the number of selection adds for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionAdds(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionAdds++;
    }

    /*
     * Records the histograms for all the sections that were requested by the merchant. This method
     * should be called when the payment request has either been completed or aborted.
     *
     * @param submissionType A string indicating the way the payment request was concluded.
     */
    public void recordJourneyStatsHistograms(String submissionType) {
        for (int i = 0; i < mSections.length; ++i) {
            String nameSuffix = "";
            switch (i) {
                case SECTION_SHIPPING_ADDRESS:
                    nameSuffix = "ShippingAddress." + submissionType;
                    break;
                case SECTION_CONTACT_INFO:
                    nameSuffix = "ContactInfo." + submissionType;
                    break;
                case SECTION_CREDIT_CARDS:
                    nameSuffix = "CreditCards." + submissionType;
                    break;
                default:
                    break;
            }

            assert !nameSuffix.isEmpty();

            // Only log the metrics for a section if it was requested by the merchant.
            if (mSections[i].mIsRequested) {
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionAdds." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionAdds, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionChanges." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionChanges, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionEdits." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionEdits, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSuggestionsShown." + nameSuffix,
                        Math.min(mSections[i].mNumberSuggestionsShown, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
            }
        }
    }
}