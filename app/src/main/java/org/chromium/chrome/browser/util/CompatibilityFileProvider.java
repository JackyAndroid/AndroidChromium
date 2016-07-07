// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import java.util.Arrays;

/**
 * A FileProvider which works around a bad assumption that particular MediaStore columns exist by
 * certain third party applications.
 * http://crbug.com/467423.
 */
public class CompatibilityFileProvider extends FileProvider {
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor source = super.query(uri, projection, selection, selectionArgs, sortOrder);

        String[] columnNames = source.getColumnNames();
        String[] newColumnNames = columnNamesWithData(columnNames);
        if (columnNames == newColumnNames) return source;

        MatrixCursor cursor = new MatrixCursor(newColumnNames, source.getCount());

        source.moveToPosition(-1);
        while (source.moveToNext()) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (int i = 0; i < columnNames.length; i++) {
                switch (source.getType(i)) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        row.add(source.getInt(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        row.add(source.getFloat(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        row.add(source.getString(i));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        row.add(source.getBlob(i));
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                    default:
                        row.add(null);
                        break;
                }
            }
        }

        source.close();
        return cursor;
    }

    private String[] columnNamesWithData(String[] columnNames) {
        for (String columnName : columnNames) {
            if (MediaStore.MediaColumns.DATA.equals(columnName)) return columnNames;
        }

        String[] newColumnNames = Arrays.copyOf(columnNames, columnNames.length + 1);
        newColumnNames[columnNames.length] = MediaStore.MediaColumns.DATA;
        return newColumnNames;
    }
}