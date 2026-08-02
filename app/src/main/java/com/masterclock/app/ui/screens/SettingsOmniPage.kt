package com.masterclock.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.R
import com.masterclock.app.logic.*
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsOmniPage(
    viewModel: OmniTimerViewModel,
    onPlay: () -> Unit
) {
    val settings by viewModel.omniSettings.collectAsState()
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 7 
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun onSettingsChanged(newSettings: OmniSettings) {
        viewModel.updateOmniSettings(newSettings)
    }

    // Hoisted: validatePhases is a plain local function, so it cannot call stringResource itself.
    // Fetching the raw templates here and formatting them below keeps the text translatable.
    val turnLabelSingle = stringResource(R.string.omni_cfg_turn)
    val turnLabelNumbered = stringResource(R.string.omni_cfg_turn_n)
    val phasesExceedTemplate = stringResource(R.string.omni_cfg_phases_exceed)

    fun validatePhases(): Boolean {
        if (!settings.usePhaseClock) return true
        
        settings.games.forEach { game ->
            game.rounds.forEach { round ->
                val turns = if (round.customTurns.isNotEmpty()) round.customTurns else listOf(OmniTurnSettings(durationMs = round.turnDurationMs))
                turns.forEachIndexed { tIdx, turn ->
                    val totalPhasesMs = turn.phases.sumOf { it.durationMs }
                    if (totalPhasesMs > turn.durationMs) {
                        val turnLabel = if (turns.size > 1) {
                            String.format(Locale.getDefault(), turnLabelNumbered, tIdx + 1)
                        } else turnLabelSingle
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = String.format(Locale.getDefault(), phasesExceedTemplate, game.name, round.name, turnLabel),
                                duration = SnackbarDuration.Short
                            )
                        }
                        return false
                    }
                }
            }
        }
        return true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WizardNavigationButtons(
                currentStep = currentStep,
                totalSteps = totalSteps,
                onBack = { if (currentStep > 1) currentStep-- },
                onNext = { 
                    if (currentStep == 5) {
                        if (validatePhases()) currentStep++
                    } else if (currentStep < totalSteps) {
                        currentStep++
                    }
                },
                onPlay = {
                    if (validatePhases()) onPlay()
                }
            )
        },
        containerColor = Color.Transparent,
        // This Scaffold is nested inside SettingsScreen's, which already applied the status-bar
        // and top-app-bar insets; without this it applies the top inset a second time and leaves
        // a large empty gap between the "Omni" title and the step progress bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            WizardProgressBar(currentStep, totalSteps)

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "WizardStepTransition"
                ) { step ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))
                        when (step) {
                            1 -> StepPlayersAndOrder(settings) { onSettingsChanged(it) }
                            2 -> StepSessionAndGames(settings) { onSettingsChanged(it) }
                            3 -> StepRounds(settings) { onSettingsChanged(it) }
                            4 -> StepTurns(settings) { onSettingsChanged(it) }
                            5 -> StepPhases(settings) { onSettingsChanged(it) }
                            6 -> StepAdvancedRules(settings) { onSettingsChanged(it) }
                            7 -> StepFinalReview(settings, { onSettingsChanged(it) }, onPlay)
                        }
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WizardProgressBar(currentStep: Int, totalSteps: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.omni_cfg_step, currentStep, totalSteps), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text("${(currentStep * 100 / totalSteps)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun WizardNavigationButtons(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPlay: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 1) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.omni_cfg_previous))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentStep < totalSteps) {
                Button(onClick = onNext, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.omni_cfg_next_step))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            } else {
                Button(
                    onClick = { onPlay() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.omni_cfg_launch_game))
                }
            }
        }
    }
}

