package com.masterclock.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.widget.MediaController
import android.widget.Toast
import androidx.core.net.toUri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import kotlin.random.Random

@Composable
fun VoiceNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    // Hoisted: these are used from permission and player callbacks, not composable scope.
    val permissionGrantedText = stringResource(R.string.toast_permission_granted)
    val micPermissionText = stringResource(R.string.toast_mic_permission)
    val startFailedText = stringResource(R.string.toast_start_failed)
    val audioFailedText = stringResource(R.string.toast_audio_failed)
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var title by remember { mutableStateOf(note.title) }; var isRecording by remember { mutableStateOf(false) }; var isPlaying by remember { mutableStateOf(false) }; var recorder by remember { mutableStateOf<MediaRecorder?>(null) }; var player by remember { mutableStateOf<MediaPlayer?>(null) }; var recordingTime by remember { mutableIntStateOf(0) }; var playbackTime by remember { mutableIntStateOf(0) }
    // Saves the title continuously instead of only on the toolbar back arrow: MainActivity's system
    // back handler (gesture/button) calls navigator.goBack() directly and never runs ToolScaffold's
    // onBack lambda, so a title edit followed by a system back used to be silently lost.
    // Debounced like every other note: this wrote the whole settings on each character of the title,
    // and once on merely opening the note. See PersistDebounced.
    PersistDebounced(title) { newTitle -> onUpdate { it.copy(title = newTitle) } }
    val scope = rememberCoroutineScope()
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var permissionRefused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) Toast.makeText(context, permissionGrantedText, Toast.LENGTH_SHORT).show() else permissionRefused = true }
    if (permissionRefused) PermissionRefusedDialog(micPermissionText) { permissionRefused = false }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        // Into the pending file, never the note's own: see pendingMediaFile.
        val pending = pendingMediaFile(File(context.filesDir, "audio_${note.id}.mp4"))
        @Suppress("DEPRECATION")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(pending.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            recordingStartedAt = SystemClock.elapsedRealtime()
            isRecording = true
        } catch (_: Exception) {
            // A recorder that failed to prepare or start still holds the microphone until it is
            // released, which nothing did -- the next attempt, or another app, then found it busy.
            newRecorder.release()
            pending.delete()
            Toast.makeText(context, startFailedText, Toast.LENGTH_SHORT).show()
        }
    }
    fun stopRecording() {
        val active = recorder ?: return
        // Cleared before stop() so a second call -- the ceiling and a disposal can arrive together
        // -- finds nothing to stop rather than stopping the same recorder twice.
        recorder = null
        isRecording = false
        val file = File(context.filesDir, "audio_${note.id}.mp4")
        val pending = pendingMediaFile(file)
        try {
            active.stop()
        } catch (_: Exception) {
            // stop() throws when the recorder has captured nothing yet, which is any take under
            // about a second. What it leaves on disk is a stub with no playable audio in it, so it
            // is deleted rather than offered to the note as a recording -- and since it was never
            // the note's own file, the recording the note already had is still there. Releasing
            // here matters: the old catch left the recorder holding the microphone.
            active.release()
            pending.delete()
            return
        }
        active.release()
        if (!commitTake(context, pending, file)) return
        shredReplacedMedia(context, note.audioPath, file)
        // Measured, where this took the counter of whole seconds: every take came out up to a second
        // short, and one under a second as 00:00.
        val durationMs = SystemClock.elapsedRealtime() - recordingStartedAt
        onUpdate { it.copy(title = title, audioPath = file.absolutePath, audioDurationMs = durationMs) }
    }
    fun startPlayback() { if (note.audioPath == null) return; try { val newPlayer = MediaPlayer().apply { setDataSource(note.audioPath); prepare(); start(); setOnCompletionListener { finished -> isPlaying = false; finished.release(); if (player === finished) player = null } }; player = newPlayer; isPlaying = true
        // Each loop follows its own player and stops as soon as that is no longer the one playing.
        // It used to run on isPlaying alone, so Stop then Play -- or Play right after a playback
        // ended -- inside its 100 ms sleep set isPlaying back to true before the old loop woke,
        // and it read currentPosition off a player already released: an IllegalStateException
        // nothing caught, and the app closed.
        scope.launch { while (isPlaying && player === newPlayer) { playbackTime = runCatching { newPlayer.currentPosition }.getOrDefault(0) / 1000; delay(100) }; if (player == null) playbackTime = 0 } } catch (_: Exception) { Toast.makeText(context, audioFailedText, Toast.LENGTH_SHORT).show() } }
    fun stopPlayback() { player?.stop(); player?.release(); player = null; isPlaying = false; playbackTime = 0 }

    // Below stopRecording() rather than above, because it has to call it. Reaching the one-minute
    // ceiling used to flip isRecording and nothing else: the MediaRecorder kept running with the
    // microphone open and the file still growing, while the screen said the recording had stopped,
    // and the take was never attached to the note. Pressing record again then started a second
    // recorder on the same file and abandoned the first, still holding the mic.
    //
    // isRecording is still true here only when the loop ended by hitting the ceiling; a manual stop
    // clears it, which both ends the loop and cancels this effect.
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        recordingTime = 0
        while (isRecording && recordingTime < 60) {
            delay(1000)
            recordingTime++
        }
        if (isRecording) stopRecording()
    }
    // Leaving the screen while recording is treated as stopping, because releasing a running
    // recorder is not the same as stopping one: release() without stop() never writes the MP4's
    // index, so the file left behind will not play, and onUpdate was never reached either. Walking
    // away therefore cost the whole take and left the unplayable orphan in filesDir.
    //
    // Through rememberUpdatedState: an effect keyed on Unit keeps the first composition's lambda, and
    // that stopRecording held the note as it was on opening. A retake left this way compared itself to
    // the original, so the recording it replaced was not shredded.
    val stopLatest by rememberUpdatedState({ stopRecording() })
    DisposableEffect(Unit) { onDispose { stopLatest(); player?.release() } }

    ToolScaffold(title = stringResource(R.string.tools_audio_note), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true); Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null, modifier = Modifier.size(64.dp).scale(if (isRecording) 1.2f else 1f), tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (isRecording) Text(text = String.format(locale, "%02d:%02d / 01:00", recordingTime / 60, recordingTime % 60), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            else if (note.audioPath != null) Text(text = if (isPlaying) String.format(locale, "%02d:%02d", playbackTime / 60, playbackTime % 60) else ((note.audioDurationMs + 500) / 1000).let { seconds -> String.format(locale, "%02d:%02d", seconds / 60, seconds % 60) }, style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { if (!isPlaying) Button(onClick = { if (isRecording) stopRecording() else startRecording() }, colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp).weight(1f)) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(when { isRecording -> stringResource(R.string.tools_stop); note.audioPath != null -> stringResource(R.string.tools_record_again); else -> stringResource(R.string.tools_record) }) }; if (note.audioPath != null && !isRecording) Button(onClick = { if (isPlaying) stopPlayback() else startPlayback() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp).weight(1f)) { Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (isPlaying) stringResource(R.string.tools_stop) else stringResource(R.string.tools_play)) } }
            if (note.audioPath != null && !isRecording && !isPlaying) DeleteMediaButton(stringResource(R.string.tools_delete_recording)) { shredNoteMedia(context, note.audioPath); onUpdate { it.copy(title = title, audioPath = null, audioDurationMs = 0) } }; Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Says a permission was refused, with the way to change that. Once it has been refused twice Android
 * stops asking and answers no at once, so a toast alone left the button doing nothing, for good, with
 * no hint of where the setting is.
 */
