package com.ember.reader.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.reader.ui.common.DevLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val bgColor = Color(0xFF0D1117)
private val dividerColor = Color(0xFF21262D)
private val timeColor = Color(0xFF8B949E)
private val tagColor = Color(0xFF79C0FF)

private val levelColors = mapOf(
    "E" to Color(0xFFFF7B72),
    "W" to Color(0xFFD29922),
    "I" to Color(0xFF3FB950),
    "D" to Color(0xFF58A6FF),
    "V" to Color(0xFF6E7681),
)

private val levelLabels = mapOf(
    "E" to "ERROR",
    "W" to "WARN",
    "I" to "INFO",
    "D" to "DEBUG",
    "V" to "VERBOSE",
)

private val levelBgColors = mapOf(
    "E" to Color(0xFF3D1117),
    "W" to Color(0xFF2D2000),
    "I" to Color(0xFF0D2818),
    "D" to Color(0xFF0D1B2A),
    "V" to Color(0xFF161B22),
)

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLogScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var refreshKey by remember { mutableIntStateOf(0) }
    val entries = remember(refreshKey) { DevLog.getEntries() }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Developer Logs", fontWeight = FontWeight.Bold)
                        Text(
                            "${entries.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            if (entries.isNotEmpty()) {
                                listState.animateScrollToItem(entries.size - 1)
                            }
                        }
                    }) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Scroll to bottom")
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Ember Logs", DevLog.allText()))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy all")
                    }
                    IconButton(onClick = {
                        DevLog.clear()
                        refreshKey++
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(bgColor),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No log entries yet",
                    color = timeColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Logs will appear as the app runs",
                    color = timeColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(bgColor),
            ) {
                items(entries) { entry ->
                    LogEntry(entry)
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun LogEntry(entry: DevLog.Entry) {
    val levelColor = levelColors[entry.level] ?: timeColor
    val entryBg = levelBgColors[entry.level] ?: Color.Transparent
    val levelLabel = levelLabels[entry.level] ?: entry.level

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (entry.level == "E" || entry.level == "W") entryBg else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Header row: level badge + tag + timestamp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Level badge
            Text(
                text = " $levelLabel ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (entry.level == "E") Color.White else levelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(levelColor.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )

            // Tag
            entry.tag?.let { tag ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = tagColor,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Timestamp
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = timeColor,
            )
        }

        // Message
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = entry.message,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE6EDF3),
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}
