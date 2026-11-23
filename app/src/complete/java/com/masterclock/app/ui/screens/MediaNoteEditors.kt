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
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.masterclock.app.R
import com.masterclock.app.logic.*
import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import kotlin.random.Random

/**
 * The voice note: a recording of up to a minute, played back, recorded again or deleted.
 *
 * Built from what the three media notes share (see MediaNoteScreen). The recorder and the player,
 * and the order things must happen in between them, are a VoiceSession, tested in core; what is
 * left here is the files, the messages and the screen.
 */
@Composable
fun VoiceNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    // Hoisted: these are used from permission and player callbacks, not composable scope.
    val permissionGrantedText = stringResource(R.string.toast_permission_granted)
    val startFailedText = stringResource(R.string.toast_start_failed)
    val audioFailedText = stringResource(R.string.toast_audio_failed)
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val title = rememberNoteTitle(note, onUpdate)
    val caption = rememberNoteCaption(note, onUpdate)
    val session = remember { VoiceSession({ MicRecorder(context) }, { MediaVoicePlayer() }, SystemClock::elapsedRealtime) }
    val state by session.state.collectAsState()
    var recordingTime by remember { mutableIntStateOf(0) }
    var playbackTime by remember { mutableIntStateOf(0) }
    val noteFile = remember { File(context.filesDir, "audio_${note.id}.mp4") }

    fun beginRecording() {
        // Into the pending file, never the note's own: see pendingMediaFile.
        val pending = pendingMediaFile(noteFile)
        if (!session.startRecording(pending.absolutePath)) {
            pending.delete()
            Toast.makeText(context, startFailedText, Toast.LENGTH_SHORT).show()
        }
    }
    // Granted, it says so and waits for the next press, as it always has.
    val startRecording = rememberPermitted(Manifest.permission.RECORD_AUDIO, R.string.toast_mic_permission, onGranted = { Toast.makeText(context, permissionGrantedText, Toast.LENGTH_SHORT).show() }) { beginRecording() }
    fun stopRecording() {
        val pending = pendingMediaFile(noteFile)
        // No take: what is on disk is a stub with no playable audio in it, deleted rather than offered
        // to the note -- and since it was never the note's own file, the recording the note already
        // had is still there.
        val durationMs = session.stopRecording() ?: run { pending.delete(); return }
        if (!commitTake(context, pending, noteFile)) return
        shredReplacedMedia(context, note.audioPath, noteFile)
        // Measured, where this took the counter of whole seconds: every take came out up to a second
        // short, and one under a second as 00:00.
        onUpdate { it.copy(title = title.value, audioPath = noteFile.absolutePath, audioDurationMs = durationMs) }
    }
    fun startPlayback() {
        val path = note.audioPath ?: return
        if (!session.startPlayback(path)) Toast.makeText(context, audioFailedText, Toast.LENGTH_SHORT).show()
    }

    // The minute's end stops the take as the button would. It once flipped a flag and nothing else:
    // the recorder kept running with the microphone open and the file growing, while the screen said
    // the recording had stopped, and the take was never attached to the note.
    LaunchedEffect(state.recording) {
        recordingTime = 0
        while (state.recording) {
            val ms = session.recordingMs
            recordingTime = (ms / 1000).toInt()
            if (ms >= MAX_VOICE_MS) { stopRecording(); break }
            delay(200)
        }
    }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            playbackTime = session.playbackMs / 1000
            delay(100)
        }
        playbackTime = 0
    }
    // Leaving the screen while recording is treated as stopping, because releasing a running
    // recorder is not the same as stopping one: release() without stop() never writes the MP4's
    // index, so the file left behind will not play, and the note was never updated either. Walking
    // away therefore cost the whole take and left the unplayable orphan in filesDir.
    //
    // Through rememberUpdatedState: an effect keyed on Unit keeps the first composition's lambda, and
    // that stopRecording held the note as it was on opening. A retake left this way compared itself to
    // the original, so the recording it replaced was not shredded.
    val stopLatest by rememberUpdatedState({ stopRecording() })
    DisposableEffect(Unit) { onDispose { stopLatest(); session.stopPlayback() } }
    // And the app left, as leaving the screen: the screen stays composed under another app, and the
    // recording went on with the microphone open until the note was left, the minute's limit aside.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { stopLatest(); session.stopPlayback() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isRecording = state.recording
    val isPlaying = state.playing
    MediaNoteScreen(R.string.tools_audio_note, title, onBack, spacing = 24.dp) {
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null, modifier = Modifier.size(64.dp).scale(if (isRecording) 1.2f else 1f), tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (isRecording) Text(text = String.format(locale, "%02d:%02d / 01:00", recordingTime / 60, recordingTime % 60), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        else if (note.audioPath != null) Text(text = if (isPlaying) String.format(locale, "%02d:%02d", playbackTime / 60, playbackTime % 60) else ((note.audioDurationMs + 500) / 1000).let { seconds -> String.format(locale, "%02d:%02d", seconds / 60, seconds % 60) }, style = MaterialTheme.typography.headlineMedium)
        CaptionField(caption)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isPlaying) Button(onClick = { if (isRecording) stopRecording() else startRecording() }, colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.height(56.dp).weight(1f)) {
                Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                Spacer(Modifier.width(8.dp))
                // One line: "Neu aufnehmen" took two beside its icon, half a row wide. The narrower padding,
                // on both buttons, lets "Gravar novamente" keep a size near its neighbour's.
                OneLineText(when { isRecording -> stringResource(R.string.tools_stop); note.audioPath != null -> stringResource(R.string.tools_record_again); else -> stringResource(R.string.tools_record) })
            }
            if (note.audioPath != null && !isRecording) Button(onClick = { if (isPlaying) session.stopPlayback() else startPlayback() }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.height(56.dp).weight(1f)) {
                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) stringResource(R.string.tools_stop) else stringResource(R.string.tools_play))
            }
        }
        if (note.audioPath != null && !isRecording && !isPlaying) DeleteMediaButton(stringResource(R.string.tools_delete_recording)) { shredNoteMedia(context, note.audioPath); onUpdate { it.copy(title = title.value, audioPath = null, audioDurationMs = 0) } }
        Spacer(Modifier.weight(1f))
    }
}

