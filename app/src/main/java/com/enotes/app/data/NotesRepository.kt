package com.enotes.app.data
import kotlinx.coroutines.flow.Flow
import java.util.UUID
class NotesRepository(private val dao: NoteDao) {
 fun observeNotes(): Flow<List<Note>> = dao.observeActiveNotes()
 suspend fun save(title: String, body: String, existing: Note? = null) {
  val now = System.currentTimeMillis()
  dao.upsert(Note(existing?.id ?: UUID.randomUUID().toString(), title.trim().ifBlank { "Untitled note" }, body,
   existing?.createdAt ?: now, now, existing?.isPinned ?: false, existing?.isArchived ?: false, null))
 }
 suspend fun trash(note: Note) { val now = System.currentTimeMillis(); dao.moveToTrash(note.id, now, now) }
 suspend fun togglePin(note: Note) { dao.setPinned(note.id, !note.isPinned, System.currentTimeMillis()) }
}