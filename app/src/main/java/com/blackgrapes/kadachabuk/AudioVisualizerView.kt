package com.blackgrapes.kadachabuk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bytes: ByteArray? = null
    private var visualizer: Visualizer? = null
    private val paint = Paint()
    private val barColor = Color.parseColor("#FFD700") // Bright gold for visibility
    private val barWidth = 10f
    private val gap = 4f
    
    private var isAnimating = false
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                invalidate()
                postDelayed(this, 50) // Update every 50ms for smooth animation
            }
        }
    }

    init {
        paint.color = barColor
        paint.strokeWidth = barWidth
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.ROUND
    }

    // Link this view to the player's audio session
    fun linkTo(audioSessionId: Int) {
        if (visualizer != null) {
            android.util.Log.d("AudioVisualizerView", "Visualizer already linked, skipping")
            return
        }

        android.util.Log.d("AudioVisualizerView", "Attempting to link to session: $audioSessionId")
        
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                android.util.Log.d("AudioVisualizerView", "Capture size: $captureSize")
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        bytes = waveform
                        // Log only occasionally to avoid spam
                        if (System.currentTimeMillis() % 1000 < 100) {
                            android.util.Log.v("AudioVisualizerView", "Waveform: ${waveform?.size} bytes")
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {}
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
                android.util.Log.d("AudioVisualizerView", "Visualizer enabled")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioVisualizerView", "Visualizer failed, using animation", e)
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            android.util.Log.d("AudioVisualizerView", "Animation started")
            post(animationRunnable)
        }
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
        android.util.Log.d("AudioVisualizerView", "Animation stopped")
    }

    fun release() {
        visualizer?.release()
        visualizer = null
        bytes = null
        stopAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val numBars = 8
        
        val currentBytes = bytes
        val time = System.currentTimeMillis()
        
        // More strict validation - check for actual varying amplitudes
        var hasValidData = false
        if (currentBytes != null && currentBytes.size > 20) {
            var minAmp = 128
            var maxAmp = 0
            for (b in currentBytes) {
                val amp = abs(b.toInt())
                if (amp < minAmp) minAmp = amp
                if (amp > maxAmp) maxAmp = amp
            }
            // Only consider valid if there's variation AND significant amplitude
            hasValidData = (maxAmp - minAmp) > 5 && maxAmp > 15
        }
        
        for (i in 0 until numBars) {
            val barHeight = if (hasValidData) {
                // Use real waveform data
                val startIdx = (i * currentBytes!!.size / numBars).coerceIn(0, currentBytes.size - 1)
                val endIdx = ((i + 1) * currentBytes.size / numBars).coerceIn(0, currentBytes.size - 1)
                
                var maxAmplitude = 0
                for (idx in startIdx until endIdx) {
                    val amplitude = abs(currentBytes[idx].toInt())
                    if (amplitude > maxAmplitude) maxAmplitude = amplitude
                }
                
                val normalizedAmplitude = (maxAmplitude / 128f) * 8f // Slightly reduced boost
                (height * normalizedAmplitude).coerceIn(10f, height * 0.90f)
            } else {
                // Animated fallback - simulate audio waves
                val phase = (time / 100.0) + (i * 0.5)
                val wave1 = sin(phase * 2.0)
                val wave2 = sin(phase * 3.0 + 1.0) * 0.5
                val combined = (wave1 + wave2) / 1.5
                val amplitude = (abs(combined) * 0.7 + 0.3).toFloat()
                (height * amplitude).coerceIn(12f, height * 0.9f)
            }

            val left = i * (width / numBars) + 2f
            val top = (height - barHeight) / 2
            val right = left + barWidth
            val bottom = top + barHeight
            
            canvas.drawRoundRect(left, top, right, bottom, 3f, 3f, paint)
        }
    }
}
