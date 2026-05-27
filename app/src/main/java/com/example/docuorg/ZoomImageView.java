package com.example.docuorg;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Minimal zoomable ImageView supporting pinch-to-zoom and panning.
 * Not a full-featured library but sufficient for image previews.
 */
public class ZoomImageView extends AppCompatImageView {

    private Matrix matrix = new Matrix();
    private float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float minScale = 1f;
    private float maxScale = 4f;

    private PointF last = new PointF();
    private boolean isDragging = false;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix = new Matrix();
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                isDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = curr.x - last.x;
                float dy = curr.y - last.y;
                if (!isDragging) {
                    isDragging = Math.hypot(dx, dy) > 5;
                }
                if (isDragging) {
                    matrix.postTranslate(dx, dy);
                    fixTranslation();
                    setImageMatrix(matrix);
                    last.set(curr.x, curr.y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return true;
    }

    private void fixTranslation() {
        // Very simple bounds check: do not allow image to drift too far away
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        // No-op for now; could be extended to clamp to view bounds
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float prev = scale;
            scale *= factor;
            scale = Math.max(minScale, Math.min(scale, maxScale));
            factor = scale / prev;
            matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            setImageMatrix(matrix);
            return true;
        }
    }
}

