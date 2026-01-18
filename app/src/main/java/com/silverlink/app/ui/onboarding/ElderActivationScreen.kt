package com.silverlink.app.ui.onboarding

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.integration.android.IntentIntegrator
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 长辈端激活页面
 * 输入配对码完成激活
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderActivationScreen(
    onBack: () -> Unit,
    onActivationComplete: (elderName: String) -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val syncRepository = remember { SyncRepository.getInstance(context) }
    
    var pairingCode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("配对码不正确，请重新输入") }
    var showSuccess by remember { mutableStateOf(false) }
    var elderName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val activity = context as? Activity
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val contents = scanResult?.contents
        if (!contents.isNullOrBlank()) {
            val parsed = userPreferences.parseQRContent(contents)
            val code = parsed?.code ?: contents.filter { it.isDigit() }.take(6)
            if (code.length == 6) {
                pairingCode = code
                isError = false
                errorMessage = ""
            } else {
                isError = true
                errorMessage = "二维码内容无效，请重试"
            }
        }
    }
    
    if (showSuccess) {
        SuccessScreen(
            elderName = elderName,
            onContinue = { onActivationComplete(elderName) }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmApricot)
        ) {
            // 顶部导航栏
            TopAppBar(
                title = { 
                    Text(
                        text = "输入配对码",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFF5D4037)
                )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                // 说明文字
                Text(
                    text = "请输入家人提供的配对码",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "6位数字",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF8D6E63)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // 配对码输入框 - 巨大的数字输入
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { 
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                pairingCode = it
                                isError = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 16.sp,
                            color = Color(0xFF5D4037)
                        ),
                        placeholder = {
                            Text(
                                text = "______",
                                style = LocalTextStyle.current.copy(
                                    fontSize = 48.sp,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 16.sp
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFBCAAA4)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            errorBorderColor = Color(0xFFD32F2F),
                            cursorColor = Color(0xFFFFB74D)
                        ),
                        isError = isError
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 扫码按钮
                Button(
                    onClick = {
                        if (activity == null) {
                            isError = true
                            errorMessage = "无法启动扫码，请重试"
                            return@Button
                        }
                        val intent = IntentIntegrator(activity)
                            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                            .setPrompt("扫描家人端二维码")
                            .setBeepEnabled(true)
                            .setOrientationLocked(true)
                            .setCaptureActivity(QrScanPortraitActivity::class.java)
                            .createScanIntent()
                        scanLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF5D4037)
                    )
                ) {
                    Text(
                        text = "扫码配对",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 确认按钮
                Button(
                    onClick = {
                        if (pairingCode.length == 6 && !isLoading) {
                            isLoading = true
                            isError = false
                            // 验证配对码（优先云端，回退本地）
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = syncRepository.verifyPairingCode(pairingCode)
                                result.onSuccess { pairingInfo ->
                                    if (pairingInfo != null) {
                                        // 配对成功
                                        elderName = pairingInfo.elderName
                                        syncRepository.completeElderActivation(elderName)
                                        showSuccess = true
                                    } else {
                                        // 配对失败
                                        isError = true
                                        errorMessage = "配对码无效，请确认家人是否已完成配置"
                                    }
                                }.onFailure {
                                    isError = true
                                    errorMessage = "网络错误，请检查网络后重试"
                                }
                                isLoading = false
                            }
                        } else if (pairingCode.length != 6) {
                            isError = true
                            errorMessage = "请输入完整的6位配对码"
                        }
                    },
                    enabled = pairingCode.length == 6 && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB74D),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFBCAAA4)
                    )
                ) {
                    Text(
                        text = if (isLoading) "验证中..." else "确认激活",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 激活成功页面 - 带礼花特效
 */
@Composable
fun SuccessScreen(
    elderName: String,
    onContinue: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFE0B2),
                        Color(0xFFFFCC80),
                        Color(0xFFFFB74D)
                    )
                )
            )
    ) {
        // 礼花特效
        ConfettiEffect()
        
        // 主要内容
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(500)) + 
                    scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 庆祝图标
                Icon(
                    imageVector = Icons.Default.Celebration,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // AI 说话
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎉",
                            fontSize = 48.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "${elderName}，",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "我住进你的手机啦！",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF8D6E63),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "以后有什么需要，随时叫我哦~",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8D6E63),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // 开始使用按钮
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFFFB74D)
                    )
                ) {
                    Text(
                        text = "开始使用",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 礼花特效
 */
@Composable
fun ConfettiEffect() {
    val confettiColors = listOf(
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFE66D),
        Color(0xFF95E1D3),
        Color(0xFFF38181),
        Color(0xFFAA96DA),
        Color(0xFFFCBAD3)
    )
    
    // 生成多个礼花粒子
    repeat(30) { index ->
        val offsetX = remember { Animatable(Random.nextFloat() * 400 - 200) }
        val offsetY = remember { Animatable(-100f) }
        val rotation = remember { Animatable(0f) }
        val alpha = remember { Animatable(1f) }
        val scale = remember { Animatable(Random.nextFloat() * 0.5f + 0.5f) }
        
        LaunchedEffect(Unit) {
            delay(index * 50L)
            // 下落动画
            launch {
                offsetY.animateTo(
                    targetValue = 1000f,
                    animationSpec = tween(durationMillis = 3000 + Random.nextInt(2000))
                )
            }
            // 旋转动画
            launch {
                rotation.animateTo(
                    targetValue = 360f * (Random.nextInt(3) + 1),
                    animationSpec = tween(durationMillis = 3000)
                )
            }
            // 淡出动画
            launch {
                delay(2000)
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 1000)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .offset(
                    x = (offsetX.value + 200).dp,
                    y = offsetY.value.dp
                )
                .rotate(rotation.value)
                .scale(scale.value)
                .alpha(alpha.value)
                .size(if (index % 2 == 0) 12.dp else 16.dp)
                .background(
                    color = confettiColors[index % confettiColors.size],
                    shape = if (index % 3 == 0) CircleShape else RoundedCornerShape(2.dp)
                )
        )
    }
}
