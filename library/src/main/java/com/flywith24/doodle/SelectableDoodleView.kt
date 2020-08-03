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
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 20f
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val mBoundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH
            strokeCap = Paint.Cap.SQUARE
        }
    }
    private val mGestureDetector by lazy { GestureDetector(context, this) }

    private val mPathList = ArrayList<PathItem>()
    private val mActionIcons = ArrayList<ActionIconItem>()
    private val mResIds = listOf(
        R.drawable.doodle_action_btn_delete_n,
        R.drawable.doodle_action_btn_rotate_n,
        R.drawable.doodle_action_btn_scale_n
    )
    private var mCurrentPath: PathItem? = null
    private var mSelectedPath: PathItem? = null
    private var mLastX = 0f
    private var mLastY = 0f

    init {
        for (resId in mResIds) {
            mActionIcons.add(ActionIconItem(BitmapFactory.decodeResource(resources, resId)))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (mSelectedPath == null) {
                // 置空 path，代表一次涂鸦完成
                mCurrentPath = null
            }

            Log.i(TAG, "onTouchEvent: ACTION_UP")
        }
        return mGestureDetector.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in mPathList) {
            val bounds = item.bounds
            if (mSelectedPath == item) {
                drawBound(canvas, bounds)
            }
            canvas.save()
            canvas.translate(item.offsetX, item.offsetY)
            canvas.drawPath(item.path, mPaint)
            canvas.restore()
        }
    }

    private fun drawBound(canvas: Canvas, bounds: RectF) {
        canvas.drawRect(
            bounds.left - INSIDE_WIDTH,
            bounds.top - INSIDE_WIDTH,
            bounds.right + INSIDE_WIDTH,
            bounds.bottom + INSIDE_WIDTH,
            mBoundPaint
        )

        //左上角 删除按钮
        val delete = mActionIcons[0]
        delete.left = bounds.left - INSIDE_WIDTH - delete.bitmap.width / 2
        delete.top = bounds.top - INSIDE_WIDTH - delete.bitmap.height / 2
        canvas.drawBitmap(delete.bitmap, delete.left, delete.top, null)

        //右上角 旋转按钮
        val rotate = mActionIcons[1]
        rotate.left = bounds.right - INSIDE_WIDTH
        rotate.top = bounds.top - INSIDE_WIDTH - rotate.bitmap.height / 2
        canvas.drawBitmap(rotate.bitmap, rotate.left, rotate.top, null)

        //右下角 缩放按钮
        val scale = mActionIcons[2]
        scale.left = bounds.right - INSIDE_WIDTH
        scale.top = bounds.bottom - INSIDE_WIDTH
        canvas.drawBitmap(scale.bitmap, scale.left, scale.top, null)
    }

    override fun onShowPress(e: MotionEvent?) {

    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        mSelectedPath = isInPath(e)
        invalidate()
        return false

    }

    private fun isInPath(e: MotionEvent): PathItem? {
        for (item in mPathList) {
            if (item.bounds.contains(e.x, e.y)) {
                return item
            }
        }
        return null
    }

    override fun onDown(e: MotionEvent): Boolean {
        Log.i(TAG, "onDown: ")
        if (mSelectedPath != null) {
            when {
                isInPath(e) == null -> mSelectedPath = null

            }
        }

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
        if (mSelectedPath == null) {
            if (mCurrentPath == null) {
                //第一笔，设置起点
                mCurrentPath = PathItem()
                mPathList.add(mCurrentPath!!)
                mCurrentPath!!.path.moveTo(e1.x, e1.y)
                mLastX = e1.x
                mLastY = e1.y
            } else {
                //贝塞尔曲线
                mCurrentPath!!.path.quadTo(
                    mLastX,
                    mLastY,
                    (e2.x + mLastX) / 2f,
                    (e2.y + mLastY) / 2f
                )
                mLastX = e2.x
                mLastY = e2.y
            }
        } else {
            mSelectedPath!!.offsetX = mSelectedPath!!.offsetX - distanceX
            mSelectedPath!!.offsetY = mSelectedPath!!.offsetY - distanceY
        }

        invalidate()
        return false
    }

    override fun onLongPress(e: MotionEvent?) {

    }

    class PathItem {
        val path = Path()
        val bounds: RectF = RectF()
            get() {
                path.computeBounds(field, true)
                field.offset(offsetX, offsetY)
                return field
            }
        var offsetX = 0f
        var offsetY = 0f
    }

    class ActionIconItem(val bitmap: Bitmap) {
        var left = 0f
        var top = 0f
        val bounds: RectF = RectF()
            get() {
                field.set(left, top, left + bitmap.width, top + bitmap.height)
                return field
            }
    }

    companion object {
        private const val TAG = "SelectableDoodleView"

        /**
         * 选中框宽度
         */
        private val STROKE_WIDTH = 1f.dp

        /**
         * 选中框内边距
         */
        private val INSIDE_WIDTH = 10f.dp

        /**
         * 防止滚出的安全距离
         */
        private val SAFE_WIDTH = 10f.dp
    }
}