package com.enotes.app.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities = [Note::class, NoteFolder::class], version = 2, exportSchema = false)
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
  fun get(context: Context): NotesDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, NotesDatabase::class.java, "enotes.db")
    .addMigrations(MIGRATION_1_2).build().also { instance = it }
  }
 }
}
