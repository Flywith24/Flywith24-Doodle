package com.flywith24.doodle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2
import kotlin.math.sqrt


/**
 * @author Flywith24
 * @date   2020/8/3
 * time   11:45
 * description
 * 涂鸦路径可选中/旋转/平移/缩放
 */
class SelectableDoodleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {
    /**
     * 路径画笔
     */
    private val mPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = ITEM_STROKE_WIDTH
            strokeCap = Paint.Cap.ROUND
        }
    }

    /**
     * 边框画笔
     */
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

    /**
     * 当前绘制路径
     */
    private var mCurrentPath: PathItem? = null

    /**
     * 当前选中路径
     */
    private var mSelectedPath: PathItem? = null
    private var mLastX = 0f
    private var mLastY = 0f

    init {
        for (resId in mResIds) {
            //初始化三个操作按钮
            mActionIcons[resId] = ActionIconItem(BitmapFactory.decodeResource(resources, resId))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            //手指抬起
            if (mSelectedPath == null) {
                mCurrentPath?.apply {
                    // 设置 originPath，防止调用 path.transform 导致 path 改变
                    originPath = Path(path)
                    /*
                     * 存储四个顶点
                     */
                    val rect = originBounds
                    pivotX = rect.centerX()
                    pivotY = rect.centerY()
                    //存储 左上，右上，左下，右下位置，内边距 INSIDE_WIDTH，否则不好看
                    srcPs = floatArrayOf(
                        rect.left - INSIDE_WIDTH, rect.top - INSIDE_WIDTH,
                        rect.right + INSIDE_WIDTH, rect.top - INSIDE_WIDTH,
                        rect.left - INSIDE_WIDTH, rect.bottom + INSIDE_WIDTH,
                        rect.right + INSIDE_WIDTH, rect.bottom + INSIDE_WIDTH
                    )
                    dstPs = srcPs.clone()
                }
                // 置空 mCurrentPath，代表一次涂鸦完成
                mCurrentPath = null
            } else {
                with(mSelectedPath!!) {
                    //将边框上的 icon 显示出来
                    isMoving = false
                    isButtonScaling = false
                    isScaling = false
                    isRoting = false
                    invalidate()
                }
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
        // 画边框 + 操作按钮
        drawFrame(canvas)
    }

    /**
     * 是否点击在所有已画 path 范围内，如果是则返回相应的 PathItem，否则返回 null
     */
    private fun isInPath(e: MotionEvent): PathItem? =
        mPathList.find { it.bounds.contains(e.x, e.y) }

    /**
     * 是否点击到 action 按钮
     */
    private fun isInActionIcon(resId: Int, e: MotionEvent): Boolean =
        mActionIcons[resId]?.bounds?.contains(e.x, e.y) ?: false

    /**
     * 绘制边框
     */
    private fun drawFrame(canvas: Canvas) {
        // 选中元素为空则不绘制
        if (mSelectedPath == null) return
        with(mSelectedPath!!) {
            /**
             * 下标 0 1 分别为左上角 x，y，依次类推
             * 01-------23
             * |        |
             * |        |
             * |        |
             * 45-------67
             */
            //(0,1) - (2,3)
            canvas.drawLine(dstPs[0], dstPs[1], dstPs[2], dstPs[3], mBoundPaint)
            //(0,1) - (4,5)
            canvas.drawLine(dstPs[0], dstPs[1], dstPs[4], dstPs[5], mBoundPaint)
            //(2,3) - (6,7)
            canvas.drawLine(dstPs[2], dstPs[3], dstPs[6], dstPs[7], mBoundPaint)
            //(4,5) - (6,7)
            canvas.drawLine(dstPs[4], dstPs[5], dstPs[6], dstPs[7], mBoundPaint)

            //旋转/缩放 状态下隐藏按钮
            if (!isButtonScaling && !isRoting && !isScaling) {
                //左上角 删除按钮
                val delete = mActionIcons[R.drawable.doodle_action_btn_delete_n]!!
                delete.left = dstPs[0] - delete.bitmap.width / 2
                delete.top = dstPs[1] - delete.bitmap.height / 2
                canvas.drawBitmap(delete.bitmap, delete.left, delete.top, null)

                //右上角 旋转按钮
                val rotate = mActionIcons[R.drawable.doodle_action_btn_rotate_n]!!
                rotate.left = dstPs[2] - rotate.bitmap.width / 2
                rotate.top = dstPs[3] - rotate.bitmap.height / 2
                canvas.drawBitmap(rotate.bitmap, rotate.left, rotate.top, null)

                //右下角 缩放按钮
                val scale = mActionIcons[R.drawable.doodle_action_btn_scale_n]!!
                scale.left = dstPs[6] - rotate.bitmap.width / 2
                scale.top = dstPs[7] - rotate.bitmap.height / 2
                canvas.drawBitmap(scale.bitmap, scale.left, scale.top, null)
            }
        }
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // 单击抬起，为选中路径 赋值，可能为 null
        val temp = isInPath(e)
        Log.i(TAG, "onSingleTapUp: 是否点击 path $temp $mSelectedPath")
        mSelectedPath = temp
        invalidate()
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        mSelectedPath?.apply {
            //已有选中路径
            //1. 判断点击区域是否为 action 按钮
            //2. 判断点击区域是否在已画路径区域内，如果不在，清空选中路径
            when {
                //点击到删除按钮，直接删除不传递事件
                isInActionIcon(R.drawable.doodle_action_btn_delete_n, e) -> {
                    mPathList.remove(this)
                    mSelectedPath = null
                    invalidate()
                    Log.d(TAG, "onDown: 点击到删除按钮，不再传递事件")
                    return false
                }
                // 点击到旋转，初始化相应数据
                isInActionIcon(R.drawable.doodle_action_btn_rotate_n, e) -> {
                    isRoting = true
                    Log.d(TAG, "onDown: 点击到旋转按钮")
                }
                // 点击到缩放，初始化相应数据
                isInActionIcon(R.drawable.doodle_action_btn_scale_n, e) -> {
                    isButtonScaling = true
                    initialScale = currentScale
                    //缩放按钮距中心点距离
                    originDistance = getDistance(pivotX, pivotY, dstPs[6], dstPs[7])
                    Log.d(TAG, "onDown: 点击到缩放按钮")
                }
                // 已有选中 item，且新点击的位置不是已画路径区域
                isInPath(e) != mSelectedPath -> mSelectedPath = null

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

    /**
     * 计算两点距离
     * [x1] [y1] 点 1 坐标
     * [x2] [y2] 点 2 坐标
     */
    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val distanceX = x2 - x1
        val distanceY = y2 - y1
        return sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    /**
     * 计算角度
     * [originX] [originY] 原始旋转按钮的位置
     * [touchX] [touchY] 旋转时手指触摸点的位置
     * [pivotX] [pivotY] 旋转中心点位置
     */
    private fun computeDegree(
        originX: Float, originY: Float,
        touchX: Float, touchY: Float,
        pivotX: Float, pivotY: Float
    ): Float {
        val angle1 = atan2(originY - pivotY, originX - pivotX)
        val angle2 = atan2(touchY - pivotY, touchX - pivotX)
        //弧度转角度
        return ((angle2 - angle1) * 180 / Math.PI).toFloat()
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
                Log.d(TAG, "onScroll: 第一笔，设置起点")
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
            with(mSelectedPath!!) {
                when {
                    //正在拖拽移动，防止误触旋转和缩放
                    isMoving -> {
                        move(distanceX, distanceY)
                    }
                    //旋转中 或 点击到了旋转按钮
                    isRoting || isInActionIcon(R.drawable.doodle_action_btn_rotate_n, e1) -> {
                        isRoting = true
                        currentRotate =
                            computeDegree(srcPs[2], srcPs[3], e2.x, e2.y, pivotX, pivotY)
                        Log.d(TAG, "旋转: 角度：$currentRotate  中心点：$pivotX $pivotY")
                        transformPath()
                    }

                    //按钮缩放中 或 点击到了缩放按钮
                    isButtonScaling || isInActionIcon(R.drawable.doodle_action_btn_scale_n, e1) -> {
                        isButtonScaling = true
                        // 本次缩放 = 手指触摸点到中心点距离 / 缩放按钮到中心点距离
                        // 当前缩放 = 初始缩放 * 本次缩放
                        currentScale =
                            initialScale * getDistance(pivotX, pivotY, e2.x, e2.y) / originDistance
                        Log.d(TAG, "onScroll: 按钮缩放 $currentScale")
                        transformPath()
                    }
                    //拖拽移动
                    else -> {
                        move(distanceX, distanceY)
                    }
                }
            }
        }
        invalidate()
        return false
    }

    /**
     * 拖拽移动
     */
    private fun PathItem.move(
        distanceX: Float,
        distanceY: Float
    ) {
        isMoving = true
        offsetX -= distanceX
        offsetY -= distanceY
        Log.d(TAG, "onScroll: 拖拽移动  $currentScale $offsetX $offsetY")
        transformPath()
    }

    /**
     * 使用 Matrix 进行「缩放/旋转/平移」的操作
     * 每次均重置原来的操作
     * 针对原始path（[PathItem.originPath]）进行操作
     */
    private fun PathItem.transformPath() {

        Log.i(TAG, "transformPath: preScale $pivotX ,${pivotY}")
        // setX 方法会重置矩阵，相当于每次重置之前的转换，使用当前值来设置「缩放/旋转/平移」
        transform.setScale(currentScale, currentScale, pivotX, pivotY)
        transform.postRotate(currentRotate, pivotX, pivotY)
        transform.postTranslate(offsetX, offsetY)

        // 对 originPath 做 transform，结果影响到 path，originPath 保持不变
        originPath.transform(transform, path)
        // 对四个顶点 transform，为了让「选中状态的边框与操作按钮」与 path 同步
        transform.mapPoints(dstPs, srcPs)
    }

    override fun onLongPress(e: MotionEvent?) {
        // 摸100ms
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "onScaleBegin: 双指手势缩放开始")
        //双指手势缩放开始，初始化 scale
        mSelectedPath?.apply {
            isScaling = true
            initialScale = currentScale
        }
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        Log.d(TAG, "onScaleEnd: 双指手势缩放结束")
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        mSelectedPath?.apply {
            currentScale = initialScale * detector.scaleFactor
            Log.d(TAG, "手势缩放: 当前 scale $currentScale")
            transformPath()
        }
        invalidate()
        return false
    }

    class PathItem {
        //经过转换后的 path
        val path = Path()

        //path 边界，每次动态计算
        val bounds: RectF = RectF()
            get() {
                path.computeBounds(field, true)
                return field
            }

        //第一次画完的原始 path
        lateinit var originPath: Path

        //原始 path 边界，每次动态计算
        val originBounds: RectF = RectF()
            get() {
                originPath.computeBounds(field, true)
                return field
            }

        // 使用 Matrix 做变换
        var transform = Matrix()

        //偏移量
        var offsetX = 0f
        var offsetY = 0f

        //当前缩放值
        var currentScale = 1f

        //当前旋转值
        var currentRotate = 0f

        //初始缩放值
        var initialScale = 1f

        //当前缩放按钮距中心点距离
        var originDistance = 0f

        //原始 path 中心点
        var pivotX = 1f
        var pivotY = 1f

        var isMoving = false
        var isButtonScaling = false
        var isScaling = false
        var isRoting = false

        /**
         * 下标 0 1 分别为左上角 x，y，依次类推
         * 01-------23
         * |        |
         * |        |
         * |        |
         * 45-------67
         */
        //原始路径四个顶点集合，这些点位置是计算了内边距 INSIDE_WIDTH 后的
        lateinit var srcPs: FloatArray

        //转换后路径四个顶点集合
        lateinit var dstPs: FloatArray
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
        private const val MAX_SCALE = 3f
    }
}