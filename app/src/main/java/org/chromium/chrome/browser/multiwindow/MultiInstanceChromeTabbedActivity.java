// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.multiwindow;

import org.chromium.chrome.browser.ChromeTabbedActivity;

/**
 * An Activity to launch ChromeTabbedActivity without "singleTask" launchMode.
 * This is needed because a chrome launcher activity and a chrome UI activity should be in the same
 * task to support samsung multi-instance.
 */
public class MultiInstanceChromeTabbedActivity extends ChromeTabbedActivity {}