package com.enotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enotes.app.data.Note
import com.enotes.app.data.NotesDatabase
import com.enotes.app.data.NotesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
 private val vm: NotesViewModel by lazy {
  val repo = NotesRepository(NotesDatabase.get(applicationContext).noteDao())
  ViewModelProvider(this, object : ViewModelProvider.Factory {
   @Suppress("UNCHECKED_CAST")
   override fun <T : ViewModel> create(c: Class<T>): T = NotesViewModel(repo) as T
  })[NotesViewModel::class.java]
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme { NotesHome(vm) } }
 }
}

class NotesViewModel(private val repo: NotesRepository) : ViewModel() {
 val notes = repo.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 fun save(title: String, body: String, old: Note?) { viewModelScope.launch { repo.save(title, body, old) } }
 fun trash(note: Note) { viewModelScope.launch { repo.trash(note) } }
 fun pin(note: Note) { viewModelScope.launch { repo.togglePin(note) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesHome(vm: NotesViewModel) {
 val notes by vm.notes.collectAsStateWithLifecycle()
 var query by remember { mutableStateOf("") }
 var editor by remember { mutableStateOf(false) }
 var editing by remember { mutableStateOf<Note?>(null) }
 val shown = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
 Scaffold(topBar = { TopAppBar(title = { Column {
  Text("ENotes", fontWeight = FontWeight.Bold)
  Text("Your ideas, organized", style = MaterialTheme.typography.labelMedium)
 } }) }, floatingActionButton = {
  FloatingActionButton(onClick = { editing = null; editor = true }) { Icon(Icons.Default.Add, contentDescription = "Create note") }
 }) { inset ->
  Column(Modifier.fillMaxSize().padding(inset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search your notes") }, singleLine = true)
   if (shown.isEmpty()) {
    Text(if (query.isBlank()) "Start with one small idea." else "No matching notes", style = MaterialTheme.typography.titleLarge)
    Text(if (query.isBlank()) "Notes are stored locally on this device in this starter version." else "Try another search.")
    if (query.isBlank()) Button(onClick = { editing = null; editor = true }) { Text("Create your first note") }
   } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(shown, key = { it.id }) { note ->
     Card(Modifier.fillMaxWidth().clickable { editing = note; editor = true }) {
      Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.pin(note) }) { Icon(Icons.Default.PushPin, contentDescription = if (note.isPinned) "Unpin note" else "Pin note") }
        IconButton(onClick = { vm.trash(note) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Move note to trash") }
       }
       if (note.body.isNotBlank()) Text(note.body, maxLines = 3)
      }
     }
    }
   }
  }
 }
 if (editor) {
  var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
  var body by remember(editing?.id) { mutableStateOf(editing?.body.orEmpty()) }
  AlertDialog(onDismissRequest = { editor = false }, title = { Text(if (editing == null) "New note" else "Edit note") },
   text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
    OutlinedTextField(body, { body = it }, label = { Text("Write something…") }, minLines = 4)
   } },
   confirmButton = { Button(onClick = { vm.save(title, body, editing); editor = false }) { Text("Save note") } },
   dismissButton = { TextButton(onClick = { editor = false }) { Text("Cancel") } }
  )
 }
}