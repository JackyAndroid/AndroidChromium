// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import org.chromium.chrome.browser.media.router.MediaRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains information about a single client connection to the {@link MediaRoute}.
 */
public class ClientRecord {
    /**
     * The route id through which the client is connected. Should be a 1:1 relationship.
     */
    public final String routeId;

    /**
     * The unique id of this client.
     */
    public final String clientId;

    /**
     * The Cast application id for this client.
     */
    public final String appId;

    /**
     * Defines the ways other clients can join or leave the corresponding session.
     */
    public final String autoJoinPolicy;

    /**
     * The origin of the frame that created/joined the route.
     */
    public final String origin;

    /**
     * The id of the tab that created/joined the route.
     */
    public final int tabId;

    /**
     * Whether the client is ready to receive messages.
     */
    public boolean isConnected = false;

    /**
     * The pending messages for the client.
     */
    public List<String> pendingMessages = new ArrayList<String>();

    ClientRecord(
            String routeId,
            String clientId,
            String appId,
            String autoJoinPolicy,
            String origin,
            int tabId) {
        this.routeId = routeId;
        this.clientId = clientId;
        this.appId = appId;
        this.autoJoinPolicy = autoJoinPolicy;
        this.origin = origin;
        this.tabId = tabId;
    }
}
