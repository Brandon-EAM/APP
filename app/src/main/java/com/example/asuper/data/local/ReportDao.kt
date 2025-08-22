package com.example.asuper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity): Long

    @Query("SELECT * FROM reportes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ReportEntity>>

    @Query("DELETE FROM reportes WHERE id = :id")
    suspend fun delete(id: Long)
}