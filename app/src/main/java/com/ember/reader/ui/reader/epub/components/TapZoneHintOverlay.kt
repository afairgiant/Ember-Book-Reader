package com.ember.reader.ui.reader.epub.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.core.model.ReaderPreferences

/** First-launch visual explanation of the configurable tap zones. */
@Composable
fun TapZoneHintOverlay(preferences: ReaderPreferences, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topH = h * preferences.topZoneHeight
            val leftW = w * preferences.leftZoneWidth
            val rightW = w * preferences.rightZoneWidth

            drawRect(
                color = Color(0x44FF9800),
                topLeft = Offset.Zero,
                size = Size(w, topH),
            )
            drawRect(
                color = Color(0x442196F3),
                topLeft = Offset(0f, topH),
                size = Size(leftW, h - topH),
            )
            drawRect(
                color = Color(0x444CAF50),
                topLeft = Offset(w - rightW, topH),
                size = Size(rightW, h - topH),
            )
            drawRect(
                color = Color(0x229C27B0),
                topLeft = Offset(leftW, topH),
                size = Size(w - leftW - rightW, h - topH),
            )
        }

        val labelStyle = MaterialTheme.typography.labelMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = preferences.topTapZone.displayName,
            style = labelStyle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        )
        Text(
            text = preferences.leftTapZone.displayName,
            style = labelStyle,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        )
        Text(
            text = preferences.rightTapZone.displayName,
            style = labelStyle,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = preferences.centerTapZone.displayName, style = labelStyle)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap anywhere to dismiss",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.8f),
                ),
            )
        }
    }
}
