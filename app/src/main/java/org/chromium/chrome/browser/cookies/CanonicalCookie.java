// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.cookies;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Java representation of net/cookies/canonical_cookie.h.
 */
class CanonicalCookie {
    private final String mUrl;
    private final String mName;
    private final String mValue;
    private final String mDomain;
    private final String mPath;
    private final long mCreation;
    private final long mExpiration;
    private final long mLastAccess;
    private final boolean mSecure;
    private final boolean mHttpOnly;
    private final boolean mFirstPartyOnly;
    private final int mPriority;

    /** Constructs a CanonicalCookie */
    CanonicalCookie(String url, String name, String value, String domain, String path,
            long creation, long expiration, long lastAccess, boolean secure, boolean httpOnly,
            boolean firstPartyOnly, int priority) {
        mUrl = url;
        mName = name;
        mValue = value;
        mDomain = domain;
        mPath = path;
        mCreation = creation;
        mExpiration = expiration;
        mLastAccess = lastAccess;
        mSecure = secure;
        mHttpOnly = httpOnly;
        mFirstPartyOnly = firstPartyOnly;
        mPriority = priority;
    }

    /** @return Priority of the cookie. */
    int getPriority() {
        return mPriority;
    }

    /** @return True if the cookie is HTTP only. */
    boolean isHttpOnly() {
        return mHttpOnly;
    }

    /** @return True if the cookie is First-Party only. */
    boolean isFirstPartyOnly() {
        return mFirstPartyOnly;
    }

    /** @return True if the cookie is secure. */
    boolean isSecure() {
        return mSecure;
    }

    /** @return Last accessed time. */
    long getLastAccessDate() {
        return mLastAccess;
    }

    /** @return Expiration time. */
    long getExpirationDate() {
        return mExpiration;
    }

    /** @return Creation time. */
    long getCreationDate() {
        return mCreation;
    }

    /** @return Cookie name. */
    String getName() {
        return mName;
    }

    /** @return Cookie path. */
    String getPath() {
        return mPath;
    }

    /** @return Cookie URL. */
    String getUrl() {
        return mUrl;
    }

    /** @return Cookie domain. */
    String getDomain() {
        return mDomain;
    }

    /** @return Cookie value. */
    String getValue() {
        return mValue;
    }

    /**
     * Serializes for saving to disk. Does not close the stream.
     * It is up to the caller to do so.
     *
     * @param out Stream to write the cookie to.
     */
    void saveToStream(DataOutputStream out) throws IOException {
        out.writeUTF(mUrl);
        out.writeUTF(mName);
        out.writeUTF(mValue);
        out.writeUTF(mDomain);
        out.writeUTF(mPath);
        out.writeLong(mCreation);
        out.writeLong(mExpiration);
        out.writeLong(mLastAccess);
        out.writeBoolean(mSecure);
        out.writeBoolean(mHttpOnly);
        out.writeBoolean(mFirstPartyOnly);
        out.writeInt(mPriority);
    }

    /**
     * Constructs a cookie by deserializing a single entry from the
     * input stream.
     *
     * @param in Stream to read a cookie entry from.
     */
    static CanonicalCookie createFromStream(DataInputStream in)
            throws IOException {
        return new CanonicalCookie(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(),
                in.readUTF(), in.readLong(), in.readLong(), in.readLong(), in.readBoolean(),
                in.readBoolean(), in.readBoolean(), in.readInt());
    }
}
