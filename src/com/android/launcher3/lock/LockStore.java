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

package com.android.launcher3.lock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class LockStore {

    public static final String METHOD_NONE = "none";
    public static final String METHOD_BIOMETRIC = "biometric";
    public static final String METHOD_PASSWORD = "password";
    public static final String METHOD_PATTERN = "pattern";

    public static final long ASK_EVERY_TIME = 0L;
    public static final long ASK_SCREEN_OFF = -1L;

    public static final int PASSWORD_MIN_LENGTH = 4;
    public static final int PASSWORD_MAX_LENGTH = 8;
    public static final int PATTERN_MIN_LENGTH = 4;

    private static final String PREFS_NAME = "launcher_lock";
    private static final String KEY_METHOD = "method";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_ASK_AGAIN = "ask_again";
    private static final String KEY_FAILED = "failed";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    private static final String KEY_FOLDERS = "folders";

    private static final int HASH_ITERATIONS = 20000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int FREE_ATTEMPTS = 5;
    private static final long LOCKOUT_STEP_MS = 30_000L;

    private static long sUnlockedAt = -1L;

    private final SharedPreferences mPrefs;

    public LockStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getMethod() {
        return mPrefs.getString(KEY_METHOD, METHOD_NONE);
    }

    public boolean isConfigured() {
        return !METHOD_NONE.equals(getMethod());
    }

    public void setBiometric() {
        mPrefs.edit()
                .putString(KEY_METHOD, METHOD_BIOMETRIC)
                .remove(KEY_SALT)
                .remove(KEY_HASH)
                .apply();
        resetFailures();
    }

    public void setSecret(@NonNull String method, @NonNull String secret) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        mPrefs.edit()
                .putString(KEY_METHOD, method)
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash(secret, salt), Base64.NO_WRAP))
                .apply();
        resetFailures();
    }

    public void clear() {
        mPrefs.edit()
                .remove(KEY_METHOD)
                .remove(KEY_SALT)
                .remove(KEY_HASH)
                .remove(KEY_FOLDERS)
                .apply();
        resetFailures();
        lockNow();
    }

    public boolean verify(@NonNull String secret) {
        String saltValue = mPrefs.getString(KEY_SALT, null);
        String hashValue = mPrefs.getString(KEY_HASH, null);
        if (saltValue == null || hashValue == null) {
            return false;
        }
        byte[] expected = Base64.decode(hashValue, Base64.NO_WRAP);
        byte[] actual = hash(secret, Base64.decode(saltValue, Base64.NO_WRAP));
        boolean match = MessageDigest.isEqual(expected, actual);
        if (match) {
            resetFailures();
        } else {
            recordFailure();
        }
        return match;
    }

    public long getLockoutRemainingMs() {
        long until = mPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
        long remaining = until - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public long getAskAgainMs() {
        return mPrefs.getLong(KEY_ASK_AGAIN, ASK_EVERY_TIME);
    }

    public void setAskAgainMs(long value) {
        mPrefs.edit().putLong(KEY_ASK_AGAIN, value).apply();
    }

    public void markUnlocked() {
        sUnlockedAt = SystemClock.elapsedRealtime();
    }

    public static void lockNow() {
        sUnlockedAt = -1L;
    }

    public boolean isUnlockValid() {
        long window = getAskAgainMs();
        if (window == ASK_EVERY_TIME || sUnlockedAt < 0) {
            return false;
        }
        if (window == ASK_SCREEN_OFF) {
            return true;
        }
        return SystemClock.elapsedRealtime() - sUnlockedAt <= window;
    }

    public boolean isFolderLocked(int folderId) {
        return mPrefs.getStringSet(KEY_FOLDERS, new HashSet<>())
                .contains(String.valueOf(folderId));
    }

    public void setFolderLocked(int folderId, boolean locked) {
        Set<String> folders = new HashSet<>(mPrefs.getStringSet(KEY_FOLDERS, new HashSet<>()));
        if (locked) {
            folders.add(String.valueOf(folderId));
        } else {
            folders.remove(String.valueOf(folderId));
        }
        mPrefs.edit().putStringSet(KEY_FOLDERS, folders).apply();
    }

    private void recordFailure() {
        int failed = mPrefs.getInt(KEY_FAILED, 0) + 1;
        SharedPreferences.Editor editor = mPrefs.edit().putInt(KEY_FAILED, failed);
        if (failed >= FREE_ATTEMPTS) {
            long step = failed - FREE_ATTEMPTS + 1;
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + step * LOCKOUT_STEP_MS);
        }
        editor.apply();
    }

    private void resetFailures() {
        mPrefs.edit().remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL).apply();
    }

    private static byte[] hash(String secret, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, HASH_ITERATIONS, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
