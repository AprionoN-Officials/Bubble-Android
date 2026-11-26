package com.bubble.screentools.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bubble.screentools.R
import com.bubble.screentools.ui.theme.BubblePalette
import com.bubble.screentools.ui.theme.supportsDynamicWallpaper

/**
 * Bubble's expressive semantic color tones for icon containers.
 */
enum class BadgeTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
    ERROR,
}

/**
 * Position inside a Material 3 Expressive segmented card stack.
 * Outer items get generous 24dp corners; adjacent inner edges use crisp 6dp corners
 * separated by a subtle 3dp gap.
 */
enum class SegmentPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM,
}

fun segmentShapeFor(position: SegmentPosition): Shape =
    when (position) {
        SegmentPosition.SINGLE -> RoundedCornerShape(24.dp)
        SegmentPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        SegmentPosition.MIDDLE -> RoundedCornerShape(6.dp)
        SegmentPosition.BOTTOM -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    }

/**
 * Speech-bubble droplet icon badge unique to Bubble's visual identity.
 * Uses Material You semantic container roles so icons harmonize with wallpaper or preset palettes.
 */
@Composable
fun BubbleIconBadge(
    icon: ImageVector,
    tone: BadgeTone = BadgeTone.PRIMARY,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) =
        when (tone) {
            BadgeTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
            BadgeTone.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
            BadgeTone.TERTIARY -> colors.tertiaryContainer to colors.onTertiaryContainer
            BadgeTone.NEUTRAL -> colors.surfaceContainerHighest to colors.onSurfaceVariant
            BadgeTone.ERROR -> colors.errorContainer to colors.onErrorContainer
        }

    // Asymmetric droplet shape inspired by a speech bubble
    val dropletShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 5.dp,
    )

    Box(
        modifier =
            modifier
                .size(42.dp)
                .clip(dropletShape)
                .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun ExpressiveSectionLabel(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ExpressiveSegmentSurface(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = segmentShapeFor(position)
    Surface(
        shape = shape,
        color = containerColor,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        content = content,
    )
}

@Composable
fun ExpressiveActionRow(
    position: SegmentPosition,
    icon: ImageVector,
    tone: BadgeTone,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    ExpressiveSegmentSurface(
        position = position,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BubbleIconBadge(icon = icon, tone = if (enabled) tone else BadgeTone.NEUTRAL)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.4f),
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            } else if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun ExpressiveSwitchRow(
    position: SegmentPosition,
    icon: ImageVector,
    tone: BadgeTone,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ExpressiveActionRow(
        position = position,
        icon = icon,
        tone = if (checked) tone else BadgeTone.NEUTRAL,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent =
                    if (checked) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveSegmentedChoiceCard(
    position: SegmentPosition,
    icon: ImageVector,
    tone: BadgeTone,
    title: String,
    subtitle: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ExpressiveSegmentSurface(position = position) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BubbleIconBadge(icon = icon, tone = tone)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {},
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BubblePaletteSelector(
    selected: BubblePalette,
    onSelect: (BubblePalette) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palettes =
        if (supportsDynamicWallpaper) {
            BubblePalette.entries
        } else {
            BubblePalette.entries.filter { it != BubblePalette.WALLPAPER }
        }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(palettes, key = { it.key }) { palette ->
            val isSelected = palette == selected
            val label =
                when (palette) {
                    BubblePalette.WALLPAPER -> stringResource(R.string.palette_wallpaper)
                    BubblePalette.BUBBLE_BLUE -> stringResource(R.string.palette_blue)
                    BubblePalette.LAGOON -> stringResource(R.string.palette_lagoon)
                    BubblePalette.LAVENDER -> stringResource(R.string.palette_lavender)
                    BubblePalette.CORAL -> stringResource(R.string.palette_coral)
                }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
                border =
                    if (isSelected) {
                        BorderStroke(1.5.dp, colors.primary)
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(palette) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (palette == BubblePalette.WALLPAPER) {
                        Box(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(palette.previewPrimary),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(palette.previewTertiary),
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                    )
                }
            }
        }
    }
}
