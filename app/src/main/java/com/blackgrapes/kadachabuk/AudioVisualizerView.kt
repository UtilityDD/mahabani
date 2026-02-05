package com.blackgrapes.kadachabuk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.log10

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var visualizer: Visualizer? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val numBars = 20
    private val barWidth = 8f
    private val barGap = 4f
    
    // Arrays to store frequency data and smoothed heights
    private val magnitudes = FloatArray(numBars)
    private val currentHeights = FloatArray(numBars)
    
    // Auto-gain variables
    private var maxObservedMagnitude = 32f // Start with a reasonable baseline
    private val autoGainDecay = 0.995f    // Slowly lower the floor
    
    private val barColorStart = Color.parseColor("#FFE0B0") // Light Gold
    private val barColorEnd = Color.parseColor("#FFD700")   // Gold
    
    private var isAnimating = false
    
    // Physics constants for smoothing
    private val decayRate = 0.12f // Slightly slower fall for more "body"
    private val riseSpeed = 0.5f  // Smooth rise

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                updateSmoothing()
                invalidate()
                postDelayed(this, 16) // ~60 FPS
            }
        }
    }

    init {
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.FILL

        glowPaint.strokeCap = Paint.Cap.ROUND
        glowPaint.style = Paint.Style.FILL
    }

    private fun updateSmoothing() {
        // Slowly decay the auto-gain baseline to keep it reactive if audio gets quieter
        maxObservedMagnitude = (maxObservedMagnitude * autoGainDecay).coerceAtLeast(16f)
        
        for (i in 0 until numBars) {
            val target = magnitudes[i]
            if (target > currentHeights[i]) {
                currentHeights[i] = currentHeights[i] + (target - currentHeights[i]) * riseSpeed
            } else {
                currentHeights[i] = (currentHeights[i] - decayRate).coerceAtLeast(0.01f)
            }
        }
    }

    fun linkTo(audioSessionId: Int) {
        release() 
        
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // Max size
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) {
                            processFft(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioVisualizerView", "Visualizer link failed: ${e.message}")
        }
    }

    private fun processFft(fft: ByteArray) {
        // Speech is concentrated in lower frequencies.
        // Even with 44.1kHz sampling, we mostly care about 0-5kHz for speech.
        // N = size/2. Usable = indices 2..N.
        val n = (fft.size / 2)
        
        // Focus bars on the first ~25% of the spectrum (audible voice range)
        val usableRange = (n * 0.25f).toInt().coerceAtLeast(numBars)
        val barsPerGroup = (usableRange / numBars).coerceAtLeast(1)
        
        var currentMaxThisFrame = 0f
        
        for (i in 0 until numBars) {
            var sum = 0f
            for (j in 0 until barsPerGroup) {
                val index = 2 + (i * barsPerGroup + j) * 2
                if (index + 1 < fft.size) {
                    val r = fft[index].toFloat()
                    val im = fft[index + 1].toFloat()
                    val mag = hypot(r, im)
                    sum += mag
                }
            }
            val average = sum / barsPerGroup
            if (average > currentMaxThisFrame) currentMaxThisFrame = average
            
            // Auto-Gain: Update the global max observed magnitude
            if (average > maxObservedMagnitude) {
                maxObservedMagnitude = average
            }

            // Normalization using Auto-Gain
            val sensitivityLimit = maxObservedMagnitude.coerceAtLeast(16f)
            val normalized = (log10(average + 1f) / log10(sensitivityLimit + 1f)).coerceIn(0f, 1f)
            
            // Add a small nonlinear boost to lower levels to make them visible
            // Power of 0.7 expands smaller values
            magnitudes[i] = Math.pow(normalized.toDouble(), 0.7).toFloat()
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            post(animationRunnable)
        }
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
        // Reset heights on stop
        for (i in 0 until numBars) {
            magnitudes[i] = 0f
            currentHeights[i] = 0f
        }
        invalidate()
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {}
        visualizer = null
        stopAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = LinearGradient(0f, h.toFloat(), 0f, 0f, barColorStart, barColorEnd, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        val totalBarWidth = barWidth + barGap
        val startX = (w - (numBars * totalBarWidth - barGap)) / 2f
        
        for (i in 0 until numBars) {
            val barHeight = currentHeights[i] * h * 0.9f
            val x = startX + i * totalBarWidth
            val top = h - barHeight
            
            // Draw Glow
            canvas.drawRoundRect(x - 2f, top - 2f, x + barWidth + 2f, h, 6f, 6f, glowPaint.apply { 
                color = barColorEnd
                alpha = 40 
            })
            
            // Draw Main Bar
            canvas.drawRoundRect(x, top, x + barWidth, h, 4f, 4f, paint)
        }
    }
}
