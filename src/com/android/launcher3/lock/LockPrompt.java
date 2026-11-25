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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.AlertDialog;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

import java.util.function.Consumer;

public final class LockPrompt {

    private LockPrompt() {
    }

    public static void authenticate(@NonNull Context context, @NonNull CharSequence title,
            @NonNull Runnable onSuccess) {
        LockStore store = new LockStore(context);
        if (!store.isConfigured() || store.isUnlockValid()) {
            onSuccess.run();
            return;
        }
        Runnable unlocked = () -> {
            store.markUnlocked();
            onSuccess.run();
        };
        switch (store.getMethod()) {
            case LockStore.METHOD_BIOMETRIC:
                promptBiometric(context, title, unlocked, null);
                break;
            case LockStore.METHOD_PASSWORD:
                showPassword(context, store, title, unlocked);
                break;
            case LockStore.METHOD_PATTERN:
                showPattern(context, store, title, unlocked);
                break;
            default:
                unlocked.run();
        }
    }

    public static void setup(@NonNull Context context, @NonNull String method,
            @NonNull Runnable onDone) {
        LockStore store = new LockStore(context);
        switch (method) {
            case LockStore.METHOD_BIOMETRIC:
                if (!canUseBiometric(context)) {
                    Toast.makeText(context, R.string.lock_biometric_unavailable,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                promptBiometric(context, context.getString(R.string.lock_setup_biometric_title),
                        () -> {
                            store.setBiometric();
                            onDone.run();
                        }, null);
                break;
            case LockStore.METHOD_PASSWORD:
                collectPassword(context, context.getString(R.string.lock_new_password),
                        first -> collectPassword(context,
                                context.getString(R.string.lock_confirm_password), second -> {
                                    if (first.equals(second)) {
                                        store.setSecret(LockStore.METHOD_PASSWORD, first);
                                        onDone.run();
                                    } else {
                                        Toast.makeText(context, R.string.lock_mismatch,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }));
                break;
            case LockStore.METHOD_PATTERN:
                collectPattern(context, context.getString(R.string.lock_new_pattern),
                        first -> collectPattern(context,
                                context.getString(R.string.lock_confirm_pattern), second -> {
                                    if (first.equals(second)) {
                                        store.setSecret(LockStore.METHOD_PATTERN, first);
                                        onDone.run();
                                    } else {
                                        Toast.makeText(context, R.string.lock_mismatch,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }));
                break;
            default:
                break;
        }
    }

    public static boolean canUseBiometric(@NonNull Context context) {
        BiometricManager manager = context.getSystemService(BiometricManager.class);
        return manager != null && manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private static void promptBiometric(Context context, CharSequence title, Runnable onSuccess,
            @Nullable Runnable onCancel) {
        BiometricPrompt.AuthenticationCallback callback =
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    }
                };
        Handler handler = MAIN_EXECUTOR.getHandler();
        BiometricPrompt prompt = new BiometricPrompt.Builder(context)
                .setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton(context.getString(android.R.string.cancel),
                        handler::post, (dialog, which) -> { })
                .build();
        prompt.authenticate(new CancellationSignal(), handler::post, callback);
    }

    private static void showPassword(Context context, LockStore store, CharSequence title,
            Runnable onSuccess) {
        PasswordDialog dialog = new PasswordDialog(context, title, secret -> {
            if (store.verify(secret)) {
                return true;
            }
            return false;
        }, onSuccess, store);
        dialog.show();
    }

    private static void collectPassword(Context context, CharSequence title,
            Consumer<String> onEntered) {
        PasswordDialog dialog = new PasswordDialog(context, title, null,
                null, null);
        dialog.setCollector(onEntered);
        dialog.show();
    }

    private static void showPattern(Context context, LockStore store, CharSequence title,
            Runnable onSuccess) {
        PatternDialog dialog = new PatternDialog(context, title, store, onSuccess, null);
        dialog.show();
    }

    private static void collectPattern(Context context, CharSequence title,
            Consumer<String> onEntered) {
        PatternDialog dialog = new PatternDialog(context, title, null, null, onEntered);
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static TextView titleView(Context context, CharSequence title) {
        TextView view = new TextView(context);
        view.setText(title);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Themes.getAttrColor(context, android.R.attr.textColorPrimary));
        return view;
    }

    private static TextView messageView(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 24));
        view.setTextColor(Themes.getAttrColor(context, android.R.attr.textColorSecondary));
        return view;
    }

    private static String lockoutMessage(Context context, long remainingMs) {
        return context.getString(R.string.lock_try_again,
                (int) Math.ceil(remainingMs / 1000.0));
    }

    private interface Verifier {
        boolean verify(String secret);
    }

    private static final class PasswordDialog {
        private final Context mContext;
        private final AlertDialog mDialog;
        private final TextView mDots;
        private final TextView mMessage;
        private final StringBuilder mEntry = new StringBuilder();
        @Nullable private final Verifier mVerifier;
        @Nullable private final Runnable mOnSuccess;
        @Nullable private final LockStore mStore;
        @Nullable private Consumer<String> mCollector;

        PasswordDialog(Context context, CharSequence title, @Nullable Verifier verifier,
                @Nullable Runnable onSuccess, @Nullable LockStore store) {
            mContext = context;
            mVerifier = verifier;
            mOnSuccess = onSuccess;
            mStore = store;
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 16));
            root.addView(titleView(context, title));
            mDots = new TextView(context);
            mDots.setTextSize(28);
            mDots.setGravity(Gravity.CENTER);
            mDots.setMinHeight(dp(context, 56));
            mDots.setTextColor(Themes.getAttrColor(context, android.R.attr.colorAccent));
            root.addView(mDots);
            mMessage = messageView(context);
            root.addView(mMessage);
            root.addView(buildKeypad(context));
            mDialog = new AlertDialog.Builder(context).setView(root).create();
            mDialog.setCanceledOnTouchOutside(true);
        }

        void setCollector(Consumer<String> collector) {
            mCollector = collector;
        }

        void show() {
            mDialog.show();
        }

        private GridLayout buildKeypad(Context context) {
            GridLayout grid = new GridLayout(context);
            grid.setColumnCount(3);
            String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0",
                    "✓"};
            for (String label : labels) {
                TextView key = new TextView(context);
                key.setText(label);
                key.setTextSize(24);
                key.setGravity(Gravity.CENTER);
                key.setTextColor(Themes.getAttrColor(context, android.R.attr.textColorPrimary));
                key.setBackgroundResource(R.drawable.bg_lock_key);
                key.setOnClickListener(v -> onKey(label));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = dp(context, 72);
                lp.height = dp(context, 64);
                lp.setMargins(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4));
                grid.addView(key, lp);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(context, 8);
            grid.setLayoutParams(params);
            return grid;
        }

        private void onKey(String label) {
            if (mStore != null && mStore.getLockoutRemainingMs() > 0) {
                mMessage.setText(lockoutMessage(mContext, mStore.getLockoutRemainingMs()));
                return;
            }
            switch (label) {
                case "⌫":
                    if (mEntry.length() > 0) {
                        mEntry.deleteCharAt(mEntry.length() - 1);
                    }
                    break;
                case "✓":
                    submit();
                    return;
                default:
                    if (mEntry.length() < LockStore.PASSWORD_MAX_LENGTH) {
                        mEntry.append(label);
                    }
            }
            mMessage.setText("");
            renderDots();
        }

        private void renderDots() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < mEntry.length(); i++) {
                builder.append("• ");
            }
            mDots.setText(builder.toString().trim());
        }

        private void submit() {
            if (mEntry.length() < LockStore.PASSWORD_MIN_LENGTH) {
                mMessage.setText(mContext.getString(R.string.lock_password_short,
                        LockStore.PASSWORD_MIN_LENGTH));
                return;
            }
            String secret = mEntry.toString();
            if (mCollector != null) {
                mDialog.dismiss();
                mCollector.accept(secret);
                return;
            }
            if (mVerifier != null && mVerifier.verify(secret)) {
                mDialog.dismiss();
                if (mOnSuccess != null) {
                    mOnSuccess.run();
                }
                return;
            }
            mEntry.setLength(0);
            renderDots();
            long remaining = mStore != null ? mStore.getLockoutRemainingMs() : 0L;
            mMessage.setText(remaining > 0 ? lockoutMessage(mContext, remaining)
                    : mContext.getString(R.string.lock_wrong_password));
        }
    }

    private static final class PatternDialog {
        private final Context mContext;
        private final AlertDialog mDialog;
        private final TextView mMessage;

        PatternDialog(Context context, CharSequence title, @Nullable LockStore store,
                @Nullable Runnable onSuccess, @Nullable Consumer<String> collector) {
            mContext = context;
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
            root.addView(titleView(context, title));
            mMessage = messageView(context);
            root.addView(mMessage);
            PatternView pattern = new PatternView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(context, 260), dp(context, 260));
            params.topMargin = dp(context, 16);
            root.addView(pattern, params);
            mDialog = new AlertDialog.Builder(context).setView(root).create();
            mDialog.setCanceledOnTouchOutside(true);
            pattern.setListener(new PatternView.Listener() {
                @Override
                public void onPatternStarted() {
                    mMessage.setText("");
                }

                @Override
                public void onPatternComplete(@NonNull String entered) {
                    if (store != null && store.getLockoutRemainingMs() > 0) {
                        mMessage.setText(lockoutMessage(mContext, store.getLockoutRemainingMs()));
                        pattern.reset();
                        return;
                    }
                    if (entered.length() < LockStore.PATTERN_MIN_LENGTH) {
                        mMessage.setText(mContext.getString(R.string.lock_pattern_short,
                                LockStore.PATTERN_MIN_LENGTH));
                        pattern.reset();
                        return;
                    }
                    if (collector != null) {
                        mDialog.dismiss();
                        collector.accept(entered);
                        return;
                    }
                    if (store != null && store.verify(entered)) {
                        mDialog.dismiss();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        return;
                    }
                    long remaining = store != null ? store.getLockoutRemainingMs() : 0L;
                    mMessage.setText(remaining > 0 ? lockoutMessage(mContext, remaining)
                            : mContext.getString(R.string.lock_wrong_pattern));
                    pattern.reset();
                }
            });
        }

        void show() {
            mDialog.show();
        }
    }
}
