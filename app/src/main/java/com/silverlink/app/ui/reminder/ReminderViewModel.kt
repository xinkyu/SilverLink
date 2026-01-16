package com.silverlink.app.ui.reminder

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.entity.Medication
import com.silverlink.app.feature.reminder.AlarmScheduler
import com.silverlink.app.feature.reminder.MedicationRecognitionService
import com.silverlink.app.feature.reminder.RecognizedMedication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 识别状态
 */
sealed class RecognitionState {
    object Idle : RecognitionState()
    object Loading : RecognitionState()
    data class Success(val medication: RecognizedMedication) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dao = SilverLinkApp.database.medicationDao()
    private val alarmScheduler = AlarmScheduler(application)
    private val recognitionService = MedicationRecognitionService()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllMedications().collect { list ->
                _medications.value = list
            }
        }
    }

    fun addMedication(name: String, dosage: String, times: List<String>) {
        viewModelScope.launch {
            val timesStr = times.joinToString(",")
            val med = Medication(
                name = name,
                dosage = dosage,
                times = timesStr,
                isTakenToday = false
            )
            val id = dao.insertMedication(med)
            val savedMed = med.copy(id = id.toInt())
            alarmScheduler.scheduleAll(savedMed)
        }
    }

    fun updateMedication(medication: Medication, name: String, dosage: String, times: List<String>) {
        viewModelScope.launch {
            // 先取消旧的闹钟
            alarmScheduler.cancelAll(medication)
            
            // 更新药品信息
            val timesStr = times.joinToString(",")
            val updated = medication.copy(
                name = name,
                dosage = dosage,
                times = timesStr
            )
            dao.updateMedication(updated)
            
            // 设置新的闹钟
            alarmScheduler.scheduleAll(updated)
        }
    }

    fun toggleTaken(medication: Medication) {
        viewModelScope.launch {
            val updated = medication.copy(isTakenToday = !medication.isTakenToday)
            dao.updateMedication(updated)
        }
    }
    
    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            alarmScheduler.cancelAll(medication)
            dao.deleteMedication(medication)
        }
    }

    /**
     * 使用 AI 识别药品图片
     */
    fun recognizeMedication(bitmap: Bitmap) {
        viewModelScope.launch {
            _recognitionState.value = RecognitionState.Loading
            
            val result = recognitionService.recognizeMedication(bitmap)
            
            result.fold(
                onSuccess = { recognized ->
                    _recognitionState.value = RecognitionState.Success(recognized)
                },
                onFailure = { error ->
                    _recognitionState.value = RecognitionState.Error(
                        error.message ?: "识别失败，请重试"
                    )
                }
            )
        }
    }

    /**
     * 重置识别状态
     */
    fun resetRecognitionState() {
        _recognitionState.value = RecognitionState.Idle
    }

    /**
     * 保存识别到的药品
     */
    fun saveRecognizedMedication(name: String, dosage: String, times: List<String>) {
        addMedication(name, dosage, times)
        resetRecognitionState()
    }
}
