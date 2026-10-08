package com.duoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.session.MediaController
import com.duoplay.music.MyMusicRoot
import com.duoplay.music.SettingsScreen
import com.duoplay.video.ClassReelRoot
import com.duoplay.video.data.HistoryStore
import com.duoplay.video.ui.VideoSource

private val ShellInk = Color(0xFF0D1411)
private val ShellSlate = Color(0xFF16201B)
private val ShellAmber = Color(0xFFF2C14E)
private val ShellChalk = Color(0xFFEDE9DD)
private val ShellMuted = Color(0xFF9AA79F)

private val shellScheme = darkColorScheme(
    primary = ShellAmber,
    background = ShellInk,
    surface = ShellSlate,
    onBackground = ShellChalk,
    onSurface = ShellChalk,
    surfaceVariant = Color(0xFF212E27),
    onSurfaceVariant = ShellMuted
)

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Music", Icons.Filled.MusicNote),
    Tab("Videos", Icons.Filled.Videocam),
    Tab("Settings", Icons.Filled.Settings)
)

@Composable
fun DuoPlayApp(
    controller: MediaController?,
    historyStore: HistoryStore,
    hasStorage: Boolean,
    onGrantStorage: () -> Unit,
    pendingVideo: VideoSource?,
    onVideoConsumed: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = shellScheme) {
        Scaffold(
            containerColor = ShellInk,
            bottomBar = {
                NavigationBar(containerColor = ShellSlate) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ShellInk,
                                selectedTextColor = ShellAmber,
                                indicatorColor = ShellAmber,
                                unselectedIconColor = ShellMuted,
                                unselectedTextColor = ShellMuted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ShellInk)
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> MyMusicRoot(controller)
                    1 -> ClassReelRoot(
                        store = historyStore,
                        hasStorage = hasStorage,
                        onGrantStorage = onGrantStorage,
                        pendingVideo = pendingVideo,
                        onVideoConsumed = onVideoConsumed
                    )
                    2 -> SettingsScreen()
                }
            }
        }
    }
}
