// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import java.io.Serializable;

/**
 * Local Storage information for a given origin.
 */
public class LocalStorageInfo implements Serializable {
    private final String mOrigin;
    private final long mSize;
    private final boolean mImportantDomain;

    LocalStorageInfo(String origin, long size, boolean importantDomain) {
        mOrigin = origin;
        mSize = size;
        mImportantDomain = importantDomain;
    }

    public String getOrigin() {
        return mOrigin;
    }

    public void clear() {
        WebsitePreferenceBridge.nativeClearCookieData(mOrigin);
        WebsitePreferenceBridge.nativeClearLocalStorageData(mOrigin);
    }

    public long getSize() {
        return mSize;
    }

    public boolean isDomainImportant() {
        return mImportantDomain;
    }
}
