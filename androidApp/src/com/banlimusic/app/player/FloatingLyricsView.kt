/**
 * 文件作用：Android 桌面歌词悬浮视图，绘制双行歌词与锁定/关闭按钮。
 */
package com.banlimusic.app.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Lightweight overlay view. Avoiding Compose here keeps the overlay usable
 * without a LifecycleOwner and makes touch/drag handling explicit.
 */
class FloatingLyricsView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val playedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(8f * density, 0f, 0f, Color.BLACK)
    }
    private val unplayedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f * density, 0f, 0f, Color.BLACK)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(5f * density, 0f, 0f, Color.BLACK)
    }
    private val lockRect = RectF()
    private val closeRect = RectF()
    private val dragSlop = 4f * density
    private val outerMargin = 12f * density
    private val controlSize = 30f * density
    private val controlTop = 8f * density

    var playedColor: Int = Color.WHITE
    var unplayedColor: Int = Color.argb(170, 255, 255, 255)
    var textSizeSp: Float = 22f
    var lineCount: Int = 2
    var locked: Boolean = false
        private set
    var primaryText: String = ""
    var secondaryText: String = ""
    var onClose: (() -> Unit)? = null
    var onLockChanged: ((Boolean) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragBy: ((dx: Float, dy: Float) -> Unit)? = null

    private var dragging = false
    private var lastRawX = 0f
    private var lastRawY = 0f

    fun applyStyle(played: Int, unplayed: Int, sizeSp: Float, lines: Int) {
        val newSize = sizeSp.coerceIn(14f, 40f)
        val newLines = lines.coerceIn(1, 2)
        val sizeChanged = newSize != textSizeSp || newLines != lineCount
        val colorChanged = played != playedColor || unplayed != unplayedColor
        playedColor = played
        unplayedColor = unplayed
        textSizeSp = newSize
        lineCount = newLines
        playedPaint.color = playedColor
        unplayedPaint.color = unplayedColor
        playedPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            resources.displayMetrics,
        )
        unplayedPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            (textSizeSp - 2f).coerceAtLeast(12f),
            resources.displayMetrics,
        )
        if (sizeChanged) requestLayout()
        if (sizeChanged || colorChanged) invalidate()
    }

    fun setLyrics(primary: String, secondary: String?) {
        val nextSecondary = secondary.orEmpty()
        if (primaryText == primary && secondaryText == nextSecondary) return
        primaryText = primary
        secondaryText = nextSecondary
        invalidate()
    }

    fun setLocked(value: Boolean) {
        if (locked == value) return
        locked = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val lineHeight = playedPaint.textSize * 1.35f
        val lines = if (lineCount == 2) 2 else 1
        // Keep reserving the (invisible) control row when locked so hiding the
        // buttons never shifts the lyric text upwards.
        val desired = (controlTop + controlSize + 6f * density + lineHeight * lines +
            16f * density).toInt()
        setMeasuredDimension(width, desired)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        closeRect.set(
            w - outerMargin - controlSize - 4f * density,
            controlTop,
            w - outerMargin - 4f * density,
            controlTop + controlSize,
        )
        lockRect.set(
            closeRect.left - controlSize - 4f * density,
            controlTop,
            closeRect.left - 4f * density,
            controlTop + controlSize,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLeft = outerMargin + 6f * density
        val textRight = width - outerMargin - 6f * density
        val available = max(1f, textRight - textLeft)
        val centerX = (textLeft + textRight) / 2f
        val lineHeight = playedPaint.textSize * 1.35f
        val hasSecond = lineCount == 2 && secondaryText.isNotBlank()
        // Unchanged between locked/unlocked states; only the icons disappear.
        val firstBaseline = controlTop + controlSize + 8f * density + playedPaint.textSize
        val current = TextUtils.ellipsize(
            primaryText,
            playedPaint,
            available,
            TextUtils.TruncateAt.END,
        ).toString()
        canvas.drawText(current, centerX, firstBaseline, playedPaint)
        if (hasSecond) {
            val second = TextUtils.ellipsize(
                secondaryText,
                unplayedPaint,
                available,
                TextUtils.TruncateAt.END,
            ).toString()
            canvas.drawText(
                second,
                centerX,
                firstBaseline + lineHeight,
                unplayedPaint,
            )
        }

        if (!locked) {
            drawLockIcon(canvas, lockRect)
            drawCloseIcon(canvas, closeRect)
        }
    }

    private fun drawLockIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val bodyTop = rect.top + rect.height() * 0.45f
        val body = RectF(
            cx - rect.width() * 0.28f,
            bodyTop,
            cx + rect.width() * 0.28f,
            rect.bottom - rect.height() * 0.16f,
        )
        canvas.drawRoundRect(body, 2f * density, 2f * density, iconPaint)
        val shackle = RectF(
            cx - rect.width() * 0.2f,
            rect.top + rect.height() * 0.18f,
            cx + rect.width() * 0.2f,
            bodyTop + 2f * density,
        )
        val startAngle = if (locked) 180f else 205f
        canvas.drawArc(shackle, startAngle, 220f, false, iconPaint)
    }

    private fun drawCloseIcon(canvas: Canvas, rect: RectF) {
        val inset = rect.width() * 0.3f
        canvas.drawLine(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset,
            iconPaint,
        )
        canvas.drawLine(
            rect.right - inset,
            rect.top + inset,
            rect.left + inset,
            rect.bottom - inset,
            iconPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (closeRect.contains(event.x, event.y)) {
                    onClose?.invoke()
                    return true
                }
                if (lockRect.contains(event.x, event.y)) {
                    setLocked(!locked)
                    onLockChanged?.invoke(locked)
                    return true
                }
                if (!locked) {
                    dragging = true
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDragStart?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging && !locked) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    if (kotlin.math.abs(dx) > dragSlop || kotlin.math.abs(dy) > dragSlop) {
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        onDragBy?.invoke(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
