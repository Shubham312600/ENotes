package com.enotes.app.data
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface NoteDao {
 @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
 fun observeActiveNotes(): Flow<List<Note>>
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun upsert(note: Note)
 @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
 suspend fun moveToTrash(id: String, deletedAt: Long, updatedAt: Long)
 @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
 suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)
}