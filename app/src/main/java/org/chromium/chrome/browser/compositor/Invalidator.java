// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

/**
 * The {@link Invalidator} invalidates a client when it is the right time.
 */
public class Invalidator {
    /**
     * Interface for the client that gets invalidated.
     */
    public interface Client {
        /**
         * Do the invalidation.
         */
        void doInvalidate();
    }

    /**
     * Interface for the host that drives the invalidations.
     */
    public interface Host {
        /**
         * Requests an invalidation of the view.
         *
         * @param view The {@link View} to invalidate.
         */
        void deferInvalidate(Client view);
    }

    private Host mHost;

    /**
     * @param host The invalidator host, responsible for invalidating views.
     */
    public void set(Host host) {
        mHost = host;
    }

    /**
     * Invalidates either immediately (if no host is specified) or at time
     * triggered by the host.
     *
     * @param client The {@link Client} to invalidate, most likely a view.
     */
    public void invalidate(Client client) {
        if (mHost != null) {
            mHost.deferInvalidate(client);
        } else {
            client.doInvalidate();
        }
    }
}