@Composable
private fun PermissionRefusedDialog(message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }
            }) { Text(stringResource(R.string.permission_open_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
fun ImageNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    val cameraPermissionText = stringResource(R.string.toast_camera_permission)
    val context = LocalContext.current; var title by remember { mutableStateOf(note.title) }; val photoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "image_${note.id}.jpg") }
    // The camera writes into the pending file, never the note's own; see pendingMediaFile.
    val pendingFile = remember { pendingMediaFile(photoFile) }; val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pendingFile) }
    // Saves the title continuously; see the matching comment in VoiceNoteEditor.
    // Debounced like every other note: this wrote the whole settings on each character of the title,
    // and once on merely opening the note. See PersistDebounced.
    PersistDebounced(title) { newTitle -> onUpdate { it.copy(title = newTitle) } }
    // Bumped on every new photo; see the image below.
    var captureCount by remember { mutableIntStateOf(0) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && commitTake(context, pendingFile, photoFile)) { captureCount++; shredReplacedMedia(context, note.imagePath, photoFile); onUpdate { it.copy(title = title, imagePath = photoFile.absolutePath) } }
        else pendingFile.delete()
    }
    var permissionRefused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) cameraLauncher.launch(photoUri) else permissionRefused = true }
    if (permissionRefused) PermissionRefusedDialog(cameraPermissionText) { permissionRefused = false }

    ToolScaffold(title = stringResource(R.string.tools_image_note), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) ) {
                val imagePath = note.imagePath
                // A retake lands at the same path, and Coil caches a file by its path alone, so the
                // old photo went on showing -- until the app was restarted, sometimes. The key adds
                // the file's modification time, which a retake changes, and is worked out again on
                // every capture even when nothing else about the note has changed.
                val imageModel = remember(imagePath, captureCount) {
                    imagePath?.let { path ->
                        val key = "$path#${File(path).lastModified()}"
                        ImageRequest.Builder(context).data(path).memoryCacheKey(key).diskCacheKey(key).build()
                    }
                }
                if (imageModel != null) AsyncImage(model = imageModel, contentDescription = stringResource(R.string.tools_note_image), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.tools_no_photo), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(photoUri) else permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(if (note.imagePath == null) Icons.Default.PhotoCamera else Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(if (note.imagePath == null) stringResource(R.string.tools_take_photo) else stringResource(R.string.tools_retake_photo)) }
            if (note.imagePath != null) DeleteMediaButton(stringResource(R.string.tools_delete_photo)) { shredNoteMedia(context, note.imagePath); onUpdate { it.copy(title = title, imagePath = null) } }
        }
    }
}

