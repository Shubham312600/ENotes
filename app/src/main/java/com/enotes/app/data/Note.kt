package com.enotes.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
@Entity(tableName = "notes")
data class Note(
 @PrimaryKey val id: String,
 val title: String,
 val body: String,
 val createdAt: Long,
 val updatedAt: Long,
 val isPinned: Boolean = false,
 val isArchived: Boolean = false,
 val deletedAt: Long? = null,
 @ColumnInfo(defaultValue = "'Others'") val category: String = "Others",
 val folder: String? = null,
 val attachmentUri: String? = null
)
