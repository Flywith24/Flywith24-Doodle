package com.flywith24.doodle

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
) : View(context, attrs, defStyleAttr), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {
    private val mPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = ITEM_STROKE_WIDTH
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
    private val mDetector by lazy { GestureDetector(context, this) }
    private val mScaleDetector by lazy { ScaleGestureDetector(context, this) }
    private var mTransform = Matrix()
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
            mActionIcons.add(ActionIconItem(BitmapFactory.decodeResource(resources, resId), resId))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (mSelectedPath == null) {
                // 设置 originPath，防止调用 path.transform 导致 path 改变
                mCurrentPath?.apply { originPath = Path(path) }
                // 置空 mCurrentPath，代表一次涂鸦完成
                mCurrentPath = null
            } else {
                //将边框上的 icon 显示出来
                mSelectedPath!!.isScaling = false
                invalidate()
            }
        }
        var result = mDetector.onTouchEvent(event)
        if (!result) result = mScaleDetector.onTouchEvent(event)
        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 画路径
        for (item in mPathList) {
            canvas.save()
            canvas.translate(item.offsetX, item.offsetY)
            canvas.drawPath(item.path, mPaint)
            canvas.restore()
        }
        // 画边框
        drawBound(canvas)
    }

    private fun isInPath(e: MotionEvent): PathItem? {
        for (item in mPathList) {
            if (item.bounds.contains(e.x, e.y)) {
                return item
            }
        }
        return null
    }

    private fun isInActionIcon(e: MotionEvent): ActionIconItem? {
        for (item in mActionIcons) {
            if (item.bounds.contains(e.x, e.y)) {
                return item
            }
        }
        return null
    }

    private fun drawBound(canvas: Canvas) {
        if (mSelectedPath == null) return

        val bounds = mSelectedPath!!.bounds
        canvas.drawRect(
            bounds.left - INSIDE_WIDTH,
            bounds.top - INSIDE_WIDTH,
            bounds.right + INSIDE_WIDTH,
            bounds.bottom + INSIDE_WIDTH,
            mBoundPaint
        )
        if (mSelectedPath?.isScaling != true) {
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
    }

    private fun iconItemClick(iconItem: ActionIconItem) {
        when (iconItem.resId) {
            R.drawable.doodle_action_btn_delete_n -> {
                Log.i(TAG, "iconItemClick: 删除")
                mPathList.remove(mSelectedPath)
                mSelectedPath = null
                invalidate()
            }
            R.drawable.doodle_action_btn_rotate_n -> {
                Log.i(TAG, "iconItemClick: 旋转")
            }
            R.drawable.doodle_action_btn_scale_n -> {
                Log.i(TAG, "iconItemClick: 缩放")
            }

        }
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        mSelectedPath = isInPath(e)
        invalidate()
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        Log.i(TAG, "onDown: ")
        if (mSelectedPath != null) {
            val iconItem = isInActionIcon(e)
            when {
                iconItem != null -> {
                    Log.i(TAG, "onDown: 点击了图标")
                    iconItemClick(iconItem)
                    //点击了图标，不接管触摸逻辑
                    return false
                }
                isInPath(e) == null -> mSelectedPath = null
            }
        }
        // 接管触摸逻辑，返回 true
        return true
    }

    override fun onShowPress(e: MotionEvent?) {

    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // 惯性滑动
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
                Log.w(TAG, "quadTo: $mLastX $mLastY")
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
            //拖拽移动
            mSelectedPath!!.offsetX = mSelectedPath!!.offsetX - distanceX
            mSelectedPath!!.offsetY = mSelectedPath!!.offsetY - distanceY
        }
        invalidate()
        return false
    }

    override fun onLongPress(e: MotionEvent?) {
        // 摸100ms
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        Log.i(TAG, "onScaleBegin: ")
        mSelectedPath?.apply {
            isScaling = true
            initialScale = currentScale
            pivotX = bounds.right - bounds.width() / 2
            pivotY = bounds.bottom - bounds.height() / 2
        }
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        Log.i(TAG, "onScaleEnd: ")
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        mSelectedPath?.apply {
            currentScale = initialScale * detector.scaleFactor
            mTransform.setScale(currentScale, currentScale, pivotX, pivotY)
            // 对 originPath 做 transform，结果影响到 path，originPath 保持不变
            originPath.transform(mTransform, path)
            Log.e(TAG, "onScale: $currentScale")
        }

        invalidate()
        return false
    }

    class PathItem {
        val path = Path()
        val bounds: RectF = RectF()
            get() {
                path.computeBounds(field, true)
                field.offset(offsetX, offsetY)
                return field
            }
        var originPath = Path(path)

        //偏移量
        var offsetX = 0f
        var offsetY = 0f

        //当前缩放值
        var currentScale = 1f

        //初始缩放值
        var initialScale = 1f

        //缩放中心点
        var pivotX = 1f
        var pivotY = 1f
        var isScaling = false
    }

    class ActionIconItem(val bitmap: Bitmap, val resId: Int) {
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
         * path 宽度
         */
        private val ITEM_STROKE_WIDTH = 5f.dp

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