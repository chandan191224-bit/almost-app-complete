package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "word", "sheet", "slide"
    val content: String,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
