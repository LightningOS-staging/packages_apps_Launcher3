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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.util.Themes;

public class PatternView extends View {

    public interface Listener {
        void onPatternStarted();

        void onPatternComplete(@NonNull String pattern);
    }

    private static final int GRID = 3;

    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean[] mVisited = new boolean[GRID * GRID];
    private final int[] mSequence = new int[GRID * GRID];
    private int mLength = 0;
    private float mTouchX;
    private float mTouchY;
    private boolean mTracking = false;
    private boolean mEnabledInput = true;
    private float mCell;
    private float mDotRadius;

    @Nullable
    private Listener mListener;

    public PatternView(Context context) {
        this(context, null);
    }

    public PatternView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        int accent = Themes.getAttrColor(context, android.R.attr.colorAccent);
        int idle = Themes.getAttrColor(context, android.R.attr.textColorSecondary);
        mDotPaint.setColor(idle);
        mActivePaint.setColor(accent);
        mHaloPaint.setColor(accent);
        mHaloPaint.setAlpha(60);
        mLinePaint.setColor(accent);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mLinePaint.setStrokeWidth(6 * density);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        mEnabledInput = enabled;
        if (!enabled) {
            reset();
        }
    }

    public void reset() {
        mTracking = false;
        mLength = 0;
        for (int i = 0; i < mVisited.length; i++) {
            mVisited[i] = false;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec) > 0
                        ? MeasureSpec.getSize(heightMeasureSpec) : Integer.MAX_VALUE);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCell = Math.min(w, h) / (float) GRID;
        mDotRadius = mCell * 0.1f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnabledInput) {
            return false;
        }
        mTouchX = event.getX();
        mTouchY = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                mTracking = true;
                if (mListener != null) {
                    mListener.onPatternStarted();
                }
                hitTest(mTouchX, mTouchY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mTracking) {
                    hitTest(mTouchX, mTouchY);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                finish();
                return true;
            case MotionEvent.ACTION_CANCEL:
                reset();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void finish() {
        if (!mTracking) {
            return;
        }
        mTracking = false;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mLength; i++) {
            builder.append(mSequence[i]);
        }
        invalidate();
        if (mLength > 0 && mListener != null) {
            mListener.onPatternComplete(builder.toString());
        }
    }

    private void hitTest(float x, float y) {
        for (int index = 0; index < mVisited.length; index++) {
            if (mVisited[index]) {
                continue;
            }
            float dx = x - centerX(index);
            float dy = y - centerY(index);
            float reach = mCell * 0.3f;
            if (dx * dx + dy * dy <= reach * reach) {
                mVisited[index] = true;
                mSequence[mLength++] = index;
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return;
            }
        }
    }

    private float centerX(int index) {
        return (index % GRID + 0.5f) * mCell;
    }

    private float centerY(int index) {
        return (index / GRID + 0.5f) * mCell;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 1; i < mLength; i++) {
            canvas.drawLine(centerX(mSequence[i - 1]), centerY(mSequence[i - 1]),
                    centerX(mSequence[i]), centerY(mSequence[i]), mLinePaint);
        }
        if (mTracking && mLength > 0) {
            int last = mSequence[mLength - 1];
            canvas.drawLine(centerX(last), centerY(last), mTouchX, mTouchY, mLinePaint);
        }
        for (int index = 0; index < mVisited.length; index++) {
            float cx = centerX(index);
            float cy = centerY(index);
            if (mVisited[index]) {
                canvas.drawCircle(cx, cy, mDotRadius * 3f, mHaloPaint);
                canvas.drawCircle(cx, cy, mDotRadius * 1.4f, mActivePaint);
            } else {
                canvas.drawCircle(cx, cy, mDotRadius, mDotPaint);
            }
        }
    }
}
