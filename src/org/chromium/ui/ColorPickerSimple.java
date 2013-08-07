// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


/**
 * Draws a grid of (predefined) colors and allows the user to choose one of
 * those colors.
 */
public class ColorPickerSimple extends View {
    private static final int ROW_COUNT = 2;

    private static final int COLUMN_COUNT = 4;

    private static final int GRID_CELL_COUNT = ROW_COUNT * COLUMN_COUNT;

    private static final int[] COLORS = { Color.RED,
                                          Color.CYAN,
                                          Color.BLUE,
                                          Color.GREEN,
                                          Color.MAGENTA,
                                          Color.YELLOW,
                                          Color.BLACK,
                                          Color.WHITE
                                        };

    private Paint mBorderPaint;

    private Rect[] mBounds;

    private Paint[] mPaints;

    private OnColorChangedListener mOnColorTouchedListener;

    private int mLastTouchedXPosition;

    private int mLastTouchedYPosition;

    public ColorPickerSimple(Context context) {
        super(context);
    }

    public ColorPickerSimple(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPickerSimple(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Initializes the listener and precalculates the grid and color positions.
     *
     * @param onColorChangedListener The listener that gets notified when the user touches
     *                               a color.
     */
    public void init(OnColorChangedListener onColorChangedListener) {
        mOnColorTouchedListener = onColorChangedListener;

        // This will get calculated when the layout size is updated.
        mBounds = null;

        mPaints = new Paint[GRID_CELL_COUNT];
        for (int i = 0; i < GRID_CELL_COUNT; ++i) {
            Paint newPaint = new Paint();
            newPaint.setColor(COLORS[i]);
            mPaints[i] = newPaint;
        }

        mBorderPaint = new Paint();
        int borderColor = getContext().getResources().getColor(R.color.color_picker_border_color);
        mBorderPaint.setColor(borderColor);

        // Responds to the user touching the grid and works out which color has been chosen as
        // a result, depending on the X,Y coordinate. Note that we respond to the click event
        // here, but the onClick() method doesn't provide us with the X,Y coordinates, so we
        // track them in onTouchEvent() below. This way the grid reacts properly to touch events
        // whereas if we put this onClick() code in onTouchEvent below then we get some strange
        // interactions with the ScrollView in the parent ColorPickerDialog.
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnColorTouchedListener != null && getWidth() > 0 && getHeight() > 0) {
                    int column = mLastTouchedXPosition * COLUMN_COUNT / getWidth();
                    int row = mLastTouchedYPosition * ROW_COUNT / getHeight();

                    int colorIndex = (row * COLUMN_COUNT) + column;
                    if (colorIndex >= 0 && colorIndex < COLORS.length) {
                        mOnColorTouchedListener.onColorChanged(COLORS[colorIndex]);
                    }
                }
            }
        });
    }

    /**
     * Draws the grid of colors, based on the rectangles calculated in onSizeChanged().
     * Also draws borders in between the colored rectangles.
     *
     * @param canvas The canvas the colors are drawn onto.
     */
    @Override
    public void onDraw(Canvas canvas) {
        if (mBounds == null || mPaints == null) {
            return;
        }

        canvas.drawColor(Color.WHITE);

        // Draw the actual colored rectangles.
        for (int i = 0; i < GRID_CELL_COUNT; ++i) {
            canvas.drawRect(mBounds[i], mPaints[i]);
        }

        // Draw 1px borders between the rows.
        for (int i = 0; i < ROW_COUNT - 1; ++i) {
          canvas.drawLine(0,
                  mBounds[i * COLUMN_COUNT].bottom + 1,
                  getWidth(),
                  mBounds[i * COLUMN_COUNT].bottom + 1,
                  mBorderPaint);
        }

        // Draw 1px borders between the columns.
        for (int j = 0; j < COLUMN_COUNT - 1; ++j) {
          canvas.drawLine(mBounds[j].right + 1,
                  0,
                  mBounds[j].right + 1,
                  getHeight(),
                  mBorderPaint);
        }
    }

    /**
     * Stores the X,Y coordinates of the touch so that we can use them in the onClick() listener
     * above to work out where the click was on the grid.
     *
     * @param event The MotionEvent the X,Y coordinates are retrieved from.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTouchedXPosition = (int) event.getX();
            mLastTouchedYPosition = (int) event.getY();
        }
        return super.onTouchEvent(event);
    }

    /**
     * Recalculates the color grid with the new sizes.
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        calculateGrid(width, height);
    }

    /**
     * Calculates the sizes and positions of the cells in the grid, splitting
     * them up as evenly as possible. Leaves 3 pixels between each cell so that
     * we can draw a border between them as well, and leaves a pixel around the
     * edge.
     */
    private void calculateGrid(final int width, final int height) {
        mBounds = new Rect[GRID_CELL_COUNT];

        for (int i = 0; i < ROW_COUNT; ++i) {
            for (int j = 0; j < COLUMN_COUNT; ++j) {
                int left = j * (width + 1) / COLUMN_COUNT + 1;
                int right = (j + 1) * (width + 1) / COLUMN_COUNT - 2;

                int top = i * (height + 1) / ROW_COUNT + 1;
                int bottom = (i + 1) * (height + 1) / ROW_COUNT - 2;

                Rect rect = new Rect(left, top, right, bottom);
                mBounds[(i * COLUMN_COUNT) + j] = rect;
            }
        }
    }
}
