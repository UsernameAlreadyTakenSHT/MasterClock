package com.masterclock.app.ui.screens

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

@Composable
fun NotebookScreen(viewModel: ChessTimerViewModel, onBack: () -> Unit) {
    // Hoisted: the note titles are assigned inside onClick, the toast inside a callback.
    val noteDeletedText = stringResource(R.string.toast_note_deleted)
    val newTextNoteTitle = stringResource(R.string.tools_new_note)
    val newDrawNoteTitle = stringResource(R.string.tools_new_draw_note)
    val newAudioNoteTitle = stringResource(R.string.tools_new_audio_note)
    val newImageNoteTitle = stringResource(R.string.tools_new_image_note)
    val newVideoNoteTitle = stringResource(R.string.tools_new_video_note)
    val newBoardNoteTitle = stringResource(R.string.tools_new_board_note)
    val notebookFullText = stringResource(R.string.tools_notebook_full)
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saveable because the camera is another app: Android may destroy this one while it is open --
    // a camera is memory-hungry -- and recreate it for the result. With a plain remember that
    // recreation came back to the list, the editor's launcher was no longer there to receive the
    // capture, and the new photo or video was left in a file the note never learnt about.
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTypeSelection by rememberSaveable { mutableStateOf(false) }
    val selectedNote = remember(selectedNoteId, settings.notebookNotes) { settings.notebookNotes.find { it.id == selectedNoteId } }
    // Back from a note goes back to the list, as the arrow in its bar does. With nothing to catch
    // it, the system Back left the notebook altogether -- as it left Game Logs from a game's detail.
    BackHandler(enabled = selectedNote != null) { selectedNoteId = null }
    // The note whose deletion is being confirmed; see the trash icon below.
    var deletingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    if (selectedNote == null) {
        val sortedNotes = remember(settings.notebookNotes) { settings.notebookNotes.sortedByDescending { it.timestamp } }
        ToolScaffold(title = stringResource(R.string.settings_more_notebook), onBack = onBack, actions = { IconButton(onClick = {
            // Refused here rather than accepted and then cut at the next launch; see MAX_NOTEBOOK_NOTES.
            if (settings.notebookNotes.size >= MAX_NOTEBOOK_NOTES) Toast.makeText(context, notebookFullText, Toast.LENGTH_LONG).show()
            else showTypeSelection = true
        }) { Icon(Icons.Default.Add, stringResource(R.string.tools_new_note)) } }) { padding ->
            if (sortedNotes.isEmpty()) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tools_no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            else { 
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding), 
                    contentPadding = PaddingValues(16.dp), 
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { 
                    items(sortedNotes, key = { it.id }) { note -> 
                        Card(
                            onClick = { selectedNoteId = note.id }, 
                            modifier = Modifier.fillMaxWidth(), 
                            shape = RoundedCornerShape(12.dp), 
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) { 
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                                Icon(
                                    imageVector = when(note.type) { 
                                        NotebookNoteType.TEXT -> Icons.Default.Description
                                        NotebookNoteType.DRAWING -> Icons.Default.Brush
                                        NotebookNoteType.VOICE -> Icons.Default.Mic
                                        NotebookNoteType.IMAGE -> Icons.Default.PhotoCamera
                                        NotebookNoteType.VIDEO -> Icons.Default.Videocam
                                        NotebookNoteType.BOARD -> Icons.Default.Grid4x4 
                                    }, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary, 
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                Column(Modifier.weight(1f)) { 
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        isoDateTime(note.timestamp),
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                                // Asked first, as deleting a note's photo or recording is: one tap shredded the
                                // note and its media for good.
                                IconButton(onClick = { deletingNoteId = note.id }) {
                                    Icon(Icons.Default.DeleteForever, stringResource(R.string.tools_shred), tint = MaterialTheme.colorScheme.error)
                                }
                            } 
                        } 
                    } 
                } 
            }
        }
        sortedNotes.firstOrNull { it.id == deletingNoteId }?.let { note ->
            ConfirmDestructiveDialog(
                title = note.title.ifBlank { stringResource(R.string.tools_shred) },
                confirmLabel = stringResource(R.string.tools_shred),
                onDismiss = { deletingNoteId = null },
                onConfirm = {
                    // The note goes first, then its media, on a thread of its own. The media went
                    // first, in the screen's scope, and the note only once that was done: shredding a
                    // video took long enough to leave the note on the list with no sign of progress,
                    // and leaving the notebook meanwhile cancelled the scope, so the note stayed --
                    // pointing at a file that was gone.
                    viewModel.removeNote(note.id)
                    Toast.makeText(context, noteDeletedText, Toast.LENGTH_SHORT).show()
                    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
                    val sweepDirs = listOf(context.filesDir, File(context.filesDir, "shares"))
                    kotlin.concurrent.thread(name = "shred-note") {
                        listOfNotNull(note.audioPath, note.imagePath, note.videoPath).forEach { shredFile(File(it), sandboxRoots) }
                        // Its own files only, by their exact names; see isNoteMediaFile.
                        sweepDirs.forEach { dir ->
                            dir.listFiles()?.filter { isNoteMediaFile(it.name, note.id) }?.forEach { shredFile(it, sandboxRoots) }
                        }
                    }
                },
            )
        }
        if (showTypeSelection) { Dialog(onDismissRequest = { showTypeSelection = false }) { Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) { Text(text = stringResource(R.string.tools_new_note), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold);                         FlowRow(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), 
                            verticalArrangement = Arrangement.spacedBy(24.dp), 
                            maxItemsInEachRow = 3
                        ) { 
                            TypeButton(stringResource(R.string.tools_text), Icons.Default.Description) { val newNote = NotebookNote(type = NotebookNoteType.TEXT, title = newTextNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton(stringResource(R.string.tools_draw), Icons.Default.Brush) { val newNote = NotebookNote(type = NotebookNoteType.DRAWING, title = newDrawNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton(stringResource(R.string.tools_audio), Icons.Default.Mic) { val newNote = NotebookNote(type = NotebookNoteType.VOICE, title = newAudioNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton(stringResource(R.string.tools_image), Icons.Default.PhotoCamera) { val newNote = NotebookNote(type = NotebookNoteType.IMAGE, title = newImageNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton(stringResource(R.string.tools_video), Icons.Default.Videocam) { val newNote = NotebookNote(type = NotebookNoteType.VIDEO, title = newVideoNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false }; 
                            TypeButton(stringResource(R.string.tools_board), Icons.Default.Grid4x4) { val newNote = NotebookNote(type = NotebookNoteType.BOARD, title = newBoardNoteTitle); viewModel.updateSettings(settings.copy(notebookNotes = settings.notebookNotes + newNote)); selectedNoteId = newNote.id; showTypeSelection = false } 
                        }; TextButton(onClick = { showTypeSelection = false }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.common_cancel)) } } } } }
    } else {
        when (selectedNote.type) {
            NotebookNoteType.DRAWING -> DrawingNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
            NotebookNoteType.VOICE -> VoiceNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
            NotebookNoteType.IMAGE -> ImageNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
            NotebookNoteType.VIDEO -> VideoNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
            NotebookNoteType.BOARD -> BoardNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
            else -> RichTextNoteEditor(note = selectedNote, onUpdate = { change -> viewModel.updateNote(selectedNote.id, change) }, onBack = { selectedNoteId = null })
        }
    }
}

/**
 * How a note editor saves: a change applied to the note as it is stored at that moment, never a
 * copy of the note the editor was composed with. A copy wrote back whatever it had captured, so
 * on leaving a voice note the title's flush -- disposed after the recording's own save -- put the
 * note back as it was before the take, and the recording was lost while its file stayed on disk.
 */
typealias NoteChange = ((NotebookNote) -> NotebookNote) -> Unit

/** Long enough to cover typing, short enough that nobody could leave a note in less. */
private const val NOTE_PERSIST_DEBOUNCE_MS = 400L

/**
 * Persists [value] once it has stopped changing, and at once if the screen is left before that.
 *
 * A note editor writing through on every change sounds cheap and is not: one write re-serialises
 * the whole of ChessClockSettings -- every note in the notebook, every stroke of every drawing --
 * and DataStore then rewrites its file. Typing a sentence into a text note meant one of those per
 * character, each one larger than the last.
 *
 * The flush on disposal is what makes the wait free rather than a way to lose the last few
 * characters. It also covers the case the write-through was there for in the first place:
 * MainActivity's system back handler calls navigator.goBack() directly and never runs
 * ToolScaffold's onBack lambda, so leaving is only ever observed here.
 *
 * What is owed is decided against the last value written, not the one the note opened with; see
 * NoteSaveTracker for the two ways the old comparison lost an edit.
 */
@Composable
internal fun <T> PersistDebounced(value: T, onPersist: (T) -> Unit) {
    val persist by rememberUpdatedState(onPersist)
    val tracker = remember { NoteSaveTracker(value) }

    LaunchedEffect(value) {
        if (!tracker.changed(value)) return@LaunchedEffect
        delay(NOTE_PERSIST_DEBOUNCE_MS)
        tracker.written(value)
        persist(value)
    }
    DisposableEffect(Unit) {
        onDispose { tracker.flush { persist(it) } }
    }
}

@Composable
fun TypeButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.size(80.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
    }
}
