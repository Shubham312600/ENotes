package com.enotes.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "notes")
data class Note(
 @PrimaryKey val id: String,
 val title: String,
 val body: String,
 val createdAt: Long,
 val updatedAt: Long,
 val isPinned: Boolean = false,
 val isArchived: Boolean = false,
 val deletedAt: Long? = null
)