/** The microphone, through MediaRecorder: AAC in an MP4, as voice notes have always been. */
private class MicRecorder(context: android.content.Context) : VoiceRecorder {
    @Suppress("DEPRECATION")
    private val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    override fun start(outputPath: String) {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        // Said outright, because left unsaid MediaRecorder falls back to telephone quality -- a
        // few kHz at a dozen kbit/s -- and every voice note sounded like a bad line whatever the
        // microphone. Mono at 64 kbit/s is a clear voice for about half a megabyte a minute.
        recorder.setAudioSamplingRate(44_100)
        recorder.setAudioEncodingBitRate(64_000)
        recorder.setAudioChannels(1)
        recorder.setOutputFile(outputPath)
        recorder.prepare()
        recorder.start()
    }

    override fun stop() = recorder.stop()
    override fun release() = recorder.release()
}

/** Playback through MediaPlayer. */
private class MediaVoicePlayer : VoicePlayer {
    private val player = MediaPlayer()

    override fun start(path: String, onDone: () -> Unit) {
        player.setDataSource(path)
        player.prepare()
        player.setOnCompletionListener { onDone() }
        player.start()
    }

    override val positionMs: Int get() = player.currentPosition
    override fun stop() = player.stop()
    override fun release() = player.release()
}

/** The photo note: a picture taken with the camera, taken again or deleted. */
@Composable
fun ImageNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = rememberNoteTitle(note, onUpdate)
    val caption = rememberNoteCaption(note, onUpdate)
    val photoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "image_${note.id}.jpg") }
    val take = rememberCameraTake(photoFile, ActivityResultContracts.TakePicture()) { file ->
        shredReplacedMedia(context, note.imagePath, file)
        onUpdate { it.copy(title = title.value, imagePath = file.absolutePath) }
    }
    MediaNoteScreen(R.string.tools_image_note, title, onBack) {
        val imagePath = note.imagePath
        MediaFrame(empty = imagePath == null, emptyIcon = Icons.Default.AddAPhoto, emptyText = R.string.tools_no_photo) {
            // A retake lands at the same path, and Coil caches a file by its path alone, so the
            // old photo went on showing -- until the app was restarted, sometimes. The key adds
            // the file's modification time, which a retake changes, and is worked out again on
            // every capture even when nothing else about the note has changed.
            val imageModel = remember(imagePath, take.count) {
                imagePath?.let { path ->
                    val key = "$path#${File(path).lastModified()}"
                    ImageRequest.Builder(context).data(path).memoryCacheKey(key).diskCacheKey(key).build()
                }
            }
            // Pinched, dragged and double-tapped to read a scoresheet, which shown whole was too small
            // to; see PhotoZoom. Held to its place, so that enlarged it does not run over the caption
            // and the buttons; whole again for each new photo.
            var zoom by remember(imagePath, take.count) { mutableStateOf(PhotoZoom()) }
            AsyncImage(
                model = imageModel,
                contentDescription = stringResource(R.string.tools_note_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(imageModel) {
                        detectTapGestures(onDoubleTap = { at -> zoom = zoom.doubleTapped(at.x, at.y, size.width.toFloat(), size.height.toFloat()) })
                    }
                    .pointerInput(imageModel) {
                        detectTransformGestures { centroid, pan, pinch, _ ->
                            zoom = zoom.transformed(pinch, centroid.x, centroid.y, pan.x, pan.y, size.width.toFloat(), size.height.toFloat())
                        }
                    }
                    // For the tests alone, which had nothing to read: the one that double-tapped and
                    // pinched passed whether or not either did anything. A key TalkBack does not speak.
                    .semantics { this[PhotoZoomScale] = zoom.scale }
                    .graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.x; translationY = zoom.y },
            )
        }
        CaptionField(caption)
        CaptureButton(
            icon = if (imagePath == null) Icons.Default.PhotoCamera else Icons.Default.Replay,
            label = stringResource(if (imagePath == null) R.string.tools_take_photo else R.string.tools_retake_photo),
            onClick = take.launch,
        )
        if (imagePath != null) DeleteMediaButton(stringResource(R.string.tools_delete_photo)) { shredNoteMedia(context, imagePath); onUpdate { it.copy(title = title.value, imagePath = null) } }
    }
}

