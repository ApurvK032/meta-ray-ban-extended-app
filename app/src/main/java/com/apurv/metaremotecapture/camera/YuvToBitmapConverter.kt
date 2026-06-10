package com.apurv.metaremotecapture.camera

import android.graphics.Bitmap
import java.nio.ByteBuffer

internal object YuvToBitmapConverter {
  private val lock = Any()
  private var pixels = IntArray(0)
  private var yuvBytes = ByteArray(0)
  private var cachedBitmap: Bitmap? = null
  private var lastWidth = 0
  private var lastHeight = 0

  fun convert(i420Buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
      return null
    }

    val frameSize = width * height
    val expectedSize = frameSize + (frameSize / 2)
    if (i420Buffer.remaining() < expectedSize) {
      return null
    }

    synchronized(lock) {
      if (pixels.size < frameSize) {
        pixels = IntArray(frameSize)
      }
      if (yuvBytes.size < expectedSize) {
        yuvBytes = ByteArray(expectedSize)
      }

      val bitmap =
          cachedBitmap?.takeIf {
            !it.isRecycled && lastWidth == width && lastHeight == height
          }
              ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                cachedBitmap?.recycle()
                cachedBitmap = it
                lastWidth = width
                lastHeight = height
              }

      val originalPosition = i420Buffer.position()
      i420Buffer.get(yuvBytes, 0, expectedSize)
      i420Buffer.position(originalPosition)

      convertI420ToArgb(yuvBytes, pixels, width, height)
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
      return bitmap
    }
  }

  private fun convertI420ToArgb(yuv: ByteArray, argb: IntArray, width: Int, height: Int) {
    val frameSize = width * height
    val uOffset = frameSize
    val vOffset = frameSize + (frameSize / 4)
    val halfWidth = width / 2

    var pixelIndex = 0
    for (row in 0 until height) {
      val uvRowOffset = (row / 2) * halfWidth
      for (col in 0 until width) {
        val uvIndex = uvRowOffset + (col / 2)
        val y = (yuv[pixelIndex].toInt() and 0xFF) - 16
        val u = (yuv[uOffset + uvIndex].toInt() and 0xFF) - 128
        val v = (yuv[vOffset + uvIndex].toInt() and 0xFF) - 128

        val yScaled = 1192 * y
        val r = (yScaled + 1836 * v).coerceIn(0, 262143)
        val g = (yScaled - 218 * u - 546 * v).coerceIn(0, 262143)
        val b = (yScaled + 2163 * u).coerceIn(0, 262143)

        argb[pixelIndex] =
            0xFF000000.toInt() or ((r shl 6) and 0x00FF0000) or
                ((g shr 2) and 0x0000FF00) or
                ((b shr 10) and 0x000000FF)
        pixelIndex++
      }
    }
  }
}
