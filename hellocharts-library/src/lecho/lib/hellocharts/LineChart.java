package lecho.lib.hellocharts;

import java.util.List;

import lecho.lib.hellocharts.anim.ChartAnimator;
import lecho.lib.hellocharts.anim.ChartAnimatorV11;
import lecho.lib.hellocharts.anim.ChartAnimatorV8;
import lecho.lib.hellocharts.model.AnimatedValue;
import lecho.lib.hellocharts.model.ChartData;
import lecho.lib.hellocharts.model.InternalLineChartData;
import lecho.lib.hellocharts.model.InternalSeries;
import lecho.lib.hellocharts.utils.Config;
import lecho.lib.hellocharts.utils.Utils;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class LineChart extends View {
	private static final String TAG = "LineChart";
	private static final float LINE_SMOOTHNES = 0.16f;
	private static final int DEFAULT_LINE_WIDTH_DP = 2;
	private static final int DEFAULT_POINT_RADIUS_DP = 6;
	private static final int DEFAULT_POINT_TOUCH_RADIUS_DP = 12;
	private static final int DEFAULT_TEXT_SIZE_DP = 14;
	private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
	private Path mLinePath = new Path();
	private Paint mLinePaint = new Paint();
	private Paint mPointPaint = new Paint();
	private Paint mRulersPaint = new Paint();
	private InternalLineChartData mData;
	private float mLineWidth;
	private float mPointRadius;
	private float mPointPressedRadius;
	private float mTouchRadius;
	private float mXMultiplier;
	private float mYMultiplier;
	private float mAvailableWidth;
	private float mAvailableHeight;
	private boolean mLinesOn = true;
	private boolean mInterpolationOn = true;
	private boolean mPointsOn = true;
	private boolean mPopupsOn = false;
	private ChartAnimator mAnimator;
	private int mSelectedSeriesIndex = Integer.MIN_VALUE;
	private int mSelectedValueIndex = Integer.MIN_VALUE;
	private OnPointClickListener mOnPointClickListener = new DummyOnPointListener();

	public LineChart(Context context) {
		super(context);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	public LineChart(Context context, AttributeSet attrs) {
		super(context, attrs);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	public LineChart(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	private void initAttributes() {
		mLineWidth = Utils.dp2px(getContext(), DEFAULT_LINE_WIDTH_DP);
		mPointRadius = Utils.dp2px(getContext(), DEFAULT_POINT_RADIUS_DP);
		mPointPressedRadius = mPointRadius + Utils.dp2px(getContext(), 4);
		mTouchRadius = Utils.dp2px(getContext(), DEFAULT_POINT_TOUCH_RADIUS_DP);
	}

	private void initPaints() {
		mLinePaint.setAntiAlias(true);
		mLinePaint.setStyle(Paint.Style.STROKE);
		mLinePaint.setStrokeWidth(mLineWidth);
		mLinePaint.setStrokeCap(Cap.ROUND);

		mPointPaint.setAntiAlias(true);
		mPointPaint.setStyle(Paint.Style.FILL);
		mPointPaint.setStrokeWidth(1);
		mPointPaint.setTextSize(Utils.dp2px(getContext(), DEFAULT_TEXT_SIZE_DP));

		mRulersPaint.setStyle(Paint.Style.STROKE);
		mRulersPaint.setColor(Color.LTGRAY);
		mRulersPaint.setStrokeWidth(1);

	}

	private void initAnimatiors() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mAnimator = new ChartAnimatorV8(this, Config.DEFAULT_ANIMATION_DURATION);
		} else {
			mAnimator = new ChartAnimatorV11(this, Config.DEFAULT_ANIMATION_DURATION);
		}
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		long time = System.nanoTime();
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		// TODO mPointRadus can change, recalculate in setter
		calculateAvailableDimensions();
		// TODO max-min can chage - recalculate in setter
		calculateMultipliers();
		Log.v(TAG, "onSizeChanged [ms]: " + (System.nanoTime() - time) / 1000000f);
	}

	/**
	 * Calculates available width and height. Should be called when chart dimensions or chart data change.
	 */
	private void calculateAvailableDimensions() {
		final float additionalPadding = 2 * mPointPressedRadius;
		mAvailableWidth = getWidth() - getPaddingLeft() - getPaddingRight() - additionalPadding;
		mAvailableHeight = getHeight() - getPaddingTop() - getPaddingBottom() - additionalPadding;
	}

	/**
	 * Calculates multipliers used to translate values into pixels. Should be called when chart dimensions or chart data
	 * change.
	 */
	private void calculateMultipliers() {
		mXMultiplier = mAvailableWidth / (mData.getMaxXValue() - mData.getMinXValue());
		mYMultiplier = mAvailableHeight / (mData.getMaxYValue() - mData.getMinYValue());
	}

	@Override
	protected void onDraw(Canvas canvas) {
		long time = System.nanoTime();
		//TODO move lines to the right of the Y values, calculate margin using formater and max Y value
		mPointPaint.setColor(mRulersPaint.getColor());
		for (float y : mData.mYRules) {
			float rawY = calculateY(y);
			float rawX1 = calculateX(mData.getMinXValue());
			float rawX2 = calculateX(mData.getMaxXValue());
			canvas.drawLine(rawX1, rawY, rawX2, rawY, mRulersPaint);
			canvas.drawText(String.valueOf(y), rawX1, rawY, mPointPaint);
		}

		if (mLinesOn) {
			drawLines(canvas);
		}
		if (mPointsOn) {
			drawPoints(canvas);
		}

		Log.v(TAG, "onDraw [ms]: " + (System.nanoTime() - time) / 1000000f);
	}

	private void drawLines(Canvas canvas) {
		for (InternalSeries internalSeries : mData.getInternalsSeries()) {
			if (mInterpolationOn) {
				drawSmoothPath(canvas, internalSeries);
			} else {
				drawPath(canvas, internalSeries);
			}
			mLinePath.reset();
		}
	}

	// TODO Drawing points can be done in the same loop as drawing lines but it may cause problems in the future. Reuse
	// calculated X/Y;
	private void drawPoints(Canvas canvas) {
		for (InternalSeries internalSeries : mData.getInternalsSeries()) {
			mPointPaint.setColor(internalSeries.getColor());
			int valueIndex = 0;
			for (float valueX : mData.getDomain()) {
				final float rawValueX = calculateX(valueX);
				final float valueY = internalSeries.getValues().get(valueIndex).getPosition();
				final float rawValueY = calculateY(valueY);
				canvas.drawCircle(rawValueX, rawValueY, mPointRadius, mPointPaint);
				if (mPopupsOn) {
					final String textValue = String.format(Config.DEFAULT_VALUE_FORMAT, valueY);
					drawValuePopup(canvas, mPointRadius, textValue, rawValueX, rawValueY);
				}
				++valueIndex;
			}
		}
		if (mSelectedSeriesIndex >= 0 && mSelectedValueIndex >= 0) {
			final float valueX = mData.getDomain().get(mSelectedValueIndex);
			final float rawValueX = calculateX(valueX);
			final float valueY = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
					.get(mSelectedValueIndex).getPosition();
			final float rawValueY = calculateY(valueY);
			mPointPaint.setColor(mData.getInternalsSeries().get(mSelectedSeriesIndex).getColor());
			canvas.drawCircle(rawValueX, rawValueY, mPointPressedRadius, mPointPaint);
			if (mPopupsOn) {
				final String textValue = String.format(Config.DEFAULT_VALUE_FORMAT, valueY);
				drawValuePopup(canvas, mPointRadius, textValue, rawValueX, rawValueY);
			}
		}
	}

	private void drawValuePopup(Canvas canvas, float offset, String text, float rawValueX, float rawValueY) {
		final float margin = (float) Utils.dp2px(getContext(), 4);
		final Rect textBounds = new Rect();
		mPointPaint.getTextBounds(text, 0, text.length(), textBounds);
		float left = rawValueX + offset;
		float right = rawValueX + offset + textBounds.width() + margin * 2;
		float top = rawValueY - offset - textBounds.height() - margin * 2;
		float bottom = rawValueY - offset;
		if (top < getPaddingTop() + mPointPressedRadius) {
			top = rawValueY + offset;
			bottom = rawValueY + offset + textBounds.height() + margin * 2;
		}
		if (right > getWidth() - getPaddingRight() - mPointPressedRadius) {
			left = rawValueX - offset - textBounds.width() - margin * 2;
			right = rawValueX - offset;
		}
		final RectF popup = new RectF(left, top, right, bottom);
		canvas.drawRoundRect(popup, margin, margin, mPointPaint);
		final int color = mPointPaint.getColor();
		mPointPaint.setColor(DEFAULT_TEXT_COLOR);
		canvas.drawText(text, left + margin, bottom - margin, mPointPaint);
		mPointPaint.setColor(color);
	}

	private void drawPath(Canvas canvas, final InternalSeries internalSeries) {
		int valueIndex = 0;
		for (float valueX : mData.getDomain()) {
			final float rawValueX = calculateX(valueX);
			final float rawValueY = calculateY(internalSeries.getValues().get(valueIndex).getPosition());
			if (valueIndex == 0) {
				mLinePath.moveTo(rawValueX, rawValueY);
			} else {
				mLinePath.lineTo(rawValueX, rawValueY);
			}
			++valueIndex;
		}
		mLinePaint.setColor(internalSeries.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
	}

	private void drawSmoothPath(Canvas canvas, final InternalSeries internalSeries) {
		final int domainSize = mData.getDomain().size();
		float previousPointX = Float.NaN;
		float previousPointY = Float.NaN;
		float currentPointX = Float.NaN;
		float currentPointY = Float.NaN;
		float nextPointX = Float.NaN;
		float nextPointY = Float.NaN;
		for (int valueIndex = 0; valueIndex < domainSize - 1; ++valueIndex) {
			if (Float.isNaN(currentPointX)) {
				currentPointX = calculateX(mData.getDomain().get(valueIndex));
				currentPointY = calculateY(internalSeries.getValues().get(valueIndex).getPosition());
			}
			if (Float.isNaN(previousPointX)) {
				if (valueIndex > 0) {
					previousPointX = calculateX(mData.getDomain().get(valueIndex - 1));
					previousPointY = calculateY(internalSeries.getValues().get(valueIndex - 1).getPosition());
				} else {
					previousPointX = currentPointX;
					previousPointY = currentPointY;
				}
			}
			if (Float.isNaN(nextPointX)) {
				nextPointX = calculateX(mData.getDomain().get(valueIndex + 1));
				nextPointY = calculateY(internalSeries.getValues().get(valueIndex + 1).getPosition());
			}
			// afterNextPoint is always new one or it is equal nextPoint.
			final float afterNextPointX;
			final float afterNextPointY;
			if (valueIndex < domainSize - 2) {
				afterNextPointX = calculateX(mData.getDomain().get(valueIndex + 2));
				afterNextPointY = calculateY(internalSeries.getValues().get(valueIndex + 2).getPosition());
			} else {
				afterNextPointX = nextPointX;
				afterNextPointY = nextPointY;
			}
			// Calculate control points.
			final float firstDiffX = (nextPointX - previousPointX);
			final float firstDiffY = (nextPointY - previousPointY);
			final float secondDiffX = (afterNextPointX - currentPointX);
			final float secondDiffY = (afterNextPointY - currentPointY);
			final float firstControlPointX = currentPointX + (LINE_SMOOTHNES * firstDiffX);
			final float firstControlPointY = currentPointY + (LINE_SMOOTHNES * firstDiffY);
			final float secondControlPointX = nextPointX - (LINE_SMOOTHNES * secondDiffX);
			final float secondControlPointY = nextPointY - (LINE_SMOOTHNES * secondDiffY);
			mLinePath.moveTo(currentPointX, currentPointY);
			mLinePath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
					nextPointX, nextPointY);
			// Shift values to prevent recalculation of values that where already calculated.
			previousPointX = currentPointX;
			previousPointY = currentPointY;
			currentPointX = nextPointX;
			currentPointY = nextPointY;
			nextPointX = afterNextPointX;
			nextPointY = afterNextPointY;
		}
		mLinePaint.setColor(internalSeries.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
	}

	private float calculateX(float valueX) {
		final float additionalPadding = mPointPressedRadius;
		return getPaddingLeft() + additionalPadding + (valueX - mData.getMinXValue()) * mXMultiplier;
	}

	private float calculateY(float valueY) {
		final float additionalPadding = mPointPressedRadius;
		return getHeight() - getPaddingBottom() - additionalPadding - (valueY - mData.getMinYValue()) * mYMultiplier;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!mPointsOn) {
			// No point - no touch events.
			return true;
		}
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// Select only the first value within touched area.
			// Reverse loop to starts with the line drawn on top.
			for (int seriesIndex = mData.getInternalsSeries().size() - 1; seriesIndex >= 0; --seriesIndex) {
				int valueIndex = 0;
				for (AnimatedValue value : mData.getInternalsSeries().get(seriesIndex).getValues()) {
					final float rawX = calculateX(mData.getDomain().get(valueIndex));
					final float rawY = calculateY(value.getPosition());
					if (Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
						mSelectedSeriesIndex = seriesIndex;
						mSelectedValueIndex = valueIndex;
						invalidate();
						return true;
					}
					++valueIndex;
				}
			}
			break;
		case MotionEvent.ACTION_UP:
			// If value was selected call click listener and clear selection.
			if (mSelectedValueIndex >= 0) {
				final float x = mData.getDomain().get(mSelectedValueIndex);
				final float y = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
						.get(mSelectedValueIndex).getPosition();
				mOnPointClickListener.onPointClick(mSelectedSeriesIndex, mSelectedValueIndex, x, y);
				mSelectedSeriesIndex = Integer.MIN_VALUE;
				mSelectedValueIndex = Integer.MIN_VALUE;
				invalidate();
			}
			break;
		case MotionEvent.ACTION_MOVE:
			// Clear selection if user is now touching outside touch area.
			if (mSelectedValueIndex >= 0) {
				final float x = mData.getDomain().get(mSelectedValueIndex);
				final float y = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
						.get(mSelectedValueIndex).getPosition();
				final float rawX = calculateX(x);
				final float rawY = calculateY(y);
				if (!Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
					mSelectedSeriesIndex = Integer.MIN_VALUE;
					mSelectedValueIndex = Integer.MIN_VALUE;
					invalidate();
				}
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			// Clear selection
			if (mSelectedValueIndex >= 0) {
				mSelectedSeriesIndex = Integer.MIN_VALUE;
				mSelectedValueIndex = Integer.MIN_VALUE;
				invalidate();
			}
			break;
		default:
			break;
		}
		return true;
	}

	public void setData(final ChartData rawData) {
		mData = InternalLineChartData.createFromRawData(rawData);
		mData.calculateRanges();
		calculateAvailableDimensions();
		calculateMultipliers();
		postInvalidate();
	}

	public ChartData getData() {
		return mData.getRawData();
	}

	public void animationUpdate(float scale) {
		for (AnimatedValue value : mData.getInternalsSeries().get(0).getValues()) {
			value.update(scale);
		}
		mData.calculateYRanges();
		calculateAvailableDimensions();
		calculateMultipliers();
		invalidate();
	}

	public void animateSeries(int index, List<Float> values) {
		mAnimator.cancelAnimation();
		mData.updateSeriesTargetPositions(index, values);
		mAnimator.startAnimation();
	}

	public void updateSeries(int index, List<Float> values) {
		mData.updateSeries(index, values);
		postInvalidate();
	}

	public void setOnPointClickListener(OnPointClickListener listener) {
		if (null == listener) {
			mOnPointClickListener = new DummyOnPointListener();
		} else {
			mOnPointClickListener = listener;
		}
	}

	// Just empty listener to avoid NPE checks.
	private static class DummyOnPointListener implements OnPointClickListener {

		@Override
		public void onPointClick(int selectedSeriesIndex, int selectedValueIndex, float x, float y) {
			// Do nothing.
		}

	}

}