@Composable
fun StepPlayersAndOrder(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text(stringResource(R.string.omni_cfg_players_order), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    // No "Players Count"/"Player Colors" section headers: the count line and the P1..Pn color
    // rows say what they are. The count doubles as the heading, so it takes the section-title
    // style SettingsSection would have used.
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            stringResource(R.string.omni_cfg_count, settings.numberOfPlayers),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Slider(
            value = settings.numberOfPlayers.toFloat(),
            onValueChange = { onSettingsChanged(settings.copy(numberOfPlayers = it.toInt())) },
            valueRange = 1f..20f,
            steps = 19
        )
    }

    // One row per player would push the rest of the step (Turn Order and its own settings) far
    // off-screen at high player counts, so the list caps at four visible rows and scrolls on its
    // own inside the page's scroll.
    val visibleColorRows = 4
    val colorRowHeight = 40.dp
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .heightIn(max = colorRowHeight * visibleColorRows)
            .verticalScroll(rememberScrollState())
    ) {
        repeat(settings.numberOfPlayers) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.omni_cfg_player_short, i + 1),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(omniPlayerColor(settings, i)),
                    modifier = Modifier.width(36.dp)
                )
                ColorRow(omniPlayerColor(settings, i), compact = true) { newColor ->
                    // Older stored settings may hold a shorter list than the defaults; pad
                    // from the defaults before writing the picked index.
                    val colors = settings.playerColors.toMutableList()
                    while (colors.size < OMNI_DEFAULT_PLAYER_COLORS.size) colors.add(OMNI_DEFAULT_PLAYER_COLORS[colors.size])
                    colors[i] = newColor
                    onSettingsChanged(settings.copy(playerColors = colors))
                }
            }
        }
    }

    SettingsSection(stringResource(R.string.omni_cfg_turn_order)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PlayerOrderType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = settings.playerOrderType == type,
                        onClick = { onSettingsChanged(settings.copy(playerOrderType = type)) },
                        shape = SegmentedButtonDefaults.itemShape(index, PlayerOrderType.entries.size),
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val exampleText = when(settings.playerOrderType) {
                PlayerOrderType.LINEAR -> stringResource(R.string.omni_cfg_pattern_linear)
                PlayerOrderType.SNAKE -> stringResource(R.string.omni_cfg_pattern_snake)
                PlayerOrderType.ROTATE -> stringResource(R.string.omni_cfg_pattern_rotate)
                PlayerOrderType.RANDOM ->
                    if (settings.randomEachTurn) "Pattern (3 players): 132, 312, 231 — a player can be skipped or picked twice"
                    else "Pattern (3 players): 231, 312, 123 — everyone plays once per round"
            }
            Text(
                text = exampleText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (settings.playerOrderType == PlayerOrderType.RANDOM) {
                Spacer(Modifier.height(12.dp))
                RandomOrderOptions(settings, onSettingsChanged)
            }
        }
    }
}

