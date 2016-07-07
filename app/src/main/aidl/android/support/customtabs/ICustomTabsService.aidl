// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.support.customtabs;

import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.ICustomTabsCallback;

import java.util.List;

/**
 * Interface to a CustomTabsService.
 * @hide
 */
interface ICustomTabsService {
    boolean warmup(long flags) = 1;
    boolean newSession(in ICustomTabsCallback callback) = 2;
    boolean mayLaunchUrl(in ICustomTabsCallback callback, in Uri url,
            in Bundle extras, in List<Bundle> otherLikelyBundles) = 3;
    Bundle extraCommand(String commandName, in Bundle args) = 4;
    boolean updateVisuals(in ICustomTabsCallback callback, in Bundle bundle) = 5;
}
