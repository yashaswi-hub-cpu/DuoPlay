package com.duoplay.music

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

// ---------- Theme packs ----------

data class ThemePack(
    val name: String,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val dark: Boolean
)

val packs = listOf(
    ThemePack("Midnight", Color(0xFF8AB4FF), Color(0xFF0E1116), Color(0xFF1A2029), true),
    ThemePack("Sunset", Color(0xFFFF8A5B), Color(0xFF1B1014), Color(0xFF2B1A20), true),
    ThemePack("Forest", Color(0xFF6FCF97), Color(0xFF0C1410), Color(0xFF16231B), true),
    ThemePack("Ocean", Color(0xFF4FD1E8), Color(0xFF08141C), Color(0xFF10252F), true),
    ThemePack("Rose", Color(0xFFFF7DA8), Color(0xFF180D12), Color(0xFF2A1620), true),
    ThemePack("Paper", Color(0xFF3D5AFE), Color(0xFFF6F4EF), Color(0xFFFFFFFF), false)
)

// ---------- Helpers ----------

fun Track.toItem(): MediaItem = MediaItem.Builder()
    .setUri(Uri.parse(uri))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
    .build()

fun displayName(ctx: Context, uri: Uri): String {
    var name: String? = null
    runCatching {
        ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
    }
    return (name ?: uri.lastPathSegment ?: "Unknown").substringBeforeLast('.')
}

fun decodeScaled(path: String): ImageBitmap {
    val src = ImageDecoder.createSource(File(path))
    val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSampleSize(max(1, info.size.width / 1080))
    }
    return bmp.asImageBitmap()
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Opens the system file picker already pointing at Downloads, for audio files. */
class PickAudio : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
            )
        }
}

// ---------- Player state for the UI ----------

class PlayerUi {
    var playing by mutableStateOf(false)
    var title by mutableStateOf("")
    var index by mutableIntStateOf(-1)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
}

@Composable
fun rememberPlayerUi(c: MediaController?): PlayerUi {
    val ui = remember { PlayerUi() }
    DisposableEffect(c) {
        if (c == null) {
            onDispose { }
        } else {
            fun refresh() {
                ui.playing = c.isPlaying
                ui.title = c.mediaMetadata.title?.toString() ?: ""
                ui.index = c.currentMediaItemIndex
                ui.dur = c.duration.coerceAtLeast(0L)
                ui.pos = c.currentPosition
                ui.shuffle = c.shuffleModeEnabled
                ui.repeat = c.repeatMode
            }
            val l = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            }
            c.addListener(l)
            refresh()
            onDispose { c.removeListener(l) }
        }
    }
    LaunchedEffect(c, ui.playing) {
        while (c != null && ui.playing) {
            ui.pos = c.currentPosition
            delay(500)
        }
    }
    return ui
}

