package com.silverlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.silverlink.app.data.local.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medication WHERE times LIKE '%' || :time || '%'")
    suspend fun getMedicationsByTime(time: String): List<Medication>
    
    @Query("SELECT * FROM medication WHERE name = :name AND dosage = :dosage LIMIT 1")
    suspend fun getMedicationByNameAndDosage(name: String, dosage: String): Medication?

    @Insert
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    // Helper to reset daily intake (will be called at midnight)
    @Query("UPDATE medication SET isTakenToday = 0")
    suspend fun resetDailyIntake()
}
