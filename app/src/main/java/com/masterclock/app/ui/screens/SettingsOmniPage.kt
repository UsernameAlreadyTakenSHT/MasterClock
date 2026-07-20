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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterclock.app.logic.*
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

    fun validatePhases(): Boolean {
        if (!settings.usePhaseClock) return true
        
        settings.games.forEach { game ->
            game.rounds.forEach { round ->
                val turns = if (round.customTurns.isNotEmpty()) round.customTurns else listOf(OmniTurnSettings(durationMs = round.turnDurationMs))
                turns.forEachIndexed { tIdx, turn ->
                    val totalPhasesMs = turn.phases.sumOf { it.durationMs }
                    if (totalPhasesMs > turn.durationMs) {
                        val turnLabel = if (turns.size > 1) "Turn ${tIdx + 1}" else "Turn"
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Game '${game.name}', Round '${round.name}', $turnLabel: Phases exceed Turn time!",
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
        containerColor = Color.Transparent
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
            Text("Step $currentStep/$totalSteps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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
                    Text("Previous")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentStep < totalSteps) {
                Button(onClick = onNext, shape = RoundedCornerShape(12.dp)) {
                    Text("Next Step")
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
                    Text("Launch Game")
                }
            }
        }
    }
}

@Composable
fun StepPlayersAndOrder(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text("Players & Order", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Players Count") {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Count: ${settings.numberOfPlayers}", fontWeight = FontWeight.Bold)
            Slider(
                value = settings.numberOfPlayers.toFloat(),
                onValueChange = { onSettingsChanged(settings.copy(numberOfPlayers = it.toInt())) },
                valueRange = 1f..20f,
                steps = 19
            )
        }
    }

    SettingsSection("Turn Order") {
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
                PlayerOrderType.LINEAR -> "Pattern (3 players): 123, 123, 123"
                PlayerOrderType.SNAKE -> "Pattern (3 players): 123, 321, 123"
                PlayerOrderType.ROTATE -> "Pattern (3 players): 123, 231, 312"
                PlayerOrderType.RANDOM -> "Pattern: Completely random every round"
            }
            Text(
                text = exampleText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun StepSessionAndGames(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text("Session & Games", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    SettingsSection("Session") {
        Column {
            BehaviorSwitch("Enable Session Timer", settings.useGlobalClock, topRounded = true, bottomRounded = !settings.useGlobalClock) { onSettingsChanged(settings.copy(useGlobalClock = it)) }
            if (settings.useGlobalClock) {
                Surface(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp), color = Color.Transparent) {
                    HMSInput("Total Duration", settings.globalDurationMs) { onSettingsChanged(settings.copy(globalDurationMs = it)) }
                }
                BehaviorSwitch("Force cutoff when session time runs out", settings.globalForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(globalForcesCutoff = it)) }
            }
        }
    }

    SettingsSection("Game Timer") {
        BehaviorSwitch("Enable Game Timer", settings.useGameClock, topRounded = true, bottomRounded = !settings.useGameClock) { onSettingsChanged(settings.copy(useGameClock = it)) }
        if (settings.useGameClock) {
            BehaviorSwitch("Force cutoff when game time runs out", settings.gameForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(gameForcesCutoff = it)) }
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
                        Text("Game ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (settings.games.size > 1) {
                            IconButton(onClick = {
                                val newList = settings.games.toMutableList().apply { removeAt(index) }
                                onSettingsChanged(settings.copy(games = newList))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    
                    OutlinedTextField(
                        value = game.name,
                        onValueChange = { onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply { this[index] = game.copy(name = it) })) },
                        placeholder = { Text("Game Name") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (settings.useGameClock) {
                        HMSInput("Duration", game.durationMs) { duration ->
                            onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply { this[index] = game.copy(durationMs = duration) }))
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = {
                val last = settings.games.lastOrNull() ?: OmniGameSettings()
                val nextGame = last.copy(id = java.util.UUID.randomUUID().toString(), name = "Game ${settings.games.size + 1}")
                onSettingsChanged(settings.copy(games = settings.games + nextGame))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Game")
        }
    }
}

@Composable
fun StepRounds(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text("Rounds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Round Timer") {
        BehaviorSwitch("Enable Round Timer", settings.useRoundClock, topRounded = true, bottomRounded = !settings.useRoundClock) { onSettingsChanged(settings.copy(useRoundClock = it)) }
        if (settings.useRoundClock) {
            BehaviorSwitch("Force cutoff when round time runs out", settings.roundForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(roundForcesCutoff = it)) }
        }
    }

    var selectedGameIdx by remember { mutableIntStateOf(0) }
    val currentGame = settings.games.getOrNull(selectedGameIdx) ?: settings.games.first()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.games.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedGameIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                settings.games.forEachIndexed { index, game ->
                    Tab(selected = selectedGameIdx == index, onClick = { selectedGameIdx = index }) {
                        Text(game.name.ifBlank { "G${index + 1}" }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
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
                        Text("Round ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (currentGame.rounds.size > 1) {
                            IconButton(onClick = {
                                val newList = currentGame.rounds.toMutableList().apply { removeAt(index) }
                                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                                    this[selectedGameIdx] = currentGame.copy(rounds = newList)
                                }))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    
                    OutlinedTextField(
                        value = round.name,
                        onValueChange = { onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                            this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply { this[index] = round.copy(name = it) })
                        })) },
                        placeholder = { Text("Round Name") },
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
                val nextRound = last.copy(id = java.util.UUID.randomUUID().toString(), name = "Round ${currentGame.rounds.size + 1}")
                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                    this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds + nextRound)
                }))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Round")
        }
    }
}

