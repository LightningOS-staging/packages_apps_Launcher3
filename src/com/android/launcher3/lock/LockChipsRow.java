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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.views.ActivityContext;

public class LockChipsRow extends LinearLayout implements FloatingHeaderRow {

    private final ActivityContext mActivityContext;
    private final TextView mAllChip;
    private final TextView mLockedChip;
    private FloatingHeaderView mParent;

    public LockChipsRow(@NonNull Context context) {
        this(context, null);
    }

    public LockChipsRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mActivityContext = ActivityContext.lookupContext(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.lock_chip_row_padding);
        setPadding(pad, 0, pad, 0);
        mAllChip = createChip(R.string.lock_chip_all);
        mLockedChip = createChip(R.string.lock_chip_locked);
        mAllChip.setSelected(true);
        mAllChip.setOnClickListener(v -> select(false));
        mLockedChip.setOnClickListener(v -> LockPrompt.authenticate(getContext(),
                getContext().getString(R.string.lock_chip_locked), () -> select(true)));
        addView(mAllChip);
        addView(mLockedChip);
        updateVisibility();
    }

    private TextView createChip(int labelRes) {
        TextView chip = new TextView(getContext());
        chip.setText(labelRes);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.bg_drawer_chip);
        chip.setTextColor(getContext().getColor(R.color.materialColorOnSurfaceVariant));
        int horizontal = getResources().getDimensionPixelSize(R.dimen.lock_chip_horizontal_padding);
        chip.setPadding(horizontal, 0, horizontal, 0);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.lock_chip_height));
        lp.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.lock_chip_spacing));
        chip.setLayoutParams(lp);
        return chip;
    }

    private void select(boolean lockedOnly) {
        mAllChip.setSelected(!lockedOnly);
        mLockedChip.setSelected(lockedOnly);
        mActivityContext.getAppsView().getPersonalAppList().setLockedOnly(lockedOnly);
    }

    private void updateVisibility() {
        boolean configured = new LockStore(getContext()).isConfigured();
        setVisibility(configured ? VISIBLE : GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateVisibility();
        if (getVisibility() == GONE) {
            mActivityContext.getAppsView().getPersonalAppList().setLockedOnly(false);
            mAllChip.setSelected(true);
            mLockedChip.setSelected(false);
        }
        if (mParent != null) {
            mParent.onHeightUpdated();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getExpectedHeight(),
                MeasureSpec.EXACTLY));
    }

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] allRows, boolean tabsHidden) {
        mParent = parent;
    }

    @Override
    public int getExpectedHeight() {
        if (getVisibility() == GONE) {
            return 0;
        }
        return getResources().getDimensionPixelSize(R.dimen.lock_chip_row_height);
    }

    @Override
    public boolean shouldDraw() {
        return getVisibility() != GONE;
    }

    @Override
    public boolean hasVisibleContent() {
        return getVisibility() != GONE;
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        if (!isScrolledOut) {
            setTranslationY(scroll);
        }
        setAlpha(isScrolledOut ? 0 : 1);
        if (getVisibility() != GONE) {
            AlphaUpdateListener.updateVisibility(this);
        }
    }

    @Override
    public Class<LockChipsRow> getTypeClass() {
        return LockChipsRow.class;
    }

    @Override
    public View getFocusedChild() {
        return mAllChip;
    }
}
