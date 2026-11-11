package `in`.aitechapp.teluguoffline

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioUiTest {
    @get:Rule val compose = createAndroidComposeRule<StudioActivity>()

    @Test
    fun rawAndMasterSelectionKeepsOriginalAudio() {
        val directory = File(compose.activity.filesDir, "fixture-master").apply { mkdirs() }
        val raw = File(directory, "narration.wav")
        compose.activity.assets.open("comparison/piper-venkatesh-2.wav").use { input ->
            raw.outputStream().use { input.copyTo(it) }
        }
        val hash = FileIntegrity.sha256(raw)
        val master = File(directory, "narration-studio.wav")
        StudioMastering.process(raw, master, java.util.concurrent.atomic.AtomicBoolean(), {})
        try {
            compose.runOnIdle {
                val vm =
                    androidx.lifecycle
                        .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                StudioJobService.result = master
                vm.navigate("Studio")
                vm.step = 3
                vm.latest = master
            }
            compose.onNodeWithText("Audio options").performScrollTo().performClick()
            compose
                .onNodeWithText("Original · no sound processing")
                .performScrollTo()
                .performClick()
            compose.onNodeWithText("Original engine audio selected").assertExists()
            compose.onNodeWithText("Processed · balanced volume").performScrollTo().performClick()
            compose.onNodeWithText("Processed audio selected", substring = true).assertExists()
            org.junit.Assert.assertEquals(hash, FileIntegrity.sha256(raw))
        } finally {
            StudioJobService.result = null
        }
    }

    @Test(timeout = 60000)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun saveControlsAndNamedProgressRemainVisibleOnNarrowPhone() {
        val file = File(compose.activity.filesDir, "export-fixture.wav")
        compose.activity.assets.open("comparison/indic-tiny-male-2.wav").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        try {
            compose.runOnIdle {
                val vm =
                    androidx.lifecycle
                        .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                StudioJobService.result = file
                vm.latest = file
                vm.step = 3
            }
            compose.onNodeWithText("Save audio").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Play / Pause").assertIsDisplayed()
            capture("07-narrow-save")
            compose.onNodeWithText("Save audio").performClick()
            compose.onNodeWithText("Save WAV · full quality").assertIsDisplayed()
            compose.onNodeWithText("Save MP3 · 320 kbps").assertIsDisplayed()
            capture("08-save-formats")
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle {
                val vm =
                    androidx.lifecycle
                        .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                vm.exportBusy = true
                vm.exportProgress = .42f
                vm.exportMessage = "Creating MP3 on your phone…"
            }
            compose.onNodeWithText("42%").assertIsDisplayed()
            compose.onNodeWithText("Cancel save").assertIsDisplayed()
            capture("09-save-progress")
            compose.runOnIdle {
                val vm =
                    androidx.lifecycle
                        .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                vm.exportBusy = false
                vm.exportedName = "My tutorial.mp3"
                vm.exportedUri = android.net.Uri.parse("content://test/audio")
            }
            compose.onNodeWithText("My tutorial.mp3").assertIsDisplayed()
            compose.onNodeWithText("Open").assertIsDisplayed()
            compose.onNodeWithText("Share").assertIsDisplayed()
            compose.onNodeWithText("Got it").assertDoesNotExist()
            capture("10-saved-file")
        } finally {
            StudioJobService.result = null
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File("build/reports/studio-screenshots").apply { mkdirs() }
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view = if(dialog?.isShowing == true) dialog.window!!.decorView else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test(timeout = 60000)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w393dp-h873dp-xhdpi")
    fun nativeScreensAndCompactPlayerAreVisible() {
        compose.onNodeWithContentDescription("Studio tab").performClick()
        capture("01-studio")
        compose.onNodeWithText("Continue to voices →").performScrollTo().performClick()
        capture("02-narrators")
        compose.onNodeWithTag("studio-list").performScrollToNode(hasText("Continue to direction →"))
        compose.onNodeWithText("Continue to direction →").performClick()
        capture("03-direction")
        compose.runOnIdle {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                .step = 3
        }
        compose.onNodeWithText("Generate narration", substring = false).assertIsDisplayed()
        compose
            .onNodeWithText("Play / Pause", substring = false)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose
            .onNodeWithText("Save audio", substring = false)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        capture("04-audio")
        compose.onNodeWithContentDescription("Voices tab").performClick()
        capture("05-voices")
        compose.onNodeWithContentDescription("Settings tab").performClick()
        capture("06-settings")
    }

    @Test
    fun modelsAreUnderSettingsAndNavigationPreservesScript() {
        compose.onNodeWithContentDescription("Settings tab").performClick()
        compose.onNodeWithText("Offline models").assertIsDisplayed().performClick()
        compose.onNodeWithText("Download once. Keep creating without a connection.").assertExists()
        compose.onNodeWithContentDescription("Models tab").assertDoesNotExist()
        compose.onNodeWithContentDescription("Studio tab").performClick()
        compose.onNodeWithText("SCRIPT EDITOR").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h873dp-xhdpi")
    fun voicesExposeConsentAndNoCloneClaim() {
        compose.onNodeWithContentDescription("Voices tab").performClick()
        compose.onNodeWithText("New voice recording").performClick()
        compose.onNodeWithText("Start recording").assertIsNotEnabled()
        compose.onNodeWithText("Reference recording only. Cloning is not installed.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Projects tab").performClick()
        compose.onNodeWithText("Your projects").assertExists()
    }
}
