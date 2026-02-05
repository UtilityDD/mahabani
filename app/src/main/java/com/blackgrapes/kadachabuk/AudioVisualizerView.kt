package com.blackgrapes.kadachabuk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bytes: ByteArray? = null
    private var visualizer: Visualizer? = null
    private val paint = Paint()
    private val path = Path()
    private val barColor = Color.parseColor("#FFD700")
    
    private var isAnimating = false
    private var animationStartTime = 0L
    private val random = Random(System.currentTimeMillis())
    
    private var volumePhase = 0.0
    private var currentVolume = 0.5f
    
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                invalidate()
                postDelayed(this, 50) // 20 FPS
            }
        }
    }

    init {
        paint.color = barColor
        paint.strokeWidth = 3f
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    fun linkTo(audioSessionId: Int) {
        if (visualizer != null) return

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        bytes = waveform
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {}
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioVisualizerView", "Visualizer failed", e)
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animationStartTime = System.currentTimeMillis()
            volumePhase = 0.0
            post(animationRunnable)
        }
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
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
        val centerY = height / 2
        
        // Simulate volume changes (slow, smooth)
        volumePhase += 0.04 // Slow progression
        
        // Create smooth volume envelope
        val mainVolume = abs(sin(volumePhase * 1.5)).toFloat() * 0.7f + 0.3f // 0.3 to 1.0
        val variation = sin(volumePhase * 4.0).toFloat() * 0.1f
        currentVolume = (mainVolume + variation).coerceIn(0.2f, 1.0f)
        
        // Draw smooth horizontal waveform
        path.reset()
        val numPoints = 40 // Points along the wave
        
        for (i in 0..numPoints) {
            val x = (i / numPoints.toFloat()) * width
            
            // Create smooth wave using multiple sine components
            val xPhase = (volumePhase + i * 0.15)
            val wave1 = sin(xPhase * 2.0).toFloat()
            val wave2 = sin(xPhase * 5.0).toFloat() * 0.3f
            val combinedWave = wave1 + wave2
            
            // Scale by current volume
            val amplitude = (height * 0.25f * currentVolume) * combinedWave
            val y = centerY + amplitude
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }
}
