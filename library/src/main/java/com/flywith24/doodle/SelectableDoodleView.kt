package com.flywith24.doodle

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.asin
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt


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

    private val mPathList = ArrayList<PathItem>()
    private val mActionIcons = HashMap<Int, ActionIconItem>()
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
            mActionIcons[resId] =
                ActionIconItem(BitmapFactory.decodeResource(resources, resId), resId)
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
                mSelectedPath!!.isRoting = false
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
            canvas.drawPath(item.path, mPaint)
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

    /**
     * 是否点击到 action 按钮
     */
    private fun isInActionIcon(resId: Int, e: MotionEvent): Boolean =
        mActionIcons[resId]?.bounds?.contains(e.x, e.y) ?: false

    private fun drawBound(canvas: Canvas) {
        if (mSelectedPath == null) return
        canvas.save()
        with(mSelectedPath!!) {
//            canvas.rotate(rotate, pivotX, pivotY)
            canvas.drawRect(
                bounds.left - INSIDE_WIDTH,
                bounds.top - INSIDE_WIDTH,
                bounds.right + INSIDE_WIDTH,
                bounds.bottom + INSIDE_WIDTH,
                mBoundPaint
            )
            if (!isScaling && !isRoting) {
                //左上角 删除按钮
                val delete = mActionIcons[R.drawable.doodle_action_btn_delete_n]!!
                delete.left = bounds.left - INSIDE_WIDTH - delete.bitmap.width / 2
                delete.top = bounds.top - INSIDE_WIDTH - delete.bitmap.height / 2
                canvas.drawBitmap(delete.bitmap, delete.left, delete.top, null)

                //右上角 旋转按钮
                val rotate = mActionIcons[R.drawable.doodle_action_btn_rotate_n]!!
                rotate.left = bounds.right - INSIDE_WIDTH
                rotate.top = bounds.top - INSIDE_WIDTH - rotate.bitmap.height / 2
                canvas.drawBitmap(rotate.bitmap, rotate.left, rotate.top, null)

                //右下角 缩放按钮
                val scale = mActionIcons[R.drawable.doodle_action_btn_scale_n]!!
                scale.left = bounds.right - INSIDE_WIDTH
                scale.top = bounds.bottom - INSIDE_WIDTH
                canvas.drawBitmap(scale.bitmap, scale.left, scale.top, null)
            }
        }
        canvas.restore()
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        mSelectedPath = isInPath(e)
        Log.i(TAG, "onSingleTapUp: 是否点击 path ${mSelectedPath != null}")
        invalidate()
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        Log.d(TAG, "onDown: ")
        mSelectedPath?.apply {
            when {
                //如果是点击到删除按钮，直接删除不传递事件
                isInActionIcon(R.drawable.doodle_action_btn_delete_n, e) -> {
                    mPathList.remove(this)
                    mSelectedPath = null
                    invalidate()
                    return false
                }
                // 如果是点击到选中或缩放，do nothing
                isInActionIcon(R.drawable.doodle_action_btn_rotate_n, e) -> {
                    isRoting = true

                }
                isInActionIcon(R.drawable.doodle_action_btn_scale_n, e) -> {
                    isScaling = true
                    initialScale = currentScale
                    pivotX = bounds.right - bounds.width() / 2
                    pivotY = bounds.bottom - bounds.height() / 2
                    originDistance = getDistance(pivotX, pivotY, bounds.right, bounds.bottom)
                }
                // 已有选中 item，且新点击的位置不是已画路径区域（前两个判断优先级更高）
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

    fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val distanceX = x2 - x1
        val distanceY = y2 - y1
        return sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    private fun getAngle(pivotX: Float, pivotY: Float, touchX: Float, touchY: Float): Float {
        val x = touchX - pivotX
        val y = touchY - pivotY
        return (asin(y / hypot(x, y)) * 180 / Math.PI).toFloat()
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
            Log.i(TAG, "onScroll: ")
            when {
                /* mSelectedPath!!.isRoting
                         || isInActionIcon(R.drawable.doodle_action_btn_rotate_n, e1) -> {
                     mSelectedPath?.apply {
                         pivotX = bounds.right - bounds.width() / 2
                         pivotY = bounds.bottom - bounds.height() / 2
                         rotate = getAngle(pivotX, pivotY, e2.x, e2.y)
                         Log.e(TAG, "旋转: ${rotate}")

                         transform.setRotate(rotate, pivotX, pivotY)
                         // 对 originPath 做 transform，结果影响到 path，originPath 保持不变
                         originPath.transform(transform, path)
                         isRoting = true
                     }
                 }*/
                isInActionIcon(R.drawable.doodle_action_btn_scale_n, e1) -> {
                    mSelectedPath!!.apply {
                        isScaling = true
                        pivotX = bounds.right - bounds.width() / 2
                        pivotY = bounds.bottom - bounds.height() / 2

                        currentScale =
                            initialScale * getDistance(pivotX, pivotY, e2.x, e2.y) / originDistance
                        currentScale = max(currentScale, MIN_SCALE)
                        Log.e(TAG, "按钮缩放 onScale: $currentScale")
                        transformPath()
                    }
                }
                //拖拽移动
                else -> {
                    mSelectedPath!!.apply {
                        offsetX -= distanceX
                        offsetY -= distanceY
                        Log.e(TAG, "拖拽 onScale: $currentScale $offsetX $offsetY")
                        transformPath()
                    }
                }
            }
        }
        invalidate()
        return false
    }

    private fun PathItem.transformPath() {
        transform.reset()
        transform.preScale(currentScale, currentScale, pivotX, pivotY)
        transform.postTranslate(offsetX, offsetY)
        // 对 originPath 做 transform，结果影响到 path，originPath 保持不变
        originPath.transform(transform, path)
    }

    override fun onLongPress(e: MotionEvent?) {
        // 摸100ms
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "onScaleBegin: ")
        mSelectedPath?.apply {
            isScaling = true
            initialScale = currentScale
            pivotX = bounds.right - bounds.width() / 2
            pivotY = bounds.bottom - bounds.height() / 2
        }
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        Log.d(TAG, "onScaleEnd: ")
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        mSelectedPath?.apply {
            currentScale = initialScale * detector.scaleFactor
            Log.d(TAG, "onScale: $currentScale")
            transformPath()
        }

        invalidate()
        return false
    }

    class PathItem {
        val path = Path()
        val bounds: RectF = RectF()
            get() {
                path.computeBounds(field, true)
                return field
            }
        var originPath = Path(path)

        var transform = Matrix()

        //偏移量
        var offsetX = 0f
        var offsetY = 0f

        //当前缩放值
        var currentScale = 1f

        //初始缩放值
        var initialScale = 1f

        var originDistance = 0f

        //缩放中心点
        var pivotX = 1f
        var pivotY = 1f
        var isScaling = false
        var isRoting = false
        var rotate = 0f
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

        private const val MIN_SCALE = 0.8f
    }
}