// ---------- Screens ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(store: Store, controller: MediaController?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val folders = remember { mutableStateListOf<Folder>().apply { addAll(store.loadFolders()) } }
    var openId by remember { mutableStateOf<String?>(null) }
    var playingFolderId by remember { mutableStateOf(store.lastFolderId) }
    var showSettings by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var themeName by remember { mutableStateOf(store.themeName) }
    var bgPath by remember { mutableStateOf(store.bgPath) }
    var dim by remember { mutableFloatStateOf(store.dim) }
    val ui = rememberPlayerUi(controller)

    val bg by produceState<ImageBitmap?>(null, bgPath) {
        value = bgPath?.let { p ->
            withContext(Dispatchers.IO) { runCatching { decodeScaled(p) }.getOrNull() }
        }
    }

    // Bring back what was playing last time (paused, at the same second).
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (c.mediaItemCount == 0) {
            val f = folders.firstOrNull { it.id == store.lastFolderId }
            if (f != null && f.tracks.isNotEmpty()) {
                val idx = store.lastIndex.coerceIn(0, f.tracks.lastIndex)
                val pos = store.lastPosition
                c.setMediaItems(f.tracks.map { it.toItem() }, idx, pos)
                c.prepare()
                playingFolderId = f.id
            }
        }
    }

    BackHandler(enabled = openId != null) { openId = null }

    val pack = packs.firstOrNull { it.name == themeName } ?: packs[0]
    val hasPhoto = bg != null
    val dark = pack.dark || hasPhoto
    val surfaceCol = if (pack.dark || !hasPhoto) pack.surface else packs[0].surface
    val scheme = if (dark) {
        darkColorScheme(
            primary = pack.primary,
            background = pack.background,
            surface = surfaceCol,
            surfaceVariant = surfaceCol
        )
    } else {
        lightColorScheme(
            primary = pack.primary,
            background = pack.background,
            surface = surfaceCol,
            surfaceVariant = surfaceCol
        )
    }
    val cardCol = surfaceCol.copy(alpha = if (hasPhoto) 0.78f else 1f)

    // ----- actions -----

    fun play(f: Folder, i: Int) {
        val c = controller ?: return
        if (playingFolderId == f.id && c.mediaItemCount == f.tracks.size) {
            c.seekToDefaultPosition(i)
        } else {
            c.setMediaItems(f.tracks.map { it.toItem() }, i, 0L)
            c.prepare()
        }
        c.play()
        store.lastFolderId = f.id
        playingFolderId = f.id
    }

    fun removeTrack(f: Folder, i: Int) {
        val fi = folders.indexOfFirst { it.id == f.id }
        if (fi < 0) return
        folders[fi] = f.copy(tracks = f.tracks.filterIndexed { j, _ -> j != i })
        store.saveFolders(folders)
        val c = controller
        if (c != null && playingFolderId == f.id && c.mediaItemCount == f.tracks.size) {
            c.removeMediaItem(i)
        }
    }

    fun deleteFolder(id: String) {
        if (playingFolderId == id) {
            controller?.run { stop(); clearMediaItems() }
            store.lastFolderId = null
            playingFolderId = null
        }
        folders.removeAll { it.id == id }
        store.saveFolders(folders)
        if (openId == id) openId = null
    }

    // ----- pickers -----

    val pickMusic = rememberLauncherForActivityResult(PickAudio()) { uris ->
        val id = openId ?: return@rememberLauncherForActivityResult
        val fi = folders.indexOfFirst { it.id == id }
        if (fi < 0) return@rememberLauncherForActivityResult
        val f = folders[fi]
        val existing = f.tracks.map { it.uri }.toSet()
        val added = uris.filter { it.toString() !in existing }.map { u ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Track(u.toString(), displayName(ctx, u))
        }
        if (added.isEmpty()) return@rememberLauncherForActivityResult
        folders[fi] = f.copy(tracks = f.tracks + added)
        store.saveFolders(folders)
        val c = controller
        if (c != null && playingFolderId == id && c.mediaItemCount == f.tracks.size) {
            c.addMediaItems(added.map { it.toItem() })
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val out = File(ctx.filesDir, "bg_${System.currentTimeMillis()}.img")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { o -> input.copyTo(o) }
                }
                val old = store.bgPath
                store.bgPath = out.absolutePath
                old?.let { File(it).delete() }
                withContext(Dispatchers.Main) { bgPath = out.absolutePath }
            }
        }
    }

    // ----- layout -----

    val openFolder = folders.firstOrNull { it.id == openId }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            bg?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {

                    // top bar
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (openFolder != null) {
                            IconButton(onClick = { openId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            openFolder?.name ?: "My Music",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (openFolder == null) {
                            IconButton(onClick = { newName = ""; showNew = true }) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                            }
                        } else {
                            IconButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add music")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Look")
                        }
                    }

                    // body
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (openFolder == null) {
                            if (folders.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap the folder+ button to make your first folder")
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(folders, key = { it.id }) { f ->
                                        Card(
                                            onClick = { openId = f.id },
                                            colors = CardDefaults.cardColors(containerColor = cardCol)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                                    Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${f.tracks.size} songs", style = MaterialTheme.typography.bodySmall)
                                                }
                                                IconButton(onClick = { deleteId = f.id }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete folder")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (openFolder.tracks.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap + to add songs from your Downloads")
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(openFolder.tracks, key = { _, t -> t.uri }) { i, t ->
                                        val current = playingFolderId == openFolder.id && ui.index == i && ui.title.isNotEmpty()
                                        Card(
                                            onClick = { play(openFolder, i) },
                                            colors = CardDefaults.cardColors(containerColor = cardCol)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (current && ui.playing) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                                                    null,
                                                    tint = if (current) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                                )
                                                Text(
                                                    t.title,
                                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                )
                                                IconButton(onClick = { removeTrack(openFolder, i) }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Remove from folder")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // mini player
                    if (controller != null && ui.title.isNotEmpty()) {
                        MiniPlayer(controller, ui, cardCol)
                    }
                }
            }
        }

        // ----- dialogs -----

        if (showNew) {
            AlertDialog(
                onDismissRequest = { showNew = false },
                title = { Text("New folder") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Folder name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            folders.add(Folder(UUID.randomUUID().toString(), newName.trim(), emptyList()))
                            store.saveFolders(folders)
                            showNew = false
                        }
                    ) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
            )
        }

        deleteId?.let { id ->
            val f = folders.firstOrNull { it.id == id }
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("Delete folder?") },
                text = { Text("\"${f?.name ?: ""}\" will be removed from the app. Your music files stay on your phone.") },
                confirmButton = {
                    TextButton(onClick = { deleteFolder(id); deleteId = null }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
            )
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Theme pack", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(packs, key = { it.name }) { p ->
                            FilterChip(
                                selected = p.name == themeName,
                                onClick = { themeName = p.name; store.themeName = p.name },
                                label = { Text(p.name) },
                                leadingIcon = {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(p.primary))
                                }
                            )
                        }
                    }

                    Text("Background photo", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Choose from gallery") }
                        if (bgPath != null) {
                            OutlinedButton(onClick = {
                                bgPath?.let { File(it).delete() }
                                store.bgPath = null
                                bgPath = null
                            }) { Text("Remove") }
                        }
                    }
                    if (bgPath != null) {
                        Text("Photo dimming")
                        Slider(
                            value = dim,
                            onValueChange = { dim = it },
                            onValueChangeFinished = { store.dim = dim },
                            valueRange = 0f..0.85f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(c: MediaController, ui: PlayerUi, surface: Color) {
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val progress = if (ui.dur > 0) (ui.pos.toFloat() / ui.dur).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                ui.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Slider(
                value = if (dragging) drag else progress,
                onValueChange = { dragging = true; drag = it },
                onValueChangeFinished = {
                    c.seekTo((drag * ui.dur).toLong())
                    dragging = false
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(if (dragging) (drag * ui.dur).toLong() else ui.pos), style = MaterialTheme.typography.bodySmall)
                Text(fmt(ui.dur), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { c.shuffleModeEnabled = !ui.shuffle }) {
                    Icon(
                        Icons.Filled.Shuffle, contentDescription = "Shuffle",
                        tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { c.seekToPreviousMediaItem() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                FilledIconButton(onClick = { if (ui.playing) c.pause() else c.play() }) {
                    Icon(
                        if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (ui.playing) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { c.seekToNextMediaItem() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = {
                    c.repeatMode = when (ui.repeat) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (ui.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
