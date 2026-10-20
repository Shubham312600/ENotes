package com.enotes.app.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities = [Note::class, NoteFolder::class], version = 3, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
 abstract fun noteDao(): NoteDao
 companion object {
  @Volatile private var instance: NotesDatabase? = null
  private val MIGRATION_1_2 = object : Migration(1, 2) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT 'Others'")
    db.execSQL("ALTER TABLE notes ADD COLUMN folder TEXT")
    db.execSQL("ALTER TABLE notes ADD COLUMN attachmentUri TEXT")
    db.execSQL("CREATE TABLE IF NOT EXISTS note_folders (name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(name))")
   }
  }
  private val MIGRATION_2_3 = object : Migration(2, 3) {
   override fun migrate(db: SupportSQLiteDatabase) {
    // Repair only the exact placeholder created by the earlier document-import flow.
    // The URI and all note metadata remain in place; the generic body is removed.
    db.execSQL("UPDATE notes SET title = 'Imported document', body = '' WHERE attachmentUri IS NOT NULL AND title <> '' AND title NOT GLOB '*[^0-9]*' AND body = 'Document attached. Open this note to review its attachment.'")
   }
  }
  fun get(context: Context): NotesDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, NotesDatabase::class.java, "enotes.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
  }
 }
}

