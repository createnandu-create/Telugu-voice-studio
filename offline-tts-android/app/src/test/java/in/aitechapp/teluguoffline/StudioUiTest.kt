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
            compose.onNodeWithText("Raw voice").performScrollTo().performClick()
            compose.onNodeWithText("Original 22.05 kHz mono · no mastering").assertExists()
            compose.onNodeWithText("Studio master").performClick()
            compose.onNodeWithText("44.1 kHz stereo", substring = true).assertExists()
            org.junit.Assert.assertEquals(hash, FileIntegrity.sha256(raw))
        } finally {
            StudioJobService.result = null
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File("build/reports/studio-screenshots").apply { mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
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
        compose.onNodeWithText("Continue to direction →").performScrollTo().performClick()
        capture("03-direction")
        compose.runOnIdle {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[StudioViewModel::class.java]
                .step = 3
        }
        compose.onNodeWithText("Generate", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Play", substring = false).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Export", substring = false).assertIsDisplayed().assertIsNotEnabled()
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