@Composable
fun VideoNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    val cameraPermissionText = stringResource(R.string.toast_camera_permission)
    val context = LocalContext.current; var title by remember { mutableStateOf(note.title) }; val videoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "video_${note.id}.mp4") }
    // The camera writes into the pending file, never the note's own; see pendingMediaFile.
    val pendingFile = remember { pendingMediaFile(videoFile) }; val videoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pendingFile) }
    // Saves the title continuously; see the matching comment in VoiceNoteEditor.
    // Debounced like every other note: this wrote the whole settings on each character of the title,
    // and once on merely opening the note. See PersistDebounced.
    PersistDebounced(title) { newTitle -> onUpdate { it.copy(title = newTitle) } }
    // A retake ends up at video_<id>.mp4 again, so the path is identical before and after and nothing
    // in the note tells the two apart. This counter is what does: it changes on every capture, and
    // the player reloads when it does.
    var captureCount by remember { mutableIntStateOf(0) }
    val videoLauncher = rememberLauncherForActivityResult(CaptureVideoUpToAMinute()) { success ->
        if (success && commitTake(context, pendingFile, videoFile)) { captureCount++; shredReplacedMedia(context, note.videoPath, videoFile); onUpdate { it.copy(title = title, videoPath = videoFile.absolutePath) } }
        else pendingFile.delete()
    }
    var permissionRefused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) videoLauncher.launch(videoUri) else permissionRefused = true }
    if (permissionRefused) PermissionRefusedDialog(cameraPermissionText) { permissionRefused = false }

    ToolScaffold(title = stringResource(R.string.tools_video_note), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                val videoPath = note.videoPath
                // The source used to be set in factory, which runs once: a retake left the old
                // recording playing, since the path it compared had not changed. Loading it from
                // update, keyed on the capture counter, is what actually picks up a new take. The
                // view also has to give the decoder back, which nothing did.
                //
                // It no longer starts by itself: opening a note played it, sound and all, which is
                // the last thing wanted at a board. Once loaded it shows its first frame and the
                // controls, and plays when asked.
                //
                // Sized by the view and centred by the Box. A VideoView keeps the film's proportions
                // and sits at its parent's origin, so filling the card left it in the top left corner.
                if (videoPath != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx -> VideoView(ctx).apply { val controller = MediaController(ctx); controller.setAnchorView(this); setMediaController(controller); setOnPreparedListener { seekTo(1); controller.show() } } },
                    update = { view -> if (view.tag != captureCount) { view.tag = captureCount; view.setVideoPath(videoPath) } },
                    onRelease = { view -> view.stopPlayback() },
                )
                }
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.tools_no_video), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) videoLauncher.launch(videoUri) else permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(if (note.videoPath == null) Icons.Default.Videocam else Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(if (note.videoPath == null) stringResource(R.string.tools_record_video) else stringResource(R.string.tools_retake_video)) }
            if (note.videoPath != null) DeleteMediaButton(stringResource(R.string.tools_delete_video)) { shredNoteMedia(context, note.videoPath); onUpdate { it.copy(title = title, videoPath = null) } }
        }
    }
}

