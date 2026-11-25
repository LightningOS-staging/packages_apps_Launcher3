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

package com.android.launcher3.pinned;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.List;

public final class PinnedAppsStore {

    public static final String MODE_SUGGESTIONS = "suggestions";
    public static final String MODE_PINNED = "pinned";

    private static final String ENTRY_SEPARATOR = "\n";
    private static final String FIELD_SEPARATOR = "#";

    private PinnedAppsStore() {
    }

    public static boolean isPinnedMode(@NonNull Context context) {
        return MODE_PINNED.equals(LauncherPrefs.DRAWER_TOP_ROW.get(context));
    }

    @NonNull
    public static List<ComponentKey> getPinned(@NonNull Context context) {
        List<ComponentKey> result = new ArrayList<>();
        String raw = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_PINNED_APPS);
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        UserManager userManager = context.getSystemService(UserManager.class);
        for (String entry : raw.split(ENTRY_SEPARATOR)) {
            String[] parts = entry.split(FIELD_SEPARATOR);
            if (parts.length != 2) {
                continue;
            }
            ComponentName component = ComponentName.unflattenFromString(parts[0]);
            UserHandle user;
            try {
                user = userManager.getUserForSerialNumber(Long.parseLong(parts[1]));
            } catch (NumberFormatException e) {
                continue;
            }
            if (component != null && user != null) {
                result.add(new ComponentKey(component, user));
            }
        }
        return result;
    }

    public static boolean isPinned(@NonNull Context context, @NonNull ComponentKey key) {
        return getPinned(context).contains(key);
    }

    public static boolean canPinMore(@NonNull Context context, int limit) {
        return getPinned(context).size() < limit;
    }

    public static boolean pin(@NonNull Context context, @NonNull ComponentKey key, int limit) {
        List<ComponentKey> pinned = getPinned(context);
        if (pinned.contains(key)) {
            return true;
        }
        if (pinned.size() >= limit) {
            return false;
        }
        pinned.add(key);
        save(context, pinned);
        return true;
    }

    public static void unpin(@NonNull Context context, @NonNull ComponentKey key) {
        List<ComponentKey> pinned = getPinned(context);
        if (pinned.remove(key)) {
            save(context, pinned);
        }
    }

    private static void save(Context context, List<ComponentKey> pinned) {
        UserManager userManager = context.getSystemService(UserManager.class);
        StringBuilder builder = new StringBuilder();
        for (ComponentKey key : pinned) {
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(key.componentName.flattenToString())
                    .append(FIELD_SEPARATOR)
                    .append(userManager.getSerialNumberForUser(key.user));
        }
        LauncherPrefs.get(context).put(LauncherPrefs.DRAWER_PINNED_APPS, builder.toString());
    }
}
