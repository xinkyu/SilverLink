package com.silverlink.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.wear.WatchApp

data class WatchMedicationItem(
    val id: Int,
    val name: String,
    val dosage: String,
    val time: String,
    val isTaken: Boolean = false
)

@Composable
fun MedicationScreen(onBack: () -> Unit) {
    val prefs = remember { WatchApp.instance.watchPreferences }
    val storedMedications = prefs.getMedications()

    val medications = remember {
        val items = storedMedications.flatMap { med ->
            med.times.map { time ->
                WatchMedicationItem(
                    id = med.id,
                    name = med.name,
                    dosage = med.dosage,
                    time = time,
                    isTaken = med.isTakenToday
                )
            }
        }
        mutableStateListOf(*items.toTypedArray())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "今日用药",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (medications.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "暂无用药记录",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(medications.size) { index ->
                        val med = medications[index]
                        MedicationCard(
                            medication = med,
                            onConfirm = {
                                medications[index] = med.copy(isTaken = true)
                            }
                        )
                    }
                }
            }

            TextButton(onClick = onBack) {
                Text("返回", color = Color(0xFFF49007), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MedicationCard(
    medication: WatchMedicationItem,
    onConfirm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (medication.isTaken) Color(0xFF1B5E20) else Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Medication,
                contentDescription = null,
                tint = if (medication.isTaken) Color(0xFF4CAF50) else Color(0xFF2196F3),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${medication.name} ${medication.dosage}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = medication.time,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            if (!medication.isTaken) {
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "确认服药",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
