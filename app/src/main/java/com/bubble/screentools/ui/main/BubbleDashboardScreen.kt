package com.bubble.screentools.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bubble.screentools.BuildConfig
import com.bubble.screentools.R
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.ui.component.BadgeTone
import com.bubble.screentools.ui.component.BubbleIconBadge
import com.bubble.screentools.ui.component.BubblePaletteSelector
import com.bubble.screentools.ui.component.ExpressiveActionRow
import com.bubble.screentools.ui.component.ExpressiveSectionLabel
import com.bubble.screentools.ui.component.ExpressiveSegmentSurface
import com.bubble.screentools.ui.component.ExpressiveSegmentedChoiceCard
import com.bubble.screentools.ui.component.ExpressiveSwitchRow
import com.bubble.screentools.ui.component.SegmentPosition
import com.bubble.screentools.ui.theme.BubblePalette
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleDashboardScreen(
    state: BubbleUiState,
    darkTheme: Boolean,
    onTryBubble: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRequestLegacyCapture: () -> Unit,
    onOpenLanguageManager: (Int) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var pickingSourceLang by remember { mutableStateOf<Boolean?>(null) }
    var showExcludedAppsDialog by remember { mutableStateOf(false) }
    var swapTurns by remember { mutableFloatStateOf(0f) }
    val swapRotation by animateFloatAsState(targetValue = swapTurns, label = "swapSpin")

    val captureModeText =
        when {
            state.isDefaultAssistant && state.isAccessibilityActive -> stringResource(R.string.status_capture_both)
            state.isDefaultAssistant -> stringResource(R.string.status_capture_assistant)
            state.supportsSilentA11yCapture && state.isAccessibilityActive -> stringResource(R.string.status_capture_a11y)
            else -> stringResource(R.string.status_capture_enable_hint)
        }

    val lensStatusText =
        when (state.lensAvailability) {
            LensIntegrationManager.LensAvailability.GOOGLE_LENS_APP -> stringResource(R.string.status_lens_ready)
            LensIntegrationManager.LensAvailability.GOOGLE_APP_SEARCH -> stringResource(R.string.status_google_app_ready)
            LensIntegrationManager.LensAvailability.SHARESHEET_FALLBACK -> stringResource(R.string.status_multi_engine_ready)
        }

    val offlineSummary =
        if (state.downloadedMlKitModels.isEmpty()) {
            stringResource(R.string.status_models_auto)
        } else {
            stringResource(
                R.string.status_models_count,
                state.downloadedMlKitModels.size,
                state.downloadedMlKitModels.sorted().joinToString(", "),
            )
        }

    val ocrSummary =
        if (state.useTesseractOcr && state.activeTesseractLang.isNotBlank()) {
            stringResource(R.string.ocr_engine_tess_fmt, state.activeTesseractLang, state.installedTessModels.size)
        } else {
            stringResource(R.string.ocr_engine_mlkit_fmt, state.installedTessModels.size)
        }

    Scaffold(
        containerColor = colors.surface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 14.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // ── Hero Header & Launch Card ──
            item(key = "hero_card") {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomEnd = 20.dp,
                                    bottomStart = 6.dp,
                                ),
                                color = colors.primaryContainer,
                                modifier = Modifier.size(54.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_bubble_logo),
                                        contentDescription = stringResource(R.string.app_name),
                                        modifier = Modifier.size(38.dp).clip(CircleShape),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = colors.onSurface,
                                    )
                                    val ready = state.isReadyToScan
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (ready) colors.primaryContainer else colors.tertiaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                                contentDescription = null,
                                                tint = if (ready) colors.onPrimaryContainer else colors.onTertiaryContainer,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Text(
                                                text =
                                                    stringResource(
                                                        if (ready) R.string.status_ready_pill else R.string.status_setup_pill,
                                                    ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (ready) colors.onPrimaryContainer else colors.onTertiaryContainer,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }

                        Button(
                            onClick = onTryBubble,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_try_bubble),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onOpenAssistantSettings,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Assistant,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_default_assistant),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenAccessibilitySettings,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccessibilityNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_accessibility),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // ── Safe Mode Blocked Notice ──
            item(key = "safe_mode_notice") {
                val lastBlocked = state.lastBlockedPackageName
                AnimatedVisibility(visible = !lastBlocked.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            BubbleIconBadge(icon = Icons.Rounded.Shield, tone = BadgeTone.ERROR)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.safe_mode_blocked_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onErrorContainer,
                                )
                                if (!lastBlocked.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.status_last_blocked_app, lastBlocked),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onErrorContainer,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { state.clearLastBlockedNotice() },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.btn_dismiss),
                                    tint = colors.onErrorContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Section 1: Status & Activation ──
            item(key = "sec_status_header") {
                ExpressiveSectionLabel(stringResource(R.string.section_status_activation))
            }
            item(key = "status_assistant") {
                ExpressiveActionRow(
                    position = SegmentPosition.TOP,
                    icon = Icons.Rounded.Assistant,
                    tone = if (state.isDefaultAssistant) BadgeTone.PRIMARY else BadgeTone.NEUTRAL,
                    title = stringResource(R.string.status_assistant_label),
                    subtitle =
                        stringResource(
                            if (state.isDefaultAssistant) R.string.status_active_check else R.string.status_not_set,
                        ),
                    showChevron = true,
                    onClick = onOpenAssistantSettings,
                )
            }
            item(key = "status_accessibility") {
                ExpressiveActionRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.AccessibilityNew,
                    tone = if (state.isAccessibilityActive) BadgeTone.PRIMARY else BadgeTone.NEUTRAL,
                    title = stringResource(R.string.status_accessibility_label),
                    subtitle =
                        stringResource(
                            if (state.isAccessibilityActive) R.string.status_active_check else R.string.status_inactive,
                        ),
                    showChevron = true,
                    onClick = onOpenAccessibilitySettings,
                )
            }
            item(key = "status_capture") {
                ExpressiveActionRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.Screenshot,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.status_capture_label),
                    subtitle = captureModeText,
                )
            }
            item(key = "status_visual") {
                ExpressiveActionRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.ImageSearch,
                    tone = BadgeTone.TERTIARY,
                    title = stringResource(R.string.status_visual_search_label),
                    subtitle = "${state.visualSearchEngine.shortLabel} ($lensStatusText)",
                )
            }
            item(key = "status_offline") {
                ExpressiveActionRow(
                    position = SegmentPosition.BOTTOM,
                    icon = Icons.Rounded.Translate,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.status_offline_langs_label),
                    subtitle = offlineSummary,
                    showChevron = true,
                    onClick = { onOpenLanguageManager(0) },
                )
            }
            item(key = "status_optional_links") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!state.supportsSilentA11yCapture) {
                        TextButton(onClick = onRequestLegacyCapture) {
                            Text(
                                text = stringResource(R.string.btn_legacy_capture),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    TextButton(onClick = onOpenOverlayPermission) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_overlay_optional),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // ── Section 2: Translation & OCR ──
            item(key = "sec_translation_header") {
                ExpressiveSectionLabel(stringResource(R.string.section_translation))
            }
            item(key = "translation_pair_card") {
                ExpressiveSegmentSurface(position = SegmentPosition.TOP) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surfaceContainerHighest,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { pickingSourceLang = true },
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.btn_source_lang_fmt,
                                            SupportedLanguages.shortLabelFor(state.effectiveSourceLanguageCode),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary,
                                    )
                                    Text(
                                        text = SupportedLanguages.displayNameFor(state.effectiveSourceLanguageCode),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            FilledIconButton(
                                onClick = {
                                    swapTurns += 180f
                                    val (newSrc, newDst) = state.swapLanguages()
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_swapped_fmt,
                                            SupportedLanguages.shortLabelFor(newSrc),
                                            SupportedLanguages.shortLabelFor(newDst),
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                shape = CircleShape,
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor = colors.primaryContainer,
                                        contentColor = colors.onPrimaryContainer,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.overlay_swap_cd),
                                    modifier = Modifier.rotate(swapRotation),
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surfaceContainerHighest,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { pickingSourceLang = false },
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.btn_target_lang_fmt,
                                            SupportedLanguages.shortLabelFor(state.targetLanguageCode),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary,
                                    )
                                    Text(
                                        text = SupportedLanguages.displayNameFor(state.targetLanguageCode),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Text(
                            text =
                                stringResource(
                                    R.string.active_lang_pair_fmt,
                                    SupportedLanguages.displayNameFor(state.effectiveSourceLanguageCode),
                                    SupportedLanguages.displayNameFor(state.targetLanguageCode),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "tr_auto_detect") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.AutoAwesome,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.switch_auto_detect),
                    checked = state.autoDetectLanguage,
                    onCheckedChange = { state.updateAutoDetect(it) },
                )
            }
            item(key = "tr_auto_translate") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.TouchApp,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.switch_auto_translate),
                    checked = state.autoTranslateSelection,
                    onCheckedChange = { state.updateAutoTranslate(it) },
                )
            }
            item(key = "tr_entire_screen") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.Fullscreen,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.switch_translate_entire_screen),
                    checked = state.translateEntireScreen,
                    onCheckedChange = { state.updateTranslateEntireScreen(it) },
                )
            }
            item(key = "tr_ocr_and_models") {
                ExpressiveSegmentSurface(position = SegmentPosition.BOTTOM) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BubbleIconBadge(icon = Icons.Rounded.DocumentScanner, tone = BadgeTone.TERTIARY)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = ocrSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenLanguageManager(0) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 11.dp, horizontal = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_download_languages),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenLanguageManager(1) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 11.dp, horizontal = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_import_ocr),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // ── Section 3: Lens & Visual Search ──
            item(key = "sec_visual_header") {
                ExpressiveSectionLabel(stringResource(R.string.section_visual_search))
            }
            item(key = "visual_engine_picker") {
                ExpressiveSegmentSurface(position = SegmentPosition.TOP) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BubbleIconBadge(icon = Icons.Rounded.ImageSearch, tone = BadgeTone.TERTIARY)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.status_visual_search_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.visual_search_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                VisualSearchEngine.GOOGLE_LENS to stringResource(R.string.engine_google_lens),
                                VisualSearchEngine.YANDEX to stringResource(R.string.engine_yandex),
                                VisualSearchEngine.TINEYE to stringResource(R.string.engine_tineye),
                                VisualSearchEngine.SHARESHEET to stringResource(R.string.engine_pick_app),
                            ).forEach { (engine, label) ->
                                val selected = state.visualSearchEngine == engine
                                FilterChip(
                                    selected = selected,
                                    onClick = { state.updateVisualSearchEngine(engine) },
                                    label = { Text(label) },
                                    leadingIcon =
                                        if (selected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "visual_auto_search") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.BOTTOM,
                    icon = Icons.Rounded.AutoAwesome,
                    tone = BadgeTone.TERTIARY,
                    title = stringResource(R.string.switch_auto_visual_search),
                    checked = state.autoSearchAfterSelection,
                    onCheckedChange = { state.updateAutoVisualSearch(it) },
                )
            }

            // ── Section 4: Overlay & QR ──
            item(key = "sec_overlay_header") {
                ExpressiveSectionLabel(stringResource(R.string.jump_overlay_qr))
            }
            item(key = "overlay_auto_qr") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.TOP,
                    icon = Icons.Rounded.QrCodeScanner,
                    tone = BadgeTone.PRIMARY,
                    title = stringResource(R.string.switch_auto_qr),
                    subtitle = stringResource(R.string.auto_qr_subtitle),
                    checked = state.autoQrScanEnabled,
                    onCheckedChange = { state.updateAutoQrScan(it) },
                )
            }
            item(key = "overlay_shape") {
                ExpressiveSegmentedChoiceCard(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.CropFree,
                    tone = BadgeTone.PRIMARY,
                    title = stringResource(R.string.label_default_shape),
                    options =
                        listOf(
                            SelectionShape.RECTANGLE to stringResource(R.string.shape_rectangle),
                            SelectionShape.ELLIPSE to stringResource(R.string.shape_ellipse),
                            SelectionShape.FREEFORM to stringResource(R.string.shape_freeform),
                        ),
                    selected = state.defaultSelectionShape,
                    onSelect = { state.updateSelectionShape(it) },
                )
            }
            item(key = "overlay_action") {
                ExpressiveSegmentedChoiceCard(
                    position = SegmentPosition.BOTTOM,
                    icon = Icons.Rounded.TouchApp,
                    tone = BadgeTone.PRIMARY,
                    title = stringResource(R.string.label_default_action),
                    options =
                        listOf(
                            ActionMode.TRANSLATE to stringResource(R.string.action_translate),
                            ActionMode.VISUAL_SEARCH to stringResource(R.string.action_visual_search),
                        ),
                    selected = state.lastActionMode,
                    onSelect = { state.updateActionMode(it) },
                )
            }

            // ── Section 5: Appearance (Material You Expressive) ──
            item(key = "sec_appearance_header") {
                ExpressiveSectionLabel(stringResource(R.string.section_appearance))
            }
            item(key = "app_theme_mode") {
                ExpressiveSegmentedChoiceCard(
                    position = SegmentPosition.TOP,
                    icon = Icons.Rounded.DarkMode,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.appearance_theme_mode),
                    subtitle =
                        when (state.themeMode) {
                            "dark" -> stringResource(R.string.theme_desc_dark)
                            "light" -> stringResource(R.string.theme_desc_light)
                            else -> stringResource(R.string.theme_desc_auto)
                        },
                    options =
                        listOf(
                            "system" to stringResource(R.string.theme_auto),
                            "light" to stringResource(R.string.theme_light),
                            "dark" to stringResource(R.string.theme_dark),
                        ),
                    selected = state.themeMode,
                    onSelect = { state.updateThemeMode(it) },
                )
            }
            item(key = "app_pure_black") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.Contrast,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.appearance_pure_black),
                    subtitle = stringResource(R.string.appearance_pure_black_desc),
                    checked = state.pureBlack,
                    enabled = darkTheme,
                    onCheckedChange = { state.updatePureBlack(it) },
                )
            }
            item(key = "app_palette") {
                ExpressiveSegmentSurface(position = SegmentPosition.MIDDLE) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BubbleIconBadge(icon = Icons.Rounded.Palette, tone = BadgeTone.SECONDARY)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.appearance_accent_color),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text =
                                        if (state.themePalette == BubblePalette.WALLPAPER) {
                                            stringResource(R.string.accent_wallpaper)
                                        } else {
                                            stringResource(R.string.palette_preset_desc)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        BubblePaletteSelector(
                            selected = state.themePalette,
                            onSelect = { state.updateThemePalette(it) },
                        )
                    }
                }
            }
            item(key = "app_language") {
                ExpressiveSegmentedChoiceCard(
                    position = SegmentPosition.BOTTOM,
                    icon = Icons.Rounded.Language,
                    tone = BadgeTone.SECONDARY,
                    title = stringResource(R.string.label_app_language),
                    options =
                        listOf(
                            LocaleHelper.LANG_SYSTEM to stringResource(R.string.app_lang_system),
                            LocaleHelper.LANG_ENGLISH to stringResource(R.string.app_lang_english),
                            LocaleHelper.LANG_INDONESIAN to stringResource(R.string.app_lang_indonesian),
                        ),
                    selected = state.appLanguageCode,
                    onSelect = { state.updateAppLanguage(it) },
                )
            }

            // ── Section 6: Privacy & Security ──
            item(key = "sec_privacy_header") {
                ExpressiveSectionLabel(stringResource(R.string.section_privacy))
            }
            item(key = "privacy_safe_mode") {
                ExpressiveSwitchRow(
                    position = SegmentPosition.TOP,
                    icon = Icons.Rounded.Shield,
                    tone = BadgeTone.TERTIARY,
                    title = stringResource(R.string.switch_safe_mode),
                    subtitle = stringResource(R.string.safe_mode_subtitle),
                    checked = state.safeModeEnabled,
                    onCheckedChange = { state.updateSafeMode(it) },
                )
            }
            item(key = "privacy_excluded") {
                ExpressiveActionRow(
                    position = SegmentPosition.MIDDLE,
                    icon = Icons.Rounded.Security,
                    tone = BadgeTone.TERTIARY,
                    title = stringResource(R.string.btn_excluded_apps_fmt, state.excludedPackages.size),
                    subtitle = stringResource(R.string.privacy_excluded_desc),
                    showChevron = true,
                    onClick = { showExcludedAppsDialog = true },
                )
            }
            item(key = "privacy_clear_cache") {
                ExpressiveActionRow(
                    position = SegmentPosition.BOTTOM,
                    icon = Icons.Rounded.CleaningServices,
                    tone = BadgeTone.NEUTRAL,
                    title = stringResource(R.string.btn_clear_cache),
                    subtitle = stringResource(R.string.privacy_clear_cache_desc),
                    onClick = {
                        PerformanceManager.clearVolatileCaches(context)
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_cache_cleared),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }

            // ── Version Footer ──
            item(key = "version_footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "${stringResource(R.string.app_name)} ${stringResource(R.string.version_label_fmt, BuildConfig.VERSION_NAME)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    pickingSourceLang?.let { isSource ->
        LanguagePickerDialog(
            title =
                stringResource(
                    if (isSource) R.string.dialog_pick_source_lang else R.string.dialog_pick_target_lang,
                ),
            options = if (isSource) SupportedLanguages.SOURCE_LANGUAGES else SupportedLanguages.TARGET_LANGUAGES,
            selectedCode = if (isSource) state.effectiveSourceLanguageCode else state.targetLanguageCode,
            onSelect = { code ->
                if (isSource) state.updateSourceLanguage(code) else state.updateTargetLanguage(code)
                pickingSourceLang = null
            },
            onDismiss = { pickingSourceLang = null },
        )
    }

    if (showExcludedAppsDialog) {
        ExcludedAppsDialog(
            packages = state.excludedPackages,
            onAddPackage = { pkg ->
                state.addExcludedPackage(pkg)
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_pkg_added, pkg),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onRemovePackage = { pkg ->
                state.removeExcludedPackage(pkg)
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_pkg_removed, pkg),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showExcludedAppsDialog = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    title: String,
    options: List<SupportedLanguages.LanguageOption>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(options, key = { it.code }) { option ->
                    val selected = option.code.equals(selectedCode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh.copy(alpha = 0f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelect(option.code) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
    )
}

@Composable
private fun ExcludedAppsDialog(
    packages: List<String>,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var newPkg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.dialog_excluded_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it },
                        placeholder = { Text(stringResource(R.string.hint_package_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            val clean = newPkg.trim()
                            if (clean.isNotEmpty()) {
                                onAddPackage(clean)
                                newPkg = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.btn_add),
                        )
                    }
                }

                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))

                if (packages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialog_excluded_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(packages, key = { it }) { pkg ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onRemovePackage(pkg) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.btn_remove),
                                        tint = colors.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
    )
}
