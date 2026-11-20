@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package `in`.aitechapp.teluguoffline

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*

private val Paper = Color(0xFFFFFCF6)
private val Ink = Color(0xFF222720)
private val Muted = Color(0xFF62695F)
private val Sun = Color(0xFFF4CF53)
private val Vermilion = Color(0xFFB9432A)
private val Line = Color(0xFFE6E7DD)
private val Manrope =
    FontFamily(
        Font(R.font.manrope, FontWeight.Normal),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
    )
private val DisplaySerif = FontFamily(Font(R.font.display_serif))

class StudioActivity : ComponentActivity() {
    private val model: StudioViewModel by viewModels()
    private var player: MediaPlayer? = null
    private var playingFile: File? = null
    private var exportFile: File? = null
    private var pendingName = "My voice"
    private var pendingLabel = "Custom"
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            val file = exportFile
            if (result.resultCode == RESULT_OK && uri != null && file != null)
                model.saveExport(file, uri)
            else model.exportMessage = "Save cancelled. Your narration is kept."
        }
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (
                result[Manifest.permission.RECORD_AUDIO] == true ||
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
            )
                model.startRecording(pendingName, pendingLabel)
            else
                model.notice =
                    "Microphone permission is required to record. Narration still works without it."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == "open-projects") model.navigate("Projects")
        if (android.os.Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(0).getBoolean("askedNotification", false)) {
            getPreferences(0).edit().putBoolean("askedNotification", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 81)
        }
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        savedInstanceState?.getString("export")?.let { exportFile = File(it) }
        setContent {
            LaunchedEffect(model.playbackEpoch) {
                player?.release()
                player = null
                playingFile = null
            }
            StudioTheme { StudioApp(model, ::play, ::export, ::record) }
        }
    }

    private fun play(file: File) {
        try {
            if (playingFile == file && player != null) {
                if (player!!.isPlaying) player!!.pause() else player!!.start()
                return
            }
            player?.release()
            player =
                MediaPlayer().apply {
                    setDataSource(file.path)
                    prepare()
                    setOnCompletionListener { finished ->
                        if (player === finished) {
                            playingFile = null
                            player = null
                        }
                        finished.release()
                    }
                    start()
                }
            playingFile = file
        } catch (e: Exception) {
            player?.release()
            player = null
            playingFile = null
            model.notice = e.message ?: "Could not play this recording"
        }
    }

    private fun export(file: File) {
        if (!file.isFile) {
            model.notice = "Audio is not available yet."
            return
        }
        exportFile = file
        val mime =
            when (file.extension) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                else -> "audio/mp4"
            }
        exportLauncher.launch(
            android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = mime
                putExtra(
                    android.content.Intent.EXTRA_TITLE,
                    "${model.exportLabel}.${file.extension}",
                )
            }
        )
    }

    private fun record(name: String, label: String) {
        player?.pause()
        pendingName = name
        pendingLabel = label
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        micPermission.launch(permissions.toTypedArray())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        exportFile?.let { outState.putString("export", it.path) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        model.persist()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Ink,
                onPrimary = Color.White,
                primaryContainer = Sun,
                onPrimaryContainer = Ink,
                secondary = Vermilion,
                secondaryContainer = Sun.copy(alpha = .35f),
                onSecondaryContainer = Ink,
                surfaceContainerHigh = Color.White,
                surfaceContainerHighest = Paper,
                surfaceTint = Color.Transparent,
                background = Paper,
                surface = Color.White,
                onSurface = Ink,
                onBackground = Ink,
                outline = Line,
            ),
        typography =
            Typography(
                bodyLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = Manrope,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    ),
                bodyMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = Manrope,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                titleLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                titleMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                labelLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
            ),
        content = content,
    )
}

@Composable
fun StudioApp(
    vm: StudioViewModel,
    play: (File) -> Unit,
    export: (File) -> Unit,
    record: (String, String) -> Unit,
) {
    BackHandler(enabled = vm.page.isNotEmpty() || vm.tab != "Studio" || vm.step > 0) {
        when {
            vm.page.isNotEmpty() -> vm.page = ""
            vm.tab != "Studio" -> vm.navigate("Studio")
            vm.step == 3 && !vm.busy -> vm.resetToScript()
            else -> vm.step--
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        if (vm.notice.isNotEmpty()) {
            val message = vm.notice
            snackbar.showSnackbar(
                message,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long,
            )
            if (vm.notice == message) vm.notice = ""
        }
    }
    LaunchedEffect(vm.playbackRequest) {
        vm.playbackRequest?.let {
            vm.playbackRequest = null
            play(it)
        }
    }
    LaunchedEffect(vm.exportReady) {
        vm.exportReady?.let {
            vm.exportReady = null
            export(it)
        }
    }
    val requestExport: (File) -> Unit = {
        vm.inlineExport = false
        vm.exportSource = it
        vm.exportMessage = ""
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).background(Sun, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.GraphicEq, null, Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TELUGU",
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Voice Studio", style = MaterialTheme.typography.titleMedium)
                    }
                    StatusPill(
                        if (vm.loading) "CHECKING"
                        else if (vm.busy) "WORKING"
                        else "LOCAL",
                        if (vm.busy) Sun else Color(0xFFE9F0E4),
                    )
                }
            }
        },
        bottomBar = { AnimatedNavigation(vm.tab, vm::navigate) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                vm.page == "models" -> ModelsScreen(vm, play)
                vm.page == "dictionary" -> DictionaryScreen(vm)
                vm.page == "samples" -> SamplesScreen(vm, play)
                vm.page == "reference-tools" -> ReferenceToolsScreen(vm)
                vm.page == "cloning" -> CloningScreen(vm)
                vm.tab == "Studio" -> StudioScreen(vm, play, requestExport)
                vm.tab == "Voices" -> VoicesScreen(vm, play, requestExport, record)
                vm.tab == "Projects" -> ProjectsScreen(vm, play, requestExport)
                else -> SettingsScreen(vm)
            }
        }
    }
    ExportDialogs(vm)
}

