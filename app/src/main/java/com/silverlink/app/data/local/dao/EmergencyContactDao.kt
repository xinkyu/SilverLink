package com.silverlink.app.data.local.dao

import androidx.room.*
import com.silverlink.app.data.local.entity.EmergencyContact
import kotlinx.coroutines.flow.Flow

/**
 * 紧急联系人数据访问对象
 */
@Dao
interface EmergencyContactDao {
    
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, createdAt ASC")
    fun getAllContacts(): Flow<List<EmergencyContact>>
    
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, createdAt ASC")
    suspend fun getAllContactsList(): List<EmergencyContact>
    
    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(): EmergencyContact?
    
    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun getContactById(id: Int): EmergencyContact?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact): Long
    
    @Update
    suspend fun updateContact(contact: EmergencyContact)
    
    @Delete
    suspend fun deleteContact(contact: EmergencyContact)
    
    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Int)
    
    @Query("UPDATE emergency_contacts SET isPrimary = 0")
    suspend fun clearPrimaryStatus()
    
    @Transaction
    suspend fun setPrimaryContact(contactId: Int) {
        clearPrimaryStatus()
        val contact = getContactById(contactId)
        if (contact != null) {
            updateContact(contact.copy(isPrimary = true))
        }
    }
    
    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int
}
