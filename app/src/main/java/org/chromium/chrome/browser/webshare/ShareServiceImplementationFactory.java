// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webshare;

import org.chromium.content_public.browser.WebContents;
import org.chromium.services.service_manager.InterfaceFactory;
import org.chromium.webshare.mojom.ShareService;

/**
 * Factory that creates instances of ShareService.
 */
public class ShareServiceImplementationFactory implements InterfaceFactory<ShareService> {
    private final WebContents mWebContents;

    public ShareServiceImplementationFactory(WebContents webContents) {
        mWebContents = webContents;
    }

    @Override
    public ShareService createImpl() {
        return new ShareServiceImpl(mWebContents);
    }
}
