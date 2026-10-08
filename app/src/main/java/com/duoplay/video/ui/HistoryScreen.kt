package com.duoplay.video.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import com.duoplay.video.data.HistoryItem
import com.duoplay.video.util.ago
import com.duoplay.video.util.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private object ThumbCache {
    private val cache = LruCache<String, Bitmap>(60)
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, b: Bitmap) {
        cache.put(k, b)
    }
}

private fun loadFrame(ctx: Context, item: HistoryItem): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(ctx, Uri.parse(item.uri))
        val f = mmr.getFrameAtTime(item.positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        f?.let {
            val h = (240f * it.height / it.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(it, 240, h, true)
        }
    } catch (_: Throwable) {
        null
    } finally {
        try {
            mmr.release()
        } catch (_: Throwable) {
        }
    }
}

private fun dayStart(offsetDays: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, -offsetDays)
    return c.timeInMillis
}

private fun groupOf(ts: Long, today: Long, yesterday: Long, week: Long): String = when {
    ts >= today -> "Today"
    ts >= yesterday -> "Yesterday"
    ts >= week -> "This week"
    else -> "Earlier"
}

/**
 * VLC-style history: search bar, date groups, thumbnail with duration badge + progress,
 * long-press to select several, "Clear all".
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.historySection(
    items: List<HistoryItem>,
    query: String,
    onQuery: (String) -> Unit,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (HistoryItem) -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelect: () -> Unit,
    onAskClear: () -> Unit
) {
    val selecting = selected.isNotEmpty()

    item(key = "hist_bar") {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTap(Icons.Filled.Close, "Cancel", onCancelSelect)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${selected.size} selected",
                    color = Chalk, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                IconTap(Icons.Filled.Delete, "Delete selected", onDeleteSelected, tint = Coral)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Slate2)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search history", color = Muted, fontSize = 15.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = Chalk, fontSize = 15.sp),
                            cursorBrush = SolidColor(Amber),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, "Clear search", tint = Muted,
                            modifier = Modifier.size(18.dp).clickable { onQuery("") }
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    IconTap(Icons.Filled.Delete, "Clear all history", onAskClear)
                }
            }
        }
    }

    val shown = if (query.isBlank()) items
    else items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    if (shown.isEmpty()) {
        item(key = "hist_empty") {
            Text(
                if (items.isEmpty()) "No history yet. Play a lecture and it shows up here."
                else "Nothing matches \"$query\".",
                color = Muted, fontSize = 14.sp,
                modifier = Modifier.padding(top = 40.dp)
            )
        }
        return
    }

    val today = dayStart(0)
    val yesterday = dayStart(1)
    val week = dayStart(6)
    var lastGroup = ""
    shown.forEach { it ->
        val g = groupOf(it.lastPlayed, today, yesterday, week)
        if (g != lastGroup) {
            lastGroup = g
            item(key = "hdr_$g") { GroupHeader(g) }
        }
        item(key = it.uri) {
            HistoryRow(
                item = it,
                selecting = selecting,
                isSelected = it.uri in selected,
                onClick = { if (selecting) onToggle(it.uri) else onOpen(it) },
                onLongClick = { onToggle(it.uri) }
            )
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        color = Amber,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    tint: Color = Chalk
) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: HistoryItem,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Amber.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(128.dp).height(76.dp).clip(RoundedCornerShape(10.dp))) {
            Thumb(item, Modifier.fillMaxSize())
            // duration badge, bottom-right (like VLC)
            if (item.durationMs > 0) {
                Text(
                    fmtTime(item.durationMs),
                    color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            // thin progress line along the bottom of the thumbnail
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(if (item.finished) 1f else item.fraction)
                        .background(Amber)
                )
            }
            if (isSelected) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = Amber, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                item.name, color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.finished) "Finished" else "Left at ${fmtTime(item.positionMs)}",
                color = if (item.finished) Muted else Amber,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp
            )
            Text(ago(item.lastPlayed), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Thumb(item: HistoryItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = ThumbCache.get(item.uri), key1 = item.uri) {
        if (value == null) {
            val b = withContext(Dispatchers.IO) { loadFrame(ctx, item) }
            if (b != null) {
                ThumbCache.put(item.uri, b)
                value = b
            }
        }
    }
    Box(modifier.background(Slate2), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.PlayArrow, null, tint = Muted)
        }
    }
}

@Composable
fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate,
        title = { Text("Clear all history?", color = Chalk) },
        text = { Text("This only removes the list. Your videos are not deleted.", color = Muted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Clear", color = Coral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Chalk) } }
    )
}
