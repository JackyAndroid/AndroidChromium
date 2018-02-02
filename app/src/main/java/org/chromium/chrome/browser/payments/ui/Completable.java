// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

/**
 * The interface for types that may be complete, i.e., can be sent to the merchant as-is, without
 * being edited by the user first.
 */
public interface Completable {
    /** @return Whether the data is complete and can be sent to the merchant as-is */
    boolean isComplete();
}