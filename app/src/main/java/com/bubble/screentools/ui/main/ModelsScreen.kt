package com.bubble.screentools.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bubble.screentools.R
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.ui.component.BadgeTone
import com.bubble.screentools.ui.component.BubbleIconBadge
import com.bubble.screentools.ui.component.ExpressiveActionRow
import com.bubble.screentools.ui.component.ExpressiveSectionLabel
import com.bubble.screentools.ui.component.ExpressiveSegmentSurface
import com.bubble.screentools.ui.component.ExpressiveSwitchRow
import com.bubble.screentools.ui.component.SegmentPosition
import kotlinx.coroutines.launch

private fun positionForIndex(index: Int, total: Int): SegmentPosition =
    when {
        total <= 1 -> SegmentPosition.SINGLE
        index == 0 -> SegmentPosition.TOP
        index == total - 1 -> SegmentPosition.BOTTOM
        else -> SegmentPosition.MIDDLE
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    state: BubbleUiState,
    initialTab: Int = 0,
    onImportTrainedData: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    val translationManager = remember { TranslationManager(LanguageDetectionManager()) }
    DisposableEffect(Unit) {
        onDispose { translationManager.release() }
    }

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var searchQuery by remember { mutableStateOf("") }
    var busyCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteTess by remember { mutableStateOf<TesseractOcrManager.InstalledTessModel?>(null) }

    val filteredLanguages =
        remember(searchQuery) {
            SupportedLanguages.TARGET_LANGUAGES.filter {
                searchQuery.isBlank() ||
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
            }
        }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.lang_manager_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.models_downloaded_count_fmt, state.downloadedMlKitModels.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.btn_close),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding() + 6.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(key = "models_tab_selector") {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    val tabs =
                        listOf(
                            0 to stringResource(R.string.tab_models_translation_short),
                            1 to stringResource(R.string.tab_models_ocr_short),
                        )
                    tabs.forEachIndexed { index, (tabIdx, label) ->
                        SegmentedButton(
                            selected = selectedTab == tabIdx,
                            onClick = { selectedTab = tabIdx },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                item(key = "mlkit_search_bar") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lang_manager_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.lang_search_hint_short),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon =
                                if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                        }
                                    }
                                } else {
                                    null
                                },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = colors.surfaceContainerLow,
                                    unfocusedContainerColor = colors.surfaceContainerLow,
                                    unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (filteredLanguages.isEmpty()) {
                    item(key = "mlkit_empty") {
                        Text(
                            text = stringResource(R.string.models_empty_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                        )
                    }
                } else {
                    itemsIndexed(filteredLanguages, key = { _, it -> "lang_${it.code}" }) { idx, lang ->
                        val isDownloaded = state.downloadedMlKitModels.contains(lang.code)
                        val isBusy = busyCodes.contains(lang.code)

                        ExpressiveSegmentSurface(
                            position = positionForIndex(idx, filteredLanguages.size),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BubbleIconBadge(
                                    icon = Icons.Rounded.Translate,
                                    tone = if (isDownloaded) BadgeTone.PRIMARY else BadgeTone.NEUTRAL,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lang.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        text =
                                            when {
                                                isBusy -> stringResource(R.string.status_processing)
                                                isDownloaded -> stringResource(R.string.status_downloaded_offline)
                                                else -> stringResource(R.string.status_not_downloaded_fmt, lang.shortLabel)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded) colors.primary else colors.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                    )
                                } else if (isDownloaded) {
                                    OutlinedButton(
                                        onClick = {
                                            busyCodes = busyCodes + lang.code
                                            scope.launch {
                                                val ok = translationManager.deleteLanguageModel(lang.code)
                                                busyCodes = busyCodes - lang.code
                                                Toast.makeText(
                                                    context,
                                                    if (ok) {
                                                        context.getString(R.string.toast_lang_deleted, lang.displayName)
                                                    } else {
                                                        context.getString(R.string.toast_lang_delete_failed)
                                                    },
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                state.refreshDownloadedModels()
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Text(stringResource(R.string.btn_delete), style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            busyCodes = busyCodes + lang.code
                                            scope.launch {
                                                val ok = translationManager.downloadLanguageModel(lang.code)
                                                busyCodes = busyCodes - lang.code
                                                Toast.makeText(
                                                    context,
                                                    if (ok) {
                                                        context.getString(R.string.toast_lang_downloaded, lang.displayName)
                                                    } else {
                                                        context.getString(R.string.toast_lang_download_failed)
                                                    },
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                state.refreshDownloadedModels()
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.btn_download), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Custom Tesseract 5 OCR Tab
                item(key = "tess_info_row") {
                    ExpressiveActionRow(
                        position = SegmentPosition.TOP,
                        icon = Icons.Rounded.DocumentScanner,
                        tone = if (state.useTesseractOcr) BadgeTone.TERTIARY else BadgeTone.NEUTRAL,
                        title = stringResource(R.string.ocr_card_title),
                        subtitle = stringResource(R.string.ocr_card_desc),
                    )
                }
                item(key = "tess_switch_row") {
                    ExpressiveSwitchRow(
                        position = SegmentPosition.BOTTOM,
                        icon = Icons.Rounded.Tune,
                        tone = BadgeTone.TERTIARY,
                        title = stringResource(R.string.switch_use_tesseract),
                        checked = state.useTesseractOcr,
                        onCheckedChange = { checked ->
                            if (checked && state.installedTessModels.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_need_one_ocr_model),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                state.updateUseTesseractOcr(checked)
                            }
                        },
                    )
                }
                item(key = "tess_import_button") {
                    Button(
                        onClick = onImportTrainedData,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_import_traineddata),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                item(key = "tess_installed_header") {
                    ExpressiveSectionLabel(
                        stringResource(R.string.ocr_installed_header_fmt, state.installedTessModels.size),
                    )
                }

                if (state.installedTessModels.isEmpty()) {
                    item(key = "tess_none") {
                        ExpressiveSegmentSurface(position = SegmentPosition.SINGLE) {
                            Text(
                                text = stringResource(R.string.ocr_none_installed),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    itemsIndexed(state.installedTessModels, key = { _, it -> "inst_${it.code}" }) { idx, model ->
                        val isActive = state.activeTesseractLang == model.code && state.useTesseractOcr
                        val sizeMb = String.format("%.1f MB", model.sizeBytes / (1024.0 * 1024.0))

                        ExpressiveSegmentSurface(
                            position = positionForIndex(idx, state.installedTessModels.size),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BubbleIconBadge(
                                    icon = Icons.Rounded.DocumentScanner,
                                    tone = if (isActive) BadgeTone.TERTIARY else BadgeTone.NEUTRAL,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text =
                                            if (isActive) {
                                                stringResource(R.string.ocr_active_label_fmt, model.code)
                                            } else {
                                                model.code
                                            },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isActive) colors.primary else colors.onSurface,
                                    )
                                    Text(
                                        text = "${model.fileName} • $sizeMb",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                if (!isActive) {
                                    FilledTonalButton(
                                        onClick = {
                                            state.activateTesseractModel(model.code)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_ocr_active_fmt, model.code),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(stringResource(R.string.btn_use), style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }
                                IconButton(onClick = { pendingDeleteTess = model }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.btn_delete),
                                        tint = colors.error,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "tess_quick_header") {
                    ExpressiveSectionLabel(stringResource(R.string.ocr_quick_download_header))
                }

                val installedCodes = state.installedTessModels.map { it.code }.toSet()
                val quickList = TesseractOcrManager.QUICK_DOWNLOAD_MODELS
                itemsIndexed(quickList, key = { _, it -> "quick_${it.code}" }) { idx, item ->
                    val already = installedCodes.contains(item.code)
                    val busyKey = "tess_${item.code}"
                    val isBusy = busyCodes.contains(busyKey)

                    ExpressiveSegmentSurface(
                        position = positionForIndex(idx, quickList.size),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                            } else if (already) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.secondaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.onSecondaryContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.btn_installed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSecondaryContainer,
                                        )
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        busyCodes = busyCodes + busyKey
                                        scope.launch {
                                            val res = TesseractOcrManager.downloadOfficialModel(context, item.code)
                                            busyCodes = busyCodes - busyKey
                                            res.onSuccess { code ->
                                                if (state.activeTesseractLang.isBlank()) {
                                                    state.activateTesseractModel(code)
                                                } else {
                                                    state.syncPreferencesAndStatus()
                                                }
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_ocr_download_ok, code),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    context,
                                                    err.message ?: context.getString(R.string.toast_lang_download_failed),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(stringResource(R.string.btn_download), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "models_bottom_spacer") {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    pendingDeleteTess?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTess = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.dialog_delete_ocr_title)) },
            text = { Text(stringResource(R.string.dialog_delete_ocr_msg, model.fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.deleteTesseractModel(model.code)
                        pendingDeleteTess = null
                    },
                ) {
                    Text(stringResource(R.string.btn_delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTess = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}
