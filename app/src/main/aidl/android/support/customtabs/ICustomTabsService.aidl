/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    boolean validatePostMessageOrigin(in ICustomTabsCallback callback) = 6;
    int postMessage(in ICustomTabsCallback callback, String message, in Bundle extras) = 7;
}
