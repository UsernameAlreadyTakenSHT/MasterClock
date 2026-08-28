package com.masterclock.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Stand-in for the rules screen in the builds that do not ship it.
 *
 * The real one lives in `src/complete` and is the only thing in the project that references the ten
 * bundled rulebook PDFs. Those PDFs are 9.3 MB of a 13.7 MB APK, and the resource shrinker keeps
 * them for as long as *something* compiled into the build points at them -- which, while
 * RulesScreen sat in `src/main`, meant all four flavors. Moving it here lets Standard, Lite and
 * Mini drop the documents they could never open, exactly as the paper build already does.
 *
 * This is unreachable in practice: Route.Rules is only ever navigated to from SettingsMorePage,
 * which renders behind FlavorConfig.hasMoreTab() and is therefore COMPLETE only. Keeping the same
 * signature as the real screen is what lets MainActivity register the route unconditionally, so no
 * flavor-specific surgery on the navigation graph is needed. It backs out immediately rather than
 * showing a blank page, so a future entry point added by mistake strands nobody.
 */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}