/** RANDOM-only sub-options, shown under the Turn Order row when that order is selected. */
@Composable
private fun RandomOrderOptions(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Column {
        val drawModes = listOf(false to stringResource(R.string.omni_cfg_shuffle_per_round), true to stringResource(R.string.omni_cfg_draw_per_turn))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            drawModes.forEachIndexed { index, (eachTurn, label) ->
                SegmentedButton(
                    selected = settings.randomEachTurn == eachTurn,
                    onClick = { onSettingsChanged(settings.copy(randomEachTurn = eachTurn)) },
                    shape = SegmentedButtonDefaults.itemShape(index, drawModes.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        BehaviorSwitch(
            label = stringResource(R.string.omni_cfg_no_repeat),
            checked = settings.randomAvoidBackToBack,
            enabled = settings.numberOfPlayers > 1,
            topRounded = true,
            bottomRounded = !settings.randomEachTurn
        ) { onSettingsChanged(settings.copy(randomAvoidBackToBack = it)) }

        // Balancing only applies to the per-turn draw: a per-round shuffle is already even by
        // construction, everyone taking exactly one turn.
        if (settings.randomEachTurn) {
            BehaviorSwitch(
                label = stringResource(R.string.omni_cfg_balance_draw),
                checked = settings.randomAutoBalance,
                bottomRounded = true
            ) { onSettingsChanged(settings.copy(randomAutoBalance = it)) }

            Text(
                text = if (settings.randomAutoBalance) {
                    stringResource(R.string.omni_cfg_balance_explain)
                } else {
                    "Every player is equally likely every turn — one of them really can take most of a round."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }
    }
}

@Composable
fun StepSessionAndGames(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    // Default name for a newly created game; hoisted because it is used from an onClick lambda.
    val newGameNameTemplate = stringResource(R.string.omni_cfg_game_n)
    Text(stringResource(R.string.omni_cfg_session_games), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    SettingsSection(stringResource(R.string.omni_cfg_session_timer)) {
        Column {
            BehaviorSwitch(stringResource(R.string.omni_cfg_enable_session_timer), settings.useGlobalClock, topRounded = true, bottomRounded = !settings.useGlobalClock) { onSettingsChanged(settings.copy(useGlobalClock = it)) }
            if (settings.useGlobalClock) {
                Surface(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp), color = Color.Transparent) {
                    HMSInput(stringResource(R.string.omni_cfg_total_duration), settings.globalDurationMs) { onSettingsChanged(settings.copy(globalDurationMs = it)) }
                }
                BehaviorSwitch(stringResource(R.string.omni_cfg_cutoff_session), settings.globalForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(globalForcesCutoff = it)) }
            }
        }
    }

    SettingsSection(stringResource(R.string.omni_cfg_game_timer)) {
        BehaviorSwitch(stringResource(R.string.omni_cfg_enable_game_timer), settings.useGameClock, topRounded = true, bottomRounded = !settings.useGameClock) { onSettingsChanged(settings.copy(useGameClock = it)) }
        if (settings.useGameClock) {
            BehaviorSwitch(stringResource(R.string.omni_cfg_cutoff_game), settings.gameForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(gameForcesCutoff = it)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        settings.games.forEachIndexed { index, game ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.omni_cfg_game_n, index + 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (settings.games.size > 1) {
                            IconButton(onClick = {
                                val newList = settings.games.toMutableList().apply { removeAt(index) }
                                onSettingsChanged(settings.copy(games = newList))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.omni_cfg_delete_game, index + 1), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    
                    OutlinedTextField(
                        value = game.name,
                        onValueChange = { onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply { this[index] = game.copy(name = it) })) },
                        placeholder = { Text(stringResource(R.string.omni_cfg_game_name)) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (settings.useGameClock) {
                        HMSInput(stringResource(R.string.omni_cfg_duration), game.durationMs) { duration ->
                            onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply { this[index] = game.copy(durationMs = duration) }))
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = {
                val last = settings.games.lastOrNull() ?: OmniGameSettings()
                val nextGame = last.copy(id = java.util.UUID.randomUUID().toString(), name = String.format(Locale.getDefault(), newGameNameTemplate, settings.games.size + 1))
                onSettingsChanged(settings.copy(games = settings.games + nextGame))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.omni_cfg_add_game))
        }
    }
}

@Composable
fun StepRounds(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    val newRoundNameTemplate = stringResource(R.string.omni_cfg_round_n)
    Text(stringResource(R.string.omni_cfg_rounds), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection(stringResource(R.string.omni_cfg_round_timer)) {
        BehaviorSwitch(stringResource(R.string.omni_cfg_enable_round_timer), settings.useRoundClock, topRounded = true, bottomRounded = !settings.useRoundClock) { onSettingsChanged(settings.copy(useRoundClock = it)) }
        if (settings.useRoundClock) {
            BehaviorSwitch(stringResource(R.string.omni_cfg_cutoff_round), settings.roundForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(roundForcesCutoff = it)) }
        }
    }

    var selectedGameIdx by remember { mutableIntStateOf(0) }
    val currentGame = settings.games.getOrNull(selectedGameIdx) ?: settings.games.first()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.games.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedGameIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                settings.games.forEachIndexed { index, game ->
                    Tab(selected = selectedGameIdx == index, onClick = { selectedGameIdx = index }) {
                        Text(game.name.ifBlank { stringResource(R.string.omni_cfg_game_short, index + 1) }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        currentGame.rounds.forEachIndexed { index, round ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.omni_cfg_round_n, index + 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (currentGame.rounds.size > 1) {
                            IconButton(onClick = {
                                val newList = currentGame.rounds.toMutableList().apply { removeAt(index) }
                                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                                    this[selectedGameIdx] = currentGame.copy(rounds = newList)
                                }))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.omni_cfg_delete_round, index + 1), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    
                    OutlinedTextField(
                        value = round.name,
                        onValueChange = { onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                            this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply { this[index] = round.copy(name = it) })
                        })) },
                        placeholder = { Text(stringResource(R.string.omni_cfg_round_name)) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (settings.useRoundClock) {
                        HMSInput("", round.durationMs) { onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                            this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply { this[index] = round.copy(durationMs = it) })
                        })) }
                    }
                }
            }
        }

        Button(
            onClick = {
                val last = currentGame.rounds.lastOrNull() ?: OmniRoundSettings()
                val nextRound = last.copy(id = java.util.UUID.randomUUID().toString(), name = String.format(Locale.getDefault(), newRoundNameTemplate, currentGame.rounds.size + 1))
                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                    this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds + nextRound)
                }))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.omni_cfg_add_round))
        }
    }
}

