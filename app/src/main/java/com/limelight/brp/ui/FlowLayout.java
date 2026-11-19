package com.limelight.brp.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays children left to right and wraps to a new row when the width runs out,
 * so the same page reads as several columns on a tablet or in landscape and as
 * a list on a narrow phone. Respects RTL. The framework has no such layout and
 * the app does not depend on Material components.
 */
public class FlowLayout extends ViewGroup {
    private final int hGap, vGap;
    /** When set, every child gets this width (columns), bounded by the row width. */
    private int childWidth = 0;

    public FlowLayout(Context c) {
        this(c, Math.round(8 * c.getResources().getDisplayMetrics().density),
                Math.round(8 * c.getResources().getDisplayMetrics().density));
    }

    public FlowLayout(Context c, int hGapPx, int vGapPx) {
        super(c);
        this.hGap = hGapPx;
        this.vGap = vGapPx;
    }

    public void setChildWidth(int px) {
        childWidth = px;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowHeight = 0, usedWidth = 0;

        int cw = childWidth;
        if (cw > 0) {
            // Stretch columns so a row is filled exactly.
            int cols = Math.max(1, (maxWidth + hGap) / (cw + hGap));
            cw = (maxWidth - (cols - 1) * hGap) / cols;
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int wSpec = cw > 0
                    ? MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY)
                    : MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            child.measure(wSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > 0 && x + w > maxWidth) {
                x = 0;
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            x += w + hGap;
            usedWidth = Math.max(usedWidth, x - hGap);
            rowHeight = Math.max(rowHeight, h);
        }
        int height = y + rowHeight + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec)
                : usedWidth + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0, y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > 0 && x + w > maxWidth) {
                x = 0;
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            int left = rtl ? (r - l - getPaddingRight() - x - w) : (getPaddingLeft() + x);
            child.layout(left, y, left + w, y + h);
            x += w + hGap;
            rowHeight = Math.max(rowHeight, h);
        }
    }
}
