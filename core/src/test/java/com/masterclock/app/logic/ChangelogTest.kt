package com.masterclock.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The in-app changelog, build by build, and the release steps it depends on that nothing else checks:
 * RELEASING.md's list has the version, AppInfo and the five fastlane notes changed together.
 */
class ChangelogTest {

    private val root = File("..")

    private fun gradleValue(module: String, key: String): String =
        Regex("""$key\s*=\s*"?([^"\s]+)"?""").find(File(root, "$module/build.gradle.kts").readText())!!.groupValues[1]

    @Test
    fun `the latest entry is the version being built`() {
        assertEquals(gradleValue("app", "versionName"), AppInfo.CHANGELOG.first().version)
        assertEquals(gradleValue("paper", "versionName"), AppInfo.CHANGELOG.first().version + "-paper")
    }

    @Test
    fun `every build has its store notes for this version`() {
        val code = gradleValue("app", "versionCode")
        assertEquals(code, gradleValue("paper", "versionCode"))
        for (dir in listOf("fastlane", "fastlane-standard", "fastlane-lite", "fastlane-mini", "fastlane-paper")) {
            val notes = File(root, "$dir/metadata/android/en-US/changelogs/$code.txt")
            assertTrue("$notes is missing", notes.isFile)
            assertTrue("$notes is over F-Droid's 500 characters", notes.readText().length <= 500)
        }
    }

    @Test
    fun `every build has notes for the latest release`() {
        for (flavor in AppFlavor.entries) {
            assertEquals(flavor.name, AppInfo.CHANGELOG.first().version, AppInfo.changelogFor(flavor).first().version)
        }
    }

    @Test
    fun `a build reads only its own notes`() {
        val mini = AppInfo.changelogFor(AppFlavor.MINI).first().notes
        assertFalse(mini.any { "Berserk" in it })
        assertTrue(AppInfo.changelogFor(AppFlavor.COMPLETE).first().notes.any { "Berserk" in it })
        // 0.8.36 changed the E-Ink build alone.
        assertTrue(AppInfo.changelogFor(AppFlavor.E_INK).any { it.version == "0.8.36" })
        assertFalse(AppInfo.changelogFor(AppFlavor.COMPLETE).any { it.version == "0.8.36" })
    }
}