/** The video note: up to a minute filmed with the camera, filmed again or deleted. */
@Composable
fun VideoNoteEditor(note: NotebookNote, onUpdate: NoteChange, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = rememberNoteTitle(note, onUpdate)
    val caption = rememberNoteCaption(note, onUpdate)
    val videoFile = remember { File(File(context.filesDir, "shares").apply { mkdirs() }, "video_${note.id}.mp4") }
    // A retake ends up at video_<id>.mp4 again, so the path is identical before and after and nothing
    // in the note tells the two apart. The take's counter is what does: it changes on every capture,
    // and the player reloads when it does.
    val take = rememberCameraTake(videoFile, CaptureVideoUpToAMinute()) { file ->
        shredReplacedMedia(context, note.videoPath, file)
        onUpdate { it.copy(title = title.value, videoPath = file.absolutePath) }
    }
    MediaNoteScreen(R.string.tools_video_note, title, onBack) {
        val videoPath = note.videoPath
        MediaFrame(empty = videoPath == null, emptyIcon = Icons.Default.VideoCall, emptyText = R.string.tools_no_video) {
            // The source used to be set in factory, which runs once: a retake left the old
            // recording playing, since the path it compared had not changed. Loading it from
            // update, keyed on the capture counter, is what actually picks up a new take. The
            // view also has to give the decoder back, which nothing did.
            //
            // It no longer starts by itself: opening a note played it, sound and all, which is
            // the last thing wanted at a board. Once loaded it shows its first frame and the
            // controls, and plays when asked.
            //
            // Sized by the view and centred by the frame. A VideoView keeps the film's proportions
            // and sits at its parent's origin, so filling the card left it in the top left corner.
            val count = take.count
            AndroidView(
                factory = { ctx -> VideoView(ctx).apply { val controller = MediaController(ctx); controller.setAnchorView(this); setMediaController(controller); setOnPreparedListener { seekTo(1); controller.show() } } },
                update = { view -> if (view.tag != count) { view.tag = count; view.setVideoPath(videoPath) } },
                onRelease = { view -> view.stopPlayback() },
            )
        }
        CaptionField(caption)
        CaptureButton(
            icon = if (videoPath == null) Icons.Default.Videocam else Icons.Default.Replay,
            label = stringResource(if (videoPath == null) R.string.tools_record_video else R.string.tools_retake_video),
            onClick = take.launch,
        )
        if (videoPath != null) DeleteMediaButton(stringResource(R.string.tools_delete_video)) { shredNoteMedia(context, videoPath); onUpdate { it.copy(title = title.value, videoPath = null) } }
    }
}

