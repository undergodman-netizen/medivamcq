package com.example.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.Icons

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (query.isBlank()) {
        Text(text, style = style, modifier = modifier, fontWeight = fontWeight, color = color, maxLines = maxLines, overflow = overflow)
        return
    }

    val startIndex = text.indexOf(query, ignoreCase = true)
    if (startIndex == -1) {
        Text(text, style = style, modifier = modifier, fontWeight = fontWeight, color = color, maxLines = maxLines, overflow = overflow)
        return
    }

    val annotatedString = buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(style = SpanStyle(background = Color.Yellow.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)) {
            append(text.substring(startIndex, startIndex + query.length))
        }
        append(text.substring(startIndex + query.length))
    }

    Text(text = annotatedString, style = style, modifier = modifier, fontWeight = fontWeight, color = color, maxLines = maxLines, overflow = overflow)
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
fun AudioSpectrumVisualizer(
    color: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "audio_spectrum")
    
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 18f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 22f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 20f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val bar4Height by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 14f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom
    ) {
        listOf(bar1Height, bar2Height, bar3Height, bar4Height).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