@Composable
fun StepTurns(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text(stringResource(R.string.omni_cfg_turns), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection(stringResource(R.string.omni_cfg_turn_timer)) {
        BehaviorSwitch(stringResource(R.string.omni_cfg_enable_turn_timer), settings.useTurnClock, topRounded = true, bottomRounded = !settings.useTurnClock) { onSettingsChanged(settings.copy(useTurnClock = it)) }
        if (settings.useTurnClock) {
            BehaviorSwitch(stringResource(R.string.omni_cfg_cutoff_turn), settings.turnForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(turnForcesCutoff = it)) }
        }
    }

    if (!settings.useTurnClock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.omni_cfg_turn_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedGameIdx by remember { mutableIntStateOf(0) }
    var selectedRoundIdx by remember { mutableIntStateOf(0) }
    val currentGame = settings.games.getOrNull(selectedGameIdx) ?: settings.games.first()
    val currentRound = currentGame.rounds.getOrNull(selectedRoundIdx) ?: currentGame.rounds.first()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.games.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedGameIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                settings.games.forEachIndexed { index, game ->
                    Tab(selected = selectedGameIdx == index, onClick = { selectedGameIdx = index }) {
                        Text(game.name.ifBlank { stringResource(R.string.omni_cfg_game_short, index + 1) }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (currentGame.rounds.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedRoundIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                currentGame.rounds.forEachIndexed { index, round ->
                    Tab(selected = selectedRoundIdx == index, onClick = { selectedRoundIdx = index }) {
                        Text(round.name.ifBlank { stringResource(R.string.omni_cfg_round_short, index + 1) }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RoundEndBehavior.entries.forEachIndexed { index, behavior ->
                SegmentedButton(
                    selected = currentRound.roundEndBehavior == behavior,
                    onClick = {
                        onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                            this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply {
                                this[selectedRoundIdx] = currentRound.copy(roundEndBehavior = behavior)
                            })
                        }))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    label = { Text(if (behavior == RoundEndBehavior.ADVANCE) stringResource(R.string.omni_cfg_end_round) else stringResource(R.string.omni_cfg_loop_turns), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        val turns = if (currentRound.customTurns.isNotEmpty()) currentRound.customTurns else listOf(OmniTurnSettings(durationMs = currentRound.turnDurationMs))
        
        turns.forEachIndexed { tIdx, turn ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.omni_cfg_turn_n, tIdx + 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (turns.size > 1) {
                            IconButton(onClick = {
                                val newList = turns.toMutableList().apply { removeAt(tIdx) }
                                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                                    this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply {
                                        this[selectedRoundIdx] = currentRound.copy(customTurns = newList, turnLogic = if (newList.size <= 1) RoundTurnLogic.FIXED else RoundTurnLogic.SEQUENCE)
                                    })
                                }))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.omni_cfg_delete_turn, tIdx + 1), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    
                    HMSInput("", turn.durationMs) { duration ->
                        val newList = turns.toMutableList().apply { this[tIdx] = turn.copy(durationMs = duration) }
                        onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                            this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply {
                                this[selectedRoundIdx] = currentRound.copy(customTurns = newList, turnLogic = if (newList.size <= 1) RoundTurnLogic.FIXED else RoundTurnLogic.SEQUENCE)
                            })
                        }))
                    }
                }
            }
        }

        Button(
            onClick = {
                val last = turns.lastOrNull() ?: OmniTurnSettings(durationMs = currentRound.turnDurationMs)
                val nextTurn = last.copy(id = java.util.UUID.randomUUID().toString())
                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                    this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply {
                        this[selectedRoundIdx] = currentRound.copy(customTurns = turns + nextTurn, turnLogic = RoundTurnLogic.SEQUENCE)
                    })
                }))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.omni_cfg_add_turn))
        }
    }
}

