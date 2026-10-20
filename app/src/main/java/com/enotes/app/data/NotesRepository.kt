package com.enotes.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NotesRepository(private val dao: NoteDao) {
    fun observeNotes(): Flow<List<Note>> = dao.observeActiveNotes()
    fun observeArchived(): Flow<List<Note>> = dao.observeArchivedNotes()
    fun observeTrash(): Flow<List<Note>> = dao.observeTrashedNotes()

    suspend fun importNotes(notes: List<Note>) {
        dao.upsertAll(notes)
    }

    suspend fun save(title: String, body: String, existing: Note? = null) {
        val now = System.currentTimeMillis()
        dao.upsert(
            Note(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "Untitled note" },
                body = body,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                isPinned = existing?.isPinned ?: false,
                isArchived = existing?.isArchived ?: false,
                deletedAt = null
            )
        )
    }

    suspend fun trash(note: Note) {
        val now = System.currentTimeMillis()
        dao.moveToTrash(note.id, now, now)
    }

    suspend fun restore(note: Note) {
        dao.restoreFromTrash(note.id, System.currentTimeMillis())
    }

    suspend fun permanentlyDelete(note: Note) {
        dao.deletePermanently(note.id)
    }

    suspend fun archive(note: Note) {
        dao.setArchived(note.id, true, System.currentTimeMillis())
    }

    suspend fun unarchive(note: Note) {
        dao.setArchived(note.id, false, System.currentTimeMillis())
    }

    suspend fun togglePin(note: Note) {
        dao.setPinned(note.id, !note.isPinned, System.currentTimeMillis())
    }
}