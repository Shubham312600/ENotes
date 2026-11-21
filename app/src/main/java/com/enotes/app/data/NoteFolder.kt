package com.enotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_folders")
data class NoteFolder(@PrimaryKey val name: String, val createdAt: Long)
