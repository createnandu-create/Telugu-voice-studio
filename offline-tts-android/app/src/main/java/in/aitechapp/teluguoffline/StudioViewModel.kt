package `in`.aitechapp.teluguoffline

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject

data class ModelRow(
    val index: Int,
    val name: String,
    val gender: String,
    val ready: Boolean,
    val partial: Long,
    val damaged: Boolean,
)

data class ProfileRow(
    val id: String,
    val name: String,
    val label: String,
    val description: String,
    val duration: Long,
    val status: String,
    val file: File,
)

data class ProjectRow(val folder: File, val name: String, val ready: Boolean, val duration: Double)

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("StudioActivity", 0)
    var tab by mutableStateOf("Studio")
    var page by mutableStateOf("")
    var script by
        mutableStateOf(
            prefs.getString("script", "నమస్కారం ఫ్రెండ్స్! ఇప్పుడు ఇక్కడ క్లిక్ చేయండి.") ?: ""
        )
    var voice by mutableIntStateOf(prefs.getInt("voice", 0).coerceIn(0, 1))
    var step by mutableIntStateOf(0)
    var plan by mutableStateOf<List<NarrationDirector.Segment>>(emptyList())
    private var plannedScript = ""
    var busy by mutableStateOf(false)
    var downloadingVoice by mutableIntStateOf(-1)
    var status by mutableStateOf("Everything happens on your phone")
    var notice by mutableStateOf("")
    var loading by mutableStateOf(false)
    var models by mutableStateOf<List<ModelRow>>(emptyList())
    var profiles by mutableStateOf<List<ProfileRow>>(emptyList())
    var projects by mutableStateOf<List<ProjectRow>>(emptyList())
    var selectedReference by mutableStateOf("")
    var recording by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var recordMillis by mutableLongStateOf(0L)
    var recordingStatus by mutableStateOf("Your voice stays on this device")
    var entries by mutableStateOf<Map<String, String>>(emptyMap())
    var readDigits by mutableStateOf(false)
    var latest by mutableStateOf<File?>(null)
    var comparisons by mutableStateOf<List<Pair<String, File>>>(emptyList())
    private var persistJob: Job? = null

    init {
        tab =
            prefs.getString("tab", "Studio")?.takeIf {
                it in listOf("Studio", "Voices", "Projects", "Settings")
            } ?: "Studio"
        work {
            val f = File(app.filesDir, "draft-plan.json")
            if (f.exists())
                runCatching { PlanStorage.parse(FileIntegrity.read(f)) }
                    .getOrNull()
                    ?.let {
                        if (it.script == script) {
                            plan = it.segments
                            plannedScript = it.script
                        }
                    }
            val speech = withContext(Dispatchers.IO) { SpeechSettings.load(app) }
            entries = speech.dictionary.toMap()
            readDigits = speech.readDigits
            withContext(Dispatchers.IO) {
                val library = VoiceProfiles(app)
                runCatching { library.migrateLegacy(app) }
                recoverRecording(library)
            }
            comparisons =
                withContext(Dispatchers.IO) {
                    listOf(
                            "Piper · Venkatesh" to "piper-venkatesh-2.wav",
                            "Piper · Padmavathi" to "piper-padmavathi-2.wav",
                            "Indic Tiny · Male" to "indic-tiny-male-2.wav",
                            "Indic Tiny · Female" to "indic-tiny-female-2.wav",
                        )
                        .map { (label, name) ->
                            val folder = File(app.cacheDir, "comparison-v04").apply { mkdirs() }
                            val file = File(folder, name)
                            app.assets.open("comparison/$name").use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                            label to file
                        }
                }
            refresh()
        }
        viewModelScope.launch {
            var priorBusy = false
            var priorRecording = false
            while (isActive) {
                busy = StudioJobService.busy
                downloadingVoice = StudioJobService.downloadingVoice
                status = StudioJobService.status
                recording = VoiceRecordingService.active
                paused = VoiceRecordingService.clock.state() == RecordingClock.State.PAUSED
                recordMillis = VoiceRecordingService.elapsedMs
                recordingStatus = VoiceRecordingService.status
                if ((priorBusy && !busy) || (priorRecording && !recording)) refresh()
                priorBusy = busy
                priorRecording = recording
                delay(250)
            }
        }
    }

    fun navigate(value: String) {
        tab = value
        page = ""
        prefs.edit().putString("tab", value).apply()
    }

    fun edit(value: String) {
        script = value
        persistJob?.cancel()
        persistJob =
            viewModelScope.launch {
                delay(500)
                persist()
            }
    }

    fun persist() {
        prefs.edit().putString("script", script).putInt("voice", voice).apply()
    }

    fun choose(index: Int) {
        voice = index
        persist()
    }

    fun direct() {
        if (script.isBlank()) {
            notice = "Paste your Telugu script first."
            return
        }
        if (plannedScript != script || plan.isEmpty()) {
            plan = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO)
            plannedScript = script
        }
        step = 2
        savePlan()
    }

    fun editDirection(index: Int, rate: Float, pause: Int, emotion: NarrationDirector.Emotion) {
        try {
            val next = plan.toMutableList()
            val old = next[index]
            next[index] = NarrationDirector.Segment(old.text, emotion, rate, old.pitch, pause)
            DirectionContract.validate(script, next)
            plan = next
            savePlan()
        } catch (e: Exception) {
            notice = e.message ?: "Invalid direction"
        }
    }

    fun neutral() {
        plan =
            plan.map {
                NarrationDirector.Segment(it.text, NarrationDirector.Emotion.NEUTRAL, 1f, 1f, 220)
            }
        savePlan()
    }

    private fun savePlan() {
        persist()
        if (plan.isNotEmpty() && plannedScript == script) {
            val value = PlanStorage(script, voice, plan).json()
            work {
                withContext(Dispatchers.IO) {
                    FileIntegrity.write(
                        File(getApplication<Application>().filesDir, "draft-plan.json"),
                        value,
                    )
                }
            }
        }
    }

    fun generate() {
        if (busy || recording) {
            notice = "Finish the current task first."
            return
        }
        if (script.length > 60000) {
            notice =
                "Split this script into parts under 60,000 characters. Your text has been kept."
            return
        }
        direct()
        if (plan.isEmpty()) return
        val snapshot = PlanStorage(script, voice, plan).json()
        work {
            withContext(Dispatchers.IO) {
                FileIntegrity.write(
                    File(getApplication<Application>().filesDir, "render-plan.json"),
                    snapshot,
                )
            }
            startJob("render")
            step = 3
        }
    }

    private fun startJob(action: String, index: Int = voice) {
        if (StudioJobService.busy) {
            notice = "A task is already running."
            return
        }
        StudioJobService.busy = true
        busy = true
        StudioJobService.status = "Preparing…"
        try {
            getApplication<Application>()
                .startForegroundService(
                    Intent(getApplication(), StudioJobService::class.java)
                        .setAction(action)
                        .putExtra("voice", index)
                )
        } catch (e: Exception) {
            StudioJobService.busy = false
            busy = false
            throw e
        }
    }

    fun install(index: Int) {
        if (busy || recording) {
            notice = "Finish the current task first."
            return
        }
        work { startJob("install", index) }
    }

    fun cancel() {
        StudioJobService.cancel()
    }

    fun verify(index: Int) {
        if (busy) return
        work {
            val ok =
                withContext(Dispatchers.IO) {
                    VoiceModelStore(getApplication()).verify(ModelCatalog.VOICES[index])
                }
            notice =
                if (ok) "Integrity verified · READY OFFLINE"
                else "Model is damaged. Tap Repair to replace only this voice."
            refresh()
        }
    }

    fun deleteModel(index: Int) {
        if (busy) return
        work {
            withContext(Dispatchers.IO) {
                VoiceModelStore(getApplication()).remove(ModelCatalog.VOICES[index])
            }
            refresh()
        }
    }

    fun checkUpdates() {
        work {
            notice =
                withContext(Dispatchers.IO) { VoiceModelStore(getApplication()).checkUpdates() }
        }
    }

    fun restartDownload(index: Int) {
        if (busy) return
        work {
            withContext(Dispatchers.IO) {
                val root = VoiceModelStore(getApplication()).root
                val f = File(root, ModelCatalog.VOICES[index].id + ".download")
                f.delete()
                File(f.path + ".properties").delete()
            }
            install(index)
        }
    }

    fun startRecording(name: String, label: String) {
        if (busy || recording) {
            notice = "Finish the current task first."
            return
        }
        try {
            if (name.trim().length > 100) {
                notice = "Use a name under 100 characters."
                return
            }
            getApplication<Application>()
                .startForegroundService(
                    Intent(getApplication(), VoiceRecordingService::class.java)
                        .setAction("start")
                        .putExtra("name", name)
                        .putExtra("label", label)
                )
        } catch (e: Exception) {
            notice = e.message ?: "Could not start microphone"
        }
    }

    fun recordingAction(action: String) {
        getApplication<Application>()
            .startService(
                Intent(getApplication(), VoiceRecordingService::class.java).setAction(action)
            )
    }

    fun renameProfile(id: String, name: String, label: String, description: String) {
        work {
            withContext(Dispatchers.IO) {
                VoiceProfiles(getApplication()).rename(id, name, label, description)
            }
            refresh()
        }
    }

    fun selectProfile(id: String) {
        work {
            withContext(Dispatchers.IO) { VoiceProfiles(getApplication()).select(id) }
            selectedReference = id
            notice = "Reference selected. Cloning is not installed; narrator voice has not changed."
        }
    }

    fun deleteProfile(id: String) {
        work {
            withContext(Dispatchers.IO) { VoiceProfiles(getApplication()).delete(id) }
            refresh()
        }
    }

    fun trimProfile(id: String, start: Long, end: Long) {
        work {
            withContext(Dispatchers.IO) { VoiceProfiles(getApplication()).trim(id, start, end) }
            refresh()
        }
    }

    fun dictionary(original: String, spoken: String) {
        if (original.isBlank() || spoken.isBlank()) {
            notice = "Enter the original word and its spoken form."
            return
        }
        entries = entries + (original.trim() to spoken.trim())
        saveSpeech()
    }

    fun removeEntry(original: String) {
        entries = entries - original
        saveSpeech()
    }

    fun digits(enabled: Boolean) {
        readDigits = enabled
        saveSpeech()
    }

    private fun saveSpeech() {
        val copy = entries.toMap()
        val digits = readDigits
        work {
            withContext(Dispatchers.IO) {
                val s = SpeechSettings()
                s.dictionary.putAll(copy)
                s.readDigits = digits
                s.save(getApplication())
            }
        }
    }

    fun speechPreview(): String = PronunciationLexicon.apply(script, entries, readDigits)

    fun openProject(project: ProjectRow) {
        work {
            val f = File(project.folder, "plan.json")
            if (!f.exists()) {
                notice = "This older project has audio only; the saved WAV is still available."
                return@work
            }
            val p = withContext(Dispatchers.IO) { PlanStorage.parse(FileIntegrity.read(f)) }
            script = p.script
            voice = p.voice
            plan = p.segments
            plannedScript = p.script
            persist()
            navigate("Studio")
            step = 2
        }
    }

    fun renameProject(project: ProjectRow, name: String) {
        work {
            withContext(Dispatchers.IO) {
                FileIntegrity.write(File(project.folder, "project-name.txt"), name.trim().take(120))
            }
            refresh()
        }
    }

    fun deleteProject(project: ProjectRow) {
        if (busy) {
            notice = "Wait for rendering to finish."
            return
        }
        work {
            withContext(Dispatchers.IO) {
                project.folder.deleteRecursively()
                if (StudioJobService.result?.parentFile == project.folder)
                    StudioJobService.result = null
            }
            refresh()
        }
    }

    fun refresh() {
        work {
            val app = getApplication<Application>()
            val loadedModels =
                withContext(Dispatchers.IO) {
                    val store = VoiceModelStore(app)
                    ModelCatalog.VOICES.mapIndexed { i, v ->
                        val ready = store.verified(v)
                        ModelRow(
                            i,
                            v.name,
                            v.gender,
                            ready,
                            File(store.root, v.id + ".download").length(),
                            store.model(v).exists() && !ready,
                        )
                    }
                }
            val loadedProfiles =
                withContext(Dispatchers.IO) {
                    VoiceProfiles(app).list().map {
                        ProfileRow(
                            it.getString("id"),
                            it.getString("name"),
                            it.optString("label", "Custom"),
                            it.optString("description"),
                            it.optLong("durationMs"),
                            it.optString("status"),
                            VoiceProfiles(app).audio(it.getString("id")),
                        )
                    }
                }
            val loadedProjects =
                withContext(Dispatchers.IO) {
                    File(app.filesDir, "renders")
                        .listFiles()
                        ?.filter { it.isDirectory }
                        ?.sortedByDescending { it.lastModified() }
                        ?.map { f ->
                            val metric =
                                runCatching {
                                        JSONObject(FileIntegrity.read(File(f, "metrics.json")))
                                    }
                                    .getOrNull()
                            val name =
                                runCatching { FileIntegrity.read(File(f, "project-name.txt")) }
                                    .getOrDefault("Narration · " + f.name.take(6))
                            ProjectRow(
                                f,
                                name,
                                File(f, "narration.wav").isFile,
                                metric?.optDouble("audio_seconds", 0.0) ?: 0.0,
                            )
                        } ?: emptyList()
                }
            models = loadedModels
            profiles = loadedProfiles
            projects = loadedProjects
            selectedReference = VoiceProfiles(app).selected()
            latest =
                StudioJobService.result?.takeIf { it.isFile }
                    ?: withContext(Dispatchers.IO) {
                        runCatching {
                                File(FileIntegrity.read(File(app.filesDir, "last-result.txt")))
                                    .takeIf { it.isFile }
                            }
                            .getOrNull()
                    }
        }
    }

    private fun recoverRecording(library: VoiceProfiles) {
        if (VoiceRecordingService.active) return
        val f = File(library.root(), "pending-recording.json")
        if (!f.isFile) return
        runCatching {
                val p = JSONObject(FileIntegrity.read(f))
                library.save(
                    p.getString("id"),
                    p.optString("name", "Recovered voice"),
                    p.optString("label", "Custom"),
                    "Recovered after interruption",
                    true,
                )
                f.delete()
            }
            .onFailure {
                recordingStatus =
                    "An interrupted file is retained in local storage but could not be decoded."
            }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: e.javaClass.simpleName
            }
        }
    }
}