/**
 * A media note's title, saved as it is typed.
 *
 * Continuously rather than only on the toolbar's back arrow: MainActivity's system back handler
 * (gesture or button) calls navigator.goBack() directly and never runs ToolScaffold's onBack, so a
 * title edit followed by a system back used to be lost. Debounced like every other note: this wrote
 * the whole settings on each character of the title, and once on merely opening the note.
 */
@Composable
private fun rememberNoteTitle(note: NotebookNote, onUpdate: NoteChange): MutableState<String> {
    val title = remember { mutableStateOf(note.title) }
    PersistDebounced(title.value) { newTitle -> onUpdate { it.copy(title = newTitle) } }
    return title
}

/**
 * What the three media notes have in common on screen: the bar, the title, and the column the rest
 * goes in. Image, audio and video each wrote it out on its own, so a fix to one of them had to be
 * made three times, and was not always.
 */
@Composable
private fun MediaNoteScreen(screenTitle: Int, title: MutableState<String>, onBack: () -> Unit, spacing: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    ToolScaffold(title = stringResource(screenTitle), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(spacing), verticalArrangement = Arrangement.spacedBy(spacing), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(value = title.value, onValueChange = { title.value = it.take(MAX_NOTE_TITLE_CHARS) }, label = { Text(stringResource(R.string.tools_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            content()
        }
    }
}

/**
 * Where a photo or a film is shown, centred, or what says there is none yet.
 *
 * Framed only when empty, where the frame marks out the place the media will go. Around a photo it
 * only added a border and grey bands above and below one not of the screen's shape.
 */
@Composable
private fun ColumnScope.MediaFrame(empty: Boolean, emptyIcon: ImageVector, emptyText: Int, content: @Composable BoxScope.() -> Unit) {
    if (!empty) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
        return
    }
    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(emptyIcon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A media note's caption, saved as it is typed, as its title is. It is the note's content, which a
 * text note keeps its text in and no other note used: import and export already carry it and hold
 * it to its length, so it needs nothing new from either.
 */
@Composable
private fun rememberNoteCaption(note: NotebookNote, onUpdate: NoteChange): MutableState<String> {
    val caption = remember { mutableStateOf(note.content) }
    PersistDebounced(caption.value) { newCaption -> onUpdate { it.copy(content = newCaption) } }
    return caption
}

/** What is said about the photo, the recording or the film, under it. */
@Composable
private fun CaptionField(caption: MutableState<String>) {
    OutlinedTextField(
        value = caption.value,
        onValueChange = { caption.value = it.take(MAX_NOTE_CAPTION_CHARS) },
        label = { Text(stringResource(R.string.tools_caption)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        maxLines = 3,
    )
}

/** A caption is a line or two; a longer text is a text note's. */
private const val MAX_NOTE_CAPTION_CHARS = 500

/** A media note's main button: take, record, or take again. */
@Composable
private fun CaptureButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * [action], once [permission] is granted: at once if it is, after asking if not. Refused, it says
 * so with the way to change that (see PermissionRefusedDialog). Granted on asking, it runs
 * [onGranted] when given, or else [action].
 */
@Composable
private fun rememberPermitted(permission: String, refusedText: Int, onGranted: (() -> Unit)? = null, action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val refusedMessage = stringResource(refusedText)
    var refused by remember { mutableStateOf(false) }
    val latestAction by rememberUpdatedState(action)
    val latestGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (latestGranted ?: latestAction)() else refused = true
    }
    if (refused) PermissionRefusedDialog(refusedMessage) { refused = false }
    return { if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) latestAction() else launcher.launch(permission) }
}

/** A take from the camera: how to start one, and how many have come in, which a new one changes. */
private class CameraTake(val launch: () -> Unit, val count: Int)

/**
 * The camera, asked for a take into [file] through [contract] once it may be used. The camera
 * writes into the pending file, never the note's own (see pendingMediaFile); a take that came in is
 * moved over [file] and handed to [onTaken], and one that did not is thrown away.
 */
@Composable
private fun rememberCameraTake(file: File, contract: ActivityResultContract<android.net.Uri, Boolean>, onTaken: (File) -> Unit): CameraTake {
    val context = LocalContext.current
    val pending = remember(file) { pendingMediaFile(file) }
    val uri = remember(pending) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pending) }
    var count by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(contract) { success ->
        if (success && commitTake(context, pending, file)) { count++; onTaken(file) }
        else pending.delete()
    }
    val launch = rememberPermitted(Manifest.permission.CAMERA, R.string.toast_camera_permission) { launcher.launchOrSay(context, uri) }
    return CameraTake(launch, count)
}

/**
 * Says a permission was refused, with the way to change that. Once it has been refused twice Android
 * stops asking and answers no at once, so a toast alone left the button doing nothing, for good, with
 * no hint of where the setting is.
 */
@Composable
internal fun PermissionRefusedDialog(message: String, onDismiss: () -> Unit) {
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
internal fun shredNoteMedia(context: android.content.Context, path: String?) {
    if (path == null) return
    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
    kotlin.concurrent.thread(name = "shred-note-media") { shredFile(File(path), sandboxRoots) }
}

/**
 * Moves a finished take from [pending] over [target], shredding the take it replaces off the main
 * thread; false when there was no take to commit, with [target] untouched. See commitPendingMedia.
 */
internal fun commitTake(context: android.content.Context, pending: File, target: File): Boolean {
    val sandboxRoots = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
    return commitPendingMedia(pending, target) { replaced ->
        kotlin.concurrent.thread(name = "shred-replaced-take") { shredFile(replaced, sandboxRoots) }
    }
}

/** How far the photo is enlarged, 1 being whole; see its use. */
internal val PhotoZoomScale = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("PhotoZoomScale")

/**
 * Shreds the media a note pointed at before a retake, when the retake wrote somewhere else.
 *
 * A capture always writes under the note's current id. An imported or restored note gets a fresh
 * id but keeps media named after its old one, so its first retake wrote a new file beside the old
 * and the old was referenced by nothing again -- left on disk for good, which for a video is a lot
 * of it. Off the main thread, since it may be exactly that video.
 */
internal fun shredReplacedMedia(context: android.content.Context, oldPath: String?, newFile: File) {
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
