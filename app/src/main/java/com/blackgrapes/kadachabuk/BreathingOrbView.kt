package com.blackgrapes.kadachabuk

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class BreathingOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var baseColor = Color.parseColor("#FFD700") 
    private val ripples = mutableListOf<Ripple>()
    private var lastPulseTime = 0L
    
    // Internal class to track each ripple's state
    private class Ripple(var progress: Float = 0f)

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f 
        paint.strokeCap = Paint.Cap.ROUND
    }

    fun setBaseColor(color: Int) {
        baseColor = color
        invalidate()
    }
    
    // Call this whenever a word is spoken
    fun pulse() {
        // Debounce: prevent pulses from stacking too closely (e.g. fast short words)
        val now = System.currentTimeMillis()
        if (now - lastPulseTime < 150) return 
        lastPulseTime = now

        ripples.add(Ripple())
        // Ensure the loop is running
        postInvalidateOnAnimation()
    }
    
    fun setTalking(talking: Boolean) {
        if (!talking) {
            ripples.clear()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (ripples.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = (Math.min(width, height) / 2f)
        
        paint.color = baseColor
        
        // Update and draw ripples
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            
            // Advance progress (Speed of expansion: 0.02 per frame is approx 50 frames or ~1 sec duration)
            ripple.progress += 0.015f 
            
            if (ripple.progress >= 1.0f) {
                iterator.remove()
                continue
            }
            
            val radius = maxRadius * ripple.progress
            // Alpha curve: Fade in quickly, then fade out slowly
            // Use sine-like curve for smooth fade out
            val alpha = (200 * (1.0f - ripple.progress)).toInt()
            
            paint.alpha = alpha
            // Stroke thins as it expands
            paint.strokeWidth = 10f * (1.0f - ripple.progress)
            
            canvas.drawCircle(cx, cy, radius, paint)
        }
        
        // Keep animating as long as there are ripples
        if (ripples.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}    

