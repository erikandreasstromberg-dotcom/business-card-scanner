package com.houseofsilver.cardscanner.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity)

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("SELECT * FROM scans WHERE id = :id")
    fun getScanById(id: String): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getScanByIdOnce(id: String): ScanEntity?

    @Query("SELECT * FROM scans ORDER BY scannedAt DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteScanById(id: String)
}
