package com.artemchep.essence.ui.drawables

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.artemchep.essence.extensions.radius

/**
 * @author Artem Chepurnoy
 */
class CircleDrawable(@ColorInt c: Int = 0) : Drawable() {

    /**
     * Setting color overrides alpha bits.
     */
    var color: Int
        get() = paint.color
        set(@ColorInt color) {
            paint.color = color or -0x1000000 // ignore alpha bits
            invalidateSelf()
        }

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    init {
        color = c
    }

    /**
     * {@inheritDoc}
     */
    override fun draw(canvas: Canvas) {
        val rect = bounds
        canvas.drawCircle(rect.exactCenterX(), rect.exactCenterY(), rect.radius, paint)
    }

    /**
     * {@inheritDoc}
     */
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    /**
     * {@inheritDoc}
     */
    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
        invalidateSelf()
    }

    /**
     * {@inheritDoc}
     */
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

}