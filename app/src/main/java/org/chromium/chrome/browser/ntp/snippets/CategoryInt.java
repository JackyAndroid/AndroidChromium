// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation used to denote int values that identify a content suggestion category.
 * Because the set of categories is not fully known at compile time, we can't use an {@code @IntDef}
 * annotation. Currently nothing enforces that a value annotated this way actually is a valid
 * category ID, but it serves as documentation.
 */
@Retention(value = RetentionPolicy.SOURCE)
public @interface CategoryInt {
}
