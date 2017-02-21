// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;

import java.util.Arrays;

/**
 * Enumerates the various failure reasons and events of interest for precaching. When the library is
 * loaded, the events are logged as UMA metrics. Otherwise the events are persisted in shared
 * preferences until the library loads in future. When the library is not loaded, only single
 * occurrence of an event is recorded, duplicate occurrence of the same event is not persisted.
 */
public class PrecacheUMA {
    /**
     * The events should not be renumbered or reused since these are used in a histogram.
     * This must remain in sync with Precache.Events in tools/metrics/histograms/histograms.xml.
     */
    public static class Event {
        /**
         * Indicates that the precache scheduled task has started. The task can be periodic or
         * one-off completion task.
         */
        public static final int PRECACHE_TASK_STARTED_PERIODIC = 0;
        public static final int PRECACHE_TASK_STARTED_ONEOFF = 1;
        // Duplicate GCM task was started while precache was running.
        public static final int PRECACHE_TASK_STARTED_DUPLICATE = 2;

        /**
         * The native library failed to load, during the run of a precache scheduled task.
         */
        public static final int PRECACHE_TASK_LOAD_LIBRARY_FAIL = 3;

        /**
         * Various failure reasons due to which precache task was cancelled.
         */
        public static final int PRECACHE_CANCEL_NO_UNMETERED_NETWORK = 4;
        public static final int PRECACHE_CANCEL_NO_POWER = 5;
        public static final int PRECACHE_CANCEL_DISABLED_PREF = 6;
        public static final int DISABLED_IN_PRECACHE_PREF = 7;
        public static final int SYNC_SERVICE_TIMEOUT = 8;
        public static final int PRECACHE_SESSION_TIMEOUT = 9;

        /**
         * Precache session started.
         */
        public static final int PRECACHE_SESSION_STARTED = 10;

        /**
         * Precache task was scheduled. The task can be periodic or one-off completion task. The
         * result of scheduling can be success or failure. The periodic task can be scheduled due to
         * Chrome upgrade or at startup.
         */
        public static final int PERIODIC_TASK_SCHEDULE_STARTUP = 11;
        public static final int PERIODIC_TASK_SCHEDULE_STARTUP_FAIL = 12;
        public static final int PERIODIC_TASK_SCHEDULE_UPGRADE = 13;
        public static final int PERIODIC_TASK_SCHEDULE_UPGRADE_FAIL = 14;
        public static final int ONEOFF_TASK_SCHEDULE = 15;
        public static final int ONEOFF_TASK_SCHEDULE_FAIL = 16;

        /**
         * Precache session completed successfully or unsuccessfully.
         */
        public static final int PRECACHE_SESSION_COMPLETE = 17;
        public static final int PRECACHE_SESSION_INCOMPLETE = 18;

        /**
         * Limit of the events.
         */
        public static final int EVENT_START = 0;
        public static final int EVENT_END = 19;

        @VisibleForTesting
        static int getBitPosition(int event) {
            assert (event >= EVENT_START) && (event < EVENT_END);
            return event;
        }

        @VisibleForTesting
        static long getBitMask(int event) {
            assert (event >= EVENT_START) && (event < EVENT_END);
            return 1L << event;
        }

        @VisibleForTesting
        static int[] getEventsFromBitMask(long bitmask) {
            int[] events = new int[EVENT_END];
            int filledEvents = 0;
            for (int event = EVENT_START; event < EVENT_END; ++event) {
                if ((getBitMask(event) & bitmask) != 0L) {
                    events[filledEvents] = event;
                    ++filledEvents;
                }
            }
            return Arrays.copyOf(events, filledEvents);
        }

        @VisibleForTesting
        static long addEventToBitMask(long bitmask, int event) {
            return bitmask | getBitMask(event);
        }
    }

    static final String PREF_PERSISTENCE_METRICS = "precache.persistent_metrics";
    static final String EVENTS_HISTOGRAM = "Precache.Events";

    /**
     * Record the precache event. The event is persisted in shared preferences if the native library
     * is not loaded. If library is loaded, the event will be recorded as UMA metric, and any prior
     * persisted events are recorded to UMA as well.
     * @param event the precache event.
     */
    public static void record(int event) {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        long persistent_metric = sharedPreferences.getLong(PREF_PERSISTENCE_METRICS, 0);
        Editor preferencesEditor = sharedPreferences.edit();

        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordEnumeratedHistogram(
                    EVENTS_HISTOGRAM, Event.getBitPosition(event), Event.EVENT_END);
            for (int e : Event.getEventsFromBitMask(persistent_metric)) {
                RecordHistogram.recordEnumeratedHistogram(
                        EVENTS_HISTOGRAM, Event.getBitPosition(e), Event.EVENT_END);
            }
            preferencesEditor.remove(PREF_PERSISTENCE_METRICS);
        } else {
            // Save the metric in preferences.
            persistent_metric = Event.addEventToBitMask(persistent_metric, event);
            preferencesEditor.putLong(PREF_PERSISTENCE_METRICS, persistent_metric);
        }
        preferencesEditor.apply();
    }
}
