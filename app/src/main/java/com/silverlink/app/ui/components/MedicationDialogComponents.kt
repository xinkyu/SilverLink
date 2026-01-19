package com.silverlink.app.ui.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 通用的添加/编辑药品对话框
 * 可用于老人端和家人端
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationFormDialog(
    title: String,
    subtitle: String? = null,
    initialName: String = "",
    initialDosage: String = "",
    initialTimes: List<String> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    confirmButtonText: String = "保存",
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, times: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var dosage by remember { mutableStateOf(initialDosage) }
    val times = remember { mutableStateListOf<String>().also { it.addAll(initialTimes) } }
    
    var showTimePicker by remember { mutableStateOf(false) }
    var pickerHour by remember { mutableStateOf(8) }
    var pickerMinute by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { 
            Text(title, style = MaterialTheme.typography.headlineSmall) 
        },
        text = {
            Column {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("药品名称") },
                    placeholder = { Text("如：阿司匹林") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isLoading,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("剂量") },
                    placeholder = { Text("如：每次1片") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isLoading,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 时间列表
                Text(
                    text = "服药时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (times.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        times.forEachIndexed { index, time ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(time, fontSize = 16.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { if (!isLoading) times.removeAt(index) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除时间",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // 添加时间按钮
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加时间", fontSize = 18.sp)
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && dosage.isNotBlank() && times.isNotEmpty()) {
                        onConfirm(name.trim(), dosage.trim(), times.toList())
                    }
                },
                enabled = !isLoading && name.isNotBlank() && dosage.isNotBlank() && times.isNotEmpty(),
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(confirmButtonText, fontSize = 18.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消", fontSize = 18.sp, color = Color.Gray)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = pickerHour,
            initialMinute = pickerMinute,
            onConfirm = { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                if (!times.contains(timeStr)) {
                    times.add(timeStr)
                    times.sortBy { it }
                }
                pickerHour = hour
                pickerMinute = minute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * 时间选择器对话框
 */
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPickerView(
                    value = hour,
                    range = 0..23,
                    onValueChange = { hour = it }
                )
                Text(text = ":", style = MaterialTheme.typography.headlineMedium)
                NumberPickerView(
                    value = minute,
                    range = 0..59,
                    onValueChange = { minute = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text("确定", fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 18.sp)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * 数字滚轮选择器
 */
@Composable
fun NumberPickerView(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                this.value = value
                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
            }
        },
        update = { picker ->
            if (picker.value != value) {
                picker.value = value
            }
        },
        modifier = Modifier
            .width(96.dp)
            .height(120.dp)
    )
}
