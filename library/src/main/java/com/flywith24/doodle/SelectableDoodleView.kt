package com.flywith24.doodle

import android.content.Context
import android.graphics.*

import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * @author Flywith24
 * @date   2020/8/3
 * time   11:45
 * description
 * 涂鸦路径可选中
 */
class SelectableDoodleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GestureDetector.OnGestureListener {
    private val mPaint by lazy {
        Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 20f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val mGestureDetector by lazy { GestureDetector(context, this) }

    private val mPathList = ArrayList<PathItem>()
    private var mCurrentPath: PathItem? = null
    private var mSelectedPath: PathItem? = null
    private var mLastX = 0f
    private var mLastY = 0f


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // 置空 path，代表一次涂鸦完成
            mCurrentPath = null
            Log.i(TAG, "onTouchEvent: ACTION_UP")
        }
        return mGestureDetector.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in mPathList) {
            canvas.save()
            canvas.translate(item.offsetX, item.offsetY)
            mPaint.color = if (mSelectedPath == item) Color.YELLOW else Color.RED
            canvas.drawPath(item.path, mPaint)
            canvas.restore()
        }
    }

    override fun onShowPress(e: MotionEvent?) {

    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        mSelectedPath = isInPath(e)
        invalidate()
        return false

    }

    private fun isInPath(e: MotionEvent): PathItem? {
        val bounds = RectF()
        for (item in mPathList) {
            item.path.computeBounds(bounds, true)
            bounds.offset(item.offsetX, item.offsetY)
            if (bounds.contains(e.x, e.y)) {
                return item
            }
        }
        return null
    }

    override fun onDown(e: MotionEvent?): Boolean {
        return true

    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {

        return false

    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (mCurrentPath == null) {
            //第一笔，设置起点
            mCurrentPath = PathItem()
            mPathList.add(mCurrentPath!!)
            mCurrentPath!!.path.moveTo(e1.x, e1.y)
            mLastX = e1.x
            mLastY = e1.y
        } else {
            //贝塞尔曲线
            mCurrentPath!!.path.quadTo(mLastX, mLastY, (e2.x + mLastX) / 2f, (e2.y + mLastY) / 2f)
            mLastX = e2.x
            mLastY = e2.y
        }
        invalidate()
        return false
    }

    override fun onLongPress(e: MotionEvent?) {

    }

    class PathItem {
        val path = Path()
        var offsetX = 0f
        var offsetY = 0f
    }

    companion object {
        private const val TAG = "SelectableDoodleView"
    }
}