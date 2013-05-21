// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

/**
 * UI for the color chooser that shows on the Android platform as a result
 * of &lt;input type=color &gt; form element.
 *
 * <p> Note that this UI is only temporary and will be replaced once the UI
 * design in
 * https://code.google.com/p/chromium/issues/detail?id=162491 is finalized
 */
public class ColorPickerDialog extends Dialog {

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    private OnColorChangedListener mListener;
    private int mInitialColor;

    private static class ColorPickerView extends View {
        private static final int CENTER_RADIUS = 32;
        private static final int DIALOG_HEIGHT = 200;
        private static final int BOUNDING_BOX_EDGE = 100;
        private static final float PI = 3.1415926f;

        private final Paint mPaint;
        private final Paint mCenterPaint;
        private final int[] mColors;
        private final OnColorChangedListener mListener;
        private boolean mTrackingCenter;
        private boolean mHighlightCenter;

        private int center_x = -1;
        private int center_y = -1;

        ColorPickerView(Context c, OnColorChangedListener listener, int color) {
            super(c);
            mListener = listener;
            mColors = new int[] {
                0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00,
                0xFFFFFF00, 0xFFFF0000
            };
            Shader shader = new SweepGradient(0, 0, mColors, null);

            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setShader(shader);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(32);

            mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mCenterPaint.setColor(color);
            mCenterPaint.setStrokeWidth(5);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (center_x == -1) {
                center_x = getWidth() / 2;
            }
            if (center_y == -1) {
                center_y = getHeight() / 2;
            }

            float r = BOUNDING_BOX_EDGE - mPaint.getStrokeWidth() * 0.5f;

            canvas.translate(center_x, center_y);

            canvas.drawOval(new RectF(-r, -r, r, r), mPaint);
            canvas.drawCircle(0, 0, CENTER_RADIUS, mCenterPaint);

            if (mTrackingCenter) {
                int color = mCenterPaint.getColor();
                mCenterPaint.setStyle(Paint.Style.STROKE);

                if (mHighlightCenter) {
                    mCenterPaint.setAlpha(0xFF);
                } else {
                    mCenterPaint.setAlpha(0x80);
                }
                canvas.drawCircle(0, 0,
                        CENTER_RADIUS + mCenterPaint.getStrokeWidth(),
                        mCenterPaint);

                mCenterPaint.setStyle(Paint.Style.FILL);
                mCenterPaint.setColor(color);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(widthMeasureSpec, DIALOG_HEIGHT);
        }

        private static int interpolate(int low, int high, float interPolant) {
            return low + java.lang.Math.round(interPolant * (high - low));
        }

        static int interpolateColor(int colors[], float x, float y) {
            float angle = (float)java.lang.Math.atan2(y, x);
            float unit = angle / (2 * PI);
            if (unit < 0) {
                unit += 1;
            }
            if (unit <= 0) {
                return colors[0];
            }
            if (unit >= 1) {
                return colors[colors.length - 1];
            }

            float p = unit * (colors.length - 1);
            int i = (int)p;
            p -= i;

            // Now p is just the fractional part [0...1) and i is the index.
            int c0 = colors[i];
            int c1 = colors[i+1];
            int a = interpolate(Color.alpha(c0), Color.alpha(c1), p);
            int r = interpolate(Color.red(c0), Color.red(c1), p);
            int g = interpolate(Color.green(c0), Color.green(c1), p);
            int b = interpolate(Color.blue(c0), Color.blue(c1), p);

            return Color.argb(a, r, g, b);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - center_x;
            float y = event.getY() - center_y;

            // equivalent to sqrt(x * x + y * y) <= CENTER_RADIUS but cheaper
            boolean inCenter = (x * x + y * y) <= (CENTER_RADIUS * CENTER_RADIUS);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTrackingCenter = inCenter;
                    if (inCenter) {
                        mHighlightCenter = true;
                        invalidate();
                        break;
                    }
                case MotionEvent.ACTION_MOVE:
                    if (mTrackingCenter) {
                        if (mHighlightCenter != inCenter) {
                            mHighlightCenter = inCenter;
                            invalidate();
                        }
                    } else {
                        mCenterPaint.setColor(interpolateColor(mColors, x, y));
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mTrackingCenter) {
                        if (inCenter) {
                            mListener.colorChanged(mCenterPaint.getColor());
                        }

                        // Draw without the halo surrounding the central circle.
                        mTrackingCenter = false;
                        invalidate();
                    }
                    break;
                 default:
                     break;
            }
            return true;
        }
    }

    public ColorPickerDialog(Context context,
            OnColorChangedListener listener,
            int initialColor) {
        super(context);

        mListener = listener;
        mInitialColor = initialColor;

        setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                mListener.colorChanged(mInitialColor);
            }
          });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new ColorPickerView(getContext(), mListener, mInitialColor));

        // TODO(miguelg): Internationalization
        setTitle("Select Color");
    }
}
