package com.example.asuper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reportes")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supervisor: String,
    val fecha: String,
    val titulo: String,
    val pdfPath: String,
    val createdAt: Long = System.currentTimeMillis()
)