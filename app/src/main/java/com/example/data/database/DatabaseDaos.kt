package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CastHistoryDao {
    @Query("SELECT * FROM cast_history ORDER BY timestamp DESC LIMIT 30")
    fun getAllHistory(): Flow<List<CastHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: CastHistoryItem)

    @Query("DELETE FROM cast_history WHERE id = :id")
    suspend fun deleteHistoryItemById(id: Int)

    @Query("DELETE FROM cast_history")
    suspend fun clearHistory()

    @Query("UPDATE cast_history SET name = :name WHERE id = :id")
    suspend fun updateHistoryItemName(id: Int, name: String)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}
