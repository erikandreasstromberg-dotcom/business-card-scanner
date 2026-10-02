package com.houseofsilver.cardscanner.ui.confirmation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseofsilver.cardscanner.R
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.ScanStatus
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationScreen(
    scanId: String,
    appModule: AppModule,
    onScanNext: () -> Unit,
    onViewHistory: () -> Unit
) {
    val scanFlow = remember(scanId) { appModule.scanDao.getScanById(scanId) }
    val scan by scanFlow.filterNotNull().collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.confirmation_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            scan?.let { entity ->
                Spacer(Modifier.height(16.dp))

                Text(
                    entity.contactName,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    entity.contactEmail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                val emailHandedOff = entity.emailStatus == ScanStatus.SENT.name
                val savedOnly = entity.emailStatus == ScanStatus.SAVED.name

                StatusPill(
                    label = stringResource(
                        if (savedOnly) R.string.contact_saved else R.string.email_opened
                    ),
                    success = emailHandedOff || savedOnly
                )
                Text(
                    stringResource(
                        if (savedOnly) R.string.contact_saved_hint else R.string.email_handoff_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } ?: run {
                CircularProgressIndicator()
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onScanNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scan_next))
            }

            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.view_history))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    success: Boolean
) {
    val (bg, fg, icon) = if (success) {
        Triple(Color(0xFFE8F5E9), Color(0xFF388E3C), Icons.Default.CheckCircle)
    } else {
        Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            Icons.Default.HourglassTop
        )
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg)
            Text(label, color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
