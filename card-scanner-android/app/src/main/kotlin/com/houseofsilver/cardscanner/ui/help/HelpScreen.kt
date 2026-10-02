package com.houseofsilver.cardscanner.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houseofsilver.cardscanner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Section(title = stringResource(R.string.help_about_title)) {
                Body(stringResource(R.string.help_about_body))
            }

            Section(title = stringResource(R.string.help_how_title)) {
                Step(1, stringResource(R.string.help_step_scan))
                Step(2, stringResource(R.string.help_step_review))
                Step(3, stringResource(R.string.help_step_send))
                Step(4, stringResource(R.string.help_step_history))
                Step(5, stringResource(R.string.help_step_export))
            }

            Section(title = stringResource(R.string.help_template_title)) {
                Body(stringResource(R.string.help_template_body))
                Spacer(Modifier.height(4.dp))
                Placeholder("{first_name}", stringResource(R.string.help_ph_first_name))
                Placeholder("{name}", stringResource(R.string.help_ph_name))
                Placeholder("{role}", stringResource(R.string.help_ph_role))
                Placeholder("{company}", stringResource(R.string.help_ph_company))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.help_privacy_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Bullet(stringResource(R.string.help_privacy_offline))
                    Bullet(stringResource(R.string.help_privacy_ocr))
                    Bullet(stringResource(R.string.help_privacy_storage))
                    Bullet(stringResource(R.string.help_privacy_email))
                    Bullet(stringResource(R.string.help_privacy_delete))
                }
            }

            Text(
                stringResource(R.string.help_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Placeholder(token: String, meaning: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            token,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 104.dp)
        )
        Text(meaning, style = MaterialTheme.typography.bodySmall)
    }
}
