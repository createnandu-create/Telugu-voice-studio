package `in`.aitechapp.teluguoffline

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val displayStem: String = name,
    val cloneReady: Boolean = false,
    val clonePreview: File? = null,
    val cloneBase: Int = 0,
)

data class ProjectRow(val folder: File, val name: String, val ready: Boolean, val duration: Double)

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("StudioActivity", 0)
    var tab by mutableStateOf("Studio")
    var page by mutableStateOf("")
    var script by
        mutableStateOf(
            prefs.getString("script", null)?.takeUnless { it == "నమస్కారం ఫ్రెండ్స్! ఇప్పుడు ఇక్కడ క్లిక్ చేయండి." }
                ?: app.assets.open("studio-demo.txt").bufferedReader().use { it.readText().trimEnd() }
        )
    var voice by
        mutableIntStateOf(prefs.getInt("voice", 0).coerceIn(0, ModelCatalog.VOICES.lastIndex))
    var step by mutableIntStateOf(0)
    var plan by mutableStateOf<List<NarrationDirector.Segment>>(emptyList())
    private var plannedScript = ""
    var queueSummary by mutableStateOf("")
    val canSubmit get() = !busy || queueSummary.isNotEmpty()
    var busy by mutableStateOf(false)
    var downloadingVoice by mutableIntStateOf(-1)
    var status by mutableStateOf("Ready · everything happens on your phone")
    var notice by mutableStateOf("")
    var verifying by mutableIntStateOf(-1)
    var verification by mutableStateOf<Map<Int, String>>(emptyMap())
    var inlineExport by mutableStateOf(false)
    var savedFormats by mutableStateOf<Set<String>>(emptySet())
    var downloadFormat by mutableStateOf("")
    var playbackRequest by mutableStateOf<File?>(null)
    var exportLabel by mutableStateOf("Telugu-narration")
    var exportSource by mutableStateOf<File?>(null)
    var exportReady by mutableStateOf<File?>(null)
    var exportBusy by mutableStateOf(false)
    var exportProgress by mutableFloatStateOf(0f)
    var exportMessage by mutableStateOf("")
    var exportedUri by mutableStateOf<android.net.Uri?>(null)
    var exportedName by mutableStateOf("")
    private val exportCancel = java.util.concurrent.atomic.AtomicBoolean()

    var loading by mutableStateOf(false)
    var models by mutableStateOf<List<ModelRow>>(emptyList())
    var speakerReady by mutableStateOf(false)
    var checkerReady by mutableStateOf(false)
    var renderingFolder by mutableStateOf<File?>(null)
    var himaCloneReady by mutableStateOf(false)
    fun clonePackReady(base: Int) = if (ModelCatalog.VOICES[base].hima()) himaCloneReady else cloneModelReady
    var cloneModelReady by mutableStateOf(false)
    var activeClone by mutableStateOf("")
    var referenceStatus by mutableStateOf("")
    var profiles by mutableStateOf<List<ProfileRow>>(emptyList())
    var projects by mutableStateOf<List<ProjectRow>>(emptyList())
    var selectedReference by mutableStateOf("")
    var recording by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var recordMillis by mutableLongStateOf(0L)
    var recordingStatus by mutableStateOf("Your voice stays on this device")
    var entries by mutableStateOf<Map<String, String>>(emptyMap())
    private val speechMutex = Mutex()
    var youtuber by mutableStateOf(true)
    var durationGuard by mutableStateOf(false)
    var questionLift by mutableStateOf(false)
    var commaBuffer by mutableStateOf(true)
    var femaleSoftening by mutableStateOf(false)
    var synthesisCleanup by mutableStateOf(false)
    var carefulDiction by mutableStateOf(!ModelCatalog.VOICES[voice].tiny())
    var englishNotation by mutableStateOf(true)
        private set
    var explainTechnical by mutableStateOf(true)
        private set
    var readDigits by mutableStateOf(false)
    var warmthEq by mutableStateOf(false)
    var latest by mutableStateOf<File?>(null)
    var comparisons by mutableStateOf<List<Pair<String, File>>>(emptyList())
    private var persistJob: Job? = null
    var playbackEpoch by mutableIntStateOf(0)
    private var observedServiceAction = false
    private var activeRunId = ""
    private var workspaceRevision = 0
    private var generatePending = false
    private var requestedSelection: Int? = null
    private var settingsLoaded = false

    init {
        // New ViewModel = a new Studio workspace. Projects and scripts remain on disk.
        tab = "Studio"
        step = 0
        latest = null
        work {
            val speech =
                withContext(Dispatchers.IO) {
                    DynamicEnglishPhonetizer.load(app.assets.open("english-cmudict.bin"))
                    SpeechSettings.load(app)
                }
            entries = speech.dictionary.toMap()
            englishNotation = speech.englishNotation
            explainTechnical = speech.explainTechnical
            synthesisCleanup = speech.synthesisCleanup
            readDigits = speech.readDigits
            applyControls(controlsFor(voice, activeClone))
            settingsLoaded = true
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
                            "Srinivas" to "hima-srinivas.wav",
                            "Bhavana" to "hima-bhavana.wav",
                            "Indic-TTS Male" to "fastpitch-male.wav",
                            "Indic-TTS Female" to "fastpitch-female.wav",
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
                val currentFolder = StudioJobService.activeProject
                if (renderingFolder != currentFolder) {
                    renderingFolder = currentFolder
                    refresh()
                }
                queueSummary = StudioJobService.queueSummary
                busy = StudioJobService.busy
                downloadingVoice = StudioJobService.downloadingVoice
                if (busy && StudioJobService.runId.isEmpty()) observedServiceAction = true
                status =
                    if ((activeRunId.isNotEmpty() && StudioJobService.runId == activeRunId) ||
                        (observedServiceAction && StudioJobService.runId.isEmpty())) StudioJobService.status
                    else "Ready · everything happens on your phone"
                recording = VoiceRecordingService.active
                paused = VoiceRecordingService.clock.state() == RecordingClock.State.PAUSED
                recordMillis = VoiceRecordingService.elapsedMs
                recordingStatus = VoiceRecordingService.status
                if ((priorBusy && !busy) || (priorRecording && !recording)) refresh()
                val wanted = requestedSelection
                if (wanted != null && settingsLoaded && canSubmit && !recording && !generatePending && !loading && verifying < 0) {
                    requestedSelection = null
                    if (wanted == workspaceRevision && script.isNotBlank()) generate()
                }
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
        if (value != script) clearActiveAudio()
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

    private fun clearActiveAudio() {
        requestedSelection = null
        workspaceRevision++
        observedServiceAction = false
        status = "Ready · everything happens on your phone"
        activeRunId = ""
        latest = null
        playbackRequest = null
        playbackEpoch++
        exportCancel.set(true)
        exportSource = null
        exportReady = null
        exportedUri = null
        exportMessage = ""
        savedFormats = emptySet()
    }

    private fun selectNarrator(index: Int, clone: String, autoRender: Boolean) {
        if (voice != index || activeClone != clone) {
            clearActiveAudio()
            voice = index
            activeClone = clone
            plan = emptyList()
            plannedScript = ""
            applyControls(controlsFor(index, clone))
            persist()
        }
        if (autoRender && script.isNotBlank() && models.any { it.index == index && it.ready } &&
            !(activeRunId.isNotEmpty() && busy)) requestedSelection = workspaceRevision
    }

    fun resetToScript() {
        clearActiveAudio()
        activeClone = ""
        plan = emptyList()
        plannedScript = ""
        applyControls(controlsFor(voice, ""))
        inlineExport = false
        notice = ""
        step = 0
        navigate("Studio")
        persist()
    }

    fun choose(index: Int, autoRender: Boolean = false) = selectNarrator(index, "", autoRender)

    fun chooseClone(id: String, autoRender: Boolean = false) {
        val p = profiles.firstOrNull { it.id == id && it.cloneReady } ?: return
        if (!clonePackReady(p.cloneBase)) {
            notice = "Install the matching pack in Settings → Voice cloning."
            return
        }
        selectNarrator(p.cloneBase, id, autoRender)
    }

    fun selectOutput(master: Boolean) {
        val folder = latest?.parentFile ?: return
        val file = File(folder, if (master) "narration-studio.wav" else "narration.wav")
        if (file.isFile) latest = file
        else notice = "This output is not available yet. Generate to create it."
    }

    fun selectPlayback() {
        File(latest?.parentFile ?: return, "narration-playback.wav")
            .takeIf { it.isFile }
            ?.let { latest = it }
    }

    fun selectDirected() {
        File(latest?.parentFile ?: return, "narration-directed.wav")
            .takeIf { it.isFile }
            ?.let { latest = it }
    }

    fun selectNativeWarmth() {
        val file = File(latest?.parentFile ?: return, "narration-native-warmth.wav")
        if (file.isFile) latest = file
    }

    fun projectAudio(folder: File): File {
        val preferred =
            runCatching { FileIntegrity.read(File(folder, "preferred-output.txt")) }.getOrNull()
        if (
            preferred in
                listOf(
                    "narration-directed.wav",
                    "narration.wav",
                    "narration-studio.wav",
                    "narration-playback.wav",
                )
        ) {
            val file = File(folder, preferred!!)
            if (file.isFile) return file
        }
        val master = File(folder, "narration-studio.wav")
        val padded = File(folder, "narration-playback.wav")
        return if (master.isFile) master
        else if (padded.isFile) padded else File(folder, "narration.wav")
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

    fun editDirection(
        index: Int,
        rate: Float,
        pause: Int,
        emotion: NarrationDirector.Emotion,
        pitch: Float,
    ) {
        try {
            val next = plan.toMutableList()
            val old = next[index]
            next[index] = NarrationDirector.Segment(old.text, emotion, rate, pitch, pause)
            DirectionContract.validate(script, next)
            plan = next
            savePlan()
        } catch (e: Exception) {
            notice = e.message ?: "Invalid direction"
        }
    }

    fun neutral() {
        synthesisCleanup = false
        durationGuard = false
        commaBuffer = false
        femaleSoftening = false
        warmthEq = false
        youtuber = false
        questionLift = false
        carefulDiction = false
        saveSpeech()
        plan =
            plan.map {
                NarrationDirector.Segment(it.text, NarrationDirector.Emotion.NEUTRAL, 1f, 1f, 220)
            }
        savePlan()
    }

    fun autoDirection() {
        durationGuard = false
        commaBuffer = true
        carefulDiction = true
        femaleSoftening = false
        plan = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO)
        plannedScript = script
        youtuber = true
        questionLift = true
        saveSpeech()
        savePlan()
    }

    private fun savePlan() {
        persist()
        if (plan.isNotEmpty() && plannedScript == script) {
            val value = PlanStorage(script, voice, plan, activeClone).json()
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

    fun generate() = generateBatch(listOf(voice to activeClone))

    fun generateBatch(voices: List<Pair<Int,String>>) {
        if (!canSubmit || generatePending || recording || verifying >= 0 || loading) {
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
        if (voices.isEmpty()) return
        val jobs = org.json.JSONArray()
        voices.distinct().forEach { (base, clone) ->
            val selected = base == voice && clone == activeClone
            val controls = if (selected) currentSpeech() else controlsFor(base, clone)
            val segments = if (selected) plan else NarrationDirector.planExact(script, NarrationDirector.Style.AUTO)
            jobs.put(JSONObject(PlanStorage(script, base, segments, clone).json()).put("speech", controls.json()))
        }
        val last = voices.last()
        selectNarrator(last.first, last.second, false)
        val id = java.util.UUID.randomUUID().toString()
        val batch = JSONObject().put("plans", jobs).put("next", 0)
        clearActiveAudio()
        val revision = workspaceRevision
        generatePending = true
        work {
            try {
                withContext(Dispatchers.IO) {
                    FileIntegrity.write(
                        File(getApplication<Application>().filesDir, "batch-$id.json"),
                        batch.toString(),
                    )
                }
                if (revision != workspaceRevision) {
                    withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir, "batch-$id.json").delete() }
                    return@work
                }
                startJob("render", id = id, label = voices.distinct().joinToString(", ") { (index, clone) -> profiles.firstOrNull { it.id == clone }?.name ?: ModelCatalog.VOICES[index].name })
                step = 3
            } finally {
                generatePending = false
            }
        }
    }

    private fun startJob(action: String, index: Int = voice, id: String = java.util.UUID.randomUUID().toString(), label: String = "") {
        if (StudioJobService.busy && !(action == "render" && StudioJobService.rendering)) {
            notice = "A task is already running."
            return
        }
        val wasBusy = StudioJobService.busy
        StudioJobService.busy = true
        busy = true
        if (!wasBusy) StudioJobService.status = "Preparing…"
        activeRunId = id
        try {
            getApplication<Application>()
                .startForegroundService(
                    Intent(getApplication(), StudioJobService::class.java)
                        .setAction(action)
                        .putExtra("runId", activeRunId)
                        .putExtra("label", label)
                        .putExtra("voice", index)
                )
        } catch (e: Exception) {
            StudioJobService.busy = wasBusy
            busy = wasBusy
            throw e
        }
    }

    var verifyWords by mutableStateOf(prefs.getBoolean("verifyWords", true))
        private set

    fun usePronunciationCheck(enabled: Boolean) {
        verifyWords = enabled
        prefs.edit().putBoolean("verifyWords", enabled).apply()
    }

    fun installChecker() {
        if (!busy && !recording && !loading) startJob("check-install")
    }

    fun install(index: Int) {
        if (busy || recording || verifying >= 0 || loading) {
            notice = "Finish the current task first."
            return
        }
        work { startJob("install", index) }
    }

    fun cancel() {
        StudioJobService.cancel()
    }

    fun verify(index: Int) {
        if (busy || verifying >= 0 || loading) return
        verifying = index
        verification = verification + (index to "Checking every model file…")
        work {
            try {
                val ok =
                    withContext(Dispatchers.IO) {
                        VoiceModelStore(getApplication()).verify(ModelCatalog.VOICES[index])
                    }
                verification =
                    verification +
                        (index to
                            if (ok)
                                "Verified at ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())} · SHA-256 matches · Ready offline"
                            else "Verification failed. Repair this model before using it.")
            } catch (e: Exception) {
                verification = verification + (index to "Verification failed: ${e.message}")
            } finally {
                verifying = -1
                refresh()
            }
        }
    }

    fun sibilantCheck() =
        renderCheck(
            "శ, ష, స. శ్రోతలకు నమస్కారం. కర్కశ శబ్దం. అభ్యాసకుడికి స్పష్టంగా చెప్పండి. అంత సులభమా? కాదు కదా! సాధ్యమౌతుందా?"
        )

    fun pronunciationCheck() = renderCheck("నమస్కారం. నమస్కారం ఫ్రెండ్స్! మీ అందరికీ నమస్కారం.")

    private fun renderCheck(text: String) {
        if (busy || generatePending || recording || verifying >= 0 || loading) return
        clearActiveAudio()
        val revision = workspaceRevision
        generatePending = true
        val segments =
            NarrationDirector.planExact(text, NarrationDirector.Style.AUTO).map {
                NarrationDirector.Segment(it.text, NarrationDirector.Emotion.NEUTRAL, 1f, 1f, 500)
            }
        val snapshot = PlanStorage(text, voice, segments, activeClone).json()
        val controls = currentSpeech()
        val id = java.util.UUID.randomUUID().toString()
        work {
            try {
                withContext(Dispatchers.IO) {
                    speechMutex.withLock { controls.save(getApplication()) }
                    FileIntegrity.write(
                        File(getApplication<Application>().filesDir, "batch-$id.json"),
                        JSONObject().put("plans", org.json.JSONArray().put(JSONObject(snapshot)))
                            .put("next", 0).put("speech", controls.json()).toString(),
                    )
                }
                if (revision != workspaceRevision) {
                    withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir, "batch-$id.json").delete() }
                    return@work
                }
                startJob("render", id = id)
                step = 3
            } finally {
                generatePending = false
            }
        }
    }

    fun openSavedFolder() {
        val context = getApplication<Application>()
        try {
            val saved = prefs.getString("lastSavedDocument", null)
            if (saved != null) {
                context.startActivity(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .putExtra(
                            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                            android.net.Uri.parse(saved),
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            val uri =
                android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download/Telugu Voice Studio",
                )
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            )
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                notice = "Open Files → Downloads → Telugu Voice Studio."
            }
        }
    }

    fun download(mp3: Boolean) {
        val source = latest ?: return
        if (exportBusy) return
        if (android.os.Build.VERSION.SDK_INT < 29) {
            inlineExport = false
            exportSource = source
            prepareExport(mp3)
            return
        }
        inlineExport = true
        exportSource = null
        exportedUri = null
        exportBusy = true
        exportProgress = 0f
        exportCancel.set(false)
        downloadFormat = if (mp3) "MP3" else "WAV"
        exportMessage = if (mp3) "Encoding MP3…" else "Saving WAV…"
        work {
            try {
                val name =
                    withContext(Dispatchers.IO) { AudioNames.forAudio(source) } +
                        if (mp3) ".mp3" else ".wav"
                withContext(Dispatchers.IO) {
                    val file =
                        if (!mp3) MonoWaveExport.prepare(source, getApplication<Application>().cacheDir, exportCancel)
                        else {
                            val converted =
                                File(getApplication<Application>().cacheDir, "download.mp3")
                            Mp3Export.encode(source, converted, exportCancel) { done, total ->
                                viewModelScope.launch {
                                    exportProgress = .7f * done.toFloat() / total
                                }
                            }
                            converted
                        }
                    AudioDownloads.save(getApplication(), file, name, exportCancel) { done, total ->
                        viewModelScope.launch {
                            exportProgress =
                                (if (mp3) .7f else 0f) +
                                    (if (mp3) .3f else 1f) * done.toFloat() / total
                        }
                    }
                }
                savedFormats =
                    savedFormats +
                        (source.path + ":" + source.lastModified() + ":" + downloadFormat)
                exportedName = name
                prefs.edit().remove("lastSavedDocument").apply()
                exportMessage = "Completed · Downloads / Telugu Voice Studio"
            } catch (e: Exception) {
                exportMessage =
                    if (exportCancel.get()) "Download cancelled"
                    else "Download failed: ${e.message}"
            } finally {
                exportBusy = false
            }
        }
    }

    fun downloaded(format: String): Boolean =
        latest?.let { (it.path + ":" + it.lastModified() + ":" + format) in savedFormats } ?: false

    fun previewVoice(index: Int) {
        if (busy || recording) return
        clearActiveAudio()
        selectNarrator(index, "", false)
        renderCheck("నమస్కారం. మీ కథను స్పష్టంగా వినిపిద్దాం.")
        val previewRevision = workspaceRevision
        viewModelScope.launch {
            var started = false
            repeat(40) {
                if (StudioJobService.busy) started = true
                if (!started) delay(250)
            }
            if (started) {
                while (StudioJobService.busy) delay(250)
                val file = StudioJobService.result
                if (file != null && activeRunId.isNotEmpty() && StudioJobService.resultRunId == activeRunId && StudioJobService.status.startsWith("Ready")) {
                    val identity = withContext(Dispatchers.IO) { AudioIdentity.read(file) }
                    if (
                        workspaceRevision == previewRevision &&
                            identity.id == ModelCatalog.VOICES[index].id
                    )
                        playbackRequest = file
                }
            }
        }
    }

    fun prepareExport(mp3: Boolean) {
        val source = exportSource ?: return
        if (exportBusy) return
        exportBusy = true
        exportProgress = 0f
        exportCancel.set(false)
        exportMessage = if (mp3) "Creating MP3 on your phone…" else "Preparing WAV…"
        work {
            try {
                exportLabel = withContext(Dispatchers.IO) { AudioNames.forAudio(source) }
                val file =
                    withContext(Dispatchers.IO) {
                        if (!mp3) MonoWaveExport.prepare(source, getApplication<Application>().cacheDir, exportCancel)
                        else {
                            val out =
                                File(getApplication<Application>().cacheDir, "narration-share.mp3")
                            Mp3Export.encode(source, out, exportCancel) { done, total ->
                                viewModelScope.launch { exportProgress = done.toFloat() / total }
                            }
                            out
                        }
                    }
                if (!exportCancel.get()) exportReady = file
                exportSource = null
            } catch (e: Exception) {
                exportMessage =
                    if (exportCancel.get()) "Save cancelled. Your narration is kept."
                    else "Could not prepare audio: ${e.message}"
            } finally {
                exportBusy = false
            }
        }
    }

    fun cancelExport() {
        exportCancel.set(true)
    }

    fun saveExport(file: File, uri: android.net.Uri) {
        if (exportBusy) return
        exportBusy = true
        exportProgress = 0f
        exportCancel.set(false)
        exportMessage = "Saving ${file.extension.uppercase()}…"
        work {
            try {
                val name =
                    withContext(Dispatchers.IO) {
                        val resolver = getApplication<Application>().contentResolver
                        file.inputStream().use { input ->
                            requireNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                                val buffer = ByteArray(65536)
                                var done = 0L
                                while (true) {
                                    if (exportCancel.get())
                                        throw java.util.concurrent.CancellationException()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    done += count
                                    val fraction = done.toFloat() / file.length()
                                    viewModelScope.launch { exportProgress = fraction }
                                }
                                output.flush()
                            }
                        }
                        resolver
                            .query(
                                uri,
                                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null,
                            )
                            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: file.name
                    }
                exportedName = name
                exportedUri = uri
                prefs.edit().putString("lastSavedDocument", uri.toString()).apply()
                exportMessage = "Saved to your selected folder"
            } catch (e: Exception) {
                // Only this newly created destination is removed; the source always remains.
                withContext(Dispatchers.IO) {
                    runCatching {
                        android.provider.DocumentsContract.deleteDocument(
                            getApplication<Application>().contentResolver,
                            uri,
                        )
                    }
                }
                exportMessage =
                    if (exportCancel.get()) "Save cancelled. Your narration is kept."
                    else "Save failed: ${e.message}"
            } finally {
                exportBusy = false
            }
        }
    }

    fun deleteModel(index: Int) {
        if (busy || verifying >= 0 || loading) return
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
                if (ModelCatalog.VOICES[index].alternative()) AlternativePack(root,ModelCatalog.VOICES[index]).restart()
                if (ModelCatalog.VOICES[index].tiny()) {
                    val tiny = TinyModelStore(root)
                    TinyModelStore.ASSETS.forEach { a ->
                        File(tiny.root, a.name + ".download").delete()
                        File(tiny.root, a.name + ".download.properties").delete()
                    }
                }
                val f = File(root, ModelCatalog.VOICES[index].id + ".download")
                f.delete()
                File(f.path + ".properties").delete()
            }
            install(index)
        }
    }

    fun startRecording(name: String, label: String) {
        if (busy || recording || verifying >= 0 || loading) {
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
            notice =
                "Reference marked. This recording cannot speak new text; the Studio narrator is unchanged."
        }
    }

    fun deleteProfile(id: String) {
        work {
            withContext(Dispatchers.IO) { VoiceProfiles(getApplication()).delete(id) }
            refresh()
        }
    }

    fun speakerAction(action: String, id: String = "") {
        if (busy || recording || loading || verifying >= 0) return
        if (action == "verify" || action == "remove") {
            loading = true
            notice =
                if (action == "verify") "Verifying speaker encoder…"
                else "Removing speaker encoder…"
            work {
                try {
                    notice =
                        withContext(Dispatchers.IO) {
                            val store = SpeakerModelStore(getApplication<Application>().filesDir)
                            if (action == "remove") {
                                store.remove()
                                "Speaker encoder removed. Personal recordings and fingerprints kept."
                            } else if (store.verify()) "Speaker encoder verified · READY OFFLINE"
                            else
                                "Speaker encoder missing or damaged. Download repairs only affected files."
                        }
                } finally {
                    loading = false
                }
                refresh()
            }
            return
        }
        busy = true
        try {
            getApplication<Application>()
                .startForegroundService(
                    Intent(getApplication(), StudioJobService::class.java)
                        .setAction(action)
                        .putExtra("profile", id)
                        .putExtra("voice", voice)
                        .putExtra("consent", action == "speaker-enroll" || action == "clone-create")
                )
        } catch (e: Exception) {
            busy = false
            notice = e.message ?: "Could not start enrollment"
        }
    }

    fun clonePackAction(remove: Boolean) {
        if (busy || recording || loading) return
        loading = true
        work {
            try {
                notice =
                    withContext(Dispatchers.IO) {
                        val store = OpenVoiceModelStore(getApplication<Application>().filesDir)
                        if (remove) {
                            store.remove()
                            "OpenVoice pack removed. Your recordings and voice profiles are kept."
                        } else if (store.verify()) "OpenVoice verified · READY OFFLINE"
                        else "Files missing or damaged. Download OpenVoice to repair them."
                    }
            } finally {
                loading = false
                refresh()
            }
        }
    }

    fun enhanceProfile(id: String, roomTail: Boolean) {
        if (busy || recording || loading || verifying >= 0) {
            notice = "Finish the current task first."
            return
        }
        busy = true
        try {
            getApplication<Application>()
                .startForegroundService(
                    Intent(getApplication(), StudioJobService::class.java)
                        .setAction("enhance")
                        .putExtra("profile", id)
                        .putExtra("roomTail", roomTail)
                )
        } catch (e: Exception) {
            busy = false
            notice = e.message ?: "Could not start cleanup"
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

    fun toggleWarmthEq(enabled: Boolean) {
        warmthEq = enabled
        saveSpeech()
    }

    fun setDelivery(kind: String, enabled: Boolean) {
        when (kind) {
            "youtuber" -> {
                youtuber = enabled
                if (enabled && plan.isNotEmpty() && plan.all {
                        it.emotion == NarrationDirector.Emotion.NEUTRAL && it.rate == 1f &&
                            it.pitch == 1f && it.pauseAfterMs == 220
                    }) {
                    plan = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO)
                    plannedScript = script
                    savePlan()
                }
            }
            "englishNotation" -> englishNotation = enabled
            "technical" -> explainTechnical = enabled
            "duration" -> durationGuard = enabled
            "question" -> questionLift = enabled
            "comma" -> commaBuffer = enabled
            "soft" -> femaleSoftening = enabled
            "cleanup" -> synthesisCleanup = enabled
            "careful" -> carefulDiction = enabled
        }
        saveSpeech()
    }

    private fun currentSpeech(): SpeechSettings =
        SpeechSettings().also { s ->
            s.dictionary.putAll(entries)
            s.englishNotation = englishNotation
            s.explainTechnical = explainTechnical
            s.readDigits = readDigits
            s.warmthEq = warmthEq
            s.youtuber = youtuber
            s.durationGuard = durationGuard
            s.questionLift = questionLift
            s.commaBuffer = commaBuffer
            s.femaleSoftening = femaleSoftening
            s.synthesisCleanup = synthesisCleanup
            s.carefulDiction = carefulDiction
        }

    private fun controlsFor(base: Int, clone: String): SpeechSettings {
        val model = ModelCatalog.VOICES[base]
        val s = SpeechSettings().also {
            it.questionLift = false
            it.femaleSoftening = false
            it.synthesisCleanup = synthesisCleanup
            it.carefulDiction = !model.tiny()
        }
        s.dictionary.putAll(entries)
        s.englishNotation = englishNotation
        s.explainTechnical = explainTechnical
        s.readDigits = readDigits
        return s
    }

    private fun applyControls(s: SpeechSettings) {
        youtuber = s.youtuber
        durationGuard = false
        questionLift = s.questionLift
        commaBuffer = s.commaBuffer
        femaleSoftening = s.femaleSoftening
        synthesisCleanup = s.synthesisCleanup
        carefulDiction = s.carefulDiction
        warmthEq = s.warmthEq
    }

    private fun saveSpeech() {
        val snapshot = currentSpeech()
        work {
            withContext(Dispatchers.IO) { speechMutex.withLock { snapshot.save(getApplication()) } }
        }
    }

    fun spokenLine(text: String): String = currentSpeech().spoken(text)

    fun speechPreview(): String = speechPreviewLines().joinToString(" ") { spokenLine(it.text) }

    private fun speechPreviewLines() = NarrationDirector.planExact(script, NarrationDirector.Style.AUTO)

    fun openProject(project: ProjectRow) {
        work {
            val f = File(project.folder, "plan.json")
            if (!f.exists()) {
                notice = "This older project has audio only; the saved WAV is still available."
                return@work
            }
            val p = withContext(Dispatchers.IO) { PlanStorage.parse(FileIntegrity.read(f)) }
            clearActiveAudio()
            selectNarrator(p.voice, p.cloneProfile, false)
            script = p.script
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

    private var refreshPending = false

    fun refresh() {
        if (loading) {
            refreshPending = true
            return
        }
        val selectedAtStart = latest
        val revisionAtStart = workspaceRevision
        loading = true
        work {
            try {
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
                                if (v.alternative()) AlternativePack(store.root,v).partial() else if (v.tiny()) TinyModelStore(store.root).partial()
                                else File(store.root, v.id + ".download").length(),
                                store.model(v).exists() && !ready,
                            )
                        }
                    }
                models = loadedModels
                checkerReady = withContext(Dispatchers.IO) { AlternativePack.pronunciation(VoiceModelStore(app).root).ready() }
                val loadedClone =
                    withContext(Dispatchers.IO) { OpenVoiceModelStore(app.filesDir).ready() }
                val loadedSpeaker =
                    withContext(Dispatchers.IO) { SpeakerModelStore(app.filesDir).ready() }
                referenceStatus =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                FileIntegrity.read(
                                    File(VoiceProfiles(app).root(), "enhancement-state.txt")
                                )
                            }
                            .getOrDefault("")
                    }
                val loadedProfiles =
                    withContext(Dispatchers.IO) {
                        VoiceProfiles(app).list().map {
                            val cloneFolder =
                                runCatching { CloneProfiles.directory(VoiceProfiles(app), it) }
                                    .getOrNull()
                            ProfileRow(
                                it.getString("id"),
                                it.getString("name"),
                                it.optString("label", "Custom"),
                                it.optString("description"),
                                it.optLong("durationMs"),
                                it.optString("status"),
                                VoiceProfiles(app).audio(it.getString("id")),
                                it.optString(
                                    "displayStem",
                                    AudioNames.stem(it.getString("name"), "", it.optLong("created")),
                                ),
                                cloneFolder != null,
                                cloneFolder?.let { folder -> File(folder, "preview.wav") },
                                it.optInt("cloneBase", 0),
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
                                    AudioNames.project(
                                        f,
                                        AudioIdentity.read(File(f, "narration.wav")).name,
                                    )
                                ProjectRow(
                                    f,
                                    name,
                                    File(f, "narration.wav").isFile,
                                    metric?.optDouble("audio_seconds", 0.0) ?: 0.0,
                                )
                            } ?: emptyList()
                    }
                himaCloneReady = withContext(Dispatchers.IO) { HimaClonePack(app).ready() }
                cloneModelReady = loadedClone
                speakerReady = loadedSpeaker
                profiles = loadedProfiles
                projects = loadedProjects
                selectedReference = VoiceProfiles(app).selected()
                val completed =
                    StudioJobService.result?.takeIf {
                        it.isFile &&
                            activeRunId.isNotEmpty() &&
                            StudioJobService.resultRunId == activeRunId &&
                            AudioIdentity.read(it).id == (if (activeClone.isEmpty()) ModelCatalog.VOICES[voice].id else "clone-$activeClone")
                    }
                if (
                    completed != null &&
                        revisionAtStart == workspaceRevision && latest == selectedAtStart &&
                        latest?.parent != completed.parent
                )
                    latest = completed
            } finally {
                loading = false
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    private fun recoverRecording(library: VoiceProfiles) {
        if (VoiceRecordingService.active) return
        val f = File(library.root(), "pending-recording.json")
        if (!f.isFile) return
        runCatching {
                val p = JSONObject(FileIntegrity.read(f))
                val id = p.getString("id")
                val capture = File(library.root(), "$id-capture.wav")
                if (capture.isFile && !library.wave(id).isFile)
                    RecordingCleanup.file(capture, library.wave(id))
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
