package com.example.asuper.repository

import android.content.Context
import com.example.asuper.data.local.AppDatabase
import com.example.asuper.data.local.ReportEntity

class ReportRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.reportDao()

    suspend fun saveReport(entity: ReportEntity): Long = dao.insert(entity)
    fun allReports() = dao.getAll()
    suspend fun delete(id: Long) = dao.delete(id)
}