@Composable
fun StepPhases(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    val newPhaseNameTemplate = stringResource(R.string.omni_cfg_phase_n)
    Text(stringResource(R.string.omni_cfg_phases), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection(stringResource(R.string.omni_cfg_phase_timer)) {
        BehaviorSwitch(stringResource(R.string.omni_cfg_enable_phase_timer), settings.usePhaseClock, topRounded = true, bottomRounded = !settings.usePhaseClock) { onSettingsChanged(settings.copy(usePhaseClock = it)) }
        if (settings.usePhaseClock) {
            BehaviorSwitch(stringResource(R.string.omni_cfg_cutoff_phase), settings.phaseForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(phaseForcesCutoff = it)) }
        }
    }

    if (!settings.usePhaseClock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.omni_cfg_phase_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedGameIdx by remember { mutableIntStateOf(0) }
    var selectedRoundIdx by remember { mutableIntStateOf(0) }
    var selectedTurnIdx by remember { mutableIntStateOf(0) }
    
    val currentGame = settings.games.getOrNull(selectedGameIdx) ?: settings.games.first()
    val currentRound = currentGame.rounds.getOrNull(selectedRoundIdx) ?: currentGame.rounds.first()
    val turnsList = if (currentRound.turnLogic == RoundTurnLogic.SEQUENCE) currentRound.customTurns else listOf(OmniTurnSettings(durationMs = currentRound.turnDurationMs))
    val currentTurn = turnsList.getOrNull(selectedTurnIdx) ?: turnsList.firstOrNull() ?: OmniTurnSettings()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.games.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedGameIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                settings.games.forEachIndexed { index, game ->
                    Tab(selected = selectedGameIdx == index, onClick = { selectedGameIdx = index }) {
                        Text(game.name.ifBlank { stringResource(R.string.omni_cfg_game_short, index + 1) }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (currentGame.rounds.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedRoundIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                currentGame.rounds.forEachIndexed { index, round ->
                    Tab(selected = selectedRoundIdx == index, onClick = { selectedRoundIdx = index }) {
                        Text(round.name.ifBlank { stringResource(R.string.omni_cfg_round_short, index + 1) }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (turnsList.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTurnIdx, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), divider = {}) {
                turnsList.forEachIndexed { index, _ ->
                    Tab(selected = selectedTurnIdx == index, onClick = { selectedTurnIdx = index }) {
                        Text(stringResource(R.string.omni_cfg_turn_short, index + 1), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        val phases = currentTurn.phases
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalPhasesMs = phases.sumOf { it.durationMs }
            val isOverTime = totalPhasesMs > currentTurn.durationMs
            
            if (isOverTime) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.omni_cfg_phase_budget, totalPhasesMs / 1000, currentTurn.durationMs / 1000),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            phases.forEachIndexed { pIdx, phase ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = phase.name,
                                onValueChange = { name ->
                                    val newPhases = phases.toMutableList().apply { this[pIdx] = phase.copy(name = name) }
                                    updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, newPhases, onSettingsChanged)
                                },
                                placeholder = { Text(stringResource(R.string.omni_cfg_phase_name)) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            if (phases.size > 1) {
                                IconButton(onClick = {
                                    val newPhases = phases.toMutableList().apply { removeAt(pIdx) }
                                    updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, newPhases, onSettingsChanged)
                                }) { Icon(Icons.Default.Delete, stringResource(R.string.omni_cfg_delete_phase, pIdx + 1), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            }
                        }
                        HMSInput("", phase.durationMs) { duration ->
                            val newPhases = phases.toMutableList().apply { this[pIdx] = phase.copy(durationMs = duration) }
                            updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, newPhases, onSettingsChanged)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val newPhase = OmniPhaseSettings(id = java.util.UUID.randomUUID().toString(), name = String.format(Locale.getDefault(), newPhaseNameTemplate, phases.size + 1))
                    updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, phases + newPhase, onSettingsChanged)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.omni_cfg_add_phase), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Updates phases and ensures the change is replicated to ALL players for the given turn index.
 */
private fun updateReplicatedPhases(
    settings: OmniSettings,
    gIdx: Int,
    rIdx: Int,
    tIdx: Int,
    newPhases: List<OmniPhaseSettings>,
    onSettingsChanged: (OmniSettings) -> Unit
) {
    val game = settings.games.getOrNull(gIdx) ?: return
    val round = game.rounds.getOrNull(rIdx) ?: return
    
    // Ensure we are working with the actual turn list (custom or synthesized)
    val turns = if (round.customTurns.isNotEmpty()) {
        round.customTurns.toMutableList()
    } else {
        mutableListOf(OmniTurnSettings(durationMs = round.turnDurationMs))
    }
    
    if (tIdx < turns.size) {
        turns[tIdx] = turns[tIdx].copy(phases = newPhases)
    }
    
    val updatedRound = if (round.customTurns.isNotEmpty()) {
        round.copy(customTurns = turns)
    } else {
        // If we added phases to a fixed turn, it must become a custom sequence
        round.copy(customTurns = turns, turnLogic = RoundTurnLogic.SEQUENCE)
    }
    
    val newRounds = game.rounds.toMutableList().apply { this[rIdx] = updatedRound }
    val newGames = settings.games.toMutableList().apply { this[gIdx] = game.copy(rounds = newRounds) }
    onSettingsChanged(settings.copy(games = newGames))
}

@Composable
fun StepAdvancedRules(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text(stringResource(R.string.omni_cfg_advanced_rules), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection(stringResource(R.string.omni_cfg_transitions)) {
        Column {
            val pausesEnabled = settings.interTurnPauseMs > 0 || settings.interRoundPauseMs > 0 || settings.interGamePauseMs > 0
            BehaviorSwitch(
                label = stringResource(R.string.omni_cfg_enable_pauses),
                checked = pausesEnabled,
                topRounded = true,
                bottomRounded = !pausesEnabled
            ) { enabled ->
                if (enabled) {
                    onSettingsChanged(settings.copy(
                        interTurnPauseMs = 5_000L,
                        interRoundPauseMs = 60_000L,
                        interGamePauseMs = 300_000L
                    ))
                } else {
                    onSettingsChanged(settings.copy(
                        interTurnPauseMs = 0L,
                        interRoundPauseMs = 0L,
                        interGamePauseMs = 0L
                    ))
                }
            }

            if (pausesEnabled) {
                BehaviorSwitch(
                    label = stringResource(R.string.omni_cfg_auto_restart),
                    checked = settings.transitionType == TransitionType.AUTOMATIC,
                    topRounded = false,
                    bottomRounded = true
                ) { auto ->
                    onSettingsChanged(settings.copy(
                        transitionType = if (auto) TransitionType.AUTOMATIC else TransitionType.MANUAL_READY
                    ))
                }
            }
        }
    }

    if (settings.interTurnPauseMs > 0 || settings.interRoundPauseMs > 0 || settings.interGamePauseMs > 0) {
        SettingsSection(stringResource(R.string.omni_cfg_pauses_durations)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HMSInput(stringResource(R.string.omni_cfg_turn_pause), settings.interTurnPauseMs) { onSettingsChanged(settings.copy(interTurnPauseMs = it)) }
                HMSInput(stringResource(R.string.omni_cfg_round_pause), settings.interRoundPauseMs) { onSettingsChanged(settings.copy(interRoundPauseMs = it)) }
                HMSInput(stringResource(R.string.omni_cfg_game_pause), settings.interGamePauseMs) { onSettingsChanged(settings.copy(interGamePauseMs = it)) }
            }
        }
    }

    SettingsSection(stringResource(R.string.omni_cfg_pressure)) {
        Column {
            BehaviorSwitch(stringResource(R.string.omni_cfg_pause_deducts_global), settings.pauseDeductsFromGlobal, topRounded = true) { onSettingsChanged(settings.copy(pauseDeductsFromGlobal = it)) }
            BehaviorSwitch(stringResource(R.string.omni_cfg_pause_deducts_game), settings.pauseDeductsFromGame) { onSettingsChanged(settings.copy(pauseDeductsFromGame = it)) }
            BehaviorSwitch(stringResource(R.string.omni_cfg_pause_deducts_round), settings.pauseDeductsFromRound) { onSettingsChanged(settings.copy(pauseDeductsFromRound = it)) }
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.omni_cfg_time_bank), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                // Was a boolean switch that could only reach NONE/ACCUMULATIVE -- GLOBAL_RESERVE (a
                // single pool shared by every player, instead of one bank per player) was unreachable
                // from the UI. See AUDIT.md §7.1.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TimeBankMode.entries.forEachIndexed { index, mode ->
                        val label = when (mode) {
                            TimeBankMode.NONE -> stringResource(R.string.common_off)
                            TimeBankMode.ACCUMULATIVE -> stringResource(R.string.omni_cfg_bank_per_player)
                            TimeBankMode.GLOBAL_RESERVE -> stringResource(R.string.omni_cfg_bank_shared)
                        }
                        SegmentedButton(
                            selected = settings.timeBankMode == mode,
                            onClick = { onSettingsChanged(settings.copy(timeBankMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, TimeBankMode.entries.size),
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }

    if (settings.timeBankMode != TimeBankMode.NONE) {
        SettingsSection(stringResource(R.string.omni_cfg_bank_clear)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TimeBankScope.entries.forEachIndexed { index, scope ->
                    val label = when(scope) {
                        TimeBankScope.TURN_TO_TURN -> stringResource(R.string.omni_cfg_turn)
                        TimeBankScope.ROUND_TO_ROUND -> stringResource(R.string.omni_cfg_round)
                        TimeBankScope.GAME_TO_GAME -> stringResource(R.string.omni_cfg_game)
                        TimeBankScope.SESSION_WIDE -> stringResource(R.string.omni_cfg_session_timer)
                    }
                    SegmentedButton(
                        selected = settings.timeBankScope == scope,
                        onClick = { onSettingsChanged(settings.copy(timeBankScope = scope)) },
                        shape = SegmentedButtonDefaults.itemShape(index, TimeBankScope.entries.size),
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }

    SettingsSection(stringResource(R.string.omni_cfg_audio)) {
        Column {
            BehaviorSwitch(stringResource(R.string.omni_cfg_turn_beep), settings.soundTurnEnd, topRounded = true) { onSettingsChanged(settings.copy(soundTurnEnd = it)) }
            BehaviorSwitch(stringResource(R.string.omni_cfg_round_gong), settings.soundRoundEnd) { onSettingsChanged(settings.copy(soundRoundEnd = it)) }
            BehaviorSwitch(stringResource(R.string.omni_cfg_final_beep), settings.soundGameEnd, bottomRounded = true) { onSettingsChanged(settings.copy(soundGameEnd = it)) }
        }
    }
}

@Composable
fun StepFinalReview(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit, onPlay: () -> Unit) {
    Text(stringResource(R.string.omni_cfg_ready), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection(stringResource(R.string.omni_cfg_launch_countdown)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            val options = listOf(0L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L)
            val currentMs = settings.launchCountdownMs
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, ms ->
                    val label = when(ms) {
                        0L -> stringResource(R.string.common_off); 10_000L -> stringResource(R.string.dur_10s); 30_000L -> stringResource(R.string.dur_30s); 60_000L -> stringResource(R.string.dur_1m); 120_000L -> stringResource(R.string.dur_2m); 300_000L -> stringResource(R.string.dur_5m); else -> ""
                    }
                    SegmentedButton(
                        selected = currentMs == ms,
                        onClick = { onSettingsChanged(settings.copy(launchCountdownMs = ms)) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.omni_cfg_summary), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.omni_cfg_summary_games, settings.games.size, settings.numberOfPlayers, settings.games.size))
            // Was always games.firstOrNull()?.rounds?.size, presented as if every game were the same
            // even when games have different round counts -- see AUDIT.md §7.1.
            val roundCounts = settings.games.map { it.rounds.size }
            Text(if (roundCounts.distinct().size <= 1) pluralStringResource(R.plurals.omni_cfg_summary_rounds_uniform, roundCounts.firstOrNull() ?: 0, roundCounts.firstOrNull() ?: 0) else stringResource(R.string.omni_cfg_summary_rounds_varied, roundCounts.joinToString(", ")))
            Text(stringResource(R.string.omni_cfg_summary_order, settings.playerOrderType.toString()))
        }
    }

    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.omni_cfg_launch_station), fontWeight = FontWeight.Black)
    }
}