/**
 * Shreds the media a note pointed at before a retake, when the retake wrote somewhere else.
 *
 * A capture always writes under the note's current id. An imported or restored note gets a fresh
 * id but keeps media named after its old one, so its first retake wrote a new file beside the old
 * and the old was referenced by nothing again -- left on disk for good, which for a video is a lot
 * of it. Off the main thread, since it may be exactly that video.
 */
/**
 * The camera, asked to stop recording at a minute.
 *
 * The editor has always said "Max 1 min" and nothing asked for it: a camera recorded for as long as
 * it was left running, at whatever quality it liked, into the app's own storage -- and every full
 * backup carried it. Voice notes already stop themselves at sixty seconds. EXTRA_DURATION_LIMIT is
 * Android's documented way to ask; it is the camera app that applies it, not this one.
 */
private class CaptureVideoUpToAMinute : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: android.content.Context, input: android.net.Uri): Intent =
        super.createIntent(context, input).putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 60)
}

/**
 * A media note's delete action. It asks first: the file is shredded, so there is no way back.
 *
 * Only the voice note had one, and it only cleared the note's path -- the recording stayed on disk
 * until the whole note was deleted, and for good when the file carried an older note's id.
 */
@Composable
private fun DeleteMediaButton(label: String, onConfirm: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(label) }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(label) },
        text = { Text(stringResource(R.string.tools_delete_media_message)) },
        confirmButton = { TextButton(onClick = { asking = false; onConfirm() }) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Shreds a note's media file off the main thread, within the app's own storage only. */
private fun shredNoteMedia(context: android.content.Context, path: String?) {
    if (path == null) return
    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
    kotlin.concurrent.thread(name = "shred-note-media") { shredFile(File(path), sandboxRoots) }
}

/**
 * Moves a finished take from [pending] over [target], shredding the take it replaces off the main
 * thread; false when there was no take to commit, with [target] untouched. See commitPendingMedia.
 */
private fun commitTake(context: android.content.Context, pending: File, target: File): Boolean {
    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
    return commitPendingMedia(pending, target) { replaced ->
        kotlin.concurrent.thread(name = "shred-replaced-take") { shredFile(replaced, sandboxRoots) }
    }
}

private fun shredReplacedMedia(context: android.content.Context, oldPath: String?, newFile: File) {
    if (oldPath == null) return
    val old = File(oldPath)
    if (runCatching { old.canonicalPath == newFile.canonicalPath }.getOrDefault(true)) return
    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
    kotlin.concurrent.thread(name = "shred-replaced-media") { shredFile(old, sandboxRoots) }
}

/**
 * Overwrites [file] with random bytes before deleting it. Bounded to [sandboxRoots] (the app's own
 * filesDir/cacheDir): notebook note paths can originate from an imported settings file, so without
 * this check a crafted path could point the shredder at any file this app can write to.
 */
internal fun shredFile(file: File, sandboxRoots: List<File>) {
    val canonical = try { file.canonicalFile } catch (_: Exception) { return }
    val isInsideSandbox = sandboxRoots.any { root ->
        canonical == root || canonical.path.startsWith(root.path + File.separator)
    }
    if (!isInsideSandbox || !canonical.exists()) return
    try {
        val length = canonical.length()
        if (length > 0) {
            // One sync at the end, not one per write: "rws" synced every 4 KB, so a minute of video
            // took tens of thousands of synchronous writes.
            RandomAccessFile(canonical, "rw").use { raf ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (written < length) {
                    Random.nextBytes(buffer)
                    val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                    raf.write(buffer, 0, toWrite)
                    written += toWrite
                }
                raf.fd.sync()
            }
        }
        canonical.delete()
    } catch (_: Exception) {
        canonical.delete()
    }
}
