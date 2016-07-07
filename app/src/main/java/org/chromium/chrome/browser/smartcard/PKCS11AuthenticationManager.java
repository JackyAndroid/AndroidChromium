// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.smartcard;

import android.content.Context;

import org.chromium.net.AndroidPrivateKey;

import java.security.cert.X509Certificate;

/**
 * Defines API for managing interaction with SmartCard-based certificate storage using PKCS11.
 */
public interface PKCS11AuthenticationManager {
    /**
     * @return true iff SmartCard-based authentication is available.
     */
    public boolean isPKCS11AuthEnabled();

    /**
     * Retrieves the preferred client certificate alias for the given host, port pair, or null if
     * none can be retrieved.
     *
     * @param hostName The host for which to retrieve client certificate.
     * @param port The port to use in conjunction with host to retrieve client certificate.
     */
    public String getClientCertificateAlias(String hostName, int port);

    /**
     * Returns the X509Certificate chain for the requested alias, or null if no there is no result.
     */
    public X509Certificate[] getCertificateChain(String alias);

    /**
     * Performs necessary initializing for using a PKCS11-based KeysStore.
     */
    public void initialize(Context context);

    /**
     * Returns the AndroidPrivateKey for the requested alias, or null if there is no result.
     */
    public AndroidPrivateKey getPrivateKey(String alias);
}
