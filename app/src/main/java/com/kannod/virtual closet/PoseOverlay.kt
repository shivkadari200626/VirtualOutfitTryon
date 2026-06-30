package com.kannod.virtualcloset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.nio.ByteBuffer

class PoseOverlay @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var pose: Pose? = null
    private var imageWidth = 480
    private var imageHeight = 640
    private var rawMask: Bitmap? = null

    private val clothingBitmap: Bitmap? = try {
        BitmapFactory.decodeResource(resources, R.drawable.shirt)
    } catch (e: Exception) {
        Log.e("PoseOverlay", "FAILED TO LOAD shirt.png", e)
        null
    }

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    fun updatePose(pose: Pose?, imgWidth: Int, imgHeight: Int) {
        this.pose = pose
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        invalidate()
    }

    fun updateMask(maskBuffer: ByteBuffer, width: Int, height: Int) {
    val floatBuffer = maskBuffer.asFloatBuffer()
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val confidence = floatBuffer.get(i)
        val alpha = if (confidence > 0.9f) 0 else 255 // changed to 0.9f
        pixels[i] = (alpha shl 24) or 0x00FFFFFF
    }
    rawMask = Bitmap.createBitmap(pixels, height, width, Bitmap.Config.ARGB_8888)
    invalidate()
   }

    fun clear() {
        pose = null
        rawMask = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    pose?.let { drawClothing(canvas, it) }
    pose?.let { drawDebugPose(canvas, it) }

    val debugPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 50f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK) // Added missing dy = 2f
    }
    canvas.drawText("Mask: ${if (rawMask == null) "NULL" else "OK ${rawMask?.width}x${rawMask?.height}"}", 20f, 80f, debugPaint)
    canvas.drawText("Pose: ${if (pose == null) "NULL" else "OK"}", 20f, 140f, debugPaint)
}

    private fun translateX(x: Float): Float {
        val scaleX = width.toFloat() / imageHeight.toFloat()
        return width - x * scaleX
    }

    private fun translateY(y: Float): Float {
        val scaleY = height.toFloat() / imageWidth.toFloat()
        return y * scaleY
    }

    private fun drawClothing(canvas: Canvas, pose: Pose) {
        
        if (clothingBitmap == null) {
           Log.e("PoseOverlay", "clothingBitmap is NULL")
           return
        }

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?: return
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?: return

        val lsx = translateX(leftShoulder.position.x)
        val lsy = translateY(leftShoulder.position.y)
        val rsx = translateX(rightShoulder.position.x)
        val rsy = translateY(rightShoulder.position.y)

        val centerX = (lsx + rsx) / 2f
        val shoulderY = (lsy + rsy) / 2f
        val shoulderWidth = kotlin.math.abs(lsx - rsx)

        val shirtW = (shoulderWidth * 1.8f).toInt().coerceAtLeast(1)
        val shirtH = (shoulderWidth * 2.2f).toInt().coerceAtLeast(1)

        val shirtLeft = (centerX - shirtW / 2f).toInt()
        val shirtTop = (shoulderY - shirtH * 0.12f).toInt()

        val shirtRect = RectF(
            shirtLeft.toFloat(),
            shirtTop.toFloat(),
            (shirtLeft + shirtW).toFloat(),
            (shirtTop + shirtH).toFloat()
        )
        Log.d("PoseOverlay", "Drawing shirt. Size: ${shirtW}x${shirtH} at $shirtRect")
        if (shirtW <= 0 || shirtH <= 0) return

        val shirtBitmap = Bitmap.createBitmap(shirtW, shirtH, Bitmap.Config.ARGB_8888)
        val shirtCanvas = Canvas(shirtBitmap)
        val shirtMatrix = Matrix()
        val src = RectF(0f, 0f, clothingBitmap.width.toFloat(), clothingBitmap.height.toFloat())
        val dst = RectF(0f, 0f, shirtW.toFloat(), shirtH.toFloat())
        shirtMatrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL)
        shirtCanvas.drawBitmap(clothingBitmap, shirtMatrix, null)

        // MASK WITH MIRROR FIX
        
        rawMask?.let { mask ->
            if (this.width == 0 || this.height == 0) return@let

            val scaledMask = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(scaledMask)
            val scaleMatrix = Matrix()
            val maskSrc = RectF(0f, 0f, mask.width.toFloat(), mask.height.toFloat())
            val maskDst = RectF(0f, 0f, this.width.toFloat(), this.height.toFloat())

            scaleMatrix.setRectToRect(maskSrc, maskDst, Matrix.ScaleToFit.FILL)
            scaleMatrix.postScale(-1f, 1f, this.width / 2f, this.height / 2f) // Mirror for front cam
            maskCanvas.drawBitmap(mask, scaleMatrix, null)

            val maskLeft = shirtRect.left.toInt()
            val maskTop = shirtRect.top.toInt()

            if (maskLeft + shirtW > 0 && maskTop + shirtH > 0 &&
                maskLeft < scaledMask.width && maskTop < scaledMask.height) {

                val cropLeft = maskLeft.coerceAtLeast(0)
                val cropTop = maskTop.coerceAtLeast(0)
                val cropRight = (maskLeft + shirtW).coerceAtMost(scaledMask.width)
                val cropBottom = (maskTop + shirtH).coerceAtMost(scaledMask.height)

                val cropW = cropRight - cropLeft
                val cropH = cropBottom - cropTop

                if (cropW > 0 && cropH > 0) {
                    val croppedMask = Bitmap.createBitmap(scaledMask, cropLeft, cropTop, cropW, cropH)
                    val offsetX = (cropLeft - maskLeft).toFloat()
                    val offsetY = (cropTop - maskTop).toFloat()

                    val maskPaint = Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    shirtCanvas.drawBitmap(croppedMask, offsetX, offsetY, maskPaint)
                    croppedMask.recycle()
                }
            }
            scaledMask.recycle()
        }
        
        canvas.drawBitmap(shirtBitmap, shirtRect.left, shirtRect.top, null)
        shirtBitmap.recycle()
    }

    private fun drawDebugPose(canvas: Canvas, pose: Pose) {
        for (landmark in pose.allPoseLandmarks) {
            val x = translateX(landmark.position.x)
            val y = translateY(landmark.position.y)
            canvas.drawCircle(x, y, 6f, pointPaint)
        }
    }
} // This closes the class
