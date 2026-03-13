package com.silverlink.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.wear.ui.theme.WatchTheme

class MedicationAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val medName = intent.getStringExtra("med_name") ?: "药物"
        val medDosage = intent.getStringExtra("med_dosage") ?: ""

        setContent {
            WatchTheme {
                MedicationAlertScreen(
                    name = medName,
                    dosage = medDosage,
                    onConfirm = {
                        // TODO: send confirmation via NearbyBridge
                        finish()
                    },
                    onSnooze = {
                        // TODO: schedule snooze alarm
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun MedicationAlertScreen(
    name: String,
    dosage: String,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Medication,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "该吃药了",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$name $dosage",
                fontSize = 14.sp,
                color = Color(0xFFF49007),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text("已服药", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text("稍后提醒", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
