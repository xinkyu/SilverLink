package com.silverlink.app.feature.memory

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 图片处理工具类
 * 从 MedicationRecognitionService 提取的通用方法
 */
object ImageUtils {
    
    private const val DEFAULT_MAX_SIZE = 1024
    private const val DEFAULT_QUALITY = 85
    private const val THUMBNAIL_SIZE = 200
    private const val MIN_QUALITY = 40
    
    /**
     * 将 Bitmap 转换为 Base64 字符串
     * @param bitmap 原始图片
     * @param quality 压缩质量 (0-100)
     * @return Base64 编码字符串
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = DEFAULT_QUALITY): String {
        val outputStream = ByteArrayOutputStream()
        val scaledBitmap = compressBitmap(bitmap, DEFAULT_MAX_SIZE)
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 将 Bitmap 转换为 Base64（控制最大字节大小）
     * 注意：CloudBase 云函数 HTTP 触发器有请求体大小限制（约 1MB），
     * Base64 编码会使数据膨胀约 33%，所以 maxBytes 应设置得保守一些。
     * @param bitmap 原始图片
     * @param maxBytes 目标最大字节（不含 Base64 膨胀）
     * @param initialMaxSize 初始最大边长
     * @return Base64 字符串，无法满足限制则返回 null
     */
    fun bitmapToBase64WithLimit(
        bitmap: Bitmap,
        maxBytes: Int = 100_000,  // 100KB，Base64 后约 133KB，确保请求体不超限
        initialMaxSize: Int = 720
    ): String? {
        var maxSize = initialMaxSize
        var quality = DEFAULT_QUALITY

        repeat(12) {  // 增加重试次数以应对超大图片
            val outputStream = ByteArrayOutputStream()
            val scaled = compressBitmap(bitmap, maxSize)
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()

            if (bytes.size <= maxBytes) {
                android.util.Log.d("ImageUtils", "压缩成功: ${bytes.size / 1024}KB, 尺寸=${scaled.width}x${scaled.height}, 质量=$quality")
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            // 更激进的压缩策略
            if (quality > MIN_QUALITY) {
                quality = (quality - 15).coerceAtLeast(MIN_QUALITY)
            } else {
                maxSize = (maxSize * 0.8f).toInt().coerceAtLeast(320)  // 允许压缩到更小尺寸
                quality = 70  // 重置质量但不要太高
            }
        }

        android.util.Log.w("ImageUtils", "无法将图片压缩到 ${maxBytes / 1024}KB 以下")
        return null
    }
    
    /**
     * 将 Bitmap 转换为带前缀的 Base64 数据 URL
     * @param bitmap 原始图片
     * @return 格式: "data:image/jpeg;base64,xxxx"
     */
    fun bitmapToDataUrl(bitmap: Bitmap): String {
        return "data:image/jpeg;base64,${bitmapToBase64(bitmap)}"
    }
    
    /**
     * 压缩图片到指定最大尺寸
     * @param bitmap 原始图片
     * @param maxSize 最大边长（像素）
     * @return 压缩后的图片
     */
    fun compressBitmap(bitmap: Bitmap, maxSize: Int = DEFAULT_MAX_SIZE): Bitmap {
        return if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
    }
    
    /**
     * 生成缩略图
     * @param bitmap 原始图片
     * @param size 缩略图尺寸
     * @return 缩略图
     */
    fun generateThumbnail(bitmap: Bitmap, size: Int = THUMBNAIL_SIZE): Bitmap {
        val scale = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
    
    /**
     * 计算图片的字节大小
     */
    fun estimateByteSize(bitmap: Bitmap, quality: Int = DEFAULT_QUALITY): Int {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.size()
    }
    
    /**
     * 将 Bitmap 转换为 JPEG 字节数组（用于直传 COS）
     * 
     * 智能压缩策略：
     * - 小于 500KB 的图片：不压缩，保持原质量
     * - 500KB-2MB：轻微压缩，保持高质量
     * - 大于 2MB：适度压缩，平衡质量和大小
     * 
     * @param bitmap 原始图片
     * @param targetMaxBytes 目标最大字节数（默认 2MB，COS 上传没有严格限制）
     * @return JPEG 格式的字节数组
     */
    fun bitmapToJpegBytes(
        bitmap: Bitmap, 
        quality: Int = 92, 
        maxSize: Int = 2560,  // 最大边长，4K 以下
        targetMaxBytes: Int = 2 * 1024 * 1024  // 目标最大 2MB
    ): ByteArray {
        // 先估算原始大小
        val originalBytes = estimateByteSize(bitmap, 95)
        
        // 小图片（< 500KB）：不压缩
        if (originalBytes < 500 * 1024 && bitmap.width <= maxSize && bitmap.height <= maxSize) {
            android.util.Log.d("ImageUtils", "小图片无需压缩: ${originalBytes / 1024}KB")
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            return outputStream.toByteArray()
        }
        
        // 中等图片（500KB - 2MB）：轻微压缩
        if (originalBytes < targetMaxBytes) {
            val scaledBitmap = compressBitmap(bitmap, maxSize)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            android.util.Log.d("ImageUtils", "中等图片轻度压缩: ${originalBytes / 1024}KB -> ${outputStream.size() / 1024}KB")
            return outputStream.toByteArray()
        }
        
        // 大图片（> 2MB）：渐进式压缩
        var currentMaxSize = maxSize
        var currentQuality = quality
        
        repeat(5) {
            val scaledBitmap = compressBitmap(bitmap, currentMaxSize)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            val resultBytes = outputStream.toByteArray()
            
            if (resultBytes.size <= targetMaxBytes) {
                android.util.Log.d("ImageUtils", "大图片压缩完成: ${originalBytes / 1024}KB -> ${resultBytes.size / 1024}KB, 尺寸=${scaledBitmap.width}x${scaledBitmap.height}, 质量=$currentQuality")
                return resultBytes
            }
            
            // 继续压缩
            if (currentQuality > 75) {
                currentQuality -= 8
            } else {
                currentMaxSize = (currentMaxSize * 0.85f).toInt().coerceAtLeast(1280)
            }
        }
        
        // 最终兜底
        val scaledBitmap = compressBitmap(bitmap, currentMaxSize)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality.coerceAtLeast(70), outputStream)
        android.util.Log.d("ImageUtils", "大图片最终压缩: ${originalBytes / 1024}KB -> ${outputStream.size() / 1024}KB")
        return outputStream.toByteArray()
    }
}
