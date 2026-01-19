package com.silverlink.app.ui.reminder

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.silverlink.app.feature.reminder.MotionDetector
import com.silverlink.app.ui.theme.GradientEnd
import com.silverlink.app.ui.theme.GradientStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "PillCheckCamera"

/**
 * 实时相机找药界面
 * 自动检测画面稳定后进行药品识别
 */
@Composable
fun PillCheckCameraScreen(
    onCapture: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // 状态
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("对准药瓶，稳住相机...") }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }
    
    // 运动检测器
    val motionDetector = remember { MotionDetector() }
    
    // 分析执行器
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }
    
    // 初始化相机
    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览
        cameraProvider?.let { provider ->
            CameraPreviewWithAnalysis(
                cameraProvider = provider,
                lifecycleOwner = lifecycleOwner,
                analysisExecutor = analysisExecutor,
                onFrameAnalyzed = { bitmap ->
                    if (!isAnalyzing) {
                        val shouldCapture = motionDetector.analyzeFrame(bitmap)
                        stabilityProgress = motionDetector.getStabilityProgress()
                        
                        if (shouldCapture) {
                            isAnalyzing = true
                            statusText = "正在识别..."
                            scope.launch {
                                onCapture(bitmap)
                            }
                        }
                    }
                }
            )
        }
        
        // 顶部关闭按钮
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 底部状态区域
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 稳定度进度条
            if (!isAnalyzing && stabilityProgress > 0) {
                LinearProgressIndicator(
                    progress = { stabilityProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = GradientEnd,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 状态文本
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            // 识别中动画
            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = GradientEnd,
                    strokeWidth = 3.dp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isAnalyzing) "请稍候..." else "保持药瓶在画面中央",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
        
        // 中央对准框
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            // 简单的对准框
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.Transparent)
            ) {
                // 四个角的标记
                CornerMarker(Modifier.align(Alignment.TopStart))
                CornerMarker(Modifier.align(Alignment.TopEnd).then(Modifier.scaleX(-1f)))
                CornerMarker(Modifier.align(Alignment.BottomStart).then(Modifier.scaleY(-1f)))
                CornerMarker(Modifier.align(Alignment.BottomEnd).then(Modifier.scaleX(-1f).scaleY(-1f)))
            }
        }
    }
}

@Composable
private fun CornerMarker(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // 水平线
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(GradientEnd)
        )
        // 垂直线
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(GradientEnd)
        )
    }
}

private fun Modifier.scaleX(scale: Float) = this.graphicsLayer(scaleX = scale)

private fun Modifier.scaleY(scale: Float) = this.graphicsLayer(scaleY = scale)

@Composable
private fun CameraPreviewWithAnalysis(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    analysisExecutor: java.util.concurrent.ExecutorService,
    onFrameAnalyzed: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            try {
                cameraProvider.unbindAll()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val bitmap = imageProxy.toBitmap()
                            if (bitmap != null) {
                                onFrameAnalyzed(bitmap)
                            }
                            imageProxy.close()
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }
    )
}

/**
 * 将 ImageProxy 转换为 Bitmap
 */
private fun ImageProxy.toBitmap(): Bitmap? {
    return try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val yuvImage = android.graphics.YuvImage(
            bytes,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
        val jpegBytes = out.toByteArray()
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        
        // 旋转图像以匹配预览方向
        val rotationDegrees = imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
        null
    }
}