@Composable
private fun AnimatedNavigation(selected: String, onSelect: (String) -> Unit) {
    val names = listOf("Studio", "Voices", "Projects", "Settings")
    val icons =
        listOf(
            Icons.Outlined.GraphicEq,
            Icons.Outlined.Mic,
            Icons.Outlined.Folder,
            Icons.Outlined.Settings,
        )
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    Surface(
        Modifier.navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .97f),
        shadowElevation = 5.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, Line),
    ) {
        BoxWithConstraints(Modifier.padding(6.dp)) {
            val slot = maxWidth / 4
            val target = slot * names.indexOf(selected).coerceAtLeast(0)
            val offset by
                animateDpAsState(
                    target,
                    if (reduced) snap() else tween(240, easing = FastOutSlowInEasing),
                    label = "active navigation pill",
                )
            Box(
                Modifier.offset(x = offset)
                    .width(slot)
                    .height(58.dp)
                    .background(Sun.copy(alpha = .8f), RoundedCornerShape(22.dp))
            )
            Row {
                names.forEachIndexed { i, name ->
                    val active = selected == name
                    val flip = remember { Animatable(0f) }
                    LaunchedEffect(active) {
                        if (active && !reduced) {
                            flip.snapTo(-40f)
                            flip.animateTo(0f, tween(240))
                        }
                    }
                    Column(
                        Modifier.weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                if (!active) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onSelect(name)
                                }
                            }
                            .semantics {
                                this.selected = active
                                role = Role.Tab
                                contentDescription = "$name tab"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icons[i],
                            null,
                            Modifier.size(22.dp).graphicsLayer { rotationY = flip.value },
                            tint = if (active) Ink else Muted,
                        )
                        Text(
                            name,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Ink else Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color = Sun) {
    Text(
        label,
        Modifier.background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = .5.sp,
        color = Ink,
    )
}

@Composable
private fun PageTitle(title: String, description: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = DisplaySerif)
        Spacer(Modifier.height(4.dp))
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line),
        color = Color.White,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun Primary(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    Button(
        onClick = action,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
    ) {
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Secondary(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Line),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(
        value,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = Muted,
    )
}

private fun duration(ms: Long) = String.format(Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60)

@Composable
private fun StudioScreen(vm: StudioViewModel, play: (File) -> Unit, export: (File) -> Unit) {
    var editing by remember { mutableIntStateOf(-1) }
    val titles =
        listOf(
            "Your next great narration",
            "Choose your narrator",
            "Shape the delivery",
            "Preview & export",
        )
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).testTag("studio-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageTitle(titles[vm.step], "Telugu narration, made locally.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Script", "Voice", "Direction", "Audio").forEachIndexed { i, s ->
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(3.dp)
                                    .background(
                                        if (i <= vm.step) Sun else Line,
                                        RoundedCornerShape(4.dp),
                                    )
                            )
                            Text(
                                s,
                                fontSize = 10.sp,
                                color = if (i == vm.step) Ink else Muted,
                                modifier = Modifier.padding(top = 7.dp),
                            )
                        }
                    }
                }
            }
            if (vm.step == 0) {
                item {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("SCRIPT EDITOR")
                            Spacer(Modifier.weight(1f))
                            StatusPill("TELUGU", Paper)
                        }
                        OutlinedTextField(
                            value = vm.script,
                            onValueChange = vm::edit,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 15,
                            placeholder = { Text("Paste your full Telugu script…") },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line),
                        )
                        Text(
                            "${vm.script.length} characters · original words stay unchanged",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                }
                item {
                    Primary("Continue to voices →", !vm.recording) {
                        if (vm.script.isBlank()) vm.notice = "Paste a script first."
                        else {
                            vm.persist()
                            vm.step = 1
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            vm.navigate("Settings")
                            vm.page = "dictionary"
                        }
                    ) {
                        Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pronunciation dictionary")
                    }
                }
            }
            if (vm.step == 1) {
                item {
                    SectionLabel("TAP A NARRATOR TO GENERATE")
                    var batchDialog by rememberSaveable { mutableStateOf(false) }
                    OutlinedButton(onClick = { vm.refresh(); batchDialog = true }, enabled = !vm.busy && !vm.recording) {
                        Text("Generate with multiple voices")
                    }
                    if (batchDialog) {
                        val ready = vm.models.filter { it.ready }
                        val voices = ready.map { (it.index to "") to it.name } +
                            vm.profiles.filter { p -> vm.clonePackReady(p.cloneBase) && p.cloneReady && ready.any { it.index == p.cloneBase } }
                                .map { (it.cloneBase to it.id) to (it.name + " · Cloned") }
                        var excluded by remember { mutableStateOf(emptySet<Pair<Int, String>>()) }
                        val selected = voices.map { it.first }.toSet() - excluded
                        AlertDialog(onDismissRequest = { batchDialog = false },
                            title = { Text("Queue voices") },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    if (voices.isEmpty()) {
                                        if (vm.loading) Text("Checking offline narrators…")
                                        else TextButton(onClick = {
                                            batchDialog = false
                                            vm.navigate("Settings")
                                            vm.page = "models"
                                        }) { Text("Install a narrator in Settings") }
                                    }
                                    voices.forEach { (key, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(key in selected, onCheckedChange = { checked ->
                                                excluded = if (checked) excluded - key else excluded + key
                                            })
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(enabled = selected.isNotEmpty(), onClick = {
                                    batchDialog = false
                                    vm.generateBatch(voices.map { it.first }.filter { it in selected })
                                }) { Text("Generate (" + selected.size + ")") }
                            },
                            dismissButton = { TextButton(onClick = { batchDialog = false }) { Text("Cancel") } })
                    }
                }
                items(vm.models, key = { it.index }) { m ->
                    VoiceChoice(
                        m,
                        vm.voice == m.index && vm.activeClone.isEmpty(),
                        { vm.choose(m.index, autoRender = true) },
                        { vm.previewVoice(m.index) },
                        {
                            vm.navigate("Settings")
                            vm.page = "models"
                        },
                    )
                }
                items(
                    vm.profiles.filter { it.cloneReady && vm.clonePackReady(it.cloneBase) },
                    key = { "clone-" + it.id },
                ) { p ->
                    Surface(
                        onClick = { vm.chooseClone(p.id, autoRender = true) },
                        color = if (vm.activeClone == p.id) Sun.copy(alpha = .3f) else Color.White,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Line),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Mic, null, tint = Color(0xFFC08A00))
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(
                                    "${p.name} · Cloned",
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(),
                                    softWrap = false,
                                    fontSize = 14.sp,
                                )
                                Text("${if (ModelCatalog.VOICES[p.cloneBase].hima()) "Hima" else "OpenVoice"} · ${p.label} · ${ModelCatalog.VOICES[p.cloneBase].name}", fontSize = 10.sp, color = Muted)
                            }
                            IconButton(onClick = { p.clonePreview?.let(play) }) {
                                Icon(Icons.Outlined.PlayArrow, "Preview cloned ${p.name}")
                            }
                            RadioButton(selected = vm.activeClone == p.id, onClick = null)
                        }
                    }
                }
                item {
                    var showTests by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { showTests = !showTests }) {
                        Text(if (showTests) "Hide pronunciation tests" else "Pronunciation tests")
                    }
                    if (showTests)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = vm::pronunciationCheck,
                                enabled =
                                    !vm.busy && vm.models.any { it.index == vm.voice && it.ready },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("నమస్కారం", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = vm::sibilantCheck,
                                enabled =
                                    !vm.busy && vm.models.any { it.index == vm.voice && it.ready },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("శ · ష · స / ?", fontSize = 12.sp)
                            }
                        }
                }
            }
            if (vm.step == 2) {
                item { DeliveryPanel(vm) }
                item {
                    Panel {
                        SectionLabel("DELIVERY NOTES")
                        Text(
                            "${vm.plan.size} segments · ${ModelCatalog.VOICES[vm.voice].name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Review the suggested pace and pauses. Editable rule-based pacing and DSP pitch; not neural emotion or voice cloning.",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !vm.youtuber,
                                onClick = vm::neutral,
                                enabled = !vm.busy,
                                label = {
                                    Text(
                                        if (!vm.youtuber) "✓ Neutral delivery"
                                        else "Neutral delivery",
                                        maxLines = 1,
                                        fontSize = 11.sp,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            )
                            FilterChip(
                                selected = vm.youtuber,
                                onClick = vm::autoDirection,
                                enabled = !vm.busy,
                                label = {
                                    Text(
                                        if (vm.youtuber) "✓ Auto direction" else "Auto direction",
                                        maxLines = 1,
                                        fontSize = 11.sp,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Sun
                                    ),
                            )
                        }
                    }
                }
                items(vm.plan.indices.toList(), key = { it }) { index ->
                    val segment = vm.plan[index]
                    Panel {
                        val isTechnical = vm.explainTechnical && TechnicalTelugu.explain(segment.text) != segment.text.trim()
                        Row {
                            Text(
                                "${index+1}".padStart(2, '0'),
                                fontSize = 12.sp,
                                color = Vermilion,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(if (isTechnical && segment.emotion == NarrationDirector.Emotion.NEUTRAL) "TECHNICAL EXPLANATION" else segment.label(), fontSize = 11.sp, color = Muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(segment.text)
                                if (isTechnical) {
                                    Text("Spoken Telugu", fontSize = 12.sp, color = Muted)
                                    Text(vm.spokenLine(segment.text), style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    "${segment.rate}×  ·  ${segment.pauseAfterMs} ms pause",
                                    fontSize = 12.sp,
                                    color = Muted,
                                )
                            }
                            TextButton(onClick = { editing = index }, enabled = !vm.busy) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }
            if (vm.step == 3) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (vm.busy) Icons.Outlined.GraphicEq
                            else if (
                                vm.status.startsWith("Could not") || vm.status.startsWith("Failed")
                            )
                                Icons.Outlined.Info
                            else Icons.Outlined.CheckCircle,
                            null,
                            tint =
                                if (
                                    vm.busy ||
                                        vm.status.startsWith("Could not") ||
                                        vm.status.startsWith("Failed")
                                )
                                    Vermilion
                                else Color(0xFF44623F),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.latest != null && vm.status.startsWith("Ready ·")) "Completed · saved in Projects"
                            else vm.status,
                            color = Muted,
                            fontSize = 12.sp,
                            modifier =
                                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (vm.busy) TextButton(onClick = vm::cancel) { Text("Pause") }
                    }
                    if (vm.busy)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = Vermilion,
                            trackColor = Line,
                        )
                }
                item {
                    vm.latest?.let { file ->
                        Panel {
                            AudioIdentityPanel(
                                file,
                                if (vm.activeClone.isEmpty()) ModelCatalog.VOICES[vm.voice].id
                                else "clone-" + vm.activeClone,
                            )
                            Text(
                                "${String.format(Locale.US,"%.1f",file.length()/1_000_000.0)} MB · lossless WAV",
                                color = Muted,
                            )
                            var details by rememberSaveable { mutableStateOf(false) }
                            TextButton(onClick = { details = !details }) {
                                Text(if (details) "Hide audio options" else "Audio options")
                            }
                            if (details) {
                                Text(
                                    "Choose the version to play and save. Processing changes tone and loudness, not pronunciation.",
                                    color = Muted,
                                )
                                if (File(file.parentFile, "narration-studio.wav").isFile) {
                                    FilterChip(
                                        selected = file.name == "narration-studio.wav",
                                        onClick = { vm.selectOutput(true) },
                                        label = { Text("Processed · balanced volume") },
                                    )
                                }
                                if (File(file.parentFile, "narration-directed.wav").isFile)
                                    FilterChip(
                                        selected = file.name == "narration-directed.wav",
                                        onClick = vm::selectDirected,
                                        label = { Text("Voice controls · native audio") },
                                    )
                                if (File(file.parentFile, "narration-playback.wav").isFile)
                                    FilterChip(
                                        selected = file.name == "narration-playback.wav",
                                        onClick = vm::selectPlayback,
                                        label = { Text("Original · playback padding") },
                                    )
                                FilterChip(
                                    selected = file.name == "narration.wav",
                                    onClick = { vm.selectOutput(false) },
                                    label = { Text("Original · no sound processing") },
                                )
                                if (File(file.parentFile, "narration-native-warmth.wav").isFile)
                                    TextButton(onClick = vm::selectNativeWarmth) {
                                        Text("Optional warmth comparison")
                                    }
                            }
                            Text(
                                if (file.name == "narration-directed.wav")
                                    "Rule-based voice controls selected · no studio EQ stacked"
                                else if (file.name == "narration-studio.wav")
                                    "Processed audio selected"
                                else "Original engine audio selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                        ?: Panel {
                            Text(
                                "No completed audio yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Generate a narration to preview and export it here.",
                                color = Muted,
                            )
                        }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::generate,
                            enabled = !vm.busy && !vm.recording,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Sun,
                                    contentColor = Ink,
                                ),
                        ) {
                            Text(if (vm.latest == null) "Generate narration" else "Generate again")
                        }
                        vm.latest?.let { AudioMiniPlayer(vm, it, play) }
                        if (vm.inlineExport && vm.exportBusy) {
                            LinearProgressIndicator(
                                progress = { vm.exportProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${(vm.exportProgress*100).toInt()}% · ${vm.exportMessage}",
                                    Modifier.weight(1f),
                                    fontSize = 12.sp,
                                )
                                TextButton(onClick = vm::cancelExport) { Text("Cancel download") }
                            }
                        }
                        if (vm.inlineExport && vm.exportMessage.startsWith("Download failed"))
                            TextButton(onClick = { vm.download(vm.downloadFormat == "MP3") }) {
                                Text("Retry")
                            }
                        if (vm.exportMessage.isNotEmpty() && !vm.exportBusy)
                            Text(
                                vm.exportMessage,
                                color = Muted,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                    }
                }
            }
        }
        if (vm.step == 3)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { if (vm.busy) vm.step = 2 else vm.resetToScript() },
                    modifier = Modifier.weight(.45f).height(48.dp),
                    contentPadding = PaddingValues(4.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(if (vm.busy) "← Direction" else "Reset & back", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = vm::openSavedFolder,
                    modifier = Modifier.weight(.55f).height(48.dp),
                    contentPadding = PaddingValues(4.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open Saved Folder", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        if (vm.step == 1 || vm.step == 2) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                StepNavigation(
                    if (vm.step == 1) "Continue to direction →" else "Generate / Resume",
                    !vm.busy && !vm.recording,
                    { vm.step-- },
                    { if (vm.step == 1) vm.direct() else vm.generate() },
                )
            }
        }
    }
    if (editing >= 0 && editing < vm.plan.size) {
        val i = editing
        val segment = vm.plan[i]
        var emotion by remember(i) { mutableStateOf(segment.emotion) }
        var pitch by remember(i) { mutableFloatStateOf(segment.pitch) }
        var moodMenu by remember { mutableStateOf(false) }
        var rate by remember(i) { mutableFloatStateOf(segment.rate) }
        var pause by remember(i) { mutableFloatStateOf(segment.pauseAfterMs.toFloat()) }
        AlertDialog(
            onDismissRequest = { editing = -1 },
            title = { Text("Sentence ${i+1}") },
            text = {
                Column {
                    Text(segment.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Pace · ${String.format(Locale.US,"%.2f",rate)}×",
                        Modifier.padding(top = 18.dp),
                    )
                    Slider(value = rate, onValueChange = { rate = it }, valueRange = .75f..1.25f)
                    Text("Pause · ${pause.toInt()} ms")
                    Slider(value = pause, onValueChange = { pause = it }, valueRange = 0f..2000f)
                    Box {
                        TextButton(onClick = { moodMenu = true }) {
                            Text("Delivery: ${emotion.name.replace('_', ' ')} ▾")
                        }
                        DropdownMenu(expanded = moodMenu, onDismissRequest = { moodMenu = false }) {
                            NarrationDirector.Emotion.values().forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.name.replace('_', ' ')) },
                                    onClick = {
                                        emotion = choice
                                        pitch =
                                            when (choice) {
                                                NarrationDirector.Emotion.WELCOME -> 1.12f
                                                NarrationDirector.Emotion.EXCITED,
                                                NarrationDirector.Emotion.SURPRISE -> 1.20f
                                                NarrationDirector.Emotion.EMPATHY -> .92f
                                                NarrationDirector.Emotion.QUESTION -> 1.13f
                                                else -> 1f
                                            }
                                        moodMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Text("Pitch · ${String.format(Locale.US, "%.2f", pitch)}×", fontSize = 12.sp)
                    Slider(value = pitch, onValueChange = { pitch = it }, valueRange = .8f..1.2f)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.editDirection(i, rate, pause.toInt(), emotion, pitch)
                        editing = -1
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { editing = -1 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepNavigation(label: String, enabled: Boolean, back: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = back, modifier = Modifier.weight(.4f).fillMaxHeight()) {
            Text("← Back", maxLines = 1)
        }
        Button(
            onClick = next,
            enabled = enabled,
            modifier = Modifier.weight(.6f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AudioMiniPlayer(vm: StudioViewModel, file: File, play: (File) -> Unit) {
    var peaks by remember(file.path, file.lastModified()) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(file.path, file.lastModified()) {
        peaks =
            withContext(Dispatchers.IO) {
                runCatching { WaveformEnvelope.read(file) }.getOrDefault(FloatArray(0))
            }
    }
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).semantics {
                contentDescription = "Audio waveform"
            }
        ) {
            if (peaks.isNotEmpty()) {
                val width = size.width / peaks.size
                peaks.forEachIndexed { index, peak ->
                    val h = (peak * size.height).coerceAtLeast(1f)
                    drawLine(
                        Vermilion,
                        androidx.compose.ui.geometry.Offset(index * width, (size.height - h) / 2),
                        androidx.compose.ui.geometry.Offset(index * width, (size.height + h) / 2),
                        strokeWidth = width * .6f,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { play(file) }) { Icon(Icons.Outlined.PlayArrow, "Play / Pause") }
            listOf("WAV", "MP3").forEach { format ->
                val saved = vm.downloaded(format)
                OutlinedButton(
                    onClick = { vm.download(format == "MP3") },
                    enabled = !vm.exportBusy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Icon(
                        if (saved) Icons.Outlined.Check else Icons.Outlined.Download,
                        null,
                        Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (saved) "$format ✓" else format,
                        fontSize = 12.sp,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (saved) "$format Downloaded" else "Download $format"
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChoice(
    m: ModelRow,
    selected: Boolean,
    choose: () -> Unit,
    preview: () -> Unit,
    install: () -> Unit,
) {
    val accent = if (m.gender == "Male") Color(0xFF1976D2) else Color(0xFFE91E63)
    val haptic = LocalView.current
    Surface(
        Modifier.fillMaxWidth()
            .height(64.dp)
            .selectable(
                selected = selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    choose()
                },
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = .07f) else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else Line),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).background(accent.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${m.gender} · ${ModelCatalog.VOICES[m.index].engine()}",
                    fontSize = 10.sp,
                    color = Muted,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = if (m.ready) preview else install,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    Modifier.size(36.dp).background(accent.copy(alpha = .08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (m.ready) Icons.Outlined.PlayArrow else Icons.Outlined.Download,
                        if (m.ready) "Preview ${m.name}" else "Install ${m.name}",
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(selectedColor = accent),
            )
        }
    }
}

@Composable
private fun VoicesScreen(
    vm: StudioViewModel,
    play: (File) -> Unit,
    export: (File) -> Unit,
    record: (String, String) -> Unit,
) {
    var showRecord by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ProfileRow?>(null) }
    var trim by remember { mutableStateOf<ProfileRow?>(null) }
    var deleting by remember { mutableStateOf<ProfileRow?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageTitle("Your recordings", "Record, clean and export your own audio.")
            Text(
                "To narrate in your voice, create and listen to a local cloning preview first.",
                fontSize = 12.sp,
                color = Muted,
            )
        }
        item {
            Panel {
                Row {
                    Icon(Icons.Outlined.Mic, null, tint = Vermilion)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (vm.recording) "Microphone active" else "Capture your voice",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (vm.recording) {
                    Text(
                        duration(vm.recordMillis),
                        fontSize = 40.sp,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { vm.recordMillis / 60000f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Vermilion,
                        trackColor = Line,
                    )
                    Text(
                        if (vm.paused) "Paused · time is held" else "Recording · up to 60 seconds",
                        color = Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Secondary(
                            if (vm.paused) "Resume" else "Pause",
                            if (vm.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        ) {
                            vm.recordingAction(if (vm.paused) "resume" else "pause")
                        }
                        Secondary("Stop & save", Icons.Outlined.Stop) { vm.recordingAction("stop") }
                    }
                } else {
                    Text(
                        "Record up to 60 seconds. Navigation will not stop your recording.",
                        color = Muted,
                    )
                    Primary("New voice recording", !vm.busy) { showRecord = true }
                }
                Text(vm.recordingStatus + if (vm.recording) "" else if (vm.clonePackReady(vm.voice)) " · Voice cloning ready offline" else " · Install the selected cloning pack in Settings", fontSize = 12.sp, color = Muted)
            }
        }
        item {
            if (vm.busy && StudioJobService.enhancing)
                Panel {
                    Text(vm.status, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { StudioJobService.cancel() }) { Text("Cancel") }
                }
            else if (
                vm.status.startsWith("Completed ·") ||
                    vm.status.startsWith("Cleanup stopped") ||
                    vm.status.startsWith("Could not")
            ) {
                Text(vm.status, fontSize = 13.sp)
            } else if (vm.referenceStatus.isNotBlank()) {
                Text(vm.referenceStatus, fontSize = 12.sp, color = Muted)
            }
        }
        item { SectionLabel("SAVED REFERENCES · ${vm.profiles.size}") }
        if (vm.profiles.isEmpty())
            item {
                Panel {
                    Icon(Icons.Outlined.VoiceOverOff, null, tint = Muted)
                    Text("Your library starts here", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a named reference above. Recordings remain on your device.",
                        color = Muted,
                    )
                }
            }
        items(vm.profiles, key = { it.id }) { p ->
            Surface(
                Modifier.animateItem(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Line),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var more by remember(p.id) { mutableStateOf(false) }
                    var cleanup by remember(p.id) { mutableStateOf(false) }
                    var roomTail by remember(p.id) { mutableStateOf(false) }
                    var create by remember(p.id) { mutableStateOf(false) }
                    var consent by remember(p.id) { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.displayStem,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${duration(p.duration)} · ${p.status}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                color = Muted,
                            )
                        }
                        IconButton(onClick = { more = !more }) {
                            Icon(Icons.Outlined.MoreVert, "Recording actions")
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { play(p.file) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("Play", maxLines = 1, softWrap = false)
                        }
                        TextButton(
                            onClick = { vm.selectProfile(p.id) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(
                                if (vm.selectedReference == p.id) "✓ Selected" else "Select",
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(
                            onClick = { cleanup = true },
                            enabled = !vm.busy,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("Enhance", maxLines = 1, softWrap = false, fontSize = 12.sp)
                        }
                    }
                    if (more) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { edit = p }, enabled = !vm.busy) {
                                Text("Rename", maxLines = 1)
                            }
                            TextButton(onClick = { trim = p }, enabled = !vm.busy) {
                                Text("Trim", maxLines = 1)
                            }
                            TextButton(onClick = { export(p.file) }) {
                                Text("Export", maxLines = 1)
                            }
                            IconButton(onClick = { deleting = p }, enabled = !vm.busy) {
                                Icon(Icons.Outlined.DeleteOutline, "Delete ${p.name}")
                            }
                        }
                        Text(
                            "Original recording is retained. Select marks a reference; it does not change the narrator.",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (vm.clonePackReady(vm.voice)) create = true
                                else {
                                    vm.navigate("Settings")
                                    vm.page = "cloning"
                                }
                            },
                            enabled = !vm.busy,
                        ) {
                            Text(
                                if (p.cloneReady) "Recreate voice preview"
                                else "Create voice preview",
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        if (p.cloneReady)
                            IconButton(onClick = { p.clonePreview?.let(play) }) {
                                Icon(Icons.Outlined.PlayArrow, "Play cloned preview")
                            }
                        if (p.cloneReady && vm.clonePackReady(p.cloneBase))
                            TextButton(
                                onClick = {
                                    vm.chooseClone(p.id)
                                    vm.navigate("Studio")
                                    vm.step = 1
                                }
                            ) {
                                Text("Use", maxLines = 1)
                            }
                    }
                    if (cleanup)
                        AlertDialog(
                            onDismissRequest = { cleanup = false },
                            title = { Text("Enhance recording") },
                            text = {
                                Column {
                                    Text(
                                        "Updates this card. The original stays saved; repeated cleanup starts from the original."
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(roomTail, { roomTail = it })
                                        Text("Reduce room tail · experimental")
                                    }
                                    Text("Noise and echo may remain.", fontSize = 12.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        cleanup = false
                                        vm.enhanceProfile(p.id, roomTail)
                                    }
                                ) {
                                    Text("Enhance")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { cleanup = false }) { Text("Cancel") }
                            },
                        )
                    if (create)
                        AlertDialog(
                            onDismissRequest = { create = false },
                            title = { Text("Create my voice preview") },
                            text = {
                                Column {
                                    Text(
                                        "${if (ModelCatalog.VOICES[vm.voice].hima()) "Hima extracts your speaker identity and speech features." else "OpenVoice converts the selected narrator."} Selected narrator: ${ModelCatalog.VOICES[ModelCatalog.previewBase(p.label, vm.voice)].name}. Listen to the preview before generating."
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(consent, { consent = it })
                                        Text("This is my voice, or I have permission to clone it.")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = consent,
                                    onClick = {
                                        create = false
                                        vm.speakerAction("clone-create", p.id)
                                    },
                                ) {
                                    Text("Create preview")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { create = false }) { Text("Cancel") }
                            },
                        )
                }
            }
        }
        item {
            Panel {
                SectionLabel("NARRATOR VOICES")
                Text(
                    "Download narrator models in Settings → Offline models. Select Hima or OpenVoice in Settings → Voice cloning.",
                    color = Muted,
                )
                Secondary("Manage offline models", Icons.Outlined.Tune) {
                    vm.navigate("Settings")
                    vm.page = "models"
                }
            }
        }
    }
    if (showRecord)
        ProfileDialog("New recording", "My voice", "Custom", "", true, { showRecord = false }) {
            name,
            label,
            _ ->
            showRecord = false
            record(name, label)
        }
    edit?.let { p ->
        ProfileDialog("Edit reference", p.name, p.label, p.description, false, { edit = null }) {
            n,
            l,
            d ->
            vm.renameProfile(p.id, n, l, d)
            edit = null
        }
    }
    trim?.let { p ->
        var start by remember(p.id) { mutableStateOf("0") }
        var end by remember(p.id) { mutableStateOf((p.duration / 1000.0).toString()) }
        AlertDialog(
            onDismissRequest = { trim = null },
            title = { Text("Trim a copy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("The original stays in your library. AAC cuts align to audio frames.")
                    OutlinedTextField(
                        start,
                        { start = it },
                        label = { Text("Start seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        end,
                        { end = it },
                        label = { Text("End seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = start.toDoubleOrNull()
                        val to = end.toDoubleOrNull()
                        if (from != null && to != null) {
                            vm.trimProfile(p.id, (from * 1000).toLong(), (to * 1000).toLong())
                            trim = null
                        } else vm.notice = "Enter valid seconds."
                    }
                ) {
                    Text("Save trimmed copy")
                }
            },
            dismissButton = { TextButton(onClick = { trim = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { p ->
        ConfirmDelete(
            "Delete ${p.name}?",
            "This removes this local recording. Other profiles are kept.",
            { deleting = null },
        ) {
            vm.deleteProfile(p.id)
            deleting = null
        }
    }
}

@Composable
private fun ProfileDialog(
    title: String,
    initialName: String,
    initialLabel: String,
    initialDescription: String,
    consent: Boolean,
    close: () -> Unit,
    save: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var label by remember { mutableStateOf(initialLabel) }
    var description by remember { mutableStateOf(initialDescription) }
    var allowed by remember { mutableStateOf(!consent) }
    AlertDialog(
        modifier = Modifier.widthIn(min = 280.dp, max = 360.dp).fillMaxWidth(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = close,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Male", "Female", "Custom").forEach { s ->
                        FilterChip(
                            selected = label == s,
                            onClick = { label = s },
                            label = { Text(s, fontSize = 12.sp) },
                        )
                    }
                }
                if (!consent)
                    OutlinedTextField(
                        description,
                        { description = it.take(500) },
                        label = { Text("Description (optional)") },
                    )
                if (consent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(allowed, { allowed = it })
                        Text(
                            "This is my voice, or I have the speaker's permission.",
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "Recording alone is not a clone. Create a local voice preview after saving.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save(name, label, description) },
                enabled = allowed && name.isNotBlank(),
            ) {
                Text(if (consent) "Start recording" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
private fun ProjectsScreen(vm: StudioViewModel, play: (File) -> Unit, export: (File) -> Unit) {
    var rename by remember { mutableStateOf<ProjectRow?>(null) }
    var delete by remember { mutableStateOf<ProjectRow?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("projects-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageTitle("Your projects", "Saved audio and resumable narration sessions.") }
        if (vm.projects.isEmpty())
            item {
                Text("Your first narration will appear here.", color = Muted)
                Primary("Create narration") { vm.navigate("Studio") }
            }
        items(vm.projects, key = { it.folder.path }) { p ->
            Surface(
                Modifier.fillMaxWidth().animateItem(),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Line),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        p.name,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (vm.renderingFolder == p.folder) "Generating audio…"
                        else if (p.ready) "${duration((p.duration*1000).toLong())} · WAV"
                        else "Paused · segments saved",
                        fontSize = 10.sp,
                        color = Muted,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ProjectAction(
                            "Play",
                            Icons.Outlined.PlayArrow,
                            p.ready && vm.renderingFolder != p.folder,
                            Modifier.weight(1f),
                        ) {
                            play(vm.projectAudio(p.folder))
                        }
                        ProjectAction(
                            "Export",
                            Icons.Outlined.FileDownload,
                            p.ready && vm.renderingFolder != p.folder,
                            Modifier.weight(1f),
                        ) {
                            export(vm.projectAudio(p.folder))
                        }
                        ProjectAction(
                            "Open script",
                            Icons.Outlined.Edit,
                            true,
                            Modifier.weight(1f),
                        ) {
                            vm.openProject(p)
                        }
                        ProjectAction("Rename", Icons.Outlined.Edit, true, Modifier.weight(1f)) {
                            rename = p
                        }
                        ProjectAction(
                            "Delete",
                            Icons.Outlined.DeleteOutline,
                            vm.renderingFolder != p.folder,
                            Modifier.weight(1f),
                        ) {
                            delete = p
                        }
                    }
                }
            }
        }
    }
    rename?.let { p ->
        var name by remember(p.folder) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Project name") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameProject(p, name)
                        rename = null
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } },
        )
    }
    delete?.let { p ->
        ConfirmDelete(
            "Delete this project?",
            "Local audio and render chunks will be removed. Files already exported elsewhere are kept.",
            { delete = null },
        ) {
            vm.deleteProject(p)
            delete = null
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, message: String, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete", color = Vermilion) } },
        dismissButton = { TextButton(onClick = close) { Text("Keep") } },
    )
}

@Composable
private fun SettingsScreen(vm: StudioViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("Make it yours", "Offline tools, clear controls and private files.") }
        item {
            SettingsTile(
                "Offline models",
                "Installed voices, storage, repair and updates",
                Icons.Outlined.Download,
            ) {
                vm.page = "models"
            }
        }
        item {
            SettingsTile(
                "Pronunciation dictionary",
                "Control spoken words without editing your script",
                Icons.Outlined.Translate,
            ) {
                vm.page = "dictionary"
            }
        }
        item {
            SettingsTile(
                "Sample audio",
                "Listen to the offline narrator samples",
                Icons.Outlined.PlayArrow,
            ) {
                vm.page = "samples"
            }
        }
        item {
            SettingsTile(
                "Voice cloning",
                "Hima and OpenVoice · local voice previews",
                Icons.Outlined.Mic,
            ) {
                vm.page = "cloning"
            }
        }
        item {
            SettingsTile(
                "Advanced recording tools",
                "Private speaker fingerprints · not voice cloning",
                Icons.Outlined.Tune,
            ) {
                vm.page = "reference-tools"
            }
        }
        item {
            Panel {
                SectionLabel("AUDIO QUALITY · EXPERIMENTAL")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Also create native EQ comparison",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Optional warmth EQ. OFF by default to preserve consonant clarity. Compare with original audio.",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                    Switch(
                        checked = vm.warmthEq,
                        onCheckedChange = vm::toggleWarmthEq,
                        enabled = !vm.busy,
                    )
                }
                Text(
                    "Studio master uses 44.1 kHz and balanced volume. Warmth applies to the comparison file only; it does not repair missing phonemes.",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }
        item {
            Panel {
                SectionLabel("ON THIS DEVICE")
                Text("Piper, Indic Tiny, Hima and Indic-TTS", style = MaterialTheme.typography.titleMedium)
                val context = LocalContext.current
                val version = remember { AppIdentity.version(context) }
                Text(
                    "App version $version · ${AppIdentity.channel(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Text(
                    "Telugu neural voices · ARM64 CPU\n2 inference threads · no cloud generation",
                    color = Muted,
                )
                Text(
                    "Updates keep your local recordings, models and projects.",
                    fontSize = 12.sp,
                    color = Muted,
                )
                Text(context.packageName, fontSize = 11.sp, color = Muted)
                HorizontalDivider(color = Line)
                Text(
                    "Personal voices require a local cloning pack and a successful preview.",
                    fontSize = 13.sp,
                    color = Muted,
                )
            }
        }
        item {
            Panel {
                SectionLabel("QUALITY & PRIVACY")
                Text(
                    "Your scripts and recordings remain on this device. Model files download only when requested. Voice references are not uploaded or trained automatically.",
                    color = Muted,
                )
                Text(
                    "Installed build: ${AppIdentity.label(LocalContext.current)}",
                    fontSize = 12.sp,
                )
                Text(
                    "GPL-3.0-or-later · sherpa Apache-2.0 · ONNX Runtime 1.31 MIT · Indic Tiny / Rasa CC BY 4.0 · Vocos MIT · Jump3r LGPL · eSpeak GPL · Piper voices CC BY 4.0 (PravalX / AI4Bharat). OpenVoice V2: MIT · RNNoise: BSD-3-Clause · CMU dictionary: BSD-style. Manrope & DM Serif Display: OFL.",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun SettingsTile(title: String, subtitle: String, icon: ImageVector, click: () -> Unit) {
    Surface(
        onClick = click,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Line),
        color = Color.White,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Vermilion)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, fontSize = 12.sp, color = Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun ModelsScreen(vm: StudioViewModel, play: (File) -> Unit) {
    var category by rememberSaveable { mutableStateOf("Installed") }
    var deleting by remember { mutableIntStateOf(-1) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = { vm.page = "" }) { Text("← Settings") }
            PageTitle("Offline models", "Download once. Keep creating without a connection.")
            Panel {
                Text("Offline pronunciation check", style = MaterialTheme.typography.titleMedium)
                Text(if (vm.checkerReady) "Ready · checks each phrase against the script and retries unclear speech."
                     else "Optional Telugu speech recognition for all narrators. Downloads 281 MB once and keeps 197 MB.", color = Muted)
                if (vm.checkerReady) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Check generated speech", modifier = Modifier.weight(1f))
                        Switch(vm.verifyWords, vm::usePronunciationCheck, enabled = !vm.busy)
                    }
                } else Primary("Download pronunciation check", !vm.busy && !vm.loading) { vm.installChecker() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Installed", "All voices").forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextButton(onClick = vm::checkUpdates, enabled = !vm.busy) {
                Text("Check model updates")
            }
        }
        if (category == "Installed" || category == "All voices") {
            val shown =
                if (category == "Installed")
                    vm.models.filter {
                        it.ready || it.partial > 0 || it.damaged || vm.downloadingVoice == it.index
                    }
                else vm.models
            if (shown.isEmpty())
                item {
                    Panel {
                        Text(
                            "No models installed yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Piper: 63.5 MB per voice. Indic Tiny: 91 MB shared male/female pack.",
                            color = Muted,
                        )
                        Primary("Browse Telugu voices") { category = "All voices" }
                    }
                }
            items(shown, key = { it.index }) { m ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${m.gender} · ${ModelCatalog.VOICES[m.index].engine()}",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                        StatusPill(
                            if (vm.downloadingVoice == m.index) "DOWNLOADING"
                            else if (m.ready) "READY OFFLINE"
                            else if (m.damaged) "VERIFY NEEDED"
                            else if (m.partial > 0) "PAUSED" else "NOT INSTALLED",
                            if (m.ready) Color(0xFFE9F0E4) else Paper,
                        )
                    }
                    var details by rememberSaveable(m.index) { mutableStateOf(false) }
                    Text(
                        if (ModelCatalog.VOICES[m.index].hima()) "102 MB shared download · 24 kHz"
                        else if (ModelCatalog.VOICES[m.index].fastPitch()) "281 MB download · 137 MB installed · 22.05 kHz"
                        else if (ModelCatalog.VOICES[m.index].tiny()) "91 MB shared pack · 24 kHz"
                        else "63.5 MB · 22.05 kHz",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                    TextButton(onClick = { details = !details }) {
                        Text(if (details) "Hide details" else "Model details")
                    }
                    if (details) {
                        Text(
                            if (ModelCatalog.VOICES[m.index].alternative())
                                "One shared pack installs both speakers. Removal affects both. Inference runs on the device."
                            else if (ModelCatalog.VOICES[m.index].tiny())
                                "Indic Tiny Telugu · experimental\n91 MB shared pack · 24 kHz · CC BY 4.0 + MIT\nRevision ${TinyModelStore.REVISION.take(12)}\nTelugu script only. Android phone validation pending."
                            else
                                "ID: te_IN-${ModelCatalog.VOICES[m.index].id}-medium\n63.5 MB · 22.05 kHz · CC BY 4.0\nStart-token repair v1 · Revision ${ModelCatalog.REVISION.take(12)}\nUpgraded installs keep a 63.5 MB backup.",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                    if (vm.verifying == m.index)
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Vermilion)
                    vm.verification[m.index]?.let {
                        Text(
                            it,
                            color = Muted,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (m.ready) {
                        Primary("Use ${m.name}", !vm.busy) {
                            vm.choose(m.index)
                            vm.navigate("Studio")
                            vm.step = 1
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Secondary(
                                if (vm.verifying == m.index) "Verifying…" else "Verify files",
                                Icons.Outlined.VerifiedUser,
                                !vm.busy && vm.verifying < 0,
                            ) {
                                vm.verify(m.index)
                            }
                            Secondary(
                                "Delete",
                                Icons.Outlined.DeleteOutline,
                                !vm.busy && vm.verifying < 0,
                            ) {
                                deleting = m.index
                            }
                        }
                    } else {
                        if (m.damaged)
                            Secondary("Verify local file", Icons.Outlined.VerifiedUser, !vm.busy) {
                                vm.verify(m.index)
                            }
                        if (m.partial > 0)
                            Text(
                                "${m.partial/1_000_000} MB saved · resumes when supported",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        Primary(
                            if (m.damaged) "Repair voice"
                            else if (m.partial > 0) "Resume download" else "Download voice",
                            !vm.busy && !vm.recording,
                        ) {
                            vm.install(m.index)
                        }
                        if (m.partial > 0)
                            TextButton(
                                onClick = { vm.restartDownload(m.index) },
                                enabled = !vm.busy,
                            ) {
                                Text("Restart this download")
                            }
                    }
                }
            }
            if (vm.busy)
                item {
                    Panel {
                        Text(
                            if (vm.status.startsWith("Ready ·"))
                                "Narration complete. Listen, then save your audio."
                            else vm.status,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Vermilion)
                        Secondary("Pause task", Icons.Outlined.Pause, action = vm::cancel)
                    }
                }
        }
    }
    if (deleting >= 0)
        ConfirmDelete(
            "Remove ${ModelCatalog.VOICES[deleting].name}?",
            "This removes the voice weights. Speakers that share this pack are removed together. Recordings, scripts and exports are kept.",
            { deleting = -1 },
        ) {
            vm.deleteModel(deleting)
            deleting = -1
        }
}

@Composable
private fun DictionaryScreen(vm: StudioViewModel) {
    var original by rememberSaveable { mutableStateOf("") }
    var spoken by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = { vm.page = "" }) { Text("← Settings") }
            PageTitle("Say it your way", "Your original script remains untouched.")
        }
        item {
            Panel {
                OutlinedTextField(
                    original,
                    { original = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Original word / phrase") },
                    placeholder = { Text("friends") },
                )
                OutlinedTextField(
                    spoken,
                    { spoken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spoken Telugu form") },
                    placeholder = { Text("ఫ్రెండ్స్") },
                )
                Primary("Save pronunciation", original.isNotBlank() && spoken.isNotBlank()) {
                    vm.dictionary(original, spoken)
                    original = ""
                    spoken = ""
                }
                Text(
                    "Exact word/phrase matches only. Corrections apply to speech input, not your saved text.",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
        }
        items(vm.entries.toList(), key = { it.first }) { entry ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.first, style = MaterialTheme.typography.titleMedium)
                        Text("→ ${entry.second}", color = Muted)
                    }
                    IconButton(onClick = { vm.removeEntry(entry.first) }) {
                        Icon(Icons.Outlined.DeleteOutline, "Remove ${entry.first}")
                    }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Read numbers digit by digit",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Optional: 15 → ఒకటి ఐదు. Useful for codes; not natural number expansion.",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                    Switch(vm.readDigits, vm::digits)
                }
                Secondary("Preview speech input", Icons.Outlined.Visibility) { preview = true }
            }
        }
    }
    if (preview)
        AlertDialog(
            onDismissRequest = { preview = false },
            title = { Text("Speech input preview") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(vm.speechPreview())
                }
            },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("Close") } },
        )
}

@Composable
private fun ExportDialogs(vm: StudioViewModel) {
    if (vm.inlineExport) return
    val context = LocalContext.current
    if (vm.exportSource != null && !vm.exportBusy) {
        val source = vm.exportSource!!
        AlertDialog(
            onDismissRequest = { vm.exportSource = null },
            title = { Text("Save your audio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a format, then select a folder on your phone.")
                    if (source.extension == "wav") {
                        Button(
                            onClick = { vm.prepareExport(false) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save WAV · full quality")
                        }
                        OutlinedButton(
                            onClick = { vm.prepareExport(true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save MP3 · 96 kbps mono")
                        }
                        Text(
                            "MP3 is created offline. Your original WAV is always kept.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else
                        Button(
                            onClick = { vm.prepareExport(false) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save voice recording")
                        }
                    if (vm.exportMessage.startsWith("Could not"))
                        Text(vm.exportMessage, color = Vermilion)
                }
            },
            confirmButton = { TextButton(onClick = { vm.exportSource = null }) { Text("Cancel") } },
        )
    }
    if (vm.exportBusy)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (vm.exportMessage.startsWith("Saving")) "Saving audio" else "Preparing audio"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.exportMessage)
                    LinearProgressIndicator(
                        progress = { vm.exportProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Vermilion,
                    )
                    Text(
                        "${(vm.exportProgress*100).toInt()}%",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelExport) { Text("Cancel save") } },
        )
    vm.exportedUri?.let { uri ->
        val mime =
            if (vm.exportedName.endsWith(".mp3")) "audio/mpeg"
            else if (vm.exportedName.endsWith(".wav")) "audio/wav" else "audio/mp4"
        AlertDialog(
            onDismissRequest = { vm.exportedUri = null },
            icon = { Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF366638)) },
            title = { Text("Audio saved") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(vm.exportedName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Saved to the folder you selected. Open it now or share it with your video editor."
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                        context.startActivity(
                                            android.content
                                                .Intent(android.content.Intent.ACTION_VIEW)
                                                .setDataAndType(uri, mime)
                                                .addFlags(
                                                    android.content.Intent
                                                        .FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                        )
                                    }
                                    .onFailure {
                                        vm.notice =
                                            "No audio player is available. Use Share to open it in your editor."
                                    }
                            }
                        ) {
                            Text("Open")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                        val intent =
                                            android.content
                                                .Intent(android.content.Intent.ACTION_SEND)
                                                .setType(mime)
                                                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                .addFlags(
                                                    android.content.Intent
                                                        .FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                intent,
                                                "Share narration",
                                            )
                                        )
                                    }
                                    .onFailure {
                                        vm.notice =
                                            "Could not open sharing. Your saved file is kept."
                                    }
                            }
                        ) {
                            Text("Share")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.exportedUri = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun AudioIdentityPanel(file: File, selected: String) {
    var identity by remember(file.parent) { mutableStateOf<AudioIdentity?>(null) }
    LaunchedEffect(file.parent) {
        identity = withContext(Dispatchers.IO) { AudioIdentity.read(file) }
    }
    identity?.let { info ->
        Text("Audio voice: ${info.name}", style = MaterialTheme.typography.titleMedium)
        Text(info.engine, style = MaterialTheme.typography.bodySmall, color = Muted)
        if (info.id.isNotEmpty() && info.id != selected) {
            val next = ModelCatalog.VOICES.firstOrNull { it.id == selected }?.name ?: selected
            Text(
                "Selected for next render: $next. Generate again to hear that voice.",
                color = Vermilion,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DeliveryPanel(vm: StudioViewModel) {
    Panel {
        SectionLabel("OFFLINE DELIVERY CONTROLS")
        Text(if (vm.activeClone.isEmpty()) ModelCatalog.VOICES[vm.voice].name else vm.profiles.firstOrNull { it.id == vm.activeClone }?.name ?: "Cloned voice", style = MaterialTheme.typography.titleMedium)
        Text("Offline speech cleanup · automatic", color = Muted)
        listOf(
                Triple("youtuber", "YouTuber delivery · conversational timing", vm.youtuber),

                Triple("technical", "Explain English technical terms and maths in Telugu", vm.explainTechnical),
                Triple("question", "Question endings · +13%", vm.questionLift),
                Triple("comma", "Comma breath · 110 ms", vm.commaBuffer),
                Triple("careful", "Careful pace · complex clauses", vm.carefulDiction),
                Triple("soft", "Female tone · gentle EQ", vm.femaleSoftening),
            )
            .filter { it.first != "soft" || (vm.activeClone.isEmpty() && ModelCatalog.VOICES[vm.voice].tiny() && ModelCatalog.VOICES[vm.voice].gender == "Female") }
            .forEach { (key, label, enabled) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { vm.setDelivery(key, it) },
                        enabled = !vm.busy,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        Text(
            "YouTuber delivery adapts phrase timing to recorded Telugu conversations. Each narrator can generate a second take. Cloned voices follow their base narrator’s timing.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
private fun SamplesScreen(vm: StudioViewModel, play: (File) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = { vm.page = "" }) { Text("← Settings") } }

        item {
            Panel {
                StatusPill("BUNDLED EXAMPLES", Paper)
                Text("Compare Telugu pronunciation", style = MaterialTheme.typography.titleMedium)
                Text("నమస్కారం ఫ్రెండ్స్!")
                Text(
                    "Pre-generated examples. To hear your own script, choose a narrator in Studio and generate it locally.",
                    color = Muted,
                )
            }
        }
        items(vm.comparisons, key = { it.first }) { sample ->
            Panel {
                Text(sample.first, style = MaterialTheme.typography.titleMedium)
                Secondary("Play / Pause sample", Icons.Outlined.PlayArrow) { play(sample.second) }
            }
        }
    }
}

@Composable
private fun ReferenceToolsScreen(vm: StudioViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = { vm.page = "" }) { Text("← Settings") } }
        item {
            Panel {
                Text(
                    "Speaker fingerprint · ECAPA-TDNN",
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusPill(if (vm.speakerReady) "READY OFFLINE" else "NOT INSTALLED", Paper)
                Text(
                    "79.7 MiB · Apache 2.0 · 192 dimensions. A speaker verification encoder; Piper and Indic Tiny cannot use this vector for cloning.",
                    fontSize = 13.sp,
                    color = Muted,
                )
                Text(
                    "RNNoise cleanup is included in the app. WPE room-tail reduction is optional in Voices → Enhance.",
                    fontSize = 13.sp,
                )
                if (!vm.speakerReady)
                    Primary("Download speaker encoder", !vm.busy && !vm.recording) {
                        vm.speakerAction("speaker-install")
                    }
                else
                    Row {
                        TextButton(
                            onClick = { vm.speakerAction("verify") },
                            enabled = !vm.busy && !vm.loading,
                        ) {
                            Text("Verify files")
                        }
                        TextButton(
                            onClick = { vm.speakerAction("remove") },
                            enabled = !vm.busy && !vm.loading,
                        ) {
                            Text("Remove encoder")
                        }
                    }
                if (vm.busy && StudioJobService.enhancing) {
                    Text(vm.status, fontSize = 12.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { StudioJobService.cancel() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun CloningScreen(vm: StudioViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = { vm.page = "" }) { Text("← Settings") }
            PageTitle("Your voice, locally", "Create a preview before using a personal narrator.")
        }
        item {
            Panel {
                Text("Hima · native Telugu cloning", style = MaterialTheme.typography.titleMedium)
                Text("Uses the speaker and acoustic features of your recording. Up to 325 MB including the Hima voices and speaker encoder.", fontSize = 13.sp)
                if (vm.himaCloneReady) {
                    StatusPill("READY OFFLINE", Paper)
                    Primary("Create my Hima voice", !vm.busy) { vm.choose(4); vm.navigate("Voices") }
                } else Primary("Download Hima cloning", !vm.busy) { vm.speakerAction("hima-install") }
            }
        }
        item {
            Panel {
                Text("OpenVoice V2", style = MaterialTheme.typography.titleMedium)
                StatusPill(if (vm.cloneModelReady) "READY OFFLINE" else "NOT INSTALLED", Paper)
                Text("41.1 MB · MIT · one-time download", fontSize = 12.sp, color = Muted)
                Text(
                    "Use at least 10 seconds of your own clear voice. A successful preview unlocks that voice in Studio. Telugu clarity and similarity need listening checks.",
                    fontSize = 13.sp,
                )
                if (!vm.cloneModelReady)
                    Primary("Download OpenVoice", !vm.busy) { vm.speakerAction("clone-install") }
                else {
                    Primary("Open my recordings", !vm.busy) { vm.navigate("Voices") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.clonePackAction(false) },
                            enabled = !vm.busy && !vm.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Verify files", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { vm.clonePackAction(true) },
                            enabled = !vm.busy && !vm.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Remove pack", maxLines = 1)
                        }
                    }
                }
                if (vm.busy) {
                    Text(vm.status, fontSize = 12.sp)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = vm::cancel) { Text("Cancel") }
                }
                Text(
                    "Processing stays on this device. Zero echo and perfect voice similarity are not guaranteed.",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun ProjectAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    action: () -> Unit,
) {
    TextButton(
        onClick = action,
        enabled = enabled,
        modifier = modifier.height(48.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(50),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(17.dp))
            Text(
                label,
                maxLines = 1,
                softWrap = false,
                fontSize = 9.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
