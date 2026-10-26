package com.example.ui.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.liquidGlass

object AppStatusColors {
    val correct @Composable get() = MaterialTheme.colorScheme.primary
    val incorrect @Composable get() = MaterialTheme.colorScheme.error
    val unanswered @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val current @Composable get() = MaterialTheme.colorScheme.primaryContainer
}

/**
 * Standard Design System spacing constants for a uniform M3 grid layout.
 */
object AppSpacing {
    val None = 0.dp
    val Tiny = 2.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Normal = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val DoubleExtraLarge = 48.dp
}

/**
 * High-quality Design System primary action button.
 * Incorporates beautiful modern shadows, rounded shapes, and accessibility-compliant touch targets.
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null,
    colors: ButtonColors? = null,
    shape: Shape = RoundedCornerShape(32.dp)
) {
    val defaultColors = colors ?: ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = shape,
        colors = defaultColors,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.Small))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Reusable M3 outlined styled button for secondary/tertiary interactions.
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null,
    border: BorderStroke? = null,
    colors: ButtonColors? = null
) {
    val defaultColors = colors ?: ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val defaultBorder = border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(32.dp),
        colors = defaultColors,
        border = defaultBorder,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.Small))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Futuristic Glassmorphic overlay button with cyber-glow visual.
 */
@Composable
fun AppGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .liquidGlass(shape as? RoundedCornerShape ?: RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.Small))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )
        }
    }
}

/**
 * Unified Card Design representing correct elevations and visual hierarchy.
 */
@Composable
fun WedikaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    shape: Shape? = null,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val enforcedShape = shape ?: RoundedCornerShape(16.dp)
    val enforcedColors = colors ?: CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val enforcedBorder = border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val enforcedElevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp)
    
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = enforcedShape,
            colors = enforcedColors,
            border = enforcedBorder,
            elevation = enforcedElevation,
            enabled = enabled,
            interactionSource = interactionSource ?: remember { MutableInteractionSource() }
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = enforcedShape,
            colors = enforcedColors,
            border = enforcedBorder,
            elevation = enforcedElevation
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * Standard overlay shown during operations like database download or authentications.
 */
@Composable
fun AppLoadingOverlay(
    visible: Boolean,
    message: String? = null,
    subMessage: String? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .blur(if (visible) 4.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )
                if (message != null) {
                    Spacer(modifier = Modifier.height(AppSpacing.Medium))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                if (subMessage != null) {
                    Spacer(modifier = Modifier.height(AppSpacing.Small))
                    Text(
                        text = subMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Multi-use high fidelity Alert Dialog featuring modern dark overlays, responsive shapes.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = text,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(
                    onClick = onDismiss ?: onDismissRequest,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Standardized reusable bottom sheet content structure inside ModelBottomSheet.
 * Normalizes title fonts, spacing, close action, and Divider to prevent dual definitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable BoxScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.Normal)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.Small, end = AppSpacing.Small, bottom = AppSpacing.Normal),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
