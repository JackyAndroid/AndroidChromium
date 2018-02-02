// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A custom ActionMode.Callback that handles copy, paste selection in omnibox and toolbar.
 */
public class ToolbarActionModeCallback implements ActionMode.Callback {

    private static boolean sInitializedTypeMethods;
    private static Method sGetTypeMethod;
    private static int sTypeFloating;

    private ActionModeController mActionModeController;

    /**
     * Sets the {@link #mActionModeController}.
     */
    public void setActionModeController(ActionModeController actionModeController) {
        mActionModeController = actionModeController;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        if (isFloatingActionMode(mode)) return;
        mActionModeController.startHideAnimation();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (isFloatingActionMode(mode)) return true;
        mActionModeController.startShowAnimation();
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    // TODO(tedchoc): Delete this method and replace with just getType() when a public M SDK is
    //                available.
    private static boolean isFloatingActionMode(ActionMode mode) {
        initializeGetTypeMethods();

        if (sGetTypeMethod == null) return false;

        Object retVal = null;
        try {
            retVal = sGetTypeMethod.invoke(mode);
        } catch (IllegalAccessException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (InvocationTargetException e) {
            return false;
        }
        if (!(retVal instanceof Integer)) return false;

        return ((Integer) retVal).intValue() == sTypeFloating;
    }

    private static void initializeGetTypeMethods() {
        if (sInitializedTypeMethods) return;
        sInitializedTypeMethods = true;

        Method getType = null;
        int typeFloating = -1;
        try {
            getType = ActionMode.class.getMethod("getType");
        } catch (NoSuchMethodException e) {
            return;
        }

        try {
            Field field = ActionMode.class.getField("TYPE_FLOATING");
            Object value = field.get(null);

            if (value instanceof Integer) {
                typeFloating = (Integer) value;
            } else {
                return;
            }
        } catch (NoSuchFieldException e) {
            return;
        } catch (IllegalAccessException e) {
            return;
        } catch (IllegalArgumentException e) {
            return;
        }

        sGetTypeMethod = getType;
        sTypeFloating = typeFloating;
    }
}
