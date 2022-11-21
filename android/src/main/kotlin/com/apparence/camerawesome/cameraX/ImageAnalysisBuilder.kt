package com.apparence.camerawesome.cameraX

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.utils.ImageUtil
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.EventChannel
import java.io.Serializable
import java.util.concurrent.Executor

enum class OutputImageFormat {
    JPEG,
    YUV_420_888,
    NV21,
}

class ImageAnalysisBuilder private constructor(
    private val format: OutputImageFormat,
    private val width: Int,
    private val height: Int,
    private val executor: Executor,
    private val previewStreamSink: EventChannel.EventSink,
){

    companion object {

        fun defaultConfig(
            aspectRatio: Int,
            format: OutputImageFormat,
            executor: Executor,
            previewStreamSink: EventChannel.EventSink,
        ): ImageAnalysisBuilder {
            val width = 1024
            val analysisAspectRatio = when (aspectRatio) {
                AspectRatio.RATIO_4_3 -> 4f/3
                else -> 16f/9
            }
            val height =  width * (1/analysisAspectRatio)
            return ImageAnalysisBuilder(
                format,
                width,
                height.toInt(),
                executor,
                previewStreamSink
            )
        }
    }

    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
    fun build(): ImageAnalysis {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            when (format) {
                OutputImageFormat.JPEG -> {
                    val jpegImage = ImageUtil.yuvImageToJpegByteArray(
                       imageProxy,
                       Rect(0, 0, imageProxy.width, imageProxy.height),
             80)
                    val imageMap = imageProxyBaseAdapter(imageProxy)
                    imageMap["jpegImage"] = jpegImage
                    previewStreamSink.success(imageMap)
                }
                OutputImageFormat.YUV_420_888 -> {
                    val planes = imagePlanesAdapter(imageProxy)
                    val imageMap = imageProxyBaseAdapter(imageProxy)
                    imageMap["planes"] = planes
                    previewStreamSink.success(imageMap)
                }
                OutputImageFormat.NV21 -> {
                    val nv21Image = ImageUtil.yuv_420_888toNv21(imageProxy)
                    val planes = imagePlanesAdapter(imageProxy)
                    val imageMap = imageProxyBaseAdapter(imageProxy)
                    imageMap["nv21Image"] = nv21Image
                    imageMap["planes"] = planes
                    previewStreamSink.success(imageMap)
                }
            }
            imageProxy.close()
        }
        return imageAnalysis
    }

    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
    private fun imageProxyBaseAdapter(imageProxy: ImageProxy): MutableMap<String, Any> {
        return mutableMapOf(
            "height" to imageProxy.image!!.height,
            "width" to imageProxy.image!!.width,
            "format" to format.name.lowercase(),
            "rotation" to imageProxy.imageInfo.rotationDegrees,
        )
    }

    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
    private fun imagePlanesAdapter(imageProxy: ImageProxy): List<Map<String, Any>> {
        return imageProxy.image!!.planes.map {
            val byteArray = ByteArray(it.buffer.remaining())
            it.buffer.get(byteArray, 0, byteArray.size)
            mapOf(
                "bytes" to byteArray,
                "rowStride" to it.rowStride,
                "pixelStride" to it.pixelStride
            )
        }
    }

}