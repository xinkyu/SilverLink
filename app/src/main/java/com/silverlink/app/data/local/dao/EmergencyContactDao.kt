package com.silverlink.app.data.local.dao

import androidx.room.*
import com.silverlink.app.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * 紧急联系人数据访问对象
 */
@Dao
interface EmergencyContactDao {
    
    /** 获取所有紧急联系人 */
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, createdAt ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>
    
    /** 获取所有紧急联系人（同步版本，用于服务） */
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, createdAt ASC")
    suspend fun getAllContactsSync(): List<EmergencyContactEntity>
    
    /** 获取首要联系人 */
    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(): EmergencyContactEntity?
    
    /** 根据ID获取联系人 */
    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun getContactById(id: Int): EmergencyContactEntity?
    
    /** 添加联系人 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long
    
    /** 更新联系人 */
    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)
    
    /** 删除联系人 */
    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)
    
    /** 根据ID删除联系人 */
    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Int)
    
    /** 设置某个联系人为首要联系人（先清除其他首要标记） */
    @Transaction
    suspend fun setPrimaryContact(contactId: Int) {
        clearAllPrimary()
        markAsPrimary(contactId)
    }
    
    @Query("UPDATE emergency_contacts SET isPrimary = 0")
    suspend fun clearAllPrimary()
    
    @Query("UPDATE emergency_contacts SET isPrimary = 1 WHERE id = :id")
    suspend fun markAsPrimary(id: Int)
    
    /** 获取联系人数量 */
    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int
}
