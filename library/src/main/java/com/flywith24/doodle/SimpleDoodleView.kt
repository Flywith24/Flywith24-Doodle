package com.flywith24.doodle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * @author Flywith24
 * @date   2020/8/3
 * time   10:36
 * description
 * 最简单的涂鸦绘制
 */
class SimpleDoodleView @JvmOverloads constructor(
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

    private val mPathList = ArrayList<Path>()
    private var mCurrentPath: Path? = null
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
        for (path in mPathList) {
            canvas.drawPath(path, mPaint)
        }
    }

    override fun onShowPress(e: MotionEvent?) {

    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        Log.i(TAG, "onSingleTapUp: ")
        return true

    }

    override fun onDown(e: MotionEvent?): Boolean {
        Log.i(TAG, "onDown: ")
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.i(TAG, "onFling: ")

        mCurrentPath = null

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
            mCurrentPath = Path()
            mPathList.add(mCurrentPath!!)
            mCurrentPath!!.moveTo(e1.x, e1.y)
            mLastX = e1.x
            mLastY = e1.y
        } else {
            //贝塞尔曲线
            mCurrentPath!!.quadTo(mLastX, mLastY, (e2.x + mLastX) / 2f, (e2.y + mLastY) / 2f)
            mLastX = e2.x
            mLastY = e2.y
        }
        invalidate()
        return false
    }

    override fun onLongPress(e: MotionEvent?) {

    }


    companion object {
        private const val TAG = "SimpleView"
    }
}