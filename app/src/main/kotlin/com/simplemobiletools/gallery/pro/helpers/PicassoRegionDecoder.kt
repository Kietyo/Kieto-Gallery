package com.simplemobiletools.gallery.pro.helpers

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.simplemobiletools.gallery.pro.enums.TileDpiQualityEnum

class PicassoRegionDecoder(
    private val showHighestQuality: Boolean,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val minTileDpi: Int,
    private val isHeic: Boolean
) : ImageRegionDecoder {
    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock = Any()

    override fun init(context: Context, uri: Uri): Point {
        val newUri = Uri.parse(uri.toString().replace("%", "%25").replace("#", "%23"))
        val inputStream = context.contentResolver.openInputStream(newUri)
        decoder = BitmapRegionDecoder.newInstance(inputStream!!, false)
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        synchronized(decoderLock) {
            var newSampleSize = sampleSize
            if (!showHighestQuality && minTileDpi == TileDpiQualityEnum.LOW.dpi) {
                if ((rect.width() > rect.height() && screenWidth > screenHeight) || (rect.height() > rect.width() && screenHeight > screenWidth)) {
                    if ((rect.width() / sampleSize > screenWidth || rect.height() / sampleSize > screenHeight)) {
                        newSampleSize *= 2
                    }
                }
            }

            val options = BitmapFactory.Options()
            options.inSampleSize = newSampleSize
            options.inPreferredConfig = if (showHighestQuality || isHeic) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            val bitmap = decoder!!.decodeRegion(rect, options)
            return bitmap ?: throw RuntimeException("Region decoder returned null bitmap - image format may not be supported")
        }
    }

    override fun isReady() = decoder != null && !decoder!!.isRecycled

    override fun recycle() {
        decoder!!.recycle()
    }
}
