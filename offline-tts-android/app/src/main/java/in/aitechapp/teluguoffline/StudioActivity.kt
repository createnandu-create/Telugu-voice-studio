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
        setContent { StudioTheme { StudioApp(model, ::play, ::export, ::record) } }
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
                    setOnCompletionListener {
                        playingFile = null
                        player?.release()
                        player = null
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
                    "Telugu-narration-${System.currentTimeMillis()}.${file.extension}",
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
    LaunchedEffect(vm.exportReady) {
        vm.exportReady?.let {
            vm.exportReady = null
            export(it)
        }
    }
    val requestExport: (File) -> Unit = {
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
                        if (vm.loading) "CHECKING" else if (vm.busy) "WORKING" else "LOCAL",
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
    ) {
        Text(label)
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label)
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
    LazyColumn(
        Modifier.fillMaxSize().testTag("studio-list"),
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
            item { SectionLabel("NEURAL NARRATORS") }
            items(vm.models, key = { it.index }) { m ->
                VoiceChoice(
                    m,
                    vm.voice == m.index,
                    { vm.choose(m.index) },
                    {
                        vm.navigate("Settings")
                        vm.page = "models"
                    },
                )
            }
            item {
                Text(
                    "Piper and Indic Tiny are fixed-speaker voices. Indic Tiny is an experimental alternative for Telugu pronunciation; it does not clone your voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            item {
                OutlinedButton(
                    onClick = vm::pronunciationCheck,
                    enabled = !vm.busy && vm.models.any { it.index == vm.voice && it.ready },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check నమస్కారం pronunciation")
                }
                Text(
                    "Creates new audio with this voice: one word, greeting, then the word inside a sentence. Your script stays unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            item {
                Primary("Continue to direction →", !vm.busy) { vm.direct() }
                TextButton(onClick = { vm.step = 0 }) { Text("← Back to script") }
            }
        }
        if (vm.step == 2) {
            item {
                Panel {
                    SectionLabel("DELIVERY NOTES")
                    Text(
                        "${vm.plan.size} segments · ${ModelCatalog.VOICES[vm.voice].name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Review the suggested pace and pauses. Emotion labels are notes; Piper cannot synthesize them.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Secondary("Neutral delivery", Icons.Outlined.Tune, !vm.busy, vm::neutral)
                }
            }
            items(vm.plan.indices.toList(), key = { it }) { index ->
                val segment = vm.plan[index]
                Panel {
                    Row {
                        Text(
                            "${index+1}".padStart(2, '0'),
                            fontSize = 12.sp,
                            color = Vermilion,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(segment.label(), fontSize = 11.sp, color = Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(segment.text)
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
            item {
                Primary("Generate / Resume", !vm.busy && !vm.recording, action = vm::generate)
                TextButton(onClick = { vm.step = 1 }) { Text("← Change narrator") }
            }
        }
        if (vm.step == 3) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (vm.busy) Icons.Outlined.GraphicEq else Icons.Outlined.Headphones,
                            null,
                            tint = Vermilion,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (vm.busy) "Creating on your phone" else "Your audio workspace",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        if (vm.status.startsWith("Ready ·"))
                            "Narration complete. Listen, then save your audio."
                        else vm.status,
                        color = Muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (vm.busy) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = Vermilion,
                            trackColor = Line,
                        )
                        Secondary("Pause task", Icons.Outlined.Pause, action = vm::cancel)
                    }
                    Text(
                        "Completed segments are kept if you pause or leave the app.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            }
            item {
                vm.latest?.let { file ->
                    Panel {
                        StatusPill("LATEST COMPLETED AUDIO", Color(0xFFE9F0E4))
                        Text("Ready for your video", style = MaterialTheme.typography.titleMedium)
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
                            if (file.name == "narration-studio.wav") "Processed audio selected"
                            else "Original engine audio selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                    ?: Panel {
                        Text("No completed audio yet", style = MaterialTheme.typography.titleMedium)
                        Text("Generate a narration to preview and export it here.", color = Muted)
                    }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::generate,
                        enabled = !vm.busy && !vm.recording,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
                    ) {
                        Text(if (vm.latest == null) "Generate narration" else "Generate again")
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.latest?.let(play) },
                            enabled = vm.latest != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text("Play / Pause")
                        }
                        OutlinedButton(
                            onClick = { vm.latest?.let(export) },
                            enabled = vm.latest != null && !vm.exportBusy,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text("Save audio")
                        }
                    }
                    if (vm.exportMessage.isNotEmpty())
                        Text(
                            vm.exportMessage,
                            color = Muted,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                }
            }

            item { TextButton(onClick = { vm.step = 2 }) { Text("← Review direction") } }
        }
    }
    if (editing >= 0 && editing < vm.plan.size) {
        val i = editing
        val segment = vm.plan[i]
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
                    Text(
                        "Emotion note: ${segment.label()} · not synthesized by Piper",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.editDirection(i, rate, pause.toInt(), segment.emotion)
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
private fun VoiceChoice(m: ModelRow, selected: Boolean, choose: () -> Unit, install: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = choose, role = Role.RadioButton),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFFFF5CC) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Sun else Line),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp)
                    .background(
                        if (m.gender == "Male") Color(0xFFE5EDF0) else Color(0xFFF9E9E2),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    null,
                    tint = if (m.gender == "Male") Color(0xFF335E70) else Vermilion,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${m.gender} · ${if(ModelCatalog.VOICES[m.index].tiny()) "Indic Tiny · experimental" else "Piper Telugu"}",
                    fontSize = 12.sp,
                    color = Muted,
                )
                Text(
                    if (m.ready) "Ready offline" else "Download needed",
                    fontSize = 11.sp,
                    color = if (m.ready) Color(0xFF44623F) else Vermilion,
                )
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Ink)
            else if (!m.ready)
                IconButton(onClick = install) { Icon(Icons.Outlined.Download, "Install ${m.name}") }
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
        item { PageTitle("Your voice library", "A private place for your personal references.") }
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
                Text(vm.recordingStatus, fontSize = 12.sp, color = Muted)
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
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.label} · ${duration(p.duration)}",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                    if (vm.selectedReference == p.id) StatusPill("SELECTED", Color(0xFFE9F0E4))
                }
                Text(p.status, fontSize = 12.sp, color = Muted)
                if (p.description.isNotBlank()) Text(p.description, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("Play", Icons.Outlined.PlayArrow) { play(p.file) }
                    Secondary("Select", Icons.Outlined.Check) { vm.selectProfile(p.id) }
                }
                Row {
                    TextButton(onClick = { edit = p }) { Text("Rename") }
                    TextButton(onClick = { trim = p }) { Text("Trim") }
                    TextButton(onClick = { export(p.file) }) { Text("Export") }
                    IconButton(onClick = { deleting = p }) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete ${p.name}")
                    }
                }
            }
        }
        item {
            Panel {
                SectionLabel("NARRATOR VOICES")
                Text(
                    "Downloadable neural voices live in Settings → Offline models. Saved references cannot be used as clones in this build.",
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
                        "Reference recording only. Cloning is not installed.",
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("Your projects", "Saved audio and resumable narration sessions.") }
        if (vm.projects.isEmpty())
            item {
                Panel {
                    Icon(Icons.Outlined.FolderOpen, null, tint = Muted)
                    Text("A home for your stories", style = MaterialTheme.typography.titleMedium)
                    Text("Your first rendered narration will appear here.", color = Muted)
                    Primary("Create narration") { vm.navigate("Studio") }
                }
            }
        items(vm.projects, key = { it.folder.path }) { p ->
            Panel {
                Row {
                    Icon(Icons.Outlined.AudioFile, null, tint = Vermilion)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (p.ready) "${duration((p.duration*1000).toLong())} · WAV"
                            else "Paused · segments saved",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.ready) {
                        Secondary("Play", Icons.Outlined.PlayArrow) {
                            play(vm.projectAudio(p.folder))
                        }
                        Secondary("Export", Icons.Outlined.FileDownload) {
                            export(vm.projectAudio(p.folder))
                        }
                    } else Secondary("Open / Resume", Icons.Outlined.Restore) { vm.openProject(p) }
                }
                Row {
                    TextButton(onClick = { vm.openProject(p) }) { Text("Open script") }
                    TextButton(onClick = { rename = p }) { Text("Rename") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { delete = p }) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete ${p.name}")
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
            Panel {
                SectionLabel("AUDIO QUALITY · EXPERIMENTAL")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Also create native EQ comparison",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Optional 22.05 kHz warmth-only copy. OFF by default. Compare with Raw voice; this does not repair missing phonemes.",
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
                    "The separate Studio master always uses the requested 44.1 kHz warmth/loudness preset. This switch adds a third native-rate comparison; it never processes the master twice.",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }
        item {
            Panel {
                SectionLabel("ON THIS DEVICE")
                Text("Piper + Indic Tiny Telugu", style = MaterialTheme.typography.titleMedium)
                val context = LocalContext.current
                val version = remember {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }
                Text(
                    "App version $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Text(
                    "Telugu neural voices · ARM64 CPU\n2 inference threads · no cloud generation",
                    color = Muted,
                )
                HorizontalDivider(color = Line)
                Text(
                    "Semantic LLM: not installed\nExpressive voice model: not installed\nPersonal voice cloning: not installed",
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
                Text("Version 0.5 · DSP testing build", fontSize = 12.sp)
                Text(
                    "GPL-3.0-or-later · sherpa Apache-2.0 · ONNX Runtime 1.31 MIT · Indic Tiny / Rasa CC BY 4.0 · Vocos MIT · Jump3r LGPL · eSpeak GPL · Piper voices CC BY 4.0 (PravalX / AI4Bharat). Manrope & DM Serif Display: OFL.",
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                        "Installed",
                        "Telugu Narration",
                        "Compare",
                        "Expressive",
                        "Cloning",
                        "Updates",
                    )
                    .forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                        )
                    }
            }
        }
        if (category == "Installed" || category == "Telugu Narration") {
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
                        Primary("Browse Telugu voices") { category = "Telugu Narration" }
                    }
                }
            items(shown, key = { it.index }) { m ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${m.gender} · ${if(ModelCatalog.VOICES[m.index].tiny()) "Indic Tiny / Vocos" else "Piper / VITS"}",
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
                    Text(
                        if (ModelCatalog.VOICES[m.index].tiny())
                            "Indic Tiny Telugu · experimental\n91 MB shared pack · 24 kHz · CC BY 4.0 + MIT\nRevision ${TinyModelStore.REVISION.take(12)}\nTelugu script only. Android phone validation pending."
                        else
                            "ID: te_IN-${ModelCatalog.VOICES[m.index].id}-medium\n63.5 MB · 22.05 kHz · CC BY 4.0\nStart-token repair v1 · Revision ${ModelCatalog.REVISION.take(12)}\nUpgraded installs keep a 63.5 MB backup.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
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
        if (category == "Compare") {
            item {
                Panel {
                    StatusPill("HOST AUDIO · FIXED SCRIPT", Paper)
                    Text(
                        "Compare Telugu pronunciation",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("నమస్కారం ఫ్రెండ్స్!")
                    Text(
                        "These unprocessed reference samples were generated locally on Linux. They are not new speech generated on this phone. You can install the experimental Indic Tiny engine under Telugu Narration to generate your own text. Phone validation remains pending.",
                        color = Muted,
                    )
                }
            }
            items(vm.comparisons, key = { it.first }) { sample ->
                Panel {
                    Text(sample.first, style = MaterialTheme.typography.titleMedium)
                    Secondary("Play / Pause sample", Icons.Outlined.PlayArrow) {
                        play(sample.second)
                    }
                }
            }
        }
        if (category == "Expressive")
            item {
                Panel {
                    StatusPill("RESEARCH · NOT INSTALLED", Paper)
                    Text("Indic-Mio + MioCodec", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Telugu emotion and voice-cloning research candidate. No verified Android runtime is installed. No download is offered until its pipeline works on the target phone.",
                        color = Muted,
                    )
                    Text(
                        "Indic Tiny TTS is a calm fixed-voice comparison model, not expressive cloning. Real host A/B samples are included with the release evidence.",
                        fontSize = 13.sp,
                        color = Muted,
                    )
                }
            }
        if (category == "Cloning")
            item {
                Panel {
                    StatusPill("NOT AVAILABLE", Paper)
                    Text("Personal voice cloning", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "You can record and organize consented references in Voices. Selecting a reference does not replace the narrator with your voice.",
                        color = Muted,
                    )
                    Secondary("Open voice library", Icons.Outlined.Mic) { vm.navigate("Voices") }
                }
            }
        if (category == "Updates")
            item {
                Panel {
                    Text("You control updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No automatic checks or downloads. This checks repository metadata only. New weights require a reviewed model revision and checksum in an app update.",
                        color = Muted,
                    )
                    Primary("Check updates", !vm.busy, action = vm::checkUpdates)
                }
            }
    }
    if (deleting >= 0)
        ConfirmDelete(
            "Remove ${ModelCatalog.VOICES[deleting].name}?",
            "This removes the voice weights. Indic Tiny male and female share one pack, so removing it removes both. Recordings, scripts and exports are kept.",
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
                            Text("Save MP3 · 320 kbps")
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
