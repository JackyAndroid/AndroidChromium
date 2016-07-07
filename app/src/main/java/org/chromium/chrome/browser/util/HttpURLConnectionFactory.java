// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

import java.net.HttpURLConnection;

/**
 * A factory class for creating a HttpURLConnection.
 */
public interface HttpURLConnectionFactory {
    /**
     * @param url the url to communicate with
     * @return a HttpURLConnection to communicate with |url|
     */
    HttpURLConnection createHttpURLConnection(String url);

}
