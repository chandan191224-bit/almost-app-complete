package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocs(): Flow<List<DocEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocById(id: Int): DocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: DocEntity): Long

    @Update
    suspend fun updateDoc(doc: DocEntity)

    @Delete
    suspend fun deleteDoc(doc: DocEntity)
}
