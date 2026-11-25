/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.allapps.search;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.Utilities.prefixTextWithIcon;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.PopupMenu;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.pinned.PinnedAppsStore;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;

/**
 * Layout to contain the All-apps search UI.
 */
public class AppsSearchContainerLayout extends ExtendedEditText
        implements SearchUiManager, SearchCallback<AdapterItem>,
        AllAppsStore.OnUpdateListener, Insettable {

    private final ActivityContext mLauncher;
    private final AllAppsSearchBarController mSearchBarController;
    private final SpannableStringBuilder mSearchQueryBuilder;

    private ActivityAllAppsContainerView<?> mAppsView;

    // The amount of pixels to shift down and overlap with the rest of the content.
    private final int mContentOverlap;

    public AppsSearchContainerLayout(Context context) {
        this(context, null);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = ActivityContext.lookupContext(context);
        mSearchBarController = new AllAppsSearchBarController();

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);

        mContentOverlap =
                getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_content_overlap);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(mAppsView != null)
            mAppsView.getAppsStore().addUpdateListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(mAppsView != null)
            mAppsView.getAppsStore().removeUpdateListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Update the width to match the grid padding
        DeviceProfile dp = mLauncher.getDeviceProfile();
        int myRequestedWidth = getSize(widthMeasureSpec);
        int rowWidth = myRequestedWidth - mAppsView.getActiveRecyclerView().getPaddingLeft()
                - mAppsView.getActiveRecyclerView().getPaddingRight();

        int cellWidth = DeviceProfile.calculateCellWidth(rowWidth,
                dp.getWorkspaceProfile().getCellLayoutBorderSpacePx().x,
                dp.getHotseatProfile().getNumShownIcons());
        int iconVisibleSize =
                Math.round(ICON_VISIBLE_AREA_FACTOR * dp.getWorkspaceProfile().getIconSizePx());
        int iconPadding = cellWidth - iconVisibleSize;

        int myWidth = rowWidth - iconPadding + getPaddingLeft() + getPaddingRight();
        super.onMeasure(makeMeasureSpec(myWidth, EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Shift the widget horizontally so that its centered in the parent (b/63428078)
        View parent = (View) getParent();
        int availableWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int myWidth = right - left;
        int expectedLeft = parent.getPaddingLeft() + (availableWidth - myWidth) / 2;
        int shift = expectedLeft - left;
        setTranslationX(shift);

        offsetTopAndBottom(mContentOverlap);
    }

    private static final int MENU_GROUP_TOP_ROW = 1;
    private static final int MENU_SUGGESTIONS = 1;
    private static final int MENU_PINNED = 2;
    private static final int MENU_BLUR = 3;

    private boolean mMenuTouch = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mMenuTouch = isMenuHit(event.getX());
        }
        if (mMenuTouch) {
            if (action == MotionEvent.ACTION_UP) {
                mMenuTouch = false;
                showMenu();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                mMenuTouch = false;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean isMenuHit(float x) {
        Drawable end = getCompoundDrawablesRelative()[2];
        if (end == null || !isSearchQueryEmpty()) {
            return false;
        }
        int reach = end.getIntrinsicWidth() + getPaddingEnd() + getPaddingStart();
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? x <= reach : x >= getWidth() - reach;
    }

    private void showMenu() {
        PopupMenu popup = new PopupMenu(getContext(), this);
        Menu menu = popup.getMenu();
        boolean pinned = PinnedAppsStore.isPinnedMode(getContext());
        menu.add(MENU_GROUP_TOP_ROW, MENU_SUGGESTIONS, 0, R.string.drawer_top_row_suggestions)
                .setCheckable(true).setChecked(!pinned);
        menu.add(MENU_GROUP_TOP_ROW, MENU_PINNED, 1, R.string.drawer_top_row_pinned)
                .setCheckable(true).setChecked(pinned);
        menu.setGroupCheckable(MENU_GROUP_TOP_ROW, true, true);
        menu.add(0, MENU_BLUR, 2, R.string.drawer_blur_title)
                .setCheckable(true).setChecked(LauncherPrefs.DRAWER_BLUR.get(getContext()));
        popup.setOnMenuItemClickListener(this::onMenuItemClicked);
        popup.show();
    }

    private boolean onMenuItemClicked(MenuItem item) {
        LauncherPrefs prefs = LauncherPrefs.get(getContext());
        switch (item.getItemId()) {
            case MENU_SUGGESTIONS:
            case MENU_PINNED:
                String mode = item.getItemId() == MENU_PINNED
                        ? PinnedAppsStore.MODE_PINNED : PinnedAppsStore.MODE_SUGGESTIONS;
                if (!mode.equals(prefs.get(LauncherPrefs.DRAWER_TOP_ROW))) {
                    prefs.put(LauncherPrefs.DRAWER_TOP_ROW, mode);
                    LauncherAppState.INSTANCE.executeIfCreated(app -> app.setNeedsRestart());
                }
                return true;
            case MENU_BLUR:
                prefs.put(LauncherPrefs.DRAWER_BLUR, !LauncherPrefs.DRAWER_BLUR.get(getContext()));
                return true;
            default:
                return false;
        }
    }

    @Override
    public void initializeSearch(ActivityAllAppsContainerView<?> appsView) {
        mAppsView = appsView;
        mSearchBarController.initialize(
                new DefaultAppSearchAlgorithm(getContext(), mLauncher.getUiExecutor(), true),
                this, mLauncher, this);
    }

    @Override
    public void onAppsUpdated() {
        mSearchBarController.refreshSearchResult();
    }

    @Override
    public void resetSearch() {
        mSearchBarController.reset();
    }

    @Override
    public boolean isSearchQueryEmpty() {
        String query = Utilities.trim(getEditableText().toString());
        return query.isEmpty();
    }

    @Override
    public void preDispatchKeyEvent(KeyEvent event) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!mSearchBarController.isSearchFieldFocused() &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            final int unicodeChar = event.getUnicodeChar();
            final boolean isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar);
            if (isKeyNotWhitespace) {
                boolean gotKey = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.getKeyCode(), event);
                if (gotKey && mSearchQueryBuilder.length() > 0) {
                    mSearchBarController.focusSearchField();
                }
            }
        }
    }

    @Override
    public void onSearchResult(String query, ArrayList<AdapterItem> items) {
        if (query.equalsIgnoreCase(getContext().getString(R.string.private_space_label))) {
            privateSpaceQuery();
            return;
        }
        if (items != null) {
            mAppsView.setSearchResults(items);
        }
    }

    @Override
    public void clearSearchResult() {
        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);
        mAppsView.onClearSearchResult();
    }

    @Override
    public void setInsets(Rect insets) {
        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        mlp.topMargin = insets.top;
        requestLayout();
    }

    @Override
    public ExtendedEditText getEditText() {
        return this;
    }

    private void privateSpaceQuery() {
        PrivateProfileManager privateProfileManager = mAppsView.getPrivateProfileManager();
        if (privateProfileManager.isPrivateSpaceHidden()) {
            privateProfileManager.setQuietMode(false);
        } else if (!mAppsView.hasPrivateProfile()) {
            final Intent privateSpaceSettingsIntent =
                    ApiWrapper.INSTANCE.get(getContext()).getPrivateSpaceSettingsIntent();
            if (privateSpaceSettingsIntent != null) {
                mLauncher.startActivitySafely(mAppsView, privateSpaceSettingsIntent, null);
            }
        }
    }
}
