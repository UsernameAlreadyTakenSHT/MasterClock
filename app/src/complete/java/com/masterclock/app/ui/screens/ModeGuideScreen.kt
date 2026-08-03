package com.masterclock.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterclock.app.R

@Composable
fun ModeGuideScreen(onBack: () -> Unit) {
    ToolScaffold(
        title = stringResource(R.string.settings_more_manual),
        onBack = onBack
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            Text(
                stringResource(R.string.guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. SUDDEN DEATH
            EngineSection(stringResource(R.string.guide_section_sudden_death)) {
                EngineItem(stringResource(R.string.guide_sudden_death_standard), stringResource(R.string.guide_sudden_death_standard_desc))
            }

            // 2. BONUS (FISCHER / BRONSTEIN / US DELAY)
            EngineSection(stringResource(R.string.guide_section_bonus)) {
                EngineItem(stringResource(R.string.guide_bonus_fischer), stringResource(R.string.guide_bonus_fischer_desc))
                EngineItem(stringResource(R.string.guide_bonus_bronstein), stringResource(R.string.guide_bonus_bronstein_desc))
                EngineItem(stringResource(R.string.guide_bonus_us_delay), stringResource(R.string.guide_bonus_us_delay_desc))
            }

            // 3. MOVE TIMER
            EngineSection(stringResource(R.string.guide_section_move_timer)) {
                EngineItem(stringResource(R.string.guide_move_standard), stringResource(R.string.guide_move_standard_desc))
                EngineItem(stringResource(R.string.guide_move_save_cap), stringResource(R.string.guide_move_save_cap_desc))
                EngineItem(stringResource(R.string.guide_move_overtime), stringResource(R.string.guide_move_overtime_desc))
                EngineItem(stringResource(R.string.guide_move_global), stringResource(R.string.guide_move_global_desc))
                EngineItem(stringResource(R.string.guide_move_shared), stringResource(R.string.guide_move_shared_desc))
                EngineItem(stringResource(R.string.guide_move_global_shared), stringResource(R.string.guide_move_global_shared_desc))
            }

            // 4. BYOYOMI
            EngineSection(stringResource(R.string.guide_section_byoyomi)) {
                EngineItem(stringResource(R.string.guide_byoyomi_japanese), stringResource(R.string.guide_byoyomi_japanese_desc))
                EngineItem(stringResource(R.string.guide_byoyomi_canadian), stringResource(R.string.guide_byoyomi_canadian_desc))
                EngineItem(stringResource(R.string.guide_byoyomi_progressive), stringResource(R.string.guide_byoyomi_progressive_desc))
            }

            // 5. CHRONOS
            EngineSection(stringResource(R.string.guide_section_chronos)) {
                EngineItem(stringResource(R.string.guide_chrono_countdown), stringResource(R.string.guide_chrono_countdown_desc))
                EngineItem(stringResource(R.string.guide_chrono_countup), stringResource(R.string.guide_chrono_countup_desc))
                EngineItem(stringResource(R.string.guide_chrono_one_for_all), stringResource(R.string.guide_chrono_one_for_all_desc))
            }

            // 6. MOVE COUNTS
            EngineSection(stringResource(R.string.guide_section_move_counts)) {
                EngineItem(stringResource(R.string.guide_counts_up), stringResource(R.string.guide_counts_up_desc))
                EngineItem(stringResource(R.string.guide_counts_down), stringResource(R.string.guide_counts_down_desc))
            }

            // 7. SPECIALTY ENGINES
            EngineSection(stringResource(R.string.guide_section_specialty)) {
                EngineItem(stringResource(R.string.guide_hourglass), stringResource(R.string.guide_hourglass_desc))
                EngineItem(stringResource(R.string.guide_gong), stringResource(R.string.guide_gong_desc))
                EngineItem(stringResource(R.string.guide_fide), stringResource(R.string.guide_fide_desc))
                EngineItem(stringResource(R.string.guide_phases), stringResource(R.string.guide_phases_desc))
            }

            // 8. EXPERIMENTAL & OMNI
            EngineSection(stringResource(R.string.guide_section_advanced)) {
                EngineItem(stringResource(R.string.guide_random), stringResource(R.string.guide_random_desc))
                EngineItem(stringResource(R.string.guide_hidden), stringResource(R.string.guide_hidden_desc))
                EngineItem(stringResource(R.string.guide_fast_move), stringResource(R.string.guide_fast_move_desc))
                EngineItem(stringResource(R.string.guide_omni), stringResource(R.string.guide_omni_desc))
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun EngineSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // MasterClock Label Style: Simple, bold, noir et blanc
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun EngineItem(name: String, description: String) {
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}