@Composable
fun StepTurns(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text("Turns", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Turn Timer") {
        BehaviorSwitch("Enable Turn Timer", settings.useTurnClock, topRounded = true, bottomRounded = !settings.useTurnClock) { onSettingsChanged(settings.copy(useTurnClock = it)) }
        if (settings.useTurnClock) {
            BehaviorSwitch("Force cutoff when turn time runs out", settings.turnForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(turnForcesCutoff = it)) }
        }
    }

    if (!settings.useTurnClock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Turn clock disabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(game.name.ifBlank { "G${index + 1}" }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (currentGame.rounds.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedRoundIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                currentGame.rounds.forEachIndexed { index, round ->
                    Tab(selected = selectedRoundIdx == index, onClick = { selectedRoundIdx = index }) {
                        Text(round.name.ifBlank { "R${index + 1}" }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
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
                    label = { Text(if (behavior == RoundEndBehavior.ADVANCE) "End Round" else "Loop Turns", style = MaterialTheme.typography.labelSmall) }
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
                        Text("Turn ${tIdx + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (turns.size > 1) {
                            IconButton(onClick = {
                                val newList = turns.toMutableList().apply { removeAt(tIdx) }
                                onSettingsChanged(settings.copy(games = settings.games.toMutableList().apply {
                                    this[selectedGameIdx] = currentGame.copy(rounds = currentGame.rounds.toMutableList().apply {
                                        this[selectedRoundIdx] = currentRound.copy(customTurns = newList, turnLogic = if (newList.size <= 1) RoundTurnLogic.FIXED else RoundTurnLogic.SEQUENCE)
                                    })
                                }))
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
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
            Text("Add Turn")
        }
    }
}

@Composable
fun StepPhases(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit) {
    Text("Phases", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Phase Timer") {
        BehaviorSwitch("Enable Phase Timer", settings.usePhaseClock, topRounded = true, bottomRounded = !settings.usePhaseClock) { onSettingsChanged(settings.copy(usePhaseClock = it)) }
        if (settings.usePhaseClock) {
            BehaviorSwitch("Force cutoff when phase time runs out", settings.phaseForcesCutoff, bottomRounded = true) { onSettingsChanged(settings.copy(phaseForcesCutoff = it)) }
        }
    }

    if (!settings.usePhaseClock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Phase clock disabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(game.name.ifBlank { "G${index + 1}" }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (currentGame.rounds.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedRoundIdx, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                currentGame.rounds.forEachIndexed { index, round ->
                    Tab(selected = selectedRoundIdx == index, onClick = { selectedRoundIdx = index }) {
                        Text(round.name.ifBlank { "R${index + 1}" }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (turnsList.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTurnIdx, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), divider = {}) {
                turnsList.forEachIndexed { index, _ ->
                    Tab(selected = selectedTurnIdx == index, onClick = { selectedTurnIdx = index }) {
                        Text("T${index + 1}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
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
                            "Total: ${totalPhasesMs/1000}s / Limit: ${currentTurn.durationMs/1000}s",
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
                                placeholder = { Text("Phase Name") },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            if (phases.size > 1) {
                                IconButton(onClick = {
                                    val newPhases = phases.toMutableList().apply { removeAt(pIdx) }
                                    updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, newPhases, onSettingsChanged)
                                }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
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
                    val newPhase = OmniPhaseSettings(id = java.util.UUID.randomUUID().toString(), name = "Phase ${phases.size + 1}")
                    updateReplicatedPhases(settings, selectedGameIdx, selectedRoundIdx, selectedTurnIdx, phases + newPhase, onSettingsChanged)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Phase", style = MaterialTheme.typography.labelSmall)
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
    Text("Advanced Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Transitions & Pauses") {
        Column {
            val pausesEnabled = settings.interTurnPauseMs > 0 || settings.interRoundPauseMs > 0 || settings.interGamePauseMs > 0
            BehaviorSwitch(
                label = "Enable Pauses (Buffers)",
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
                    label = "Automatic Restart",
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
        SettingsSection("Pauses Durations") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HMSInput("Turn Pause", settings.interTurnPauseMs) { onSettingsChanged(settings.copy(interTurnPauseMs = it)) }
                HMSInput("Round Pause", settings.interRoundPauseMs) { onSettingsChanged(settings.copy(interRoundPauseMs = it)) }
                HMSInput("Game Pause", settings.interGamePauseMs) { onSettingsChanged(settings.copy(interGamePauseMs = it)) }
            }
        }
    }

    SettingsSection("Pressure & Economy") {
        Column {
            BehaviorSwitch("Pause deducts Global", settings.pauseDeductsFromGlobal, topRounded = true) { onSettingsChanged(settings.copy(pauseDeductsFromGlobal = it)) }
            BehaviorSwitch("Pause deducts Game", settings.pauseDeductsFromGame) { onSettingsChanged(settings.copy(pauseDeductsFromGame = it)) }
            BehaviorSwitch("Pause deducts Round", settings.pauseDeductsFromRound) { onSettingsChanged(settings.copy(pauseDeductsFromRound = it)) }
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Time Bank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                // Was a boolean switch that could only reach NONE/ACCUMULATIVE -- GLOBAL_RESERVE (a
                // single pool shared by every player, instead of one bank per player) was unreachable
                // from the UI. See AUDIT.md §7.1.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TimeBankMode.entries.forEachIndexed { index, mode ->
                        val label = when (mode) {
                            TimeBankMode.NONE -> "Off"
                            TimeBankMode.ACCUMULATIVE -> "Per-player"
                            TimeBankMode.GLOBAL_RESERVE -> "Shared pool"
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
        SettingsSection("Clear Bank at end of:") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TimeBankScope.entries.forEachIndexed { index, scope ->
                    val label = when(scope) {
                        TimeBankScope.TURN_TO_TURN -> "Turn"
                        TimeBankScope.ROUND_TO_ROUND -> "Round"
                        TimeBankScope.GAME_TO_GAME -> "Game"
                        TimeBankScope.SESSION_WIDE -> "Session"
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

    SettingsSection("Audio Alerts") {
        Column {
            BehaviorSwitch("Turn Beep", settings.soundTurnEnd, topRounded = true) { onSettingsChanged(settings.copy(soundTurnEnd = it)) }
            BehaviorSwitch("Round Gong", settings.soundRoundEnd) { onSettingsChanged(settings.copy(soundRoundEnd = it)) }
            BehaviorSwitch("Final Beep", settings.soundGameEnd, bottomRounded = true) { onSettingsChanged(settings.copy(soundGameEnd = it)) }
        }
    }
}

@Composable
fun StepFinalReview(settings: OmniSettings, onSettingsChanged: (OmniSettings) -> Unit, onPlay: () -> Unit) {
    Text("Ready to Play", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

    SettingsSection("Launch Countdown") {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            val options = listOf(0L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L)
            val currentMs = settings.launchCountdownMs
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, ms ->
                    val label = when(ms) {
                        0L -> "Off"; 10_000L -> "10s"; 30_000L -> "30s"; 60_000L -> "1m"; 120_000L -> "2m"; 300_000L -> "5m"; else -> ""
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
            Text("Summary", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text("• ${settings.numberOfPlayers} Players | ${settings.games.size} Games")
            // Was always games.firstOrNull()?.rounds?.size, presented as if every game were the same
            // even when games have different round counts -- see AUDIT.md §7.1.
            val roundCounts = settings.games.map { it.rounds.size }
            Text("• " + if (roundCounts.distinct().size <= 1) "${roundCounts.firstOrNull() ?: 0} Rounds per Game" else "Rounds per Game: ${roundCounts.joinToString(", ")}")
            Text("• Order: ${settings.playerOrderType}")
        }
    }

    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(12.dp)); Text("LAUNCH STATION", fontWeight = FontWeight.Black)
    }
}
