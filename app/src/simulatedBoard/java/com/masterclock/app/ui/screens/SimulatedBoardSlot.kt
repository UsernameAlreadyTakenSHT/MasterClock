package com.masterclock.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.BoardFrameLog
import com.masterclock.app.logic.ChessTimerViewModel
import com.masterclock.app.logic.ConnectionState
import com.masterclock.app.logic.SimulatedBoard
import com.masterclock.app.logic.SimulatedBoardManager
import kotlinx.coroutines.flow.StateFlow

/**
 * The boards that are not there, in the debug build, which is the only one that holds them: one of
 * each make, playing a short game, and a log of a real board played back. The simulator lives in
 * `core`'s debug sources and this in a directory only the debug build compiles; the builds anyone
 * installs have the other `SimulatedBoardSlot`, which is nothing at all.
 *
 * In English whatever the language, as nothing a player reads.
 */
object SimulatedBoardSlot {

    /** One for the app, as the real links' managers are one for the ViewModel: a board outlives its screen. */
    val manager = SimulatedBoardManager()

    val connectionState: StateFlow<ConnectionState> get() = manager.connectionState
    val lastMove: StateFlow<String?> get() = manager.lastMove

    fun disconnect() = manager.disconnect()

    /** As much of a log file as is read: a full log is about two megabytes. */
    private const val MAX_LOG_FILE_BYTES = 4 * 1024 * 1024

    @Composable
    fun Chips(viewModel: ChessTimerViewModel, enabled: Boolean, beforeConnect: () -> Unit) {
        val context = LocalContext.current
        // What a simulated board says is written down as a real one's is.
        manager.frameLog = viewModel.boardFrameLog

        val pickLog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            // A file from anywhere: read to a bound, and parse() takes of it only what is a frame.
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = ByteArray(MAX_LOG_FILE_BYTES)
                    var size = 0
                    while (size < bytes.size) {
                        val read = input.read(bytes, size, bytes.size - size)
                        if (read <= 0) break
                        size += read
                    }
                    String(bytes, 0, size, Charsets.UTF_8)
                }
            }.getOrNull().orEmpty()
            beforeConnect()
            if (!manager.replay(BoardFrameLog.parse(text)) { viewModel.recordBoardMove(it) }) {
                Toast.makeText(context, "Nothing in this file from a board the app decodes", Toast.LENGTH_LONG).show()
            }
        }

        Text("Simulated boards (debug build only)", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("simulated_boards"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SimulatedBoard.makes.forEach { make ->
                AssistChip(
                    onClick = { beforeConnect(); manager.connect(make) { viewModel.recordBoardMove(it) } },
                    enabled = enabled,
                    label = { Text(make.name) },
                )
            }
            AssistChip(onClick = { pickLog.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = enabled, label = { Text("Replay a log") })
        }
        Spacer(Modifier.height(16.dp))
    }
}
