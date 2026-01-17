package com.silverlink.app.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 家人端配置流程
 * 1. 输入老人称呼
 * 2. 生成配对码和二维码
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySetupScreen(
    onBack: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val syncRepository = remember { SyncRepository.getInstance(context) }
    
    var currentStep by remember { mutableStateOf(1) }
    var elderName by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var qrContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmApricot)
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { 
                Text(
                    text = if (currentStep == 1) "配置智能伴侣" else "分享给长辈",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (currentStep > 1) {
                        currentStep--
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color(0xFF5D4037)
            )
        )
        
        when (currentStep) {
            1 -> ElderInfoStep(
                elderName = elderName,
                onNameChange = { elderName = it },
                onNext = {
                    if (elderName.isNotBlank() && !isLoading) {
                        isLoading = true
                        // 创建配对码并同步到云端
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = syncRepository.createPairingCodeOnCloud(elderName)
                            result.onSuccess { code ->
                                pairingCode = code
                                // 生成二维码内容（包含配对码和长辈称呼）
                                qrContent = userPreferences.generateQRContent(code, elderName)
                                currentStep = 2
                            }
                            isLoading = false
                        }
                    }
                }
            )
            2 -> PairingCodeStep(
                elderName = elderName,
                pairingCode = pairingCode,
                qrContent = qrContent,
                onComplete = {
                    onSetupComplete()
                }
            )
        }
    }
}

/**
 * 步骤1: 输入长辈信息
 */
@Composable
fun ElderInfoStep(
    elderName: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // 图标
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "请输入长辈的称呼",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "这将用于AI伴侣称呼长辈",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 输入框
        OutlinedTextField(
            value = elderName,
            onValueChange = onNameChange,
            label = { Text("长辈称呼", fontSize = 18.sp) },
            placeholder = { Text("例如：王爷爷、李奶奶", fontSize = 18.sp) },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFB74D),
                unfocusedBorderColor = Color(0xFFBCAAA4),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            )
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 下一步按钮
        Button(
            onClick = onNext,
            enabled = elderName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB74D),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBCAAA4)
            )
        ) {
            Text(
                text = "下一步",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 步骤2: 显示配对码
 */
@Composable
fun PairingCodeStep(
    elderName: String,
    pairingCode: String,
    qrContent: String,
    onComplete: () -> Unit
) {
    // 生成二维码（使用包含长辈信息的内容）
    val qrCodeBitmap = remember(qrContent) {
        generateQRCode(qrContent)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "配置成功！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF388E3C)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请让${elderName}扫描二维码或输入配对码",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF5D4037),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 二维码卡片
        Card(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                qrCodeBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "配对二维码",
                        modifier = Modifier.size(180.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "或输入配对码",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 配对码显示
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = pairingCode,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037),
                letterSpacing = 8.sp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 完成按钮
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB74D),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "完成配置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 生成二维码
 */
fun generateQRCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK 
                    else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
