package com.houseofsilver.cardscanner.ui.history

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.houseofsilver.cardscanner.R
import com.houseofsilver.cardscanner.data.local.ScanEntity
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.ScanStatus
import java.text.SimpleDateFormat
import java.util.*

private val ExportIdle = Color(0xFFE07A3F)
private val ExportReady = Color(0xFF7DBE73)
private val ExportOnColor = Color(0xFF14150F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    appModule: AppModule,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(appModule) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val expandedId by vm.expandedId.collectAsStateWithLifecycle()
    val pendingDeleteId by vm.pendingDeleteId.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val exportError by vm.exportError.collectAsStateWithLifecycle()
    var fullscreenImage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (state.scans.isNotEmpty()) {
                ExportBar(
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    error = exportError,
                    onStartSelection = { vm.startSelection() },
                    onCancel = { vm.cancelSelection() },
                    onExport = { vm.exportSelected(context) }
                )
            }
        }
    ) { padding ->
        if (state.scans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_history),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.scans, key = { it.id }) { scan ->
                    HistoryItem(
                        scan = scan,
                        isExpanded = !selectionMode && expandedId == scan.id,
                        selectionMode = selectionMode,
                        isSelected = scan.id in selectedIds,
                        onClick = {
                            if (selectionMode) vm.toggleSelected(scan.id) else vm.toggleExpand(scan.id)
                        },
                        onResend = { vm.resend(context, scan.id) },
                        onDelete = { vm.requestDelete(scan.id) },
                        onOpenImage = { fullscreenImage = it },
                        onOpenLinkedin = { vm.openLinkedin(context, scan.id) }
                    )
                }
            }
        }
    }

    fullscreenImage?.let { uriStr ->
        ZoomableImageDialog(uriStr = uriStr, onDismiss = { fullscreenImage = null })
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text(stringResource(R.string.delete_scan_title)) },
            text = { Text(stringResource(R.string.delete_scan_message)) },
            confirmButton = {
                TextButton(onClick = { vm.confirmDelete() }) {
                    Text(stringResource(R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

/**
 * Fullscreen card viewer with pinch-zoom. Panning is only allowed while zoomed in, and the
 * offset resets when zoom returns to 1x so the image can't be stranded off-screen.
 */
@Composable
private fun ZoomableImageDialog(uriStr: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 6f)
            offset = if (scale > 1f) offset + panChange else Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            AsyncImage(
                model = Uri.parse(uriStr),
                contentDescription = stringResource(R.string.view_card_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ExportBar(
    selectionMode: Boolean,
    selectedCount: Int,
    error: String?,
    onStartSelection: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (selectionMode) {
                Text(
                    stringResource(R.string.export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel_button))
                    }
                }
                val ready = selectedCount > 0
                Button(
                    onClick = { if (!selectionMode) onStartSelection() else if (ready) onExport() },
                    enabled = !selectionMode || ready,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ready) ExportReady else ExportIdle,
                        contentColor = ExportOnColor,
                        disabledContainerColor = ExportIdle.copy(alpha = 0.5f),
                        disabledContentColor = ExportOnColor.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        when {
                            !selectionMode -> stringResource(R.string.export_button)
                            ready -> stringResource(R.string.export_count, selectedCount)
                            else -> stringResource(R.string.export_select_prompt)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    scan: ScanEntity,
    isExpanded: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenLinkedin: () -> Unit
) {
    val dateStr = remember(scan.scannedAt) {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(scan.scannedAt))
    }
    val emailStatus = ScanStatus.valueOf(scan.emailStatus)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                }

                scan.imageUri?.let { uriStr ->
                    Card(
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        AsyncImage(
                            model = Uri.parse(uriStr),
                            contentDescription = "Card thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(scan.contactName, style = MaterialTheme.typography.titleMedium)
                    scan.contactCompany?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(dateStr, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!selectionMode) StatusChip(emailStatus)
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                // Full-width card image sits between the divider and the details, so the
                // scan can actually be read rather than squinted at in the 56dp thumbnail.
                scan.imageUri?.let { uriStr ->
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = Uri.parse(uriStr),
                        contentDescription = stringResource(R.string.view_card_image),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenImage(uriStr) },
                        contentScale = ContentScale.FillWidth
                    )
                }

                Spacer(Modifier.height(12.dp))

                DetailRow("Email", scan.contactEmail)
                scan.contactRole?.let { DetailRow("Role", it) }
                scan.contactPhone?.let { DetailRow("Phone", it) }
                scan.contactWebsite?.let { DetailRow("Website", it) }
                scan.contactLinkedin?.let { DetailRow("LinkedIn", it) }
                scan.contactAddress?.let { DetailRow("Address", it) }
                scan.context?.let { DetailRow("Note", it) }

                scan.emailSubjectSent?.let { subject ->
                    Spacer(Modifier.height(8.dp))
                    Text("Subject: $subject", style = MaterialTheme.typography.labelSmall)
                }
                scan.emailBodySent?.let { body ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        body.take(300) + if (body.length > 300) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Without an address there is no draft to reopen; the profile is the way back in
                    val hasEmail = scan.contactEmail.isNotBlank()
                    OutlinedButton(
                        onClick = if (hasEmail) onResend else onOpenLinkedin,
                        modifier = Modifier.weight(1f),
                        enabled = hasEmail || !scan.contactLinkedin.isNullOrBlank()
                    ) {
                        Text(
                            stringResource(
                                if (hasEmail) R.string.reopen_in_email_app else R.string.open_linkedin
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScanStatus) {
    val (bg, fg, label) = when (status) {
        ScanStatus.SENT -> Triple(Color(0xFFE8F5E9), Color(0xFF388E3C), "Sent")
        ScanStatus.SAVED -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Saved")
        ScanStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "Failed"
        )
        ScanStatus.PENDING -> Triple(Color(0xFFFFF8E1), Color(0xFFF9A825), "Pending")
        ScanStatus.CANCELLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Cancelled"
        )
        ScanStatus.FALLBACK_TO_SELF -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), "Fallback")
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 72.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
