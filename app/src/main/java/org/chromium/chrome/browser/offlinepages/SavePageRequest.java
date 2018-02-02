// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * Data class representing an underlying request to save a page later.
 */
@JNINamespace("offline_pages::android")
public class SavePageRequest {
    // Int representation of the org.chromium.components.offlinepages.RequestState enum.
    private int mRequestState;
    private long mRequestId;
    private String mUrl;
    private ClientId mClientId;

    /**
     * Creates a SavePageRequest that's a copy of the C++ side version.
     *
     * NOTE: This does not mirror all fields so it cannot be used to create a full SavePageRequest
     * on its own.
     *
     * @param savePageResult Result of the saving. Uses
     *     {@see org.chromium.components.offlinepages.RequestState} enum.
     * @param requestId The unique ID of the request.
     * @param url The URL to download
     * @param clientIdNamespace a String that will be the namespace of the client ID of this
     *     request.
     * @param clientIdId a String that will be the ID of the client ID of this request.
     */
    @CalledByNative("SavePageRequest")
    public static SavePageRequest create(
            int state, long requestId, String url, String clientIdNamespace, String clientIdId) {
        return new SavePageRequest(
                state, requestId, url, new ClientId(clientIdNamespace, clientIdId));
    }

    private SavePageRequest(int state, long requestId, String url, ClientId clientId) {
        mRequestState = state;
        mRequestId = requestId;
        mUrl = url;
        mClientId = clientId;
    }

    public int getRequestState() {
        return mRequestState;
    }

    public long getRequestId() {
        return mRequestId;
    }

    public String getUrl() {
        return mUrl;
    }

    public ClientId getClientId() {
        return mClientId;
    }
}
