/*
 * Copyright (C) 2026 The LightningOS Project
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

package com.android.launcher3.folder;

import android.content.Context;
import android.content.SharedPreferences;

public final class FolderColorStore {

    public static final int DEFAULT = 0;
    public static final int COUNT = 5;

    private static final String PREFS_NAME = "launcher_folder_colors";

    private final SharedPreferences mPrefs;

    public FolderColorStore(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int get(int folderId) {
        int value = mPrefs.getInt(key(folderId), DEFAULT);
        return value >= 0 && value < COUNT ? value : DEFAULT;
    }

    public void set(int folderId, int color) {
        if (color == DEFAULT) {
            mPrefs.edit().remove(key(folderId)).apply();
        } else {
            mPrefs.edit().putInt(key(folderId), color).apply();
        }
    }

    private static String key(int folderId) {
        return "folder_" + folderId;
    }